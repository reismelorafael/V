package com.vectras.vterm;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vectras.vm.BuildConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.vectras.vm.R;
import com.vectras.vm.VMManager;
import com.vectras.vm.AppConfig;
import com.vectras.vm.VectrasApp;
import com.vectras.vm.utils.ClipboardUltils;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.NotificationUtils;
import com.vectras.vm.core.BoundedStringRingBuffer;
import com.vectras.vm.core.ProcessOutputDrainer;
import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.core.ProcessRuntimeOps.ExecutionCategory;
import com.vectras.vm.core.ProcessRuntimeOps.TimeoutExecutionResult;
import com.vectras.vm.core.ProotCommandBuilder;
import com.vectras.vm.core.TokenBucketRateLimiter;
import com.vectras.vm.core.VmFlowState;
import com.vectras.vm.core.VmFlowTracker;
import com.vectras.vm.audit.AuditEvent;
import com.vectras.vm.audit.AuditLedger;

public class Terminal {
    private static final String TAG = "Vterm";
    private final Context context;
    private static final String user = "root";

    public static volatile Process qemuProcess;
    public static String DISPLAY = ":0";
    private static final AtomicBoolean STREAM_STOP_TOKEN = new AtomicBoolean(false);
    private static final int MAX_LOG_LINES = 1500;
    private static final int MAX_LOG_BYTES = 512 * 1024;
    private static final int RATE_LINES_PER_SEC = 60;
    private static final int RATE_BURST = 120;
    private static final String TRANSIENT_VM_ID_PREFIX = "launch-";

    public Terminal(Context context) {
        this.context = context;
    }

    static String resolveOutputText(AtomicReference<StringBuilder> outputRef) {
        if (outputRef == null) {
            return "";
        }
        StringBuilder buffer = outputRef.get();
        return buffer == null ? "" : buffer.toString();
    }

    static String resolveFinalOutputText(AtomicReference<StringBuilder> outputRef, String errors) {
        String outputText = resolveOutputText(outputRef);
        return outputText.isEmpty() ? (errors == null ? "" : errors) : outputText;
    }

    private void showDialog(String message, Context context, String usercommand) {
        if (VMManager.isExecutedCommandError(usercommand, message, context))
            return;

        DialogUtils.twoDialog(context, "Execution Result", message, context.getString(R.string.copy), context.getString(R.string.close), true, R.drawable.round_terminal_24, true,
                () -> ClipboardUltils.copyToClipboard(context, message), null, null);
    }

    private boolean ensureVmProcessCapacity(Context dialogContext) {
        if (VMManager.canRegisterAnotherVmProcess()) {
            return true;
        }
        int active = VMManager.getActiveSupervisedVmProcessCount();
        int max = VMManager.getMaxSupervisedVmProcesses();
        String message = "VM process limit reached for Android 15 compatibility (" + active + "/" + max + "). Stop an active VM process and try again.";
        Log.w(TAG, message);
        if (dialogContext != null) {
            new Handler(Looper.getMainLooper()).post(() -> DialogUtils.oneDialog(dialogContext, "Process limit reached", message, R.drawable.round_terminal_24));
        }
        return false;
    }


