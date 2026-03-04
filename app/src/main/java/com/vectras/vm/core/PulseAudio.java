package com.vectras.vm.core;

import android.content.Context;
import android.util.Log;

import com.termux.app.TermuxService;
import com.vectras.vm.logger.VectrasStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PulseAudio {
    private static final String TAG = "PulseAudio";
    private static final String LAUNCH_FEATURE = "audio.pulseaudio";
    private static final String LAUNCH_TAG = "daemon-start";
    private static final String LAUNCH_CALLER = "PulseAudio#start";

    private volatile Process pulseAudioProcess;
    private final Context context;
    private ExecutorService executorService;
    private volatile Future<?> processFuture;
    private volatile ProcessLaunch.LaunchTicket launchTicket;

    public PulseAudio(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public synchronized void start() {
        stop();
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        String tmpDir = TermuxService.PREFIX_PATH + "/tmp";

        Runnable processRunnable = () -> {
            Process localProcess = null;
            ProcessLaunch.LaunchTicket localTicket = null;
            try {
                localTicket = ProcessLaunch.withBudget(
                        LAUNCH_FEATURE,
                        LAUNCH_TAG,
                        LAUNCH_CALLER,
                        0L,
                        () -> {
                            ProcessBuilder processBuilder = new ProcessBuilder(
                                    "/system/bin/sh", "-c",
                                    "XDG_RUNTIME_DIR=" + tmpDir + " TMPDIR=" + tmpDir + " " +
                                            TermuxService.PREFIX_PATH + "/bin/pulseaudio --start " +
                                            "--load=\"module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1\" " +
                                            "--exit-idle-time=-1"
                            );
                            processBuilder.redirectErrorStream(true);
                            return processBuilder.start();
                        });
                localProcess = localTicket.process();
                launchTicket = localTicket;
                pulseAudioProcess = localProcess;

                BufferedReader reader = new BufferedReader(new InputStreamReader(localProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, "[" + localTicket.diagnosticPrefix() + "] " + line);
                    VectrasStatus.logInfo(TAG + " > [" + localTicket.diagnosticPrefix() + "] " + line);
                }
            } catch (IOException e) {
                Log.e(TAG, "start failed " + ProcessLaunch.diagnosticPrefix(LAUNCH_FEATURE, LAUNCH_TAG, LAUNCH_CALLER), e);
                VectrasStatus.logInfo(TAG + " > start failed "
                        + ProcessLaunch.diagnosticPrefix(LAUNCH_FEATURE, LAUNCH_TAG, LAUNCH_CALLER)
                        + " " + e);
            } finally {
                cleanupProcess(localProcess);
                pulseAudioProcess = null;
                if (localTicket != null) {
                    localTicket.release("pulseaudio_start_finally");
                }
                launchTicket = null;
            }
        };

        processFuture = executorService.submit(processRunnable);
    }

    public synchronized void stop() {
        Future<?> localFuture = processFuture;
        processFuture = null;
        if (localFuture != null) {
            localFuture.cancel(true);
        }

        Process localProcess = pulseAudioProcess;
        pulseAudioProcess = null;
        cleanupProcess(localProcess);

        ProcessLaunch.LaunchTicket localTicket = launchTicket;
        launchTicket = null;
        if (localTicket != null) {
            localTicket.release("pulseaudio_stop");
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }

        Log.d(TAG, "stopped " + ProcessLaunch.diagnosticPrefix(LAUNCH_FEATURE, LAUNCH_TAG, LAUNCH_CALLER));
        VectrasStatus.logInfo(TAG + " > stopped " + ProcessLaunch.diagnosticPrefix(LAUNCH_FEATURE, LAUNCH_TAG, LAUNCH_CALLER));
    }

    private void cleanupProcess(Process process) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
