package com.vectras.vm.core;

import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Operações runtime de processo centralizadas para reduzir redundância,
 * manter compatibilidade entre toolchains e preservar comportamento determinístico.
 */
public final class ProcessRuntimeOps {

    private static final long DESTROY_GRACE_PERIOD_MS = 250L;

    public enum ExecutionCategory {
        INTERACTIVE(3_000L, TimeUnit.MILLISECONDS),
        NON_INTERACTIVE(1_500L, TimeUnit.MILLISECONDS),
        SETUP_EXTRACTION(120L, TimeUnit.SECONDS),
        QUICK_QUERY(700L, TimeUnit.MILLISECONDS);

        private final long timeout;
        private final TimeUnit timeUnit;

        ExecutionCategory(long timeout, TimeUnit timeUnit) {
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }

        public long timeout() {
            return timeout;
        }

        public TimeUnit timeUnit() {
            return timeUnit;
        }
    }

    public static final class TimeoutExecutionResult {
        public enum Status {
            SUCCESS,
            ERROR,
            TIMEOUT
        }

        public final Status status;
        public final int exitCode;
        public final boolean timedOut;
        public final String message;

        private TimeoutExecutionResult(Status status, int exitCode, boolean timedOut, String message) {
            this.status = status;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.message = message;
        }

        public static TimeoutExecutionResult success(int exitCode, String message) {
            return new TimeoutExecutionResult(Status.SUCCESS, exitCode, false, message);
        }

        public static TimeoutExecutionResult timeout(int exitCode, String message) {
            return new TimeoutExecutionResult(Status.TIMEOUT, exitCode, true, message);
        }

        public static TimeoutExecutionResult failed(String message) {
            return new TimeoutExecutionResult(Status.ERROR, -1, false, message);
        }
    }

    public static final class TrackedProcess {
        private final ProcessBudgetRegistry.SlotToken slot;
        public final Process process;
        public final String feature;
        public final String tag;
        public final String caller;

        private TrackedProcess(ProcessBudgetRegistry.SlotToken slot,
                               Process process,
                               String feature,
                               String tag,
                               String caller) {
            this.slot = slot;
            this.process = process;
            this.feature = feature;
            this.tag = tag;
            this.caller = caller;
        }
    }

    private ProcessRuntimeOps() {
        throw new AssertionError("ProcessRuntimeOps is utility-only");
    }

    public static long monoMs() {
        return SystemClock.elapsedRealtime();
    }

    public static TrackedProcess launchTrackedProcess(List<String> command,
                                                      String feature,
                                                      String tag,
                                                      String caller) throws java.io.IOException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command_missing");
        }
        ProcessBudgetRegistry.SlotToken slot = ProcessBudgetRegistry.tryAcquireSlot(
                feature,
                tag,
                caller,
                "system"
        );
        if (slot == null) {
            throw new java.io.IOException("process_budget_exhausted");
        }
        try {
            Process process = new ProcessBuilder(command).start();
            ProcessBudgetRegistry.bindProcess(slot, process);
            return new TrackedProcess(slot, process, feature, tag, caller);
        } catch (java.io.IOException | RuntimeException e) {
            ProcessBudgetRegistry.releaseSlot(slot, "launch_failed");
            throw e;
        }
    }

    public static void releaseTrackedProcess(TrackedProcess trackedProcess, String reason) {
        if (trackedProcess == null) {
            return;
        }
        ProcessBudgetRegistry.releaseSlot(trackedProcess.slot, reason);
    }

    public static long wallMs() {
        return System.currentTimeMillis();
    }

    public static long safePid(Process process) {
        if (process == null) return -1L;
        try {
            Method method = Process.class.getMethod("pid");
            Object value = method.invoke(process);
            if (value instanceof Long) {
                long pid = (Long) value;
                if (pid > 0L) {
                    return pid;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            try {
                long reflectedPid = field.getLong(process);
                if (reflectedPid > 0L) {
                    return reflectedPid;
                }
            } finally {
                field.setAccessible(false);
            }
        } catch (Exception ignored) {
        }

        return -1L;
    }

    public static boolean isQmpAck(String result) {
        return result != null && result.contains("return");
    }

    public static boolean isLikelyInteractiveCommand(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase();
        return normalized.isEmpty()
                || "bash".equals(normalized)
                || "sh".equals(normalized)
                || normalized.startsWith("top")
                || normalized.startsWith("vi")
                || normalized.startsWith("vim")
                || normalized.startsWith("less")
                || normalized.startsWith("more");
    }

    public static TimeoutExecutionResult waitForWithCategory(Process process, ExecutionCategory category) {
        return waitForByCategory(process, category);
    }

    public static TimeoutExecutionResult waitForByCategory(Process process, ExecutionCategory category) {
        if (process == null) {
            return TimeoutExecutionResult.failed("process_missing");
        }
        if (category == null) {
            return TimeoutExecutionResult.failed("category_missing");
        }
        return waitForWithTimeout(process, category.timeout(), category.timeUnit(), category.name().toLowerCase());
    }

    public static TimeoutExecutionResult waitForWithTimeout(Process process,
                                                            long timeout,
                                                            TimeUnit timeUnit,
                                                            String label) {
        if (process == null) {
            return TimeoutExecutionResult.failed("process_missing");
        }
        if (timeUnit == null) {
            return TimeoutExecutionResult.failed("time_unit_missing");
        }
        try {
            if (!process.waitFor(Math.max(0L, timeout), timeUnit)) {
                return terminateAfterTimeout(process, label);
            }
            return TimeoutExecutionResult.success(process.exitValue(), label + "_exit");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TimeoutExecutionResult.failed(label + "_interrupted");
        } catch (IllegalThreadStateException e) {
            return TimeoutExecutionResult.failed(label + "_not_terminated");
        }
    }

    public static boolean stopProcessWithTimeout(Process process,
                                                 long gracefulTimeoutMs,
                                                 long forcedTimeoutMs) {
        if (process == null || !process.isAlive()) {
            return true;
        }
        process.destroy();
        if (waitForExit(process, Math.max(1L, gracefulTimeoutMs), TimeUnit.MILLISECONDS) != Integer.MIN_VALUE) {
            return true;
        }
        process.destroyForcibly();
        return waitForExit(process, Math.max(1L, forcedTimeoutMs), TimeUnit.MILLISECONDS) != Integer.MIN_VALUE;
    }

    private static TimeoutExecutionResult terminateAfterTimeout(Process process, String label) {
        process.destroy();
        int exitCodeAfterDestroy = waitForExit(process, DESTROY_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);
        if (exitCodeAfterDestroy != Integer.MIN_VALUE) {
            return TimeoutExecutionResult.timeout(exitCodeAfterDestroy, label + "_timeout_destroy");
        }

        process.destroyForcibly();
        int exitCodeAfterForce = waitForExit(process, DESTROY_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);
        if (exitCodeAfterForce != Integer.MIN_VALUE) {
            return TimeoutExecutionResult.timeout(exitCodeAfterForce, label + "_timeout_forced");
        }

        return TimeoutExecutionResult.timeout(-1, label + "_timeout_kill_pending");
    }

    private static int waitForExit(Process process, long timeout, TimeUnit unit) {
        try {
            if (!process.waitFor(Math.max(0L, timeout), unit)) {
                return Integer.MIN_VALUE;
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Integer.MIN_VALUE;
        } catch (IllegalThreadStateException e) {
            return Integer.MIN_VALUE;
        }
    }
}
