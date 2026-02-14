package com.vectras.vm.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import android.util.Log;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concurrent stdout/stderr drainer that never blocks on one side only.
 */
public class ProcessOutputDrainer {
    private static final String TAG = "ProcessOutputDrainer";
    public interface OutputLineConsumer {
        void onLine(String stream, String line);
    }

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public void drain(Process process, OutputLineConsumer consumer) throws InterruptedException {
        Future<?> out = streamExecutor.submit(() -> readStream("stdout", process.getInputStream(), consumer));
        Future<?> err = streamExecutor.submit(() -> readStream("stderr", process.getErrorStream(), consumer));

        try {
            waitFuture(out);
            waitFuture(err);
        } catch (InterruptedException e) {
            out.cancel(true);
            err.cancel(true);
            throw e;
        } catch (RuntimeException e) {
            out.cancel(true);
            err.cancel(true);
            Log.w(TAG, "drain failed", e);
            throw e;
        }
    }

    public void shutdown() {
        streamExecutor.shutdownNow();
        try {
            streamExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void readStream(String name, InputStream stream, OutputLineConsumer consumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while (!cancelled.get() && (line = reader.readLine()) != null) {
                consumer.onLine(name, line);
            }
        } catch (IOException ignored) {
            // non-fatal by design
        }
    }

    private static void waitFuture(Future<?> future) throws InterruptedException {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            throw new IllegalStateException("stream drain failed", e.getCause());
        } catch (CancellationException e) {
            throw new IllegalStateException("stream drain cancelled", e);
        }
    }
}
