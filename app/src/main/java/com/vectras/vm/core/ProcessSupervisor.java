package com.vectras.vm.core;

import android.content.Context;
import android.util.Log;
import com.vectras.qemu.utils.QmpClient;
import com.vectras.vm.audit.AuditEvent;
import com.vectras.vm.audit.AuditLedger;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Supervisiona o processo principal da VM com uma máquina de estados determinística.
 *
 * <p>Fluxo nominal: {@code START -> VERIFY -> RUN -> STOP}.
 * Em cenários de degradação/falha, o fluxo avança por {@code DEGRADED} e
 * {@code FAILOVER}, mantendo trilha auditável em {@link AuditLedger} para
 * análise operacional e pós-morte.</p>
 *
 * <p>A política de parada é escalonada: tenta QMP (quando habilitado),
 * depois {@code destroy()} (TERM), e por fim {@code destroyForcibly()} (KILL),
 * sempre com timeout explícito para preservar previsibilidade.</p>
 */
public class ProcessSupervisor {
    private static final String TAG = "ProcessSupervisor";
    interface QmpTransport {
        String sendPowerdown();
    }

    interface TransitionSink {
        void onTransition(State from, State to, String cause, String action, long stallMs, int droppedLogs, long bytes);
    }

    interface Clock {
        long monoMs();
        long wallMs();
    }


    private static final QmpTransport DEFAULT_QMP_TRANSPORT =
            () -> QmpClient.sendCommandForStopPath("{ \"execute\": \"system_powerdown\" }");

    private static final ExecutionExecutors EXECUTORS = ExecutionExecutors.get();

    private static final TransitionSink NOOP_TRANSITION_SINK =
            (from, to, cause, action, stallMs, droppedLogs, bytes) -> {
            };

    private static final Clock SYSTEM_CLOCK = new Clock() {
        @Override
        public long monoMs() {
            return ProcessRuntimeOps.monoMs();
        }

        @Override
        public long wallMs() {
            return ProcessRuntimeOps.wallMs();
        }
    };

    /** Estados operacionais suportados pelo supervisor. */
    public enum State {
        /** Supervisor criado e ainda sem processo vinculado. */
        START,
        /** Processo vinculado e em verificação inicial. */
        VERIFY,
        /** Execução estável do processo da VM. */
        RUN,
        /** Execução degradada por flood/pressão de saída. */
        DEGRADED,
        /** Sequência de fallback de parada em progresso. */
        FAILOVER,
        /** Processo finalizado com sucesso, timeout ou ausência. */
        STOP
    }

    private final Context context;
    private final String vmId;
    private final QmpTransport qmpTransport;
    private final TransitionSink transitionSink;
    private final Clock clock;
    private volatile Process process;
    private volatile State state = State.START;
    private volatile long startWallMs;
    private volatile long startMonoMs;

    public ProcessSupervisor(Context context, String vmId) {
        this(context, vmId, DEFAULT_QMP_TRANSPORT, NOOP_TRANSITION_SINK, SYSTEM_CLOCK);
    }

    ProcessSupervisor(Context context,
                      String vmId,
                      QmpTransport qmpTransport,
                      TransitionSink transitionSink,
                      Clock clock) {
        this.context = context;
        this.vmId = vmId == null ? "unknown" : vmId;
        this.qmpTransport = qmpTransport;
        this.transitionSink = transitionSink;
        this.clock = clock;
    }

    /**
     * Vincula um processo ao supervisor e realiza as transições iniciais.
     *
     * @param process processo principal da VM
     */
    public synchronized void bindProcess(Process process) {
        if (process == null) {
            throw new IllegalArgumentException("process == null");
        }
        if (this.process == process) {
            // Idempotência para callbacks duplicados no ciclo de vida Android.
            return;
        }
        if (this.process != null) {
            long currentPid = ProcessRuntimeOps.safePid(this.process);
            if (currentPid <= 0L) {
                Log.w(TAG, "bindProcess: current bound PID unavailable for vmId=" + vmId);
            }
            long incomingPid = ProcessRuntimeOps.safePid(process);
            if (incomingPid <= 0L) {
                Log.w(TAG, "bindProcess: incoming PID unavailable for vmId=" + vmId);
            }
            boolean currentAlive = this.process.isAlive();
            if (currentPid > 0L && incomingPid > 0L && currentPid == incomingPid && currentAlive) {
                // Em algumas reentrâncias Android, o processo pode ser reenvelopado
                // em outra instância Java para o mesmo PID; tratamos como idempotente.
                return;
            }

            if (!currentAlive) {
                // Supervisor ainda estava com referência antiga, mas o processo já encerrou.
                // Permite rebind para evitar crash por corrida de ciclo de vida.
                this.process = null;
                this.state = State.START;
            }

            if (this.process != null) {
                throw new IllegalStateException("process already bound");
            }
        }

        try {
            this.process = process;
            this.startMonoMs = clock.monoMs();
            this.startWallMs = clock.wallMs();
            transition(State.START, State.VERIFY, "process_bound", 0, 0, 0, "bind");
            transition(State.VERIFY, State.RUN, "verified", 0, 0, 0, "run");
        } catch (RuntimeException e) {
            this.process = null;
            this.state = State.START;
            throw e;
        }
    }

