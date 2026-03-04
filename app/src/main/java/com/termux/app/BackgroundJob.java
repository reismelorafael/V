package com.termux.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.vectras.vm.BuildConfig;
import com.vectras.vm.VMManager;
import com.vectras.vm.core.BoundedStringRingBuffer;
import com.vectras.vm.core.ProcessRuntimeOps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A background job launched by .
 */
public final class BackgroundJob {

    private static final String LOG_TAG = "termux-task";
    private static final int MAX_CAPTURE_CHARS = 256 * 1024;
    private static final int MAX_LINE_CHARS = 4096;
    private static final String CAPTURE_TRUNCATED_MARKER = BoundedStringRingBuffer.TRUNCATED_MARKER;
    private static final String TERMUX_BG_ID_PREFIX = "termux-bg-";
    private static final String SLOT_FEATURE = "termux";
    private static final String SLOT_TAG = "background-job";
    private static final String SLOT_CALLER = "BackgroundJob::<ctor>";
    private static final Object SLOT_LOCK = new Object();
    private static final AtomicLong NEXT_SLOT_ID = new AtomicLong(1L);
    private static int sReservedSlotCount = 0;

    final Process mProcess;
    private final String mRegistryId;
    private final boolean mStarted;

    public BackgroundJob(String cwd, String fileToExecute, final String[] args, final TermuxService service){
        this(cwd, fileToExecute, args, service, null);
    }

