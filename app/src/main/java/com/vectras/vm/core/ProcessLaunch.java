package com.vectras.vm.core;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.vectras.vm.VMManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Locale;

/**
 * Wrapper para start/wait/register/unregister com orçamento de execução por categoria.
 */
public final class ProcessLaunch {

    private static final String TAG = "ProcessLaunch";

    public enum LaunchStatus {
        SUCCESS,
        ERROR,
        TIMEOUT,
        CANCELLED,
        START_ERROR
    }

    public interface StreamLineCallback {
        void onLine(String line);
    }

    public interface ProcessInputWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    public static final class LaunchResult {
        public final LaunchStatus status;
        public final int exitCode;
        public final boolean timedOut;
        public final String diagnosis;
        public final String feature;
        public final String tag;
        public final String caller;
        public final String registryId;
        public final ProcessRuntimeOps.ExecutionCategory category;

        private LaunchResult(LaunchStatus status,
                             int exitCode,
                             boolean timedOut,
                             String diagnosis,
                             String feature,
                             String tag,
                             String caller,
                             String registryId,
                             ProcessRuntimeOps.ExecutionCategory category) {
            this.status = status;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.diagnosis = diagnosis;
            this.feature = feature;
            this.tag = tag;
            this.caller = caller;
            this.registryId = registryId;
            this.category = category;
        }

        public boolean isSuccess() {
            return status == LaunchStatus.SUCCESS;
        }
    }

    private ProcessLaunch() {
        throw new AssertionError("ProcessLaunch is utility-only");
    }

    public static LaunchResult withBudget(Context context,
                                          String feature,
                                          String tag,
                                          String caller,
                                          @Nullable String vmOrRegistryId,
                                          ProcessRuntimeOps.ExecutionCategory category,
                                          ProcessBuilder processBuilder,
                                          @Nullable StreamLineCallback stdoutCallback,
                                          @Nullable StreamLineCallback stderrCallback,
                                          @Nullable ProcessInputWriter inputWriter) {
        String registryId = buildRegistryId(feature, tag, caller, vmOrRegistryId);
        Process process = null;
        Thread stdoutThread = null;
        Thread stderrThread = null;
        try {
            process = processBuilder.start();
            VMManager.registerVmProcess(context, registryId, process);

            stdoutThread = startCollector("stdout", process.getInputStream(), stdoutCallback);
            stderrThread = startCollector("stderr", process.getErrorStream(), stderrCallback);

            if (inputWriter != null) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    inputWriter.write(writer);
                    writer.flush();
                }
            }

            ProcessRuntimeOps.TimeoutExecutionResult waitResult = ProcessRuntimeOps.waitForByCategory(process, category);
            joinCollector(stdoutThread);
            joinCollector(stderrThread);

            LaunchStatus launchStatus = mapStatus(waitResult);
            return new LaunchResult(
                    launchStatus,
                    waitResult.exitCode,
                    waitResult.timedOut,
                    waitResult.message,
                    feature,
                    tag,
                    caller,
                    registryId,
                    category
            );
        } catch (IOException e) {
            return new LaunchResult(
                    LaunchStatus.START_ERROR,
                    -1,
                    false,
                    e.toString(),
                    feature,
                    tag,
                    caller,
                    registryId,
                    category
            );
        } finally {
            if (process != null) {
                VMManager.unregisterVmProcess(registryId, process);
                if (process.isAlive()) {
                    process.destroy();
                }
            }
        }
    }

    private static LaunchStatus mapStatus(ProcessRuntimeOps.TimeoutExecutionResult waitResult) {
        if (waitResult == null || waitResult.status == null) {
            return LaunchStatus.ERROR;
        }
        if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.TIMEOUT) {
            return LaunchStatus.TIMEOUT;
        }
        if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR) {
            if (waitResult.message != null && waitResult.message.toLowerCase(Locale.US).contains("interrupted")) {
                return LaunchStatus.CANCELLED;
            }
            return LaunchStatus.ERROR;
        }
        return LaunchStatus.SUCCESS;
    }

    private static Thread startCollector(String label,
                                         InputStream inputStream,
                                         @Nullable StreamLineCallback callback) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (callback != null) {
                        callback.onLine(line);
                    }
                }
            } catch (IOException ioException) {
                Log.w(TAG, "collector(" + label + ") failed: " + ioException.getMessage());
            }
        }, "process-launch-" + label + "-collector");
        thread.start();
        return thread;
    }

    private static void joinCollector(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String buildRegistryId(String feature,
                                          String tag,
                                          String caller,
                                          @Nullable String vmOrRegistryId) {
        String stablePrefix = sanitize(feature) + "-" + sanitize(tag) + "-" + sanitize(caller);
        String vmPart = sanitize(vmOrRegistryId == null ? "none" : vmOrRegistryId);
        String tsPart = Long.toHexString(ProcessRuntimeOps.wallMs() & 0xFFFFFL);
        return stablePrefix + "-" + vmPart + "-" + tsPart;
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
