package com.vectras.vm.core;

import android.util.Log;

import com.vectras.vm.logger.VectrasStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShellExecutor {
    private static final String TAG = "ShellExecutor";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final int OUTPUT_MAX_LINES = 512;
    private static final int OUTPUT_MAX_BYTES = 256 * 1024;

    private final ExecutorService executorService;
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
        this.executorService = Executors.newCachedThreadPool();
    }

    public void exec(String command) {
        processFuture = executorService.submit(() -> execute(command, DEFAULT_TIMEOUT_MS));
    }

    public ExecResult execute(String command, long timeoutMs) {
        long effectiveTimeoutMs = timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
        CallableExec callable = new CallableExec(command, effectiveTimeoutMs);
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
                localProcess = new ProcessBuilder(shellPath).start();
                shellExecutorProcess = localProcess;
                try (OutputStream outputStream = localProcess.getOutputStream()) {
                    outputStream.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }

                Process finalProcess = localProcess;
                Future<?> drainerFuture = executorService.submit(() -> {
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
                });

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
                    drainerFuture.get(2, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    drainer.cancel();
                    drainerFuture.cancel(true);
                } catch (ExecutionException e) {
                    Log.w(TAG, "drainer failed", e.getCause());
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