    public BackgroundJob(String cwd, String fileToExecute, final String[] args, final TermuxService service, PendingIntent pendingIntent) {
        String[] env = buildEnvironment(false, cwd);
        if (cwd == null) cwd = TermuxService.HOME_PATH;

        final SlotLease slotLease = tryAcquireSlot();
        if (slotLease == null) {
            mProcess = null;
            mRegistryId = null;
            mStarted = false;
            String errorMessage = "Failed running background job: slot limit reached for feature="
                    + SLOT_FEATURE + ", tag=" + SLOT_TAG + ", caller=" + SLOT_CALLER;
            Log.w(LOG_TAG, errorMessage);
            sendResultSafely(service, pendingIntent, Activity.RESULT_CANCELED, -1, "", errorMessage);
            service.onBackgroundJobExited(this);
            return;
        }

        final String[] progArray = setupProcessArgs(fileToExecute, args);
        final String processDescription = Arrays.toString(progArray);

        Process process = null;
        String registryId = null;
        boolean started = false;
        try {
            process = Runtime.getRuntime().exec(progArray, env, new File(cwd));
            registryId = buildRegistryId(process);
            VMManager.registerVmProcess(service.getApplicationContext(), registryId, process);
            slotLease.markBoundToProcess();
            started = true;
        } catch (IOException e) {
            mProcess = null;
            mRegistryId = null;
            mStarted = false;
            String errorMessage = "Failed running background job: " + processDescription + ": " + e.getMessage();
            Log.e(LOG_TAG, errorMessage, e);

            try {
                sendResultSafely(service, pendingIntent, Activity.RESULT_CANCELED, -1, "", errorMessage);
            } finally {
                releaseSlot(slotLease);
            }

            service.onBackgroundJobExited(this);
            return;
        } catch (RuntimeException e) {
            if (process != null) {
                process.destroy();
            }
            if (registryId != null && process != null) {
                try {
                    VMManager.unregisterVmProcess(registryId, process);
                } catch (RuntimeException unregisterError) {
                    Log.w(LOG_TAG, "Failed to rollback background job registration: " + registryId, unregisterError);
                }
            }
            mProcess = null;
            mRegistryId = null;
            mStarted = false;
            String errorMessage = "Failed registering background job: " + processDescription + ": " + e.getMessage();
            Log.e(LOG_TAG, errorMessage, e);

            try {
                sendResultSafely(service, pendingIntent, Activity.RESULT_CANCELED, -1, "", errorMessage);
            } finally {
                releaseSlot(slotLease);
            }

            service.onBackgroundJobExited(this);
            return;
        }

        mProcess = process;
        mRegistryId = registryId;
        mStarted = started;
        final int pid = getPid(mProcess);
        final Bundle result = new Bundle();
        final StringBuilder outResult = new StringBuilder();
        final StringBuilder errResult = new StringBuilder();

        Thread errThread = new Thread() {
            @Override
            public void run() {
                String line;
                try (InputStream stderr = mProcess.getErrorStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                    // FIXME: Long lines.
                    while ((line = reader.readLine()) != null) {
                        String safeLine = sanitizeLine(line);
                        appendBounded(errResult, safeLine);
                        Log.i(LOG_TAG, "[" + pid + "] stderr: " + safeLine);
                    }
                } catch (IOException e) {
                    // Ignore.
                }
            }
        };
        errThread.start();

        new Thread() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "[" + pid + "] starting: " + processDescription);
                String line;
                try (InputStream stdout = mProcess.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                    // FIXME: Long lines.
                    while ((line = reader.readLine()) != null) {
                        String safeLine = sanitizeLine(line);
                        Log.i(LOG_TAG, "[" + pid + "] stdout: " + safeLine);
                        appendBounded(outResult, safeLine);
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error reading output", e);
                }

                try {
                    int exitCode = mProcess.waitFor();
                    if (exitCode == 0) {
                        Log.i(LOG_TAG, "[" + pid + "] exited normally");
                    } else {
                        Log.w(LOG_TAG, "[" + pid + "] exited with code: " + exitCode);
                    }

                    result.putString("stdout", outResult.toString());
                    result.putInt("exitCode", exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(LOG_TAG, "[" + pid + "] interrupted while waiting for process", e);
                } finally {
                    try {
                        errThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        errThread.interrupt();
                        Log.w(LOG_TAG, "[" + pid + "] interrupted while joining stderr thread", e);
                    }

                    result.putString("stderr", errResult.toString());

                    Intent data = new Intent();
                    data.putExtra("result", result);

                    try {
                        if (pendingIntent != null) {
                            try {
                                pendingIntent.send(service.getApplicationContext(), Activity.RESULT_OK, data);
                            } catch (PendingIntent.CanceledException e) {
                                // The caller doesn't want the result? That's fine, just ignore.
                            }
                        }
                    } finally {
                        releaseSlot(slotLease);
                    }
                    safeUnregisterFromGlobalRegistry();
                    service.onBackgroundJobExited(BackgroundJob.this);
                }
            }
        }.start();
    }

    public boolean isStarted() {
        return mStarted && mProcess != null;
    }

    private static String buildRegistryId(Process process) {
        long pid = ProcessRuntimeOps.safePid(process);
        if (pid > 0L) {
            return TERMUX_BG_ID_PREFIX + pid;
        }
        int reflectedPid = getPid(process);
        if (reflectedPid > 0) {
            return TERMUX_BG_ID_PREFIX + reflectedPid;
        }
        return TERMUX_BG_ID_PREFIX + "unknown-" + System.nanoTime();
    }

    private void safeUnregisterFromGlobalRegistry() {
        if (mRegistryId == null) return;
        try {
            VMManager.unregisterVmProcess(mRegistryId, mProcess);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Failed to unregister background job from VM registry: " + mRegistryId, e);
        }
    }

    private static String sanitizeLine(String line) {
        if (line == null) return "";
        if (line.length() <= MAX_LINE_CHARS) return line;
        return line.substring(0, MAX_LINE_CHARS) + " …[truncated]";
    }

    private static void appendBounded(StringBuilder builder, String line) {
        if (builder == null) return;
        if (builder.length() >= MAX_CAPTURE_CHARS) {
            appendCaptureMarkerIfNeeded(builder);
            return;
        }
        String sanitized = sanitizeLine(line);
        int remaining = MAX_CAPTURE_CHARS - builder.length();
        if (sanitized.length() + 1 > remaining) {
            if (remaining > 1) {
                builder.append(sanitized, 0, remaining - 1);
            }
            builder.append('\n');
            appendCaptureMarkerIfNeeded(builder);
            return;
        }
        builder.append(sanitized).append('\n');
    }

    private static void appendCaptureMarkerIfNeeded(StringBuilder builder) {
        if (builder == null || builder.length() == 0) {
            return;
        }
        if (builder.lastIndexOf(CAPTURE_TRUNCATED_MARKER) >= 0) {
            return;
        }
        builder.append(CAPTURE_TRUNCATED_MARKER).append('\n');
    }

    private static void addToEnvIfPresent(List<String> environment, String name) {
        String value = System.getenv(name);
        if (value != null) {
            environment.add(name + "=" + value);
        }
    }

    private static void sendResultSafely(TermuxService service,
                                         PendingIntent pendingIntent,
                                         int resultCode,
                                         int exitCode,
                                         String stdout,
                                         String stderr) {
        if (pendingIntent == null) return;
        Bundle result = new Bundle();
        result.putInt("exitCode", exitCode);
        result.putString("stdout", stdout == null ? "" : stdout);
        result.putString("stderr", stderr == null ? "" : stderr);

        Intent data = new Intent();
        data.putExtra("result", result);
        try {
            pendingIntent.send(service.getApplicationContext(), resultCode, data);
        } catch (PendingIntent.CanceledException e) {
            // The caller doesn't want the result? That's fine, just ignore.
        }
    }

    private static SlotLease tryAcquireSlot() {
        synchronized (SLOT_LOCK) {
            if (!VMManager.canRegisterAnotherVmProcess()) {
                return null;
            }
            long slotId = NEXT_SLOT_ID.getAndIncrement();
            sReservedSlotCount++;
            Log.i(LOG_TAG, "Acquired background slot id=" + slotId
                    + " feature=" + SLOT_FEATURE
                    + " tag=" + SLOT_TAG
                    + " caller=" + SLOT_CALLER
                    + " reserved=" + sReservedSlotCount);
            return new SlotLease(slotId);
        }
    }

    private static void releaseSlot(SlotLease lease) {
        if (lease == null || lease.isReleased()) return;
        synchronized (SLOT_LOCK) {
            if (lease.isReleased()) return;
            lease.markReleased();
            if (sReservedSlotCount > 0) {
                sReservedSlotCount--;
            }
            Log.i(LOG_TAG, "Released background slot id=" + lease.id
                    + " bound=" + lease.wasBoundToProcess
                    + " reserved=" + sReservedSlotCount);
        }
    }

    private static final class SlotLease {
        private final long id;
        private boolean wasBoundToProcess;
        private boolean released;

        private SlotLease(long id) {
            this.id = id;
        }

        private boolean isReleased() {
            return released;
        }

        private void markBoundToProcess() {
            wasBoundToProcess = true;
        }

        private void markReleased() {
            released = true;
        }
    }

    static String[] buildEnvironment(boolean failSafe, String cwd) {
        new File(TermuxService.HOME_PATH).mkdirs();

        if (cwd == null) cwd = TermuxService.HOME_PATH;

        List<String> environment = new ArrayList<>();

        environment.add("TERMUX_VERSION=" + BuildConfig.VERSION_NAME);
        environment.add("TERM=xterm-256color");
        environment.add("COLORTERM=truecolor");
        environment.add("DISPLAY=:0");
        environment.add("PULSE_SERVER=127.0.0.1");
        environment.add("XDG_RUNTIME_DIR=${TMPDIR}");
        environment.add("HOME=" + TermuxService.HOME_PATH);
        //environment.add("PREFIX=" + TermuxService.PREFIX_PATH);
        //environment.add("LD_LIBRARY_PATH=" + TermuxService.PREFIX_PATH + "/lib");
        environment.add("LANG=en_US.UTF-8");
        //environment.add("PATH=" + TermuxService.PREFIX_PATH + "/bin");
        environment.add("PWD=" + cwd);
        environment.add("TMPDIR=/tmp");
        environment.add("BOOTCLASSPATH=" + System.getenv("BOOTCLASSPATH"));
        environment.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"));
        environment.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"));
        // EXTERNAL_STORAGE is needed for /system/bin/am to work on at least
        // Samsung S7 - see https://plus.google.com/110070148244138185604/posts/gp8Lk3aCGp3.
        environment.add("EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE"));

        // These variables are needed if running on Android 10 and higher.
        addToEnvIfPresent(environment, "ANDROID_ART_ROOT");
        addToEnvIfPresent(environment, "DEX2OATBOOTCLASSPATH");
        addToEnvIfPresent(environment, "ANDROID_I18N_ROOT");
        addToEnvIfPresent(environment, "ANDROID_RUNTIME_ROOT");
        addToEnvIfPresent(environment, "ANDROID_TZDATA_ROOT");

        return environment.toArray(new String[0]);
    }

    public static int getPid(Process p) {
        try {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            try {
                return f.getInt(p);
            } finally {
                f.setAccessible(false);
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    static String[] setupProcessArgs(String fileToExecute, String[] args) {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        String interpreter = null;
        try {
            File file = new File(fileToExecute);
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[256];
                int bytesRead = in.read(buffer);
                if (bytesRead > 4) {
                    if (buffer[0] == 0x7F && buffer[1] == 'E' && buffer[2] == 'L' && buffer[3] == 'F') {
                        // Elf file, do nothing.
                    } else if (buffer[0] == '#' && buffer[1] == '!') {
                        // Try to parse shebang.
                        StringBuilder builder = new StringBuilder();
                        for (int i = 2; i < bytesRead; i++) {
                            char c = (char) buffer[i];
                            if (c == ' ' || c == '\n') {
                                if (builder.length() == 0) {
                                    // Skip whitespace after shebang.
                                } else {
                                    // End of shebang.
                                    String executable = builder.toString();
                                    if (executable.startsWith("/usr") || executable.startsWith("/bin")) {
                                        String[] parts = executable.split("/");
                                        String binary = parts[parts.length - 1];
                                        interpreter = TermuxService.PREFIX_PATH + "/bin/" + binary;
                                    }
                                    break;
                                }
                            } else {
                                builder.append(c);
                            }
                        }
                    } else {
                        // No shebang and no ELF, use standard shell.
                        interpreter = TermuxService.PREFIX_PATH + "/bin/sh";
                    }
                }
            }
        } catch (IOException e) {
            // Ignore.
        }

        List<String> result = new ArrayList<>();
        if (interpreter != null) result.add(interpreter);
        result.add(fileToExecute);
        if (args != null) Collections.addAll(result, args);
        return result.toArray(new String[0]);
    }

}
