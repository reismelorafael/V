package com.vectras.vm.core;

import android.content.Context;
import android.os.SystemClock;

import com.vectras.qemu.utils.QmpClient;
import com.vectras.vm.audit.AuditEvent;
import com.vectras.vm.audit.AuditLedger;

import java.util.concurrent.TimeUnit;

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
    private volatile Process process;
    private volatile State state = State.START;
    private volatile long startWallMs;
    private volatile long startMonoMs;

    public ProcessSupervisor(Context context, String vmId) {
        this.context = context;
        this.vmId = vmId == null ? "unknown" : vmId;
    }

    /**
     * Vincula um processo ao supervisor e realiza as transições iniciais.
     *
     * @param process processo principal da VM
     */
    public synchronized void bindProcess(Process process) {
        this.process = process;
        this.startMonoMs = SystemClock.elapsedRealtime();
        this.startWallMs = System.currentTimeMillis();
        transition(State.START, State.VERIFY, "process_bound", 0, 0, 0, "bind");
        transition(State.VERIFY, State.RUN, "verified", 0, 0, 0, "run");
    }

    /**
     * Marca o supervisor em estado degradado por pressão de logs.
     *
     * @param droppedLogs quantidade de linhas descartadas por backpressure
     * @param bytes bytes envolvidos no período degradado
     */
    public void onDegraded(int droppedLogs, long bytes) {
        transition(state, State.DEGRADED, "log_flood", droppedLogs, bytes, 0, "degrade_logs");
    }

    /**
     * Para o processo com estratégia escalonada e deterministicamente auditável.
     *
     * @param tryQmp quando true, tenta desligamento limpo via QMP antes de TERM/KILL
     * @return true se o processo foi finalizado durante a janela de timeout
     */
    public synchronized boolean stopGracefully(boolean tryQmp) {
        if (process == null) {
            transition(state, State.STOP, "missing_process", 0, 0, 0, "no_op");
            return true;
        }

        long stallMs = Math.max(0L, SystemClock.elapsedRealtime() - startMonoMs);
        boolean qmpRequested = false;
        if (tryQmp) {
            qmpRequested = true;
            String result = QmpClient.sendCommand("{ \"execute\": \"system_powerdown\" }");
            if (result != null && result.contains("return")) {
                if (awaitExit(3_000)) {
                    transition(state, State.STOP, "qmp_shutdown", 0, 0, stallMs, "qmp");
                    return true;
                }
            }
        }

        transition(state, State.FAILOVER, qmpRequested ? "qmp_timeout" : "no_qmp", 0, 0, stallMs, "term_kill");
        process.destroy();
        if (awaitExit(3_000)) {
            transition(State.FAILOVER, State.STOP, "term_success", 0, 0, stallMs, "term");
            return true;
        }

        process.destroyForcibly();
        boolean killed = awaitExit(2_000);
        transition(State.FAILOVER, State.STOP, killed ? "kill_success" : "kill_timeout", 0, 0, stallMs, "kill");
        return killed;
    }

    private boolean awaitExit(long timeoutMs) {
        try {
            return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void transition(State from,
                            State to,
                            String cause,
                            int droppedLogs,
                            long bytes,
                            long stallMs,
                            String action) {
        this.state = to;
        AuditLedger.record(context, new AuditEvent(
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis(),
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

    public long getPid() {
        if (process == null) return -1L;
        try {
            return process.pid();
        } catch (UnsupportedOperationException ex) {
            return -1L;
        }
    }

    public State getState() {
        return state;
    }
}
