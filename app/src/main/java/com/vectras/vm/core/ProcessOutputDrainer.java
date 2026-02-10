package com.vectras.vm.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concurrent stdout/stderr drainer that never blocks on one side only.
 */
public class ProcessOutputDrainer {
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
        waitFuture(out);
        waitFuture(err);
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
            throw e;
        } catch (Exception ignored) {
            // non-fatal by design
        }
    }
}
