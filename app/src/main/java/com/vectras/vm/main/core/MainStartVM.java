package com.vectras.vm.main.core;

import static android.os.Build.VERSION.SDK_INT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vectras.qemu.Config;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.qemu.MainVNCActivity;
import com.vectras.qemu.utils.QmpClient;
import com.vectras.vm.AppConfig;
import com.vectras.vm.BuildConfig;
import com.vectras.vm.MainService;
import com.vectras.vm.R;
import com.vectras.vm.StartVM;
import com.vectras.vm.VMManager;
import com.vectras.vm.logger.VectrasStatus;
import com.vectras.vm.core.VmFlowState;
import com.vectras.vm.core.VmFlowTracker;
import com.vectras.vm.qemu.VmLaunchLedger;
import com.vectras.vm.settings.ExternalVNCSettingsActivity;
import com.vectras.vm.setupwizard.SetupFeatureCore;
import com.vectras.vm.utils.DeviceUtils;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.utils.NetworkUtils;
import com.vectras.vm.utils.PackageUtils;
import com.vectras.vm.utils.ServiceUtils;
import com.vectras.vterm.Terminal;
import com.vectras.vm.rafaelia.RafaeliaBenchManager;
import com.vectras.vm.rafaelia.RafaeliaConfig;
import com.vectras.vm.rafaelia.RafaeliaEventRecorder;
import com.vectras.vm.rafaelia.RafaeliaSettings;