    /**
     * Marca o supervisor em estado degradado por pressão de logs.
     *
     * @param droppedLogs quantidade de linhas descartadas por backpressure
     * @param bytes bytes envolvidos no período degradado
     */
    public synchronized void onDegraded(int droppedLogs, long bytes) {
        transition(state, State.DEGRADED, "log_flood", droppedLogs, bytes, 0, "degrade_logs");
    }

    /**
     * Para o processo com estratégia escalonada e deterministicamente auditável.
     *
     * @param tryQmp quando true, tenta desligamento limpo via QMP antes de TERM/KILL
     * @return true se o processo foi finalizado durante a janela de timeout
     */
    public synchronized boolean stopGracefully(boolean tryQmp) {
        Process running = process;
        if (running == null) {
            transition(state, State.STOP, "missing_process", 0, 0, 0, "no_op");
            return true;
        }

        try {
            long stallMs = Math.max(0L, clock.monoMs() - startMonoMs);
            boolean qmpRequested = false;
            boolean qmpTimedOut = false;
            if (tryQmp) {
                qmpRequested = true;
                String result = sendPowerdownWithTimeout(EXECUTORS.qmpGraceTimeoutMs());
                qmpTimedOut = result == null;
                if (ProcessRuntimeOps.isQmpAck(result) && awaitExit(running, 3_000)) {
                    transition(state, State.STOP, "qmp_shutdown", 0, 0, stallMs, "qmp");
                    return true;
                }
            }

            transition(state, State.FAILOVER, qmpRequested ? (qmpTimedOut ? "qmp_timeout" : "qmp_reject") : "no_qmp", 0, 0, stallMs, "term_kill");
            running.destroy();
            if (awaitExit(running, 3_000)) {
                transition(State.FAILOVER, State.STOP, "term_success", 0, 0, stallMs, "term");
                return true;
            }

            running.destroyForcibly();
            boolean killed = awaitExit(running, 2_000);
            transition(State.FAILOVER, State.STOP, killed ? "kill_success" : "kill_timeout", 0, 0, stallMs, "kill");
            return killed;
        } finally {
            this.process = null;
        }
    }

    private String sendPowerdownWithTimeout(long timeoutMs) {
        Future<String> future = EXECUTORS.submitProcessSupervisorQmp(new Callable<String>() {
            @Override
            public String call() {
                return qmpTransport.sendPowerdown();
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return null;
        } catch (ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        }
    }

    private boolean awaitExit(Process target, long timeoutMs) {
        if (target == null) {
            return false;
        }
        try {
            return target.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private synchronized void transition(State from,
                            State to,
                            String cause,
                            int droppedLogs,
                            long bytes,
                            long stallMs,
                            String action) {
        this.state = to;
        transitionSink.onTransition(from, to, cause, action, stallMs, droppedLogs, bytes);
        AuditLedger.record(context, new AuditEvent(
                clock.monoMs(),
                clock.wallMs(),
                vmId,
                from.name(),
                to.name(),
                cause,
                droppedLogs,
                bytes,
                stallMs,
                action
        ));
    }

    public synchronized boolean isBoundTo(Process candidate) {
        return candidate != null && process == candidate;
    }

    public long getPid() {
        long pid = ProcessRuntimeOps.safePid(process);
        if (process != null && pid <= 0L) {
            Log.w(TAG, "getPid: PID unavailable for vmId=" + vmId + " state=" + state);
        }
        return pid;
    }

    public boolean isProcessAlive() {
        Process running = process;
        return running != null && running.isAlive();
    }

    public long getStartMonoMs() {
        return startMonoMs;
    }

    public State getState() {
        return state;
    }

    public static ExecutionExecutors.Snapshot observabilitySnapshot() {
        return EXECUTORS.snapshot(ExecutionExecutors.Domain.PROCESS_SUPERVISOR_QMP);
    }

    static ExecutionExecutors.Snapshot getQmpExecutorSnapshotForTests() {
        return EXECUTORS.snapshot(ExecutionExecutors.Domain.PROCESS_SUPERVISOR_QMP);
    }

    static int getQmpExecutorMaxThreadsForTests() {
        return ExecutionBudgetPolicy.defaults().processSupervisorQmp().maxThreads;
    }

    public static ExecutionExecutors.DomainSnapshot getQmpExecutorSnapshot() {
        return ExecutionExecutors.get().processSupervisorQmpSnapshot();
    }

    static boolean isQmpExecutorCallerRunsPolicyForTests() {
        return false;
    }
}