    private static String nextTransientVmId() {
        return com.vectras.vm.main.core.MainStartVM.createTransientLaunchVmId();
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isTransientVmId(String vmId) {
        return vmId != null && vmId.startsWith(TRANSIENT_VM_ID_PREFIX);
    }

    private static String resolveCurrentVmId() {
        String vmId = com.vectras.vm.main.core.MainStartVM.lastVMID;
        if (isNullOrEmpty(vmId)) {
            return com.vectras.vm.main.core.MainStartVM.ensureLastVmIdInitialized(nextTransientVmId());
        }
        return vmId;
    }

    private static void rotateTransientVmIdAfterBootstrapFailure(String vmId) {
        if (!isTransientVmId(vmId)) {
            return;
        }
        if (vmId.equals(com.vectras.vm.main.core.MainStartVM.lastVMID)) {
            com.vectras.vm.main.core.MainStartVM.lastVMID = nextTransientVmId();
        }
    }

    private String currentVmId() {
        return resolveCurrentVmId();
    }

    private static void safeUnregisterVmProcess(String vmId, Process process) {
        try {
            VMManager.unregisterVmProcess(vmId, process);
        } catch (Exception e) {
            Log.w(TAG, "unregisterVmProcess failed for vmId=" + vmId, e);
        }
    }

    private static void safeRegisterVmProcess(Context context, String vmId, Process process, StringBuilder errors) {
        try {
            VMManager.registerVmProcess(context, vmId, process);
        } catch (Exception e) {
            Log.e(TAG, "registerVmProcess failed for vmId=" + vmId, e);
            com.vectras.vm.logger.VectrasStatus.logError("<font color='red'>VTERM registerVmProcess failed: vmId="
                    + vmId + " error=" + e.getMessage() + "</font>");
            if (errors != null) {
                errors.append("registerVmProcess failed: ").append(e.getMessage()).append("\n");
            }
        }
    }

    private static void clearQemuProcessIfMatches(Process process) {
        if (process == null) {
            return;
        }
        synchronized (Terminal.class) {
            if (qemuProcess == process) {
                qemuProcess = null;
            }
        }
    }

    private static void dismissProgressDialogSafely(AlertDialog progressDialog) {
        if (progressDialog == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            progressDialog.dismiss();
            return;
        }
        new Handler(Looper.getMainLooper()).post(progressDialog::dismiss);
    }

    private boolean acquireVmStartSlot(Context dialogContext, String vmId) {
        if (!VMManager.tryMarkVmStarting(vmId)) {
            String message = "VM start already in progress or VM already running for id=" + vmId;
            Log.w(TAG, message);
            if (dialogContext != null) {
                new Handler(Looper.getMainLooper()).post(() -> DialogUtils.oneDialog(dialogContext, "VM busy", message, R.drawable.round_terminal_24));
            }
            return false;
        }
        return true;
    }

    // Method to execute the shell command
    public void executeShellCommand(String userCommand, boolean showResultDialog, boolean showProgressDialog, Context dialogActivity) {
        executeShellCommand(userCommand, showResultDialog, showProgressDialog, dialogActivity.getString(R.string.executing_command_please_wait), dialogActivity);
    }

    public void executeShellCommand(String userCommand, boolean showResultDialog, boolean showProgressDialog, String progressDialogMessage, Context dialogActivity) {
        if (!ensureVmProcessCapacity(dialogActivity)) return;
        String vmId = currentVmId();
        if (!acquireVmStartSlot(dialogActivity, vmId)) return;
        AtomicReference<StringBuilder> output = new AtomicReference<>(new StringBuilder());
        StringBuilder errors = new StringBuilder();
        Log.d(TAG, userCommand);
        com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");

        // Show ProgressDialog
        View progressView = LayoutInflater.from(dialogActivity).inflate(R.layout.dialog_progress_style, null);
        TextView progress_text = progressView.findViewById(R.id.progress_text);
        progress_text.setText(progressDialogMessage);
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(dialogActivity, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();

        if (showProgressDialog) progressDialog.show();

        new Thread(() -> {
            Process launchedProcess = null;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();

                String filesDir = Objects.requireNonNull(context.getFilesDir().getAbsolutePath());
                ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(context, filesDir + "/distro", "/root")
                        .setUser(user)
                        .setDisplay(DISPLAY)
                        .setPulseServer("127.0.0.1");
                prootCommandBuilder.applyEnvironment(processBuilder.environment());
                processBuilder.command(prootCommandBuilder.buildCommand());
                launchedProcess = processBuilder.start();
                qemuProcess = launchedProcess;
                Terminal.resetStreamingStopToken();
                safeRegisterVmProcess(getContext(), vmId, launchedProcess, errors);

                output.set(streamLog(userCommand, launchedProcess, false, null));
            } catch (IOException e) {
                VMManager.clearVmStarting(vmId);
                rotateTransientVmIdAfterBootstrapFailure(vmId);
                dismissProgressDialogSafely(progressDialog);
                output.get().append(e.getMessage());
                errors.append(Log.getStackTraceString(e));
            } finally {
                safeUnregisterVmProcess(vmId, launchedProcess);
                com.vectras.vm.utils.FileUtils.closeFdsForVm(vmId);
                VMManager.clearVmStarting(vmId);
                new Handler(Looper.getMainLooper()).post(() -> {
                    dismissProgressDialogSafely(progressDialog);
                    String finalErrors = errors.toString();
                    String finalOutput = resolveOutputText(output);
                    AppConfig.temporaryLastedTerminalOutput = resolveFinalOutputText(output, finalErrors);
                    if (showResultDialog) {
                        showDialog(finalOutput.isEmpty() ? finalErrors : finalOutput.replace("read interrupted", "Done!"), dialogActivity, userCommand);
                    }
                });
            }
        }).start();
    }

    public void executeShellCommand2(String userCommand, boolean showResultDialog, Context dialogActivity) {
        if (!ensureVmProcessCapacity(dialogActivity)) return;
        String vmId = currentVmId();
        if (!acquireVmStartSlot(dialogActivity, vmId)) return;
        AtomicReference<StringBuilder> output = new AtomicReference<>(new StringBuilder());
        StringBuilder errors = new StringBuilder();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, userCommand);
            com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");
        }
        new Thread(() -> {
            Process launchedProcess = null;
            try {
                // Set up the qemuProcess builder to start PRoot with environmental variables and commands
                ProcessBuilder processBuilder = new ProcessBuilder();

                // Adjust these environment variables as necessary for your app
                String filesDir = getContext().getFilesDir().getAbsolutePath();

                String tmpDirPath = "/tmp";

                ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(context, filesDir + "/distro", "/root")
                        .setUser(user)
                        .setDisplay(DISPLAY)
                        .setPulseServer("127.0.0.1")
                        .setXdgRuntimeDir(tmpDirPath)
                        .setSdlVideoDriver("x11");
                prootCommandBuilder.applyEnvironment(processBuilder.environment());
                processBuilder.command(prootCommandBuilder.buildCommand());
                launchedProcess = processBuilder.start();
                qemuProcess = launchedProcess;
                Terminal.resetStreamingStopToken();
                safeRegisterVmProcess(getContext(), vmId, launchedProcess, errors);

                output.set(streamLog(userCommand, launchedProcess, false, null));
            } catch (IOException e) {
                VMManager.clearVmStarting(vmId);
                rotateTransientVmIdAfterBootstrapFailure(vmId);
                output.get().append(e.getMessage());
                errors.append(Log.getStackTraceString(e));
                NotificationUtils.clearAll(VectrasApp.getContext());
            } finally {
                safeUnregisterVmProcess(vmId, launchedProcess);
                com.vectras.vm.utils.FileUtils.closeFdsForVm(vmId);
                VMManager.clearVmStarting(vmId);
                // Switch to main thread after execution
                new Handler(Looper.getMainLooper()).post(() -> {
                    String finalErrors = errors.toString();
                    String finalOutput = resolveOutputText(output);
                    AppConfig.temporaryLastedTerminalOutput = resolveFinalOutputText(output, finalErrors);
                    // If showResultDialog is enabled, show the dialog with the result or errors
                    if (showResultDialog) {
                        // bcuz there is dumb users bruh
                        showDialog(finalOutput.isEmpty() ? finalErrors : finalOutput.replace("read interrupted", "Done!"), dialogActivity, userCommand);
                    }
                });
            }
        }).start();
    }

    public static String executeShellCommandWithResult(String userCommand, Context context) {
        if (!VMManager.canRegisterAnotherVmProcess()) {
            Log.w(TAG, "executeShellCommandWithResult blocked: VM process limit reached " + VMManager.getActiveSupervisedVmProcessCount() + "/" + VMManager.getMaxSupervisedVmProcesses());
            return "VM process limit reached for Android 15 compatibility.";
        }
        String vmId = resolveCurrentVmId();
        if (!VMManager.tryMarkVmStarting(vmId)) {
            Log.w(TAG, "executeShellCommandWithResult blocked: VM start in-flight or already running for id=" + vmId);
            return "VM start already in progress or VM already running.";
        }
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Log.d(TAG, userCommand);
        com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");

        Process launchedProcess = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();

            String filesDir = Objects.requireNonNull(context.getFilesDir().getAbsolutePath());
            ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(context, filesDir + "/distro", "/root")
                    .setUser(user)
                    .setDisplay(DISPLAY)
                    .setPulseServer("127.0.0.1");
            prootCommandBuilder.applyEnvironment(processBuilder.environment());
            processBuilder.command(prootCommandBuilder.buildCommand());
            launchedProcess = processBuilder.start();
            qemuProcess = launchedProcess;
            Terminal.resetStreamingStopToken();
            safeRegisterVmProcess(context, vmId, launchedProcess, errors);

            output = streamLog(userCommand, launchedProcess, false, null);
        } catch (IOException e) {
            VMManager.clearVmStarting(vmId);
            rotateTransientVmIdAfterBootstrapFailure(vmId);
            output.append(e.getMessage());
            errors.append(Log.getStackTraceString(e));
        } finally {
            safeUnregisterVmProcess(vmId, launchedProcess);
            com.vectras.vm.utils.FileUtils.closeFdsForVm(vmId);
            VMManager.clearVmStarting(vmId);
        }
        return output.toString();
    }

    public interface CommandCallback {
        void onCommandCompleted(String output, String errors);
    }

    public String executeShellCommand(String userCommand, Context dialogActivity, boolean isShowProgressDialog, CommandCallback callback) {
        if (!ensureVmProcessCapacity(dialogActivity)) {
            callback.onCommandCompleted("", "VM process limit reached for Android 15 compatibility.");
            return "Execution blocked: process limit reached.";
        }
        String vmId = currentVmId();
        if (!acquireVmStartSlot(dialogActivity, vmId)) {
            callback.onCommandCompleted("", "VM start already in progress or already running.");
            return "Execution blocked: VM busy.";
        }
        AtomicReference<StringBuilder> output = new AtomicReference<>(new StringBuilder());
        StringBuilder errors = new StringBuilder();
        Log.d(TAG, userCommand);
        com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");

        // Show ProgressDialog on the main thread
        View progressView = LayoutInflater.from(dialogActivity).inflate(R.layout.dialog_progress_style, null);
        TextView progress_text = progressView.findViewById(R.id.progress_text);
        progress_text.setText(dialogActivity.getString(R.string.executing_command_please_wait));
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(dialogActivity, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();

        // Make sure to show the dialog on the main thread
        if (isShowProgressDialog) new Handler(Looper.getMainLooper()).post(progressDialog::show);

        new Thread(() -> {
            Process launchedProcess = null;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();

                String filesDir = Objects.requireNonNull(context.getFilesDir().getAbsolutePath());
                ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(context, filesDir + "/distro", "/root")
                        .setUser(user)
                        .setDisplay(DISPLAY)
                        .setPulseServer("127.0.0.1");
                prootCommandBuilder.applyEnvironment(processBuilder.environment());
                processBuilder.command(prootCommandBuilder.buildCommand());
                launchedProcess = processBuilder.start();
                qemuProcess = launchedProcess;
                Terminal.resetStreamingStopToken();
                safeRegisterVmProcess(getContext(), vmId, launchedProcess, errors);

                output.set(streamLog(userCommand, launchedProcess, !ProcessRuntimeOps.isLikelyInteractiveCommand(userCommand), null));

            } catch (IOException e) {
                VMManager.clearVmStarting(vmId);
                output.get().append(e.getMessage());
                errors.append(Log.getStackTraceString(e));
            } finally {
                safeUnregisterVmProcess(vmId, launchedProcess);
                com.vectras.vm.utils.FileUtils.closeFdsForVm(vmId);
                VMManager.clearVmStarting(vmId);
                dismissProgressDialogSafely(progressDialog);

                // Use callback to return both output and errors
                new Handler(Looper.getMainLooper()).post(() -> callback.onCommandCompleted(resolveOutputText(output), errors.toString()));
            }
        }).start();

        return "Execution is in progress..."; // Returning a message indicating the command execution is ongoing
    }

    /**
     * Checks if a package is installed using PackageManager first, then optional Termux fallback.
     */
    private boolean isPackageInstalled(String packageName) {
        try {
            getContext().getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            // continue to termux fallback
        } catch (Exception e) {
            Log.e(TAG, "PackageManager lookup failed: " + packageName, e);
        }

        try {
            Process process = new ProcessBuilder("pkg", "list-installed", packageName).start();
            StringBuilder output = streamLog("", process, true, ExecutionCategory.QUICK_QUERY);
            return output.toString().contains(packageName);
        } catch (Exception e) {
            Log.e(TAG, "Termux fallback package check failed: " + packageName, e);
        }
        return false;
    }

    public static void requestStopStreaming() {
        STREAM_STOP_TOKEN.set(true);
    }

    public static void resetStreamingStopToken() {
        STREAM_STOP_TOKEN.set(false);
    }

    public static StringBuilder streamLog(String command, Process process, boolean isShortProcess, ExecutionCategory shortProcessCategory) {
        BoundedStringRingBuffer ringBuffer = new BoundedStringRingBuffer(MAX_LOG_LINES, MAX_LOG_BYTES);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(RATE_LINES_PER_SEC, RATE_BURST);
        ProcessOutputDrainer drainer = new ProcessOutputDrainer();
        AtomicInteger droppedLogs = new AtomicInteger(0);
        AtomicLong bytesSeen = new AtomicLong(0);
        AtomicBoolean degraded = new AtomicBoolean(false);
        AtomicBoolean degradedMarked = new AtomicBoolean(false);

        try {
            if (command != null && !command.isEmpty()) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                writer.write(command);
                writer.newLine();
                writer.flush();
                writer.close();
            }

            Thread drainThread = new Thread(() -> {
                try {
                    drainer.drain(process, (stream, line) -> {
                        if (STREAM_STOP_TOKEN.get()) {
                            drainer.cancel();
                            return;
                        }
                        bytesSeen.addAndGet(line.getBytes().length);
                        if (!limiter.tryAcquire()) {
                            int dropped = droppedLogs.incrementAndGet();
                            if (dropped % 100 == 1) {
                                ringBuffer.addLine("[DEGRADED] log flood active, dropped=" + dropped);
                            }
                            degraded.set(true);
                            if (degradedMarked.compareAndSet(false, true)) {
                                String vmIdForDegraded = com.vectras.vm.main.core.MainStartVM.lastVMID;
                                vmIdForDegraded = isNullOrEmpty(vmIdForDegraded) ? resolveCurrentVmId() : vmIdForDegraded;
                                VmFlowTracker.mark(VectrasApp.getContext(), vmIdForDegraded, VmFlowState.DEGRADED, "log_flood", "rate_limit");
                            }
                            return;
                        }
                        if ("stderr".equals(stream)) {
                            Log.w(TAG, line);
                            com.vectras.vm.logger.VectrasStatus.logError("<font color='red'>VTERM ERROR: >" + line + "</font>");
                        } else {
                            com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + line + "</font>");
                        }
                        ringBuffer.addLine(line);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "vterm-log-drainer");
            drainThread.start();

            if (isShortProcess) {
                ExecutionCategory category = shortProcessCategory != null ? shortProcessCategory : ExecutionCategory.QUICK_QUERY;
                TimeoutExecutionResult result = ProcessRuntimeOps.waitForByCategory(process, category);
                if (result.status == TimeoutExecutionResult.Status.TIMEOUT) {
                    ringBuffer.addLine("Execution timed out for category " + category.name() + ".");
                    ProcessRuntimeOps.stopProcessWithTimeout(process, 2_000, 1_000);
                } else if (result.status == TimeoutExecutionResult.Status.SUCCESS) {
                    int exitCode = result.exitCode;
                    ringBuffer.addLine(exitCode == 0
                            ? "Execution finished successfully."
                            : "Execution finished with exit code: " + exitCode);
                } else {
                    ringBuffer.addLine("Execution failed: " + result.message);
                }
            } else {
                while (!STREAM_STOP_TOKEN.get() && process.isAlive()) {
                    Thread.sleep(150);
                }
                if (STREAM_STOP_TOKEN.get() && process.isAlive()) {
                    ProcessRuntimeOps.stopProcessWithTimeout(process, 2_000, 1_000);
                }
            }

            drainThread.join(2000L);
            if (degraded.get()) {
                String vmId = com.vectras.vm.main.core.MainStartVM.lastVMID;
                AuditLedger.record(VectrasApp.getContext(), new AuditEvent(
                        android.os.SystemClock.elapsedRealtime(),
                        System.currentTimeMillis(),
                        isNullOrEmpty(vmId) ? resolveCurrentVmId() : vmId,
                        "RUN",
                        "DEGRADED",
                        "LOG_FLOOD",
                        droppedLogs.get(),
                        bytesSeen.get(),
                        0,
                        "RATE_LIMIT"
                ));
                String finalVmId = isNullOrEmpty(vmId) ? resolveCurrentVmId() : vmId;
                VmFlowTracker.mark(VectrasApp.getContext(), finalVmId, VmFlowState.RUNNING, "log_flood_recovered", "resume");
            }
        } catch (Exception e) {
            ringBuffer.addLine(String.valueOf(e.getMessage()));
            Log.e(TAG, "streamLog: ", e);
        } finally {
            drainer.cancel();
            drainer.shutdown();
        }
        return new StringBuilder(ringBuffer.snapshot());
    }


    static boolean stopProcessWithTimeout(Process process, long gracefulTimeoutMs, long forcedTimeoutMs) {
        return ProcessRuntimeOps.stopProcessWithTimeout(process, gracefulTimeoutMs, forcedTimeoutMs);
    }

    private Context getContext() {
        if (context == null) {
            return VectrasApp.getContext();
        }
        return context;
    }
}
