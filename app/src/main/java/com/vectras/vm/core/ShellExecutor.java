package com.vectras.vm.core;

import android.util.Log;

import com.vectras.vm.logger.VectrasStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShellExecutor {
    private static final String TAG = "ShellExecutor";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final int OUTPUT_MAX_LINES = 512;
    private static final int OUTPUT_MAX_BYTES = 256 * 1024;
    private final ThreadPoolExecutor executorService;
    private final boolean ownsExecutorService;
    private volatile Process shellExecutorProcess;
    private volatile Future<?> processFuture;

    public static class ExecResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;

        public ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }
    }

    public ShellExecutor() {
        this(ExecutionExecutors.get().newShellExecutorPool());
    }

    ShellExecutor(ThreadPoolExecutor executorService) {
        this(executorService, true);
    }

    private ShellExecutor(ThreadPoolExecutor executorService, boolean ownsExecutorService) {
        this.executorService = executorService;
        this.ownsExecutorService = ownsExecutorService;
    }

    public void exec(String command) {
        // Fire-and-forget path must not recursively submit into the same executor.
        // Submitting execute() would enqueue CallableExec again and can deadlock under saturation.
        processFuture = executorService.submit(new CallableExec(command, DEFAULT_TIMEOUT_MS));
    }

    public ExecResult execute(String command, long timeoutMs) {
        long effectiveTimeoutMs = timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
        CallableExec callable = new CallableExec(command, effectiveTimeoutMs);
        if (Thread.currentThread().getName().startsWith("shell-executor-")) {
            // Avoid recursive submission to the same pool (can self-block when queue is saturated).
            callable.run();
            try {
                return callable.await();
            } catch (TimeoutException e) {
                callable.cancel();
                Log.e(TAG, "exec timeout", e);
                VectrasStatus.logInfo(TAG + " > " + e);
                return new ExecResult(-1, "", "timeout", true);
            } catch (Exception e) {
                Log.e(TAG, "exec failed", e);
                VectrasStatus.logInfo(TAG + " > " + e);
                return new ExecResult(-1, "", e.toString(), false);
            }
        }
        Future<?> localFuture = executorService.submit(callable);
        processFuture = localFuture;
        try {
            return callable.await();
        } catch (TimeoutException e) {
            localFuture.cancel(true);
            callable.cancel();
            Log.e(TAG, "exec timeout", e);
            VectrasStatus.logInfo(TAG + " > " + e);
            return new ExecResult(-1, "", "timeout", true);
        } catch (Exception e) {
            localFuture.cancel(true);
            Log.e(TAG, "exec failed", e);
            VectrasStatus.logInfo(TAG + " > " + e);
            return new ExecResult(-1, "", e.toString(), false);
        }
    }

    public void cancel() {
        Process process = shellExecutorProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Future<?> future = processFuture;
        if (future != null) {
            future.cancel(true);
        }
    }

    public void shutdown() {
        cancel();
        if (ownsExecutorService) {
            executorService.shutdownNow();
        }
    }

    int getPoolSizeLimitForTests() {
        return executorService.getMaximumPoolSize();
    }

    int getQueueCapacityForTests() {
        return executorService.getQueue().remainingCapacity() + executorService.getQueue().size();
    }

    int getCreatedThreadCountForTests() {
        return (int) ExecutionExecutors.snapshotOf(executorService, "shell-executor").createdThreads;
    }

    public ExecutionExecutors.Snapshot observabilitySnapshot() {
        return ExecutionExecutors.snapshotOf(executorService, "shell-executor");
    }

    private class CallableExec implements Runnable {
        private final String command;
        private final long timeoutMs;
        private volatile ExecResult result;
        private volatile Exception error;
        private final Object monitor = new Object();

        CallableExec(String command, long timeoutMs) {
            this.command = command;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public void run() {
            String shellPath = "/system/bin/sh";
            BoundedStringRingBuffer outBuffer = new BoundedStringRingBuffer(OUTPUT_MAX_LINES, OUTPUT_MAX_BYTES);
            BoundedStringRingBuffer errBuffer = new BoundedStringRingBuffer(OUTPUT_MAX_LINES, OUTPUT_MAX_BYTES);
            ProcessOutputDrainer drainer = new ProcessOutputDrainer();
            int exitCode = -1;
            boolean timedOut = false;
            Process localProcess = null;

            try {
                localProcess = new ProcessBuilder(shellPath, "-c", command).start();
                shellExecutorProcess = localProcess;
                // Command is passed as a distinct ProcessBuilder arg; stdin is not used for command injection.
                try (OutputStream outputStream = localProcess.getOutputStream()) {
                    outputStream.close();
                }

                Process finalProcess = localProcess;
                // IMPORTANT: drainerFuture MUST NOT use the shell executor pool.
                // If 2 execute() calls run concurrently, both shell-executor threads are
                // occupied in CallableExec.run(); submitting drainer to the same pool
                // would fill the queue but never execute → deadlock.
                // Fix: spawn a dedicated daemon thread for draining (low-cost, bounded lifetime).
                Thread drainThread = new Thread(() -> {
                    try {
                        drainer.drain(finalProcess, (stream, line) -> {
                            if ("stderr".equals(stream)) {
                                errBuffer.addLine(line);
                            } else {
                                outBuffer.addLine(line);
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "shell-drainer");
                drainThread.setDaemon(true);
                drainThread.start();
                // Wrap in a future-compatible handle using drainer.shutdown() for cancellation.
                @SuppressWarnings("UnnecessaryLocalVariable")
                final Thread drainThreadRef = drainThread;

                if (!localProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    timedOut = true;
                    drainer.cancel();
                    localProcess.destroy();
                    if (!localProcess.waitFor(3, TimeUnit.SECONDS)) {
                        localProcess.destroyForcibly();
                        localProcess.waitFor(2, TimeUnit.SECONDS);
                    }
                }
                if (!timedOut) {
                    exitCode = localProcess.exitValue();
                }

                try {
                    drainThreadRef.join(2_000L);
                    if (drainThreadRef.isAlive()) {
                        drainer.cancel();
                        drainThreadRef.interrupt();
                    }
                } catch (InterruptedException e) {
                    drainer.cancel();
                    drainThreadRef.interrupt();
                    Thread.currentThread().interrupt();
                }
            } catch (IOException | InterruptedException e) {
                error = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                if (localProcess != null && localProcess.isAlive()) {
                    localProcess.destroyForcibly();
                }
                shellExecutorProcess = null;
                drainer.shutdown();
                result = new ExecResult(exitCode, outBuffer.snapshot(), errBuffer.snapshot(), timedOut);
                synchronized (monitor) {
                    monitor.notifyAll();
                }
            }
        }

        void cancel() {
            Process process = shellExecutorProcess;
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        ExecResult await() throws Exception {
            long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs + 5_000L);
            synchronized (monitor) {
                while (result == null && error == null) {
                    long remainingNs = deadlineNs - System.nanoTime();
                    if (remainingNs <= 0) {
                        break;
                    }
                    long waitMs = TimeUnit.NANOSECONDS.toMillis(remainingNs);
                    if (waitMs <= 0) {
                        waitMs = 1;
                    }
                    monitor.wait(waitMs);
                }
            }
            if (error != null) throw error;
            if (result == null) {
                throw new TimeoutException("shell execution timed out while waiting for result");
            }
            return result;
        }
    }
}