import java.io.File;
import java.util.concurrent.Future;
import java.io.IOException;
import java.net.ServerSocket;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MainStartVM {
    public static final String TAG = "HomeStartVM";
    public static AlertDialog progressDialog;
    public static boolean skipIDEwithARM64DialogInStartVM = false;
    public static boolean isStopNow = false;
    private static final LaunchPoller LAUNCH_POLLER = new LaunchPoller();
    private static final ExecutorService VNC_PASSWORD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicReference<ServerSocket> RESERVED_SPICE_PORT = new AtomicReference<>(null);
    private static volatile String reservedSpiceVmId = "";
    private static volatile Future<?> vncPasswordTask;

    public static String lastVMName = "";
    public static String lastEnv = "";
    public static String lastVMID = "";
    public static String lastThumbnailFile = "";
    public static String pendingVMName = "";
    public static String pendingEnv = "";
    public static String pendingVMID = "";
    public static String pendingThumbnailFile = "";
    public static boolean isLaunchFromPending = false;
    public static final String BASE_RUN_COMMAND_FORMAT = "export TMPDIR=/tmp && mkdir -p $TMPDIR/pulse && export XDG_RUNTIME_DIR=/tmp && chmod -R 775 $TMPDIR/pulse && pulseaudio --start --exit-idle-time=-1 > /dev/null 2>&1 && %s";

    private static final AtomicLong TRANSIENT_VM_ID_COUNTER = new AtomicLong(0L);
    private static final String TRANSIENT_VM_ID_PREFIX = "launch-";

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String createTransientLaunchVmId() {
        return TRANSIENT_VM_ID_PREFIX + System.currentTimeMillis() + "-" + TRANSIENT_VM_ID_COUNTER.incrementAndGet();
    }

    public static String ensureLastVmIdInitialized(String preferredVmId) {
        if (!isNullOrEmpty(preferredVmId)) {
            lastVMID = preferredVmId;
            return lastVMID;
        }
        if (isNullOrEmpty(lastVMID)) {
            lastVMID = createTransientLaunchVmId();
        }
        return lastVMID;
    }

    static String resolveRunCommandFormat(boolean isX11Ui, boolean isVmRunning, boolean useXterm) {
        if (!isX11Ui || isVmRunning) {
            return BASE_RUN_COMMAND_FORMAT;
        }

        String runWrapperFormat = useXterm ? "xterm -e bash -c \"%s\"" : "bash -c \"%s\"";
        return String.format(BASE_RUN_COMMAND_FORMAT, runWrapperFormat);
    }

    public static void startNow(
            Context context,
            String vmName,
            String env,
            String vmID,
            String thumbnailFile
    ) {
        stopLaunchPoller();

        boolean isX11Ui = MainSettingsManager.getVmUi(context).equals("X11");
        String runCommandFormat = resolveRunCommandFormat(
                isX11Ui,
                VMManager.isVMRunning(context, vmID),
                MainSettingsManager.getRunQemuWithXterm(context)
        );

        if (isLaunchFromPending) {
            isLaunchFromPending = false;
            if (pendingVMID.isEmpty()) {
                stopLaunchPoller();
                return;
            }
            pendingVMID = "";
        } else {
            lastVMName = vmName;
            lastEnv = env;
            lastVMID = ensureLastVmIdInitialized(vmID);
            lastThumbnailFile = thumbnailFile;

            if (isX11Ui && !VMManager.isVMRunning(context, vmID)) {
                if (DisplaySystem.isUseBuiltInX11()) {
                    pendingVMName = vmName;
                    pendingEnv = env;
                    pendingVMID = ensureLastVmIdInitialized(vmID);
                    pendingThumbnailFile = thumbnailFile;
                    DisplaySystem.launch(context);
                    stopLaunchPoller();
                    return;
                }
            }
        }

        isStopNow = false;

        String finalvmID = ensureLastVmIdInitialized(vmID);
        VmFlowTracker.mark(context, finalvmID, VmFlowState.STARTING, "launch_requested", "start");

        Config.vmID = finalvmID;
        boolean headless = AppConfig.engineHeadlessMode || env.contains("-display none") || env.contains("headless=true");
        if (headless) {
            Log.i(TAG, "engine-only mode enabled (headless=true)");
        }

        SetupFeatureCore.PreflightResult preflightResult = SetupFeatureCore.runVmStartPreflight(
                context,
                StartVM.requiredQemuBinary(context),
                new SetupFeatureCore.VmStartPreflightOptions(
                        MainSettingsManager.getVmUi(context),
                        MainSettingsManager.getRunQemuWithXterm(context),
                        headless
                )
        );
        if (!preflightResult.ok) {
            VectrasStatus.logError("VM preflight failed: " + preflightResult.shortSummary());
            VmLaunchLedger.append(
                    context,
                    finalvmID,
                    StartVM.lastResolvedProfile,
                    headless,
                    false,
                    preflightResult.ledgerReason(),
                    "PRECHECK_ABORT"
            );
            DialogUtils.twoDialog(
                    context,
                    context.getString(R.string.problem_has_been_detected),
                    preflightResult.uiSummary(),
                    context.getString(R.string.reinstall_system),
                    context.getString(R.string.cancel),
                    true,
                    R.drawable.warning_48px,
                    true,
                    () -> SetupFeatureCore.launchReinstallSetup(context),
                    null,
                    null
            );
            VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "preflight_failed", "abort");
            stopLaunchPoller();
            return;
        }

        File romDir = new File(Config.getCacheDir() + "/" + finalvmID);
        if (!romDir.exists()) {
            if (!romDir.mkdirs()) {
                DialogUtils.oneDialog(
                        context,
                        context.getString(R.string.problem_has_been_detected),
                        context.getString(R.string.vm_cache_dir_failed_to_create_content),
                        R.drawable.warning_48px
                );
                VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "cache_dir_create_failed", "abort");
                stopLaunchPoller();
                return;
            }
        }

        if (!VMManager.isthiscommandsafe(env, context.getApplicationContext())) {
            DialogUtils.oneDialog(
                    context,
                    context.getString(R.string.problem_has_been_detected),
                    context.getString(R.string.harmful_command_was_detected) + " " + context.getResources().getString(R.string.reason) + ": " + VMManager.latestUnsafeCommandReason,
                    R.drawable.verified_user_24px
            );
            VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "unsafe_command", "abort");
            stopLaunchPoller();
            return;
        }

        if (MainSettingsManager.getSharedFolder(context)
                && !MainSettingsManager.getArch(context).equals("I386")
                && FileUtils.getFolderSize(FileUtils.getExternalFilesDirectory(context).getPath() + "/SharedFolder") * Math.pow(10, -6) > 516) {
            DialogUtils.twoDialog(
                    context,
                    context.getString(R.string.problem_has_been_detected),
                    context.getString(R.string.shared_folder_is_too_large_content),
                    context.getString(R.string.open_shared_folder),
                    context.getString(R.string.close),
                    true,
                    R.drawable.warning_48px,
                    true,
                    () -> FileUtils.openFolder(context, FileUtils.getExternalFilesDirectory(context).getPath() + "/SharedFolder"),
                    null,
                    null
            );
            VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "shared_folder_too_large", "abort");
            stopLaunchPoller();
            return;
        }

        env = reserveSpicePortIfNeeded(context, finalvmID, env);
        if (env != null && env.contains(StartVM.SPICE_PORT_PLACEHOLDER)) {
            VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "spice_port_reservation_failed", "abort");
            stopLaunchPoller();
            return;
        }
        VMManager.lastQemuCommand = env;
        final String envFinal = env;

        if (VMManager.isVMRunning(context, finalvmID)) {
            VmFlowTracker.mark(context, finalvmID, VmFlowState.RUNNING, "already_running", "attach");
            Toast.makeText(context, "This VM is already running.", Toast.LENGTH_LONG).show();
            DisplaySystem.launch(context);
            stopLaunchPoller();
            return;
        }

        if (AppConfig.getSetupFiles().contains("arm") && !AppConfig.getSetupFiles().contains("arm64")) {
            if (envFinal.contains("tcg,thread=multi")) {
                DialogUtils.twoDialog(context, context.getResources().getString(R.string.problem_has_been_detected), context.getResources().getString(R.string.can_not_use_mttcg), context.getString(R.string.ok), context.getString(R.string.cancel), true, R.drawable.warning_48px, true,
                        () -> startNow(context, vmName, envFinal.replace("tcg,thread=multi", "tcg,thread=single"), finalvmID, thumbnailFile), null, null);
                stopLaunchPoller();
                return;
            }
        }

        if (MainSettingsManager.getArch(context).equals("ARM64") && MainSettingsManager.getIfType(context).equals("ide") && skipIDEwithARM64DialogInStartVM) {
            DialogUtils.twoDialog(context, context.getString(R.string.problem_has_been_detected), context.getString(R.string.you_cannot_use_IDE_hard_drive_type_with_ARM64), context.getString(R.string.continuetext), context.getString(R.string.cancel), true, R.drawable.warning_48px, true,
                    () -> {
                        skipIDEwithARM64DialogInStartVM = true;
                        startNow(context, vmName, envFinal, finalvmID, thumbnailFile);
                    }, null, null);
            stopLaunchPoller();
            return;
        } else if (skipIDEwithARM64DialogInStartVM) {
            skipIDEwithARM64DialogInStartVM = false;
        }

        if (MainSettingsManager.getSharedFolder(context) && MainSettingsManager.getArch(context).equals("I386")) {
            Toast.makeText(context, R.string.shared_folder_is_not_used_because_i386_does_not_support_it, Toast.LENGTH_LONG).show();
        }

        if (MainSettingsManager.getVncExternal(context) &&
                NetworkUtils.isPortOpen("localhost", Config.defaultVNCPort, 500)) {
            DialogUtils.twoDialog(context, context.getString(R.string.problem_has_been_detected),
                    "Unable to reserve a local SPICE port.",
                    context.getString(R.string.go_to_settings),
                    context.getString(R.string.close),
                    true, R.drawable.warning_48px, true,
                    () -> context.startActivity(new Intent(context, ExternalVNCSettingsActivity.class)),
                    null,
                    null);
            VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "vnc_port_in_use", "abort");
            stopLaunchPoller();
            return;
        }

        if (!headless) {
            showProgressDialog(context, vmName, thumbnailFile);
        }

        VMManager.isQemuStopedWithError = false;

        String finalCommand = VMManager.addAudioDevSdl(String.format(runCommandFormat, env));

        if (MainSettingsManager.getVmUi(context).equals("X11") && !headless) {
            finalCommand = "export DISPLAY=:0 && " + finalCommand;
            DisplaySystem.startDesktop(context);
        }
        if (BuildConfig.DEBUG) {
            Log.i(TAG, finalCommand);
        }

        VmLaunchLedger.append(
                context,
                finalvmID,
                StartVM.lastResolvedProfile,
                headless,
                StartVM.lastKvmEnabled,
                StartVM.lastKvmReason,
                finalCommand
        );

        RafaeliaConfig rafaeliaConfig = RafaeliaConfig.fromPreferences(context);
        if (rafaeliaConfig.getEnabled() && RafaeliaSettings.isLogCaptureEnabled(context)) {
            File logFile = RafaeliaSettings.logFile(context);
            if (logFile.exists()) {
                logFile.delete();
            }
        }

        RafaeliaEventRecorder.recordStart(context, vmName);

        if (ServiceUtils.isServiceRunning(context, MainService.class)) {
            MainService.startCommand(finalCommand, context);
        } else {
            Intent serviceIntent = new Intent(context, MainService.class);
            MainService.setActivityContext(context);
            MainService.env = finalCommand;
            MainService.CHANNEL_ID = vmName;
            if (SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }

        RafaeliaBenchManager.scheduleBenchIfNeeded(context, vmName);

        if (MainSettingsManager.getVmUi(context).equals("X11") && !DisplaySystem.isUseBuiltInX11()) {
            if (!PackageUtils.isInstalled("com.termux.x11", context)) {
                DialogUtils.needInstallTermuxX11(context);
                VmFlowTracker.mark(context, finalvmID, VmFlowState.ERROR, "termux_x11_missing", "abort");
                stopLaunchPoller();
                return;
            }
        }
        LAUNCH_POLLER.start(context, finalvmID, headless);

        String[] params = env.split("\\s+");
        VectrasStatus.logInfo("Params:");
        Log.d("HomeStartVM", "Params:");
        for (int i = 0; i < params.length; i++) {
            VectrasStatus.logInfo(i + ": " + params[i]);
            Log.d("HomeStartVM", i + ": " + params[i]);
        }

    }

    public static void startTryAgain(Context context) {
        startNow(context, lastVMName, lastEnv, lastVMID, lastThumbnailFile);
        VMManager.isTryAgain = false;
    }

    public static void startPending(Context context) {
        isLaunchFromPending = true;
        startNow(context, pendingVMName, pendingEnv, pendingVMID, pendingThumbnailFile);
    }

    public static void showProgressDialog(Context context, String _content, String thumbnailFile) {
        View progressView = LayoutInflater.from(context).inflate(R.layout.dialog_start_vm, null);
        TextView tvVMName = progressView.findViewById(R.id.vm_name);
        tvVMName.setText(_content);

        if (thumbnailFile != null) {
            ImageView ivThumbnail = progressView.findViewById(R.id.iv_thumbnail);

            if (thumbnailFile.isEmpty()) {
                VMManager.setIconWithName(ivThumbnail, _content);
            } else {
                if (FileUtils.isFileExists(thumbnailFile)) {
                    Glide.with(context.getApplicationContext())
                            .load(new File(thumbnailFile))
                            .placeholder(R.drawable.ic_computer_180dp_with_padding)
                            .error(R.drawable.ic_computer_180dp_with_padding)
                            .into(ivThumbnail);
                } else {
                    VMManager.setIconWithName(ivThumbnail, _content);
                }
            }
        }

        ImageView ivStop = progressView.findViewById(R.id.ivStop);
        ivStop.setOnClickListener(v -> {
            isStopNow = true;
            stopLaunchPoller();
            VMManager.shutdownCurrentVM();
        });

        progressDialog = new MaterialAlertDialogBuilder(context, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.show();
    }

    public static void setDefault() {
        // Compatibility no-op: command format is now immutable and derived locally in startNow(...).
    }

    private static void stopLaunchPoller() {
        LAUNCH_POLLER.stop();
        cancelPendingVncPasswordTask();
    }

    private static void dismissProgressDialog(Context context) {
        if (!(context instanceof Activity activity)) {
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }


    private static String reserveSpicePortIfNeeded(Context context, String vmId, String env) {
        releaseReservedSpicePort();
        reservedSpiceVmId = "";

        if (!MainSettingsManager.getVmUi(context).equals("SPICE")) {
            return env;
        }
        if (env == null || !env.contains(StartVM.SPICE_PORT_PLACEHOLDER)) {
            return env;
        }

        ServerSocket reservedPort = reserveRandomPort();
        if (reservedPort == null) {
            DialogUtils.oneDialog(
                    context,
                    context.getString(R.string.problem_has_been_detected),
                    "Unable to reserve a local SPICE port.",
                    R.drawable.warning_48px
            );
            return env;
        }

        RESERVED_SPICE_PORT.set(reservedPort);
        reservedSpiceVmId = vmId;
        return env.replace(StartVM.SPICE_PORT_PLACEHOLDER, String.valueOf(reservedPort.getLocalPort()));
    }

    private static void releaseReservedSpicePort() {
        ServerSocket reserved = RESERVED_SPICE_PORT.getAndSet(null);
        releaseReservedPort(reserved);
        reservedSpiceVmId = "";
    }

    private static void cancelPendingVncPasswordTask() {
        cancelVncPasswordTask();
    }

    private static ServerSocket reserveRandomPort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            return socket;
        } catch (IOException e) {
            Log.w(TAG, "Unable to reserve random port", e);
            return null;
        }
    }

    private static void releaseReservedPort(ServerSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static synchronized void submitVncPasswordTask(String password) {
        if (vncPasswordTask != null) {
            vncPasswordTask.cancel(true);
        }
        vncPasswordTask = VNC_PASSWORD_EXECUTOR.submit(() ->
                QmpClient.sendCommand(QmpClient.setVncPassword(password), 3, 500)
        );
    }

    private static synchronized void cancelVncPasswordTask() {
        if (vncPasswordTask != null) {
            vncPasswordTask.cancel(true);
            vncPasswordTask = null;
        }
    }

    private static void applyVncPasswordOverQmpIfNeeded(Context context) {
        if (!MainSettingsManager.getVmUi(context).equals("VNC")) {
            return;
        }

        if (!MainSettingsManager.getVncExternal(context)) {
            return;
        }

        String password = MainSettingsManager.getVncExternalPassword(context);
        if (password == null || password.isEmpty()) {
            return;
        }

        VNC_PASSWORD_EXECUTOR.execute(() -> QmpClient.sendCommand(QmpClient.setVncPassword(password), 3, 500));
    }

    private static final class LaunchPoller {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private WeakReference<Context> contextRef = new WeakReference<>(null);
        private Context appContext;
        private String vmId;
        private boolean headless;
        private boolean running;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                if (isStopNow || VMManager.isQemuStopedWithError || FileUtils.isFileExists(Config.getLocalQMPSocketPath())) {
                    stop();
                    Context uiContext = contextRef.get();
                    if (uiContext != null) {
                        dismissProgressDialog(uiContext);
                    }

                    if (!isStopNow && !VMManager.isQemuStopedWithError) {
                        Context launchContext = uiContext != null ? uiContext : appContext;
                        if (headless) {
                            Log.i(TAG, "engine-only launch completed without frontend attach");
                        } else if (MainSettingsManager.getVmUi(launchContext).equals("VNC")) {
                            applyVncPasswordOverQmpIfNeeded(launchContext);
                            String externalPassword = MainSettingsManager.getVncExternalPassword(launchContext);
                            boolean hasExternalPassword = externalPassword != null && !externalPassword.isEmpty();
                            if (MainSettingsManager.getVncExternal(launchContext) && hasExternalPassword) {
                                Config.currentVNCServervmID = vmId;
                                DialogUtils.oneDialog(launchContext,
                                        launchContext.getString(R.string.vnc_server),
                                        launchContext.getString(R.string.running_vm_with_vnc_server_content) + " " + (Integer.parseInt(MainSettingsManager.getVncExternalDisplay(launchContext)) + 5900) + ".",
                                        R.drawable.cast_24px
                                );
                            } else {
                                MainVNCActivity.started = true;
                                Intent intent = new Intent(launchContext, MainVNCActivity.class);
                                if (!(launchContext instanceof Activity)) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                }
                                launchContext.startActivity(intent);
                            }
                        } else if (MainSettingsManager.getVmUi(launchContext).equals("X11") && !DisplaySystem.isUseBuiltInX11()) {
                            DisplaySystem.launch(launchContext);
                        }

                        if (vmId != null && vmId.equals(reservedSpiceVmId)) {
                            releaseReservedSpicePort();
                        }
                        VmFlowTracker.mark(launchContext, vmId, VmFlowState.RUNNING, "launch_ready", "running");
                        Log.i(TAG, "Virtual machine running.");
                    }

                    skipIDEwithARM64DialogInStartVM = false;
                    return;
                }

                handler.postDelayed(this, 500);
            }
        };

        void start(Context context, String vmId, boolean headless) {
            stop();
            this.contextRef = new WeakReference<>(context);
            this.appContext = context.getApplicationContext();
            this.vmId = vmId;
            this.headless = headless;
            this.running = true;
            handler.postDelayed(tick, 1000);
        }

        void stop() {
            running = false;
            handler.removeCallbacksAndMessages(null);
        }
    }
}
