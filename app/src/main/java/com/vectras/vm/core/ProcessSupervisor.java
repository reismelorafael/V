package com.vectras.vm.core;

import android.content.Context;
import android.os.SystemClock;

import com.vectras.qemu.utils.QmpClient;
import com.vectras.vm.audit.AuditEvent;
import com.vectras.vm.audit.AuditLedger;

import java.util.concurrent.TimeUnit;

public class ProcessSupervisor {
    public enum State {
        START,
        VERIFY,
        RUN,
        DEGRADED,
        FAILOVER,
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

    public synchronized void bindProcess(Process process) {
        this.process = process;
        this.startMonoMs = SystemClock.elapsedRealtime();
        this.startWallMs = System.currentTimeMillis();
        transition(State.START, State.VERIFY, "process_bound", 0, 0, 0, "bind");
        transition(State.VERIFY, State.RUN, "verified", 0, 0, 0, "run");
    }

    public void onDegraded(int droppedLogs, long bytes) {
        transition(state, State.DEGRADED, "log_flood", droppedLogs, bytes, 0, "degrade_logs");
    }

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
