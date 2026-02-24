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
    private static final double QMP_EXECUTION_FAILURE_LOG_REFILL_PER_SEC = 0.1d;
    private static final int QMP_EXECUTION_FAILURE_LOG_BURST = 2;
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
    private final TokenBucketRateLimiter qmpExecutionFailureLogLimiter =
            new TokenBucketRateLimiter(QMP_EXECUTION_FAILURE_LOG_REFILL_PER_SEC, QMP_EXECUTION_FAILURE_LOG_BURST);
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
            if (currentAlive && process.isAlive() && currentPid <= 0L && incomingPid <= 0L) {
                // Android 14/15 pode retornar PID indisponível (-1) em reentrâncias do ciclo de vida.
                // Tratamos bind duplicado com PID desconhecido como idempotente para evitar crash.
                Log.w(TAG, "bindProcess: PID unavailable; treating duplicate bind as idempotent for vmId=" + vmId);
                return;
            }

            if (!currentAlive) {
                // Supervisor ainda estava com referência antiga, mas o processo já encerrou.
                // Permite rebind para evitar crash por corrida de ciclo de vida.
                this.process = null;
                this.state = State.START;
            }

            if (this.process != null) {
                StringBuilder warn = new StringBuilder("bindProcess: replacing already bound process vmId=")
                        .append(vmId)
                        .append(" state=")
                        .append(state)
                        .append(" currentAlive=")
                        .append(currentAlive)
                        .append(" incomingAlive=")
                        .append(process.isAlive());
                if (currentPid > 0L) {
                    warn.append(" currentPid=").append(currentPid);
                }
                if (incomingPid > 0L) {
                    warn.append(" incomingPid=").append(incomingPid);
                }
                Log.w(TAG, warn.toString());
                Process previousProcess = this.process;
                boolean stopped = stopGracefully(false);
                if (!stopped && previousProcess != null && previousProcess.isAlive()) {
                    Log.w(TAG, "bindProcess: previous process did not stop before rebind vmId=" + vmId);
                }
                this.state = State.START;
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
    public boolean stopGracefully(boolean tryQmp) {
        Process running;
        State currentState;
        long stallMs;
        synchronized (this) {
            running = process;
            currentState = state;
            stallMs = Math.max(0L, clock.monoMs() - startMonoMs);
            if (running == null) {
                transition(currentState, State.STOP, "missing_process", 0, 0, 0, "no_op");
                return true;
            }
        }

        boolean stopped = false;
        boolean qmpRequested = false;
        String qmpFailoverCause = "qmp_reject";
        try {
            if (tryQmp) {
                qmpRequested = true;
                QmpAttemptResult attemptResult = sendPowerdownWithTimeout(EXECUTORS.qmpGraceTimeoutMs());
                qmpFailoverCause = attemptResult.failoverCause();
                if (attemptResult.isAck() && awaitExit(running, 3_000)) {
                    synchronized (this) {
                        transition(state, State.STOP, "qmp_shutdown", 0, 0, stallMs, "qmp");
                    }
                    stopped = true;
                    return true;
                }
            }

            synchronized (this) {
                transition(state, State.FAILOVER, qmpRequested ? qmpFailoverCause : "no_qmp", 0, 0, stallMs, "term_kill");
            }
            running.destroy();
            if (awaitExit(running, 3_000)) {
                synchronized (this) {
                    transition(State.FAILOVER, State.STOP, "term_success", 0, 0, stallMs, "term");
                }
                stopped = true;
                return true;
            }

            running.destroyForcibly();
            boolean killed = awaitExit(running, 2_000);
            synchronized (this) {
                transition(State.FAILOVER, State.STOP, killed ? "kill_success" : "kill_timeout", 0, 0, stallMs, "kill");
            }
            stopped = killed;
            return killed;
        } finally {
            synchronized (this) {
                if (stopped || (process != null && !process.isAlive())) {
                    process = null;
                }
            }
        }
    }

    private QmpAttemptResult sendPowerdownWithTimeout(long timeoutMs) {
        Future<String> future = EXECUTORS.submitProcessSupervisorQmp(new Callable<String>() {
            @Override
            public String call() {
                return qmpTransport.sendPowerdown();
            }
        });
        try {
            return QmpAttemptResult.fromAck(ProcessRuntimeOps.isQmpAck(future.get(timeoutMs, TimeUnit.MILLISECONDS)));
        } catch (TimeoutException e) {
            future.cancel(true);
            return QmpAttemptResult.timeout();
        } catch (ExecutionException e) {
            Throwable rootCause = e.getCause();
            if (qmpExecutionFailureLogLimiter.tryAcquire()) {
                Log.w(TAG, "sendPowerdownWithTimeout execution failure vmId=" + vmId
                        + " cause=" + (rootCause == null ? "unknown" : rootCause.getClass().getSimpleName()),
                        rootCause == null ? e : rootCause);
            }
            return QmpAttemptResult.executionFailure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return QmpAttemptResult.interrupted();
        }
    }

    private static final class QmpAttemptResult {
        enum Status {
            ACK,
            REJECT,
            TIMEOUT,
            EXECUTION_FAILURE,
            INTERRUPTED
        }

        private final Status status;

        private QmpAttemptResult(Status status) {
            this.status = status;
        }

        static QmpAttemptResult fromAck(boolean ack) {
            return new QmpAttemptResult(ack ? Status.ACK : Status.REJECT);
        }

        static QmpAttemptResult timeout() {
            return new QmpAttemptResult(Status.TIMEOUT);
        }

        static QmpAttemptResult executionFailure() {
            return new QmpAttemptResult(Status.EXECUTION_FAILURE);
        }

        static QmpAttemptResult interrupted() {
            return new QmpAttemptResult(Status.INTERRUPTED);
        }

        boolean isAck() {
            return status == Status.ACK;
        }

        String failoverCause() {
            if (status == Status.TIMEOUT) {
                return "qmp_timeout";
            }
            if (status == Status.EXECUTION_FAILURE) {
                return "qmp_exec_failure";
            }
            if (status == Status.INTERRUPTED) {
                return "qmp_interrupted";
            }
            return "qmp_reject";
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
        return CoreExecutionBudgetPolicy.defaults().processSupervisorQmp().maxThreads;
    }

    public static ExecutionExecutors.Snapshot getQmpExecutorSnapshot() {
        return ExecutionExecutors.get().processSupervisorQmpSnapshot();
    }

    static boolean isQmpExecutorCallerRunsPolicyForTests() {
        return false;
    }
}
