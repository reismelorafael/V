package com.vectras.vm.setupwizard;

import static android.content.Intent.ACTION_VIEW;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.TransitionManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.termux.app.TermuxActivity;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.VMManager;
import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.core.ProotCommandBuilder;
import com.vectras.vm.network.RequestNetwork;
import com.vectras.vm.network.RequestNetworkController;
import com.vectras.vm.databinding.ActivitySetupWizard2Binding;
import com.vectras.vm.databinding.ListViewBinding;
import com.vectras.vm.databinding.SetupQemuDoneBinding;
import com.vectras.vm.databinding.SimpleLayoutListViewWithCheckBinding;
import com.vectras.vm.main.MainActivity;
import com.vectras.vm.utils.DeviceUtils;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.utils.ClipboardUltils;
import com.vectras.vm.utils.JSONUtils;
import com.vectras.vm.utils.LibraryChecker;
import com.vectras.vm.utils.ListUtils;
import com.vectras.vm.utils.PermissionUtils;
import com.vectras.vm.utils.SafeFileName;
import com.vectras.vm.utils.TarUtils;
import com.vectras.vm.utils.UIUtils;
import com.vectras.vm.utils.CommandUtils;
import com.vectras.vterm.Terminal;
import com.vectras.vterm.TerminalBottomSheetDialog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetupWizard2Activity extends AppCompatActivity {
    private static final String TAG = "SetupWizard2Activity";
    public static final String ACTION_DEBUG_PROOT_SELF_CHECK = "com.vectras.vm.action.DEBUG_PROOT_SELF_CHECK";
    public static final String EXTRA_DEBUG_PROOT_SELF_CHECK = "debug_proot_self_check";
    private static final String BOOTSTRAP_PREFIX_ARIA2 = " aria2c -x 4 --async-dns=false --disable-ipv6 -o setup.tar.gz ";
    private static final String BOOTSTRAP_PREFIX_CURL = " curl -o setup.tar.gz -L ";
    private static final String[] BOOTSTRAP_COMPATIBLE_ABI_PREFIXES = new String[]{"arm64-v8a", "aarch64", "armeabi-v7a", "arm", "armhf", "x86_64", "amd64", "x86", "i686"};
    private static final Pattern ARIA2_PROGRESS_PATTERN = Pattern.compile("\\((\\d{1,3})%\\)");
    private static final Pattern CURL_PROGRESS_PATTERN = Pattern.compile("^\\s*(\\d{1,3})\\s+\\d");
    private static final Pattern PACKAGE_PROGRESS_PATTERN = Pattern.compile("\\((\\d+)/(\\d+)\\)");
    private static final Pattern BOOTSTRAP_HOST_PATTERN = Pattern.compile("^(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[A-Fa-f0-9]{64}$");
    private static final int MAX_LOG_BUFFER_CHARS = 64 * 1024;
    private static final String BOOTSTRAP_SIGNATURE_PUBLIC_KEY_PEM = "";

    private enum SetupSource {
        REMOTE,
        OFFLINE_FALLBACK,
        MANUAL_FILE,
        BUNDLED_ASSET
    }

    private enum InstallState {
        INIT,
        DOWNLOADING,
        STAGING,
        PROMOTING,
        VERIFYING,
        ROLLBACK,
        FAILED,
        COMPLETED
    }

    ActivitySetupWizard2Binding binding;
    SetupQemuDoneBinding bindingFinalSteps;
    public static final int ACTION_SYSTEM_UPDATE = 1;
    final int STEP_REQUEST_PERMISSION = 1;
    final int STEP_EXTRACTING_SYSTEM_FILES = 2;
    final int STEP_GETTING_DATA = 3;
    final int STEP_SETUP_OPTIONS = 4;
    final int STEP_INSTALLING_PACKAGES = 5;
    final int STEP_ERROR = 6;
    final int STEP_PATERON = 8;
    final int STEP_FINISH = 9;
    final int STEP_SYSTEM_UPDATE = -1;
    int currentStep = 0;
    String logs = "";
    String bootstrapFileLink = "";
    String bootstrapExpectedSha256 = "";
    String bootstrapExpectedSignature = "";
    String selectedMirrorCommand = "echo ";
    String selectedMirrorLocation = "";
    String downloadBootstrapsCommand = "";
    String tarPath = "";
    String setupArchiveFileName = "setup.tar.gz";
    String progressText ="0%";
    int setupProgressPercent = 0;
    boolean bootstrapDownloadActive = false;
    int packageInstallTotal = 0;
    int extractEntryCounter = 0;
    SetupSource setupSource = SetupSource.REMOTE;
    boolean isSystemUpdateMode = false;
    boolean isExecutingCommand = false;
    boolean isLibProotError = false;
    boolean aria2Error = false;
    boolean isServerError = false;
    boolean criticalSetupStderr = false;
    boolean isNotEnoughStorageSpace = false;
    boolean isCustomSetupMode = false;
    boolean pendingStandardSetupStart = false;
    boolean setupSuccessMarkerSeen = false;
    InstallState installState = InstallState.INIT;
    String installStateDetail = "";
    String normalizedLastError = "";
    String activeSetupTimestamp = "";
    String lastProotSelfCheckBlock = "";
    boolean rollbackAvailable = false;
    final ArrayList<HashMap<String, String>> mirrorList = new ArrayList<>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<Uri> storagePermissionLauncher =
            PermissionUtils.registerOpenDocumentTreeLauncher(this, uri -> {
                if (uri != null) {
                    Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show();
                    if (currentStep == STEP_REQUEST_PERMISSION) {
                        extractSystemFiles();
                    }
                } else {
                    UIUtils.toastShort(this, getString(R.string.storage_permission_explanation_android11));
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.edgeToEdge(this);
        binding = ActivitySetupWizard2Binding.inflate(getLayoutInflater());
        bindingFinalSteps = binding.layoutFinalSteps;
        setContentView(binding.getRoot());
        UIUtils.setOnApplyWindowInsetsListener(findViewById(R.id.main));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > STEP_PATERON) {
                    uiControllerFinalSteps(currentStep - 1);
                } else if (!isExecutingCommand) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        initialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreSetupSnapshot();
        if (currentStep == 1 && PermissionUtils.storagepermission(this, false)) {
            extractSystemFiles();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadingIndicatorController(currentStep);
    }

    private void initialize() {
        tarPath = getExternalFilesDir("data") + "/data.tar.gz";

        ListUtils.setupMirrorListForListmap(mirrorList);
        applySelectedMirror(MainSettingsManager.getSelectedMirror(this));

        String persistedBootstrapLink = MainSettingsManager.getLastSetupBootstrapUrl(this);
        if (isBootstrapLinkValid(persistedBootstrapLink)) {
            bootstrapFileLink = persistedBootstrapLink;
            bootstrapExpectedSha256 = "";
            bootstrapExpectedSignature = "";
            downloadBootstrapsCommand = buildBootstrapDownloadCommand(bootstrapFileLink, false);
        }

        bindingFinalSteps.main.setVisibility(View.GONE);

        if (!DeviceUtils.is64bit()) binding.ln32BitWarning.setVisibility(View.VISIBLE);

        binding.btnLetStart.setOnClickListener(v -> {
            if (PermissionUtils.storagepermission(this, false)) {
                extractSystemFiles();
            } else {
                uiController(STEP_REQUEST_PERMISSION);
            }
        });

        binding.btnAllowPermission.setOnClickListener(v -> PermissionUtils.requestStoragePermission(this, storagePermissionLauncher));

        binding.standardSetupOption.setOnClickListener(v -> {
            if (downloadBootstrapsCommand.isEmpty()) {
                pendingStandardSetupStart = true;
                showStandardSetupUnavailableDialog();
            } else {
                pendingStandardSetupStart = false;
                isCustomSetupMode = false;
                startSetup();
            }

        });

        binding.customSetupOption.setOnClickListener(v -> bootstrapFilePicker.launch("*/*"));

        binding.selectMirrorOption.setOnClickListener(v -> selectMirror());

        binding.ivOpenTerminal.setOnClickListener(v -> {
            if (DeviceUtils.is64bit() && DeviceUtils.isArm()) {
                startActivity(new Intent(this, TermuxActivity.class));
            } else {
                TerminalBottomSheetDialog VTERM = new TerminalBottomSheetDialog(this);
                VTERM.showVterm();
            }
        });
        binding.ivOpenTerminal.setOnLongClickListener(v -> {
            triggerDebugProotSelfCheck("ui-long-press");
            return true;
        });

        binding.btnRetrySetup.setOnClickListener(v -> retrySetupIdempotent());

        binding.btnRollbackSetup.setOnClickListener(v -> {
            if (!rollbackAvailable || TextUtils.isEmpty(activeSetupTimestamp)) {
                UIUtils.toastShort(this, getString(R.string.setup_rollback_not_available));
                return;
            }
            transitionInstallState(InstallState.ROLLBACK, "Manual rollback requested.");
            executeBestEffortRollback(activeSetupTimestamp, "manual rollback from ui");
            uiController(STEP_ERROR, logs);
        });

        binding.btnExportDiagnostic.setOnClickListener(v -> exportSetupDiagnostic());

        //Final steps
        bindingFinalSteps.tvLater.setOnClickListener(v -> uiControllerFinalSteps(currentStep + 1));

        bindingFinalSteps.btnContinue.setOnClickListener(v -> {
            if (currentStep == STEP_PATERON) {
                uiControllerFinalSteps(currentStep + 1);
                Intent intent = new Intent(ACTION_VIEW, Uri.parse(AppConfig.patreonLink));
                startActivity(intent);
            } else {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });


        //System update
        binding.btnSkipSystemUpdate.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        binding.btnSystemUpdate.setOnClickListener(v -> {
            uiController(STEP_EXTRACTING_SYSTEM_FILES);
            new Thread(() -> {
                VMManager.killallqemuprocesses(this);
                FileUtils.deleteDirectory(getFilesDir().getAbsolutePath() + "/data");
                FileUtils.deleteDirectory(getFilesDir().getAbsolutePath() + "/distro");
                FileUtils.deleteDirectory(getFilesDir().getAbsolutePath() + "/usr");
                runOnUiThread(this::extractSystemFiles);
            }).start();
        });

        Intent launchIntent = getIntent();
        if (ACTION_DEBUG_PROOT_SELF_CHECK.equals(launchIntent.getAction())
                || launchIntent.getBooleanExtra(EXTRA_DEBUG_PROOT_SELF_CHECK, false)
                ) {
            triggerDebugProotSelfCheck("intent");
        }

        if (launchIntent.hasExtra("action")) {
            if (launchIntent.getIntExtra("action", -1) == ACTION_SYSTEM_UPDATE) {
                isSystemUpdateMode = true;
                uiController(STEP_SYSTEM_UPDATE);
            }
        }
    }

    private void triggerDebugProotSelfCheck(String triggerOrigin) {
        executor.execute(() -> {
            boolean checkOk = SetupFeatureCore.runProotSelfCheck(this).ok;
            Log.i(TAG, "runProotSelfCheck trigger=" + triggerOrigin + " ok=" + checkOk);
        });
    }

    private void uiController(int step) {
        uiController(step, "");
    }

    private void uiController(int step, String log) {
        TransitionManager.beginDelayedTransition(binding.main);

        binding.lnWelcome.setVisibility(View.GONE);
        binding.lnAllowPermission.setVisibility(View.GONE);
        binding.lnExtractingSystemFiles.setVisibility(View.GONE);
        binding.lnGettingData.setVisibility(View.GONE);
        binding.lnSetupOptions.setVisibility(View.GONE);
        binding.lnInstallingPackages.setVisibility(View.GONE);
        binding.lnSystemUpdate.setVisibility(View.GONE);
        binding.lnInstallingPackagesFailed.setVisibility(View.GONE);

        TransitionManager.beginDelayedTransition(binding.main);

        if (step == STEP_REQUEST_PERMISSION) {
            binding.lnAllowPermission.setVisibility(View.VISIBLE);
        } else if (step == STEP_EXTRACTING_SYSTEM_FILES) {
            binding.lnExtractingSystemFiles.setVisibility(View.VISIBLE);
        } else if (step == STEP_GETTING_DATA) {
            binding.lnGettingData.setVisibility(View.VISIBLE);
        } else if (step == STEP_SETUP_OPTIONS) {
            binding.lnSetupOptions.setVisibility(View.VISIBLE);
        } else if (step == STEP_INSTALLING_PACKAGES) {
            binding.lnInstallingPackages.setVisibility(View.VISIBLE);
        } else if (step == STEP_SYSTEM_UPDATE) {
            binding.lnSystemUpdate.setVisibility(View.VISIBLE);
        } else if (step == STEP_ERROR) {
            binding.lnInstallingPackagesFailed.setVisibility(View.VISIBLE);
            binding.tvErrorLogContent.setText(log.isEmpty() ? getString(R.string.there_are_no_logs) : log);

            if (isNotEnoughStorageSpace) {
                binding.ivErrorLarge.setImageResource(R.drawable.disc_full_100px);
                binding.tvErrorTitle.setText(getString(R.string.not_enough_storage_space));
                binding.tvErrorSubtitle.setText(getString(R.string.not_enough_storage_to_set_up_content));
            } else if (isLibProotError) {
                binding.ivErrorLarge.setImageResource(R.drawable.error_96px);
                binding.tvErrorTitle.setText(getString(R.string.vectras_vm_cannot_run_on_this_device));
                binding.tvErrorSubtitle.setText(getString(R.string.a_serious_problem_has_occurred));
            } else if (isServerError || aria2Error) {
                binding.ivErrorLarge.setImageResource(R.drawable.android_wifi_3_bar_alert_100px);
                binding.tvErrorTitle.setText(getString(R.string.unable_to_connect_to_server));
                binding.tvErrorSubtitle.setText(getString(R.string.check_your_internet_connection));
            } else {
                binding.ivErrorLarge.setImageResource(R.drawable.error_96px);
                binding.tvErrorTitle.setText(getString(R.string.something_went_wrong));
                binding.tvErrorSubtitle.setText(getString(R.string.the_setup_could_not_be_completed_and_below_is_the_log));
            }
            updateStructuredStatusUi();
        } else if (step == STEP_PATERON) {
            bindingFinalSteps.main.setVisibility(View.VISIBLE);
        }

        loadingIndicatorController(step);

        currentStep = step;
    }

    private void loadingIndicatorController(int step) {
        float dp = 200f;
        float px = dp * getResources().getDisplayMetrics().density;

        if (step == STEP_EXTRACTING_SYSTEM_FILES) {
            binding.lnExtractingSystemFilesCpiContainer.post(() -> {
                int heightPx = binding.lnExtractingSystemFilesCpiContainer.getHeight();

                if (heightPx < px) {
                    binding.cpiExtractingSystemFiles.setVisibility(View.GONE);
                    binding.lpiExtractingSystemFiles.setVisibility(View.VISIBLE);
                } else {
                    binding.cpiExtractingSystemFiles.setVisibility(View.VISIBLE);
                    binding.lpiExtractingSystemFiles.setVisibility(View.GONE);
                }
            });
        } else if (step == STEP_GETTING_DATA) {
            binding.lnGettingDataCpiContainer.post(() -> {
                int heightPx = binding.lnGettingDataCpiContainer.getHeight();

                if (heightPx < px) {
                    binding.cpiGettingData.setVisibility(View.GONE);
                    binding.lpiGettingData.setVisibility(View.VISIBLE);
                } else {
                    binding.cpiGettingData.setVisibility(View.VISIBLE);
                    binding.lpiGettingData.setVisibility(View.GONE);
                }
            });
        } else if (step == STEP_INSTALLING_PACKAGES) {
            binding.lnInstallingPackagesCpiContainer.post(() -> {
                int heightPx = binding.lnInstallingPackagesCpiContainer.getHeight();

                if (heightPx < px) {
                    binding.cpiInstallingPackages.setVisibility(View.GONE);
                    binding.lpiInstallingPackages.setVisibility(View.VISIBLE);
                } else {
                    binding.cpiInstallingPackages.setVisibility(View.VISIBLE);
                    binding.lpiInstallingPackages.setVisibility(View.GONE);
                }
            });
        }
    }

    private void uiControllerFinalSteps(int step) {
        TransitionManager.beginDelayedTransition(bindingFinalSteps.mainContent);

        bindingFinalSteps.lineardonate.setVisibility(View.GONE);
        bindingFinalSteps.linearwelcomehome.setVisibility(View.GONE);

        TransitionManager.beginDelayedTransition(bindingFinalSteps.mainContent);

        if (step == STEP_PATERON) {
            bindingFinalSteps.lineardonate.setVisibility(View.VISIBLE);
            bindingFinalSteps.tvLater.setVisibility(View.VISIBLE);
            bindingFinalSteps.btnContinue.setText(getString(R.string.join));
        } else if (step == STEP_FINISH) {
            bindingFinalSteps.linearwelcomehome.setVisibility(View.VISIBLE);
            bindingFinalSteps.tvLater.setVisibility(View.GONE);
            bindingFinalSteps.btnContinue.setText(getString(R.string.done));
        }

        currentStep = step;
    }

    private void extractSystemFiles() {
        uiController(STEP_EXTRACTING_SYSTEM_FILES);

        executor.execute(() -> {
            isNotEnoughStorageSpace = DeviceUtils.isStorageLow(this, false);
            runOnUiThread(() -> {
                if (isNotEnoughStorageSpace) {
                    uiController(STEP_ERROR);
                    return;
                }

                new Thread(() -> {
                    boolean result = SetupFeatureCore.startExtractSystemFiles(this, bootstrapExpectedSha256);

                    runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (result) {
                            runAndDisplayProotSelfCheck();
                            SetupFeatureCore.PostInstallCheckResult postInstallCheckResult = SetupFeatureCore.runPostInstallCheck(this);
                            if (postInstallCheckResult.ok) {
                                getDataForStandardSetup();
                            } else {
                                uiController(STEP_ERROR, withSetupSourceDiagnostic(postInstallCheckResult.summary()));
                            }
                        } else {
                            uiController(STEP_ERROR, withSetupSourceDiagnostic(getString(R.string.system_files_installation_failed_content) + (!SetupFeatureCore.lastErrorLog.isEmpty() ? "\n\n" + SetupFeatureCore.lastErrorLog : "")));
                        }
                    }, 1000));
                }).start();
            });
        });
    }

    private void getDataForStandardSetup() {
        uiController(STEP_GETTING_DATA);
        setSetupSource(SetupSource.REMOTE, "Requesting setup metadata from remote endpoint.");

        RequestNetwork net = new RequestNetwork(this);
        RequestNetwork.RequestListener _net_request_listener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                boolean hasResolvedBootstrap;
                if (JSONUtils.isValidFromString(response)) {
                    HashMap<String, Object> mmap = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {
                    }.getType());
                    hasResolvedBootstrap = updateBootstrapForCurrentArchitecture(mmap);
                    if (hasResolvedBootstrap) {
                        setSetupSource(SetupSource.REMOTE, "Remote bootstrap URL resolved successfully.");
                    }
                } else {
                    hasResolvedBootstrap = false;
                }

                if (!hasResolvedBootstrap) {
                    hasResolvedBootstrap = applyOfflineBootstrapFallback(false);
                }

                Log.d(TAG, "getDataForStandardSetup resolved=" + hasResolvedBootstrap + ", source=" + setupSource);
                final boolean resolvedBootstrap = hasResolvedBootstrap;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isSystemUpdateMode && resolvedBootstrap) {
                        startSetup();
                    } else if (pendingStandardSetupStart && resolvedBootstrap) {
                        pendingStandardSetupStart = false;
                        isCustomSetupMode = false;
                        startSetup();
                    } else {
                        pendingStandardSetupStart = false;
                        uiController(STEP_SETUP_OPTIONS);
                    }
                }, 1000);
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                Log.d(TAG, "getDataForStandardSetup onErrorResponse message=" + message + ", source=" + setupSource);
                applyOfflineBootstrapFallback();
                boolean hasResolvedBootstrap = !downloadBootstrapsCommand.isEmpty();
                boolean shouldNotifyUnavailable = pendingStandardSetupStart && !hasResolvedBootstrap;
                pendingStandardSetupStart = false;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    uiController(STEP_SETUP_OPTIONS);
                    if (shouldNotifyUnavailable) {
                        showStandardSetupUnavailableDialog();
                    }
                }, 1000);
            }
        };

        net.startRequestNetwork(RequestNetworkController.GET, AppConfig.bootstrapfileslink, "", _net_request_listener);
    }

    private void startSetup() {
        if (!isCustomSetupMode && downloadBootstrapsCommand.isEmpty()) {
            pendingStandardSetupStart = false;
            uiController(STEP_SETUP_OPTIONS);
            showStandardSetupUnavailableDialog();
            return;
        }

        uiController(STEP_INSTALLING_PACKAGES);

        new Thread(() -> {
            if (isCustomSetupMode) {
                runOnUiThread(() -> appendTextAndScroll(" | " + getString(R.string.checking)));

                try {
                    if (!TarUtils.isAllowExtract(tarPath)) {
                        runOnUiThread(() -> uiController(STEP_ERROR, withSetupSourceDiagnostic(getString(R.string.this_bootstrap_file_is_invalid))));
                        return;
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> uiController(STEP_ERROR, withSetupSourceDiagnostic(e.toString())));
                    return;
                }
            }

            runOnUiThread(() -> {
                logs = "";
                progressText = "";
                setupProgressPercent = 0;
                bootstrapDownloadActive = false;
                packageInstallTotal = 0;
                extractEntryCounter = 0;
                aria2Error = false;
                isServerError = false;
                setupSuccessMarkerSeen = false;
                normalizedLastError = "";
                transitionInstallState(InstallState.INIT, "Setup started.");
                String vncPassword = MainSettingsManager.getVncExternalPassword(this);
                if (vncPassword == null || vncPassword.isEmpty()) {
                    vncPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    MainSettingsManager.setVncExternalPassword(this, vncPassword);
                }
                String escapedVncPassword = CommandUtils.shellSingleQuote(vncPassword);
                LibraryChecker.PackageManagerType managerType = LibraryChecker.detectPackageManagerType(this);
                String requiredPackages = resolveRequiredPackages(managerType);
                String updateCommand = resolveUpdateCommand(managerType);
                String installCommand = LibraryChecker.buildInstallCommand(managerType, requiredPackages);

            //   # PackageManagerType packageManagerType = detectPackageManagerType();
           //   #  String updateCommand = resolveUpdateCommand(packageManagerType);
            // #   String installCommand = resolveInstallCommand(packageManagerType);
           // #    String requiredPackages = resolveRequiredPackages(packageManagerType);

                criticalSetupStderr = false;
                String setupTimestamp = String.valueOf(Instant.now().toEpochMilli());
                activeSetupTimestamp = setupTimestamp;
                rollbackAvailable = true;
                persistSetupSnapshot();
                String stagingBase = "/root/.vectras-staging";
                String backupBase = "/root/.vectras-backups";
                String stateBase = "/root/.vectras-setup";
                String stageDir = stagingBase + "/" + setupTimestamp;
                String stageRoot = stageDir + "/rootfs";
                String setupArchive = stageDir + "/setup.tar.gz";

                String bootstrapAcquireCommand;
                String expectedSha256ForAudit = bootstrapExpectedSha256;
                String expectedSignatureForAudit = bootstrapExpectedSignature;
                if (isCustomSetupMode) {
                    setupArchive = stageDir + "/" + setupArchiveFileName;
                    bootstrapAcquireCommand = "cp " + CommandUtils.shellSingleQuote(tarPath) + " " + CommandUtils.shellSingleQuote(setupArchive);
                    expectedSha256ForAudit = "";
                    expectedSignatureForAudit = "";
                } else {
                    if (FileUtils.isFileExists(getFilesDir().getAbsolutePath() + "/distro/root/setup.tar.gz"))
                        FileUtils.deleteDirectory(getFilesDir().getAbsolutePath() + "/distro/root/setup.tar.gz");
                    if (FileUtils.isFileExists(getFilesDir().getAbsolutePath() + "/distro/root/setup.tar"))
                        FileUtils.deleteDirectory(getFilesDir().getAbsolutePath() + "/distro/root/setup.tar");
                    bootstrapAcquireCommand = "cd '" + stageDir + "' && " + downloadBootstrapsCommand;
                }

                String hasExpectedSha256 = !TextUtils.isEmpty(expectedSha256ForAudit) ? "1" : "0";
                String expectedSha256Quoted = CommandUtils.shellSingleQuote(TextUtils.isEmpty(expectedSha256ForAudit) ? "" : expectedSha256ForAudit.toLowerCase(Locale.ROOT));
                String hasExpectedSignature = !TextUtils.isEmpty(expectedSignatureForAudit) ? "1" : "0";
                String expectedSignatureQuoted = CommandUtils.shellSingleQuote(expectedSignatureForAudit == null ? "" : expectedSignatureForAudit);
                String signatureKeyQuoted = CommandUtils.shellSingleQuote(BOOTSTRAP_SIGNATURE_PUBLIC_KEY_PEM == null ? "" : BOOTSTRAP_SIGNATURE_PUBLIC_KEY_PEM);

                Log.i(TAG, "AUDIT setup command template=mirror;set -e;... | setupTs=" + setupTimestamp + " | customSetup=" + isCustomSetupMode + " | setupArchive=" + setupArchive + " | stageDir=" + stageDir + " | mirrorLocation=" + selectedMirrorLocation + " | bootstrapSource=" + setupSource + " | hasExpectedSha256=" + hasExpectedSha256 + " | hasExpectedSig=" + hasExpectedSignature);

                String cmd = selectedMirrorCommand + ";" +
                        " set -e;" +
                        " SETUP_TS='" + setupTimestamp + "';" +
                        " STAGING_BASE='" + stagingBase + "';" +
                        " BACKUP_BASE='" + backupBase + "';" +
                        " STATE_BASE='" + stateBase + "';" +
                        " STAGE_DIR='" + stageDir + "';" +
                        " STAGE_ROOT='" + stageRoot + "';" +
                        " SETUP_ARCHIVE='" + setupArchive + "';" +
                        " EXPECTED_SHA256=" + expectedSha256Quoted + ";" +
                        " EXPECTED_SIG_B64=" + expectedSignatureQuoted + ";" +
                        " SIGN_PUBKEY_PEM=" + signatureKeyQuoted + ";" +
                        " HAS_EXPECTED_SHA256='" + hasExpectedSha256 + "';" +
                        " HAS_EXPECTED_SIG='" + hasExpectedSignature + "';" +
                        " STATE_FILE='" + stateBase + "/setup_state.json';" +
                        " mkdir -p \"$STAGING_BASE\" \"$BACKUP_BASE\" \"$STATE_BASE\" \"$STAGE_ROOT\";" +
                        " ln -sfn \"$STAGE_DIR\" \"$STAGING_BASE/latest\";" +
                        " write_state(){ PHASE=\"$1\"; MSG=\"$2\"; printf '{\\\"version\\\":1,\\\"timestamp\\\":\\\"%s\\\",\\\"phase\\\":\\\"%s\\\",\\\"stage_dir\\\":\\\"%s\\\",\\\"message\\\":\\\"%s\\\"}\\n' \"$SETUP_TS\" \"$PHASE\" \"$STAGE_DIR\" \"$MSG\" > \"$STATE_FILE\"; };" +
                        " rollback_setup(){ REASON=\"$1\"; write_state ROLLBACK \"$REASON\"; echo STATE_TRANSITION:ROLLBACK; echo \"CRITICAL_STDERR: rollback reason=$REASON\"; if [ -d \"$BACKUP_BASE/current/usr-local-bin\" ]; then rm -rf /usr/local/bin; cp -a \"$BACKUP_BASE/current/usr-local-bin\" /usr/local/bin; fi; if [ -f \"$BACKUP_BASE/current/etc-profile\" ]; then cp -a \"$BACKUP_BASE/current/etc-profile\" /etc/profile; fi; rm -rf \"$STAGE_DIR\"; };" +
                        " write_state INIT 'Preparing staging pipeline'; echo STATE_TRANSITION:INIT;" +
                        " echo \"Starting setup...\";" +
                        " echo STATE_TRANSITION:DOWNLOADING;" +
                        " " + updateCommand + " || { rollback_setup 'update command failed'; exit 41; };" +
                        " echo \"Installing packages...\";" +
                        " " + installCommand + " || { rollback_setup 'package install failed'; exit 42; };" +
                        " echo \"Downloading Qemu...\";" +
                        " " + bootstrapAcquireCommand + " || { rollback_setup 'bootstrap acquisition failed'; exit 43; };" +
                        " ARCHIVE_SHA256=\"$(sha256sum \"$SETUP_ARCHIVE\" | awk '{print $1}')\";" +
                        " echo \"AUDIT bootstrap integrity metadata source=" + setupSource + " expected_sha256=$EXPECTED_SHA256 actual_sha256=$ARCHIVE_SHA256 origin_url=" + CommandUtils.shellSingleQuote(bootstrapFileLink) + "\";" +
                        " if [ \"$HAS_EXPECTED_SHA256\" = '1' ]; then [ \"$ARCHIVE_SHA256\" = \"$EXPECTED_SHA256\" ] || { rollback_setup \"bootstrap sha256 mismatch expected=$EXPECTED_SHA256 actual=$ARCHIVE_SHA256\"; exit 49; }; else echo 'AUDIT bootstrap sha256 missing in metadata; skipping hash enforcement'; fi;" +
                        " if [ \"$HAS_EXPECTED_SIG\" = '1' ]; then if [ -n \"$SIGN_PUBKEY_PEM\" ] && command -v openssl >/dev/null 2>&1; then printf '%s' \"$EXPECTED_SIG_B64\" | base64 -d > \"$STAGE_DIR/bootstrap.sig\" || { rollback_setup 'bootstrap signature decode failed'; exit 50; }; printf '%s\n' \"$SIGN_PUBKEY_PEM\" > \"$STAGE_DIR/bootstrap.pub\"; openssl dgst -sha256 -verify \"$STAGE_DIR/bootstrap.pub\" -signature \"$STAGE_DIR/bootstrap.sig\" \"$SETUP_ARCHIVE\" >/dev/null 2>&1 || { rollback_setup 'bootstrap signature verification failed'; exit 51; }; echo 'AUDIT bootstrap signature verification=passed'; else echo 'AUDIT bootstrap signature metadata provided but verifier unavailable; skipping'; fi; fi;" +
                        " echo STATE_TRANSITION:STAGING;" +
                        " echo \"Installing Qemu...\";" +
                        " if gzip -t \"$SETUP_ARCHIVE\" >/dev/null 2>&1; then tar -xzf \"$SETUP_ARCHIVE\" -C \"$STAGE_ROOT\"; else tar -xf \"$SETUP_ARCHIVE\" -C \"$STAGE_ROOT\"; fi || { rollback_setup 'bootstrap extraction failed'; exit 44; };" +
                        " test -d \"$STAGE_ROOT/usr/local/bin\" || { rollback_setup 'missing /usr/local/bin in staging'; exit 45; };" +
                        " test -x \"$STAGE_ROOT/usr/local/bin/qemu-system-x86_64\" -o -x \"$STAGE_ROOT/usr/local/bin/qemu-system-aarch64\" || { rollback_setup 'missing qemu binary in staging'; exit 46; };" +
                        " chmod 775 \"$STAGE_ROOT\"/usr/local/bin/* || { rollback_setup 'invalid binary permissions'; exit 47; };" +
                        " write_state STAGING 'Staging validation completed';" +
                        " mkdir -p \"$BACKUP_BASE/$SETUP_TS\";" +
                        " [ -L \"$BACKUP_BASE/current\" ] && ln -sfn \"$(readlink -f \"$BACKUP_BASE/current\")\" \"$BACKUP_BASE/previous\" || true;" +
                        " cp -a /usr/local/bin \"$BACKUP_BASE/$SETUP_TS/usr-local-bin\";" +
                        " cp -a /etc/profile \"$BACKUP_BASE/$SETUP_TS/etc-profile\";" +
                        " ln -sfn \"$BACKUP_BASE/$SETUP_TS\" \"$BACKUP_BASE/current\";" +
                        " rm -rf /usr/local/bin; mv \"$STAGE_ROOT/usr/local/bin\" /usr/local/bin || { rollback_setup 'promotion failed'; exit 48; };" +
                        " echo STATE_TRANSITION:PROMOTING;" +
                        " write_state PROMOTING 'Promotion finished'; echo STATE_TRANSITION:VERIFYING;" +
                        " rm -f \"$SETUP_ARCHIVE\";" +
                        " echo \"Just a sec...\";" +
                        " grep -q 'export TMPDIR=/tmp' /etc/profile || echo export TMPDIR=/tmp >> /etc/profile;" +
                        " mkdir -p $TMPDIR/pulse;" +
                        " grep -q 'export PULSE_SERVER=127.0.0.1' /etc/profile || echo export PULSE_SERVER=127.0.0.1 >> /etc/profile;" +
                        " mkdir -p ~/.vnc && printf '%s\n' " + escapedVncPassword + " " + escapedVncPassword + " | vncpasswd -f > ~/.vnc/passwd && chmod 0600 ~/.vnc/passwd;" +
                        " rm -rf \"$STAGE_DIR\";" +
                        " write_state VERIFYING 'Post-promotion checks done';" +
                        " write_state COMPLETED 'Setup completed';" +
                        " echo STATE_TRANSITION:COMPLETED;" +
                        " echo \"Installation successful! xssFjnj58Id\"";

                executeShellCommand(cmd, setupTimestamp);
            });
        }).start();
    }

    private void showStandardSetupUnavailableDialog() {
        DialogUtils.threeDialog(SetupWizard2Activity.this,
                getString(R.string.oops),
                getString(R.string.standard_setup_unavailable_no_network_no_cache),
                getString(R.string.try_again),
                getString(R.string.ok),
                getString(R.string.use_bundled_bootstrap_package),
                true,
                R.drawable.warning_48px,
                true,
                this::getDataForStandardSetup,
                null,
                this::startBundledBootstrapSetup,
                null);
    }

    private void startBundledBootstrapSetup() {
        uiController(STEP_INSTALLING_PACKAGES);
        new Thread(() -> {
            if (!prepareBundledBootstrapArchive()) {
                runOnUiThread(() -> uiController(STEP_ERROR, withSetupSourceDiagnostic(getString(R.string.unable_to_prepare_bundled_bootstrap_package))));
                return;
            }

            runOnUiThread(() -> {
                isCustomSetupMode = true;
                setSetupSource(SetupSource.BUNDLED_ASSET, "Using bundled bootstrap package from app assets.");
                startSetup();
            });
        }).start();
    }

    private boolean prepareBundledBootstrapArchive() {
        String selectedAssetPath = null;
        String selectedAbi = null;
        for (String abiCandidate : BootstrapAbiMapper.resolveCandidates(Build.SUPPORTED_ABIS)) {
            String candidatePath = "alpine19/" + abiCandidate + ".tar";
            try (InputStream ignored = getAssets().open(candidatePath)) {
                selectedAssetPath = candidatePath;
                selectedAbi = abiCandidate;
                break;
            } catch (IOException ignored) {
            }
        }

        if (selectedAssetPath == null) {
            Log.e(TAG, "PROOT_BOOTSTRAP ABI_RESOLUTION_FAIL bundled candidates="
                    + BootstrapAbiMapper.resolveCandidates(Build.SUPPORTED_ABIS));
            return false;
        }

        setupArchiveFileName = "setup.tar";
        Log.i(TAG, "PROOT_BOOTSTRAP ABI_SELECTED bundled selectedAbi=" + selectedAbi + " asset=" + selectedAssetPath);

        try (InputStream input = getAssets().open(selectedAssetPath);
             FileOutputStream output = new FileOutputStream(tarPath)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            Log.i(SetupFeatureCore.ABI_RESOLVE_TAG, "Bundled bootstrap prepared from path=" + selectedAssetPath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to prepare bundled bootstrap archive: " + selectedAssetPath, e);
            return false;
        }
    }

    private String resolveRequiredPackages(LibraryChecker.PackageManagerType managerType) {
        switch (managerType) {
            case APK:
                return DeviceUtils.is64bit() ? AppConfig.neededPkgsAlpine() : AppConfig.neededPkgs32bitAlpine();
            case APT:
                return DeviceUtils.is64bit() ? AppConfig.neededPkgsDebianUbuntu() : AppConfig.neededPkgs32bitDebianUbuntu();
            case PKG:
                return DeviceUtils.is64bit() ? AppConfig.neededPkgsTermux() : AppConfig.neededPkgs32bitTermux();
            case UNKNOWN:
            default:
                return DeviceUtils.is64bit() ? AppConfig.neededPkgsAlpine() : AppConfig.neededPkgs32bitAlpine();
        }
    }

    private String resolveUpdateCommand(LibraryChecker.PackageManagerType managerType) {
        switch (managerType) {
            case PKG:
                return "pkg update -y";
            case APT:
                return "apt-get update";
            case APK:
            case UNKNOWN:
            default:
                return "apk update";
        }
    }

    private final ActivityResultLauncher<String> bootstrapFilePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String fileName = FileUtils.getFileNameFromUri(this, uri);
                    if (isCompatibleBootstrapArchive(fileName)) {
                        try {
                            SafeFileName.normalizeFromDisplayName(fileName);
                        } catch (IllegalArgumentException invalidName) {
                            Log.e(TAG, "Rejected setup archive name from URI: " + fileName + " uri=" + uri, invalidName);
                            uiController(STEP_ERROR, withSetupSourceDiagnostic(getString(R.string.the_file_could_not_be_processed_content) + "\n" + invalidName.getMessage()));
                            return;
                        }

                        setupArchiveFileName = fileName.endsWith(".tar") ? "setup.tar" : "setup.tar.gz";
                        uiController(STEP_INSTALLING_PACKAGES);
                        new Thread(() -> {
                            try {
                                FileUtils.copyFileFromUri(this, uri, tarPath);
                                runOnUiThread(() -> {
                                    isCustomSetupMode = true;
                                    setSetupSource(SetupSource.MANUAL_FILE, "Custom setup tar selected by user.");
                                    startSetup();
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to import setup archive from URI: " + uri, e);
                                runOnUiThread(() -> uiController(STEP_ERROR, withSetupSourceDiagnostic(getString(R.string.the_file_could_not_be_processed_content) + "\n" + e.getMessage())));
                            }
                        }).start();
                    } else {
                        DialogUtils.oneDialog(this,
                                getString(R.string.invalid_file),
                                getString(R.string.please_select) + " vectras-vm-<abi>.tar.gz",
                                getResources().getString(R.string.ok),
                                true,
                                R.drawable.warning_48px,
                                true,
                                null,
                                null);
                    }
                }
            });

    public void executeShellCommand(String userCommand, String setupTimestamp) {
        isExecutingCommand = true;
        new Thread(() -> {
            try {
                String filesDir = getFilesDir().getAbsolutePath();
                String tmpDirPath = getFilesDir().getAbsolutePath() + "/usr/tmp";
                SetupFeatureCore.ProotBootstrapValidationResult prootValidation = SetupFeatureCore.validateProotBootstrapState(this);
                if (!prootValidation.ok) {
                    String validationError = "PROOT_PREFLIGHT_FAIL:" + prootValidation.summary();
                    Log.e(TAG, validationError);
                    runOnUiThread(() -> uiController(STEP_ERROR, withSetupSourceDiagnostic(validationError)));
                    isExecutingCommand = false;
                    return;
                }

                ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(this, filesDir + "/distro", "/root")
                        .setPath("/bin:/usr/bin:/sbin:/usr/sbin")
                        .setTmpDir(tmpDirPath);
                ProcessBuilder processBuilder = new ProcessBuilder(prootCommandBuilder.buildCommand());
                prootCommandBuilder.applyEnvironment(processBuilder.environment());
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    writer.write(userCommand);
                    writer.newLine();
                    writer.flush();

                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String outputLine = line;
                        runOnUiThread(() -> appendTextAndScroll(outputLine + "\n"));
                    }
                }

                ProcessRuntimeOps.TimeoutExecutionResult result = ProcessRuntimeOps.waitForByCategory(
                        process,
                        ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION
                );

                if (result.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.TIMEOUT) {
                    isExecutingCommand = false;
                    final String timeoutMessage = "Command timed out ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "]: "
                            + result.message;
                    Log.e(TAG, withSetupSourceDiagnostic(timeoutMessage));
                    executeBestEffortRollback(setupTimestamp, "timeout during setup");
                    runOnUiThread(() -> {
                        appendTextAndScroll("Error: " + withSetupSourceDiagnostic(timeoutMessage) + "\n");
                        uiController(STEP_ERROR, logs);
                    });
                    return;
                }

                if (result.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR) {
                    isExecutingCommand = false;
                    final String operationErrorMessage = "Command execution error ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "]: "
                            + result.message;
                    Log.e(TAG, withSetupSourceDiagnostic(operationErrorMessage));
                    executeBestEffortRollback(setupTimestamp, "execution error during setup");
                    runOnUiThread(() -> {
                        appendTextAndScroll("Error: " + withSetupSourceDiagnostic(operationErrorMessage) + "\n");
                        uiController(STEP_ERROR, logs);
                    });
                    return;
                }

                int exitValue = result.exitCode;
                if (exitValue == 0 && criticalSetupStderr) {
                    isExecutingCommand = false;
                    executeBestEffortRollback(setupTimestamp, "critical stderr detected");
                    runOnUiThread(() -> {
                        appendTextAndScroll("Error: " + withSetupSourceDiagnostic("critical stderr detected during setup") + "\n");
                        uiController(STEP_ERROR, logs);
                    });
                    return;
                }

                if (exitValue != 0) {
                    isExecutingCommand = false;
                    if (aria2Error && downloadBootstrapsCommand.contains("aria2c")) {
                        runOnUiThread(() -> {
                            downloadBootstrapsCommand = buildBootstrapDownloadCommand(bootstrapFileLink, true);
                            startSetup();
                        });
                    } else {
                        executeBestEffortRollback(setupTimestamp, "non-zero exit code: " + exitValue);
                        runOnUiThread(() -> {
                            String toastMessage = "Command failed with exit code: " + exitValue;
                            appendTextAndScroll("Error: " + withSetupSourceDiagnostic(toastMessage) + "\n");
                            uiController(STEP_ERROR, logs);
                        });
                    }
                    return;
                }

                if (!setupSuccessMarkerSeen) {
                    isExecutingCommand = false;
                    handleSetupFailureWithRollback(
                            setupTimestamp,
                            "success marker missing",
                            SetupFeatureCore.formatPostCheckFailure(java.util.Arrays.asList("success-marker-missing"))
                    );
                    return;
                }

                SetupFeatureCore.SetupPostCheckResult postCheckResult = SetupFeatureCore.runSetupPostCheck(this);
                if (!postCheckResult.ok) {
                    isExecutingCommand = false;
                    String postCheckIdentifier = postCheckResult.technicalReason();
                    handleSetupFailureWithRollback(setupTimestamp, postCheckIdentifier, postCheckIdentifier);
                    return;
                }

                isExecutingCommand = false;
                runOnUiThread(() -> {
                    transitionInstallState(InstallState.COMPLETED, "Setup completed successfully.");
                    clearSetupSnapshot();
                    finalizeSetupSuccess();
                });
            } catch (IOException e) {
                isExecutingCommand = false;
                Log.e(TAG, withSetupSourceDiagnostic("executeShellCommand IO error: " + e.getMessage()), e);
                handleSetupFailureWithRollback(
                        setupTimestamp,
                        "io error during setup",
                        SetupFeatureCore.formatPostCheckFailure(java.util.Arrays.asList("io-error"))
                );
            }
        }).start();
    }

    private void triggerStepErrorWithSetupDiagnostic(String errorIdentifier) {
        String safeErrorIdentifier = errorIdentifier == null || errorIdentifier.trim().isEmpty()
                ? SetupFeatureCore.POST_CHECK_FAIL_PREFIX + "unknown"
                : errorIdentifier.trim();
        normalizedLastError = normalizeSetupError(safeErrorIdentifier);
        transitionInstallState(InstallState.FAILED, normalizedLastError);
        Log.e(TAG, withSetupSourceDiagnostic(safeErrorIdentifier));
        appendTextAndScroll("Error: " + withSetupSourceDiagnostic(safeErrorIdentifier) + "\n");
        uiController(STEP_ERROR, withSetupSourceDiagnostic(logs));
    }

    private void handleSetupFailureWithRollback(String setupTimestamp, String rollbackReason, String errorIdentifier) {
        executeBestEffortRollback(setupTimestamp, rollbackReason);
        runOnUiThread(() -> triggerStepErrorWithSetupDiagnostic(errorIdentifier));
    }

    private String validatePostInstallSynchronously(String setupTimestamp) {
        if (setupTimestamp == null || setupTimestamp.isEmpty()) {
            return "missing setup timestamp";
        }

        String validationCommand = "set -e; " +
                "STATE_FILE='/root/.vectras-setup/setup_state.json'; " +
                "test -f /usr/local/bin/qemu-system-x86_64 -o -f /usr/local/bin/qemu-system-aarch64 || exit 61; " +
                "test -f \"$STATE_FILE\" || exit 62; " +
                "grep -q '\"phase\":\"COMPLETED\"' \"$STATE_FILE\" || exit 63; " +
                "grep -q '\"timestamp\":\"" + setupTimestamp + "\"' \"$STATE_FILE\" || exit 64;";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            String filesDir = getFilesDir().getAbsolutePath();
            String tmpDirPath = getFilesDir().getAbsolutePath() + "/usr/tmp";
            SetupFeatureCore.ProotBootstrapValidationResult prootValidation = SetupFeatureCore.validateProotBootstrapState(this);
            if (!prootValidation.ok) {
                return "validation prerequisites failed: " + prootValidation.summary();
            }
            ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(this, filesDir + "/distro", "/root")
                    .setPath("/bin:/usr/bin:/sbin:/usr/sbin")
                    .setTmpDir(tmpDirPath);
            prootCommandBuilder.applyEnvironment(processBuilder.environment());
            processBuilder.command(prootCommandBuilder.buildCommand());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                writer.write(validationCommand);
                writer.newLine();
                writer.flush();

                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append(" | ");
                    }
                    output.append(line);
                }
            }

            ProcessRuntimeOps.TimeoutExecutionResult validationWaitResult = ProcessRuntimeOps.waitForByCategory(
                    process,
                    ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION
            );

            if (validationWaitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.TIMEOUT) {
                return "validation timeout: " + validationWaitResult.message;
            }

            if (validationWaitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR) {
                return "validation execution error: " + validationWaitResult.message;
            }

            if (validationWaitResult.exitCode != 0) {
                String processOutput = output.length() == 0 ? "no validation output" : output.toString();
                return "validation exit code " + validationWaitResult.exitCode + " | " + processOutput;
            }

            return null;
        } catch (Exception e) {
            return "validation exception: " + e.getMessage();
        }
    }

    private void executeBestEffortRollback(String setupTimestamp, String reason) {
        if (setupTimestamp == null || setupTimestamp.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                String sanitizedReason = reason == null ? "unknown error" : reason.replace("\"", "").replace("\n", " ");
                String rollbackCommand = "set -e; " +
                        "BACKUP_BASE=/root/.vectras-backups; " +
                        "STAGE_DIR=/root/.vectras-staging/" + setupTimestamp + "; " +
                        "mkdir -p /root/.vectras-setup; " +
                        "if [ -d \"$BACKUP_BASE/current/usr-local-bin\" ]; then rm -rf /usr/local/bin; cp -a \"$BACKUP_BASE/current/usr-local-bin\" /usr/local/bin; fi; " +
                        "if [ -f \"$BACKUP_BASE/current/etc-profile\" ]; then cp -a \"$BACKUP_BASE/current/etc-profile\" /etc/profile; fi; " +
                        "rm -rf \"$STAGE_DIR\"; " +
                        "echo ROLLBACK > /root/.vectras-setup/setup_state.json";
                ProcessBuilder processBuilder = new ProcessBuilder();
                String filesDir = getFilesDir().getAbsolutePath();
                String tmpDirPath = getFilesDir().getAbsolutePath() + "/usr/tmp";
                SetupFeatureCore.ProotBootstrapValidationResult prootValidation = SetupFeatureCore.validateProotBootstrapState(this);
                if (!prootValidation.ok) {
                    Log.e(TAG, "PROOT_PREFLIGHT_FAIL:" + prootValidation.summary());
                    return;
                }
                ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(this, filesDir + "/distro", "/root")
                        .setPath("/bin:/usr/bin:/sbin:/usr/sbin")
                        .setTmpDir(tmpDirPath);
                prootCommandBuilder.applyEnvironment(processBuilder.environment());
                processBuilder.command(prootCommandBuilder.buildCommand());
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(rollbackCommand);
                    writer.newLine();
                    writer.flush();
                }
                process.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Best-effort rollback failed: " + e.getMessage(), e);
            }
        }).start();
    }

    @SuppressLint("SetTextI18n")
    private void appendTextAndScroll(String newLog) {
        appendToLogs(newLog);
        String normalizedLog = newLog.toLowerCase();

        if (newLog.contains("STATE_TRANSITION:")) {
            handleStateTransitionLog(newLog);
        }

        if (newLog.contains("xssFjnj58Id")) {
            setupSuccessMarkerSeen = true;
        } else if (normalizedLog.contains("libproot.so --help") || normalizedLog.contains("/bin/sh: can't fork:")) {
            isLibProotError = true;
        } else if (normalizedLog.contains("not complete: /root/setup.tar.gz") || normalizedLog.contains("download not complete")) {
            aria2Error = true;
        } else if (normalizedLog.contains("temporary error") || normalizedLog.contains("server returned") || normalizedLog.contains("http response header was bad")) {
            isServerError = true;
        } else if (normalizedLog.contains("critical_stderr:")
                || normalizedLog.contains("permission denied")
                || normalizedLog.contains("no such file or directory")
                || normalizedLog.contains("cannot stat")
                || normalizedLog.contains("segmentation fault")) {
            criticalSetupStderr = true;
        }

        updateProgressText(newLog);

        binding.tvLastestCommandResult.setText(progressText + "[" + setupSource + "] " + newLog);
    }

    private void appendToLogs(String newLog) {
        logs += newLog;
        if (logs.length() > MAX_LOG_BUFFER_CHARS) {
            logs = logs.substring(logs.length() - MAX_LOG_BUFFER_CHARS);
        }
    }

    private void updateProgressText(String newLog) {
        if (newLog.contains("Starting setup...")) {
            advanceSetupProgress(3);
        } else if (newLog.contains("fetch http") || newLog.contains("Hit:") || newLog.contains("Get:")) {
            advanceSetupProgress(8);
        } else if (newLog.contains("Installing packages...")) {
            packageInstallTotal = 0;
            advanceSetupProgress(12);
        }

        if (setupProgressPercent < 70) {
            Matcher packageProgressMatcher = PACKAGE_PROGRESS_PATTERN.matcher(newLog);
            if (packageProgressMatcher.find()) {
                int done = safeParseInt(packageProgressMatcher.group(1));
                int total = safeParseInt(packageProgressMatcher.group(2));
                if (total > 0) {
                    packageInstallTotal = Math.max(packageInstallTotal, total);
                    int normalizedDone = Math.min(done, packageInstallTotal);
                    int mappedPackageProgress = 12 + (normalizedDone * 58) / packageInstallTotal;
                    advanceSetupProgress(Math.min(mappedPackageProgress, 70));
                }
            }
        }

        if (newLog.contains("Downloading Qemu...")) {
            bootstrapDownloadActive = true;
            advanceSetupProgress(70);
        }

        if (bootstrapDownloadActive) {
            Matcher aria2Matcher = ARIA2_PROGRESS_PATTERN.matcher(newLog);
            if (aria2Matcher.find()) {
                int downloadPercent = safeParseInt(aria2Matcher.group(1));
                int mappedDownloadProgress = 70 + Math.min(10, Math.max(0, (downloadPercent * 10) / 100));
                advanceSetupProgress(mappedDownloadProgress);
            }

            Matcher curlMatcher = CURL_PROGRESS_PATTERN.matcher(newLog);
            if (curlMatcher.find()) {
                int curlPercent = safeParseInt(curlMatcher.group(1));
                int mappedCurlProgress = 70 + Math.min(10, Math.max(0, (curlPercent * 10) / 100));
                advanceSetupProgress(mappedCurlProgress);
            }
        }

        if (newLog.contains("Installing Qemu...")) {
            bootstrapDownloadActive = false;
            advanceSetupProgress(80);
        }

        if (newLog.contains("Installing Qemu...")) {
            extractEntryCounter = 0;
        }

        if (newLog.contains("STATE_TRANSITION:PROMOTING")) {
            advanceSetupProgress(90);
        }

        if (newLog.contains("qemu-system")) {
            advanceSetupProgress(92);
        }

        if (newLog.contains("Just a sec...")) {
            advanceSetupProgress(97);
        }

        if (newLog.contains("xssFjnj58Id")) {
            advanceSetupProgress(100);
        }

        progressText = setupProgressPercent + "% | " + installState + " | ";
    }

    private void finalizeSetupSuccess() {
        MainSettingsManager.setStandardSetupVersion(this, AppConfig.standardSetupVersion);
        MainSettingsManager.setsetUpWithManualSetupBefore(this, isCustomSetupMode);
        clearSetupSnapshot();
        uiController(STEP_PATERON);
        if (isSystemUpdateMode) {
            uiControllerFinalSteps(STEP_FINISH);
        } else {
            uiControllerFinalSteps(STEP_PATERON);
        }
    }

    private void advanceSetupProgress(int targetPercent) {
        if (targetPercent > setupProgressPercent) {
            setupProgressPercent = targetPercent;
        }
    }

    private int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }


    private void retrySetupIdempotent() {
        if (isSystemUpdateMode) {
            uiController(STEP_SYSTEM_UPDATE);
            binding.btnSkipSystemUpdate.setVisibility(View.GONE);
            return;
        }

        if (isLibProotError) {
            Intent intent = new Intent();
            intent.setAction(ACTION_VIEW);
            intent.setData(Uri.parse(AppConfig.community));
            startActivity(intent);
            return;
        }

        transitionInstallState(InstallState.INIT, "Retry requested by user.");
        if (SetupFeatureCore.isInstalledSystemFiles(this)) {
            runAndDisplayProotSelfCheck();
            SetupFeatureCore.PostInstallCheckResult postInstallCheckResult = SetupFeatureCore.runPostInstallCheck(this);
            if (postInstallCheckResult.ok) {
                getDataForStandardSetup();
            } else {
                uiController(STEP_ERROR, withSetupSourceDiagnostic(postInstallCheckResult.summary()));
            }
        } else {
            extractSystemFiles();
        }
    }

    private void runAndDisplayProotSelfCheck() {
        try {
            java.lang.reflect.Method runSelfCheckMethod = SetupFeatureCore.class.getMethod("runProotSelfCheck", Context.class);
            Object result = runSelfCheckMethod.invoke(null, this);
            if (result == null) {
                lastProotSelfCheckBlock = "prootSelfCheck=unavailable\nreason=result-null";
            } else {
                java.lang.reflect.Method structuredTextMethod = result.getClass().getMethod("toStructuredText");
                Object structuredTextValue = structuredTextMethod.invoke(result);
                lastProotSelfCheckBlock = structuredTextValue == null
                        ? "prootSelfCheck=unavailable\nreason=structured-text-null"
                        : structuredTextValue.toString();
            }
        } catch (Exception e) {
            lastProotSelfCheckBlock = "prootSelfCheck=unavailable\nreason=" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }

        for (String line : lastProotSelfCheckBlock.split("\\R")) {
            if (!line.trim().isEmpty()) {
                Log.i(SetupFeatureCore.ABI_RESOLVE_TAG, line);
            }
        }
        appendTextAndScroll("\n[PROOT_SELF_CHECK]\n" + lastProotSelfCheckBlock + "\n");
    }

    private void exportSetupDiagnostic() {
        String diagnostic = "state=" + installState + "\n"
                + "detail=" + installStateDetail + "\n"
                + "lastError=" + normalizedLastError + "\n"
                + "setupSource=" + setupSource + "\n"
                + "timestamp=" + activeSetupTimestamp + "\n"
                + "prootSelfCheck=" + (lastProotSelfCheckBlock.isEmpty() ? "not-run" : "captured") + "\n"
                + (lastProotSelfCheckBlock.isEmpty() ? "" : "\n[PROOT_SELF_CHECK]\n" + lastProotSelfCheckBlock + "\n")
                + "\n"
                + logs;
        ClipboardUltils.copyToClipboard(this, diagnostic);
        UIUtils.toastShort(this, getString(R.string.export_results));
    }

    private void handleStateTransitionLog(String line) {
        int idx = line.indexOf("STATE_TRANSITION:");
        if (idx < 0) {
            return;
        }
        String token = line.substring(idx + "STATE_TRANSITION:".length()).trim();
        token = token.split("\\s+")[0].trim();
        try {
            InstallState parsed = InstallState.valueOf(token);
            transitionInstallState(parsed, "Transition from setup runtime.");
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void transitionInstallState(InstallState state, String detail) {
        installState = state;
        installStateDetail = detail == null ? "" : detail;
        if (state == InstallState.FAILED) {
            rollbackAvailable = !TextUtils.isEmpty(activeSetupTimestamp);
        } else if (state == InstallState.COMPLETED) {
            rollbackAvailable = false;
        }
        persistSetupSnapshot();
        updateStructuredStatusUi();
    }

    private void updateStructuredStatusUi() {
        if (binding == null) {
            return;
        }
        binding.tvInstallCurrentStep.setText(installState.name());
        binding.tvInstallLastError.setText(normalizedLastError.isEmpty() ? getString(R.string.setup_status_no_error) : normalizedLastError);
        String action = getString(R.string.setup_action_retry);
        if (installState == InstallState.FAILED && rollbackAvailable) {
            action = getString(R.string.setup_action_rollback);
        }
        binding.tvInstallRecommendedAction.setText(action);
        binding.btnRollbackSetup.setEnabled(rollbackAvailable);
    }

    private String normalizeSetupError(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return getString(R.string.something_went_wrong);
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith(SetupFeatureCore.POST_CHECK_FAIL_PREFIX)
                || trimmed.startsWith(SetupFeatureCore.COPY_FAIL_PREFIX)
                || trimmed.startsWith(SetupFeatureCore.INTEGRITY_FAIL_PREFIX)
                || trimmed.startsWith(SetupFeatureCore.EXTRACTION_FAIL_PREFIX)) {
            return trimmed;
        }
        String lowered = trimmed.toLowerCase();
        if (lowered.contains("timeout")) {
            return "timeout";
        }
        if (lowered.contains("network") || lowered.contains("temporary error") || lowered.contains("unable_to_connect")) {
            return "network-unavailable";
        }
        if (lowered.contains("permission") || lowered.contains("cannot stat") || lowered.contains("no such file")) {
            return "filesystem-access";
        }
        if (lowered.contains("segmentation fault") || lowered.contains("libproot")) {
            return "runtime-failure";
        }
        return trimmed;
    }

    private void persistSetupSnapshot() {
        MainSettingsManager.setSetupInstallState(this, installState.name());
        MainSettingsManager.setSetupInstallStateDetail(this, installStateDetail);
        MainSettingsManager.setSetupInstallTimestamp(this, activeSetupTimestamp);
    }

    private void restoreSetupSnapshot() {
        String persistedState = MainSettingsManager.getSetupInstallState(this);
        if (persistedState == null || persistedState.isEmpty()) {
            return;
        }
        try {
            installState = InstallState.valueOf(persistedState);
        } catch (IllegalArgumentException ignored) {
            installState = InstallState.INIT;
        }
        installStateDetail = MainSettingsManager.getSetupInstallStateDetail(this);
        activeSetupTimestamp = MainSettingsManager.getSetupInstallTimestamp(this);
        rollbackAvailable = !TextUtils.isEmpty(activeSetupTimestamp)
                && installState != InstallState.COMPLETED
                && installState != InstallState.INIT;
        updateStructuredStatusUi();
    }

    private void clearSetupSnapshot() {
        MainSettingsManager.clearSetupInstallSnapshot(this);
    }

    private String buildBootstrapDownloadCommand(String link, boolean forceCurl) {
        String sanitizedBootstrapUrl = sanitizeBootstrapUrl(link);
        if (sanitizedBootstrapUrl == null) {
            return "";
        }

        Log.i(TAG, "SETUP_URL_NORMALIZED raw=" + link + " normalized=" + sanitizedBootstrapUrl);

        String template = forceCurl
                ? "curl -o setup.tar.gz -L <bootstrap-url>"
                : "aria2c -x 4 --async-dns=false --disable-ipv6 -o setup.tar.gz <bootstrap-url>";
        Log.i(TAG, "AUDIT bootstrap download template=" + template + " | url=" + sanitizedBootstrapUrl);

        String prefix = forceCurl ? BOOTSTRAP_PREFIX_CURL : BOOTSTRAP_PREFIX_ARIA2;
        return prefix + CommandUtils.shellSingleQuote(sanitizedBootstrapUrl);
    }

    private boolean isBootstrapLinkValid(String link) {
        return sanitizeBootstrapUrl(link) != null;
    }

    private static String sanitizeBootstrapUrl(String link) {
        if (link == null) {
            return null;
        }

        String trimmed = link.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            Uri parsed = Uri.parse(trimmed);
            String scheme = parsed.getScheme();
            String host = parsed.getHost();

            if (scheme == null || host == null) {
                return null;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
                return null;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!BOOTSTRAP_HOST_PATTERN.matcher(normalizedHost).matches()) {
                return null;
            }

            String authority = parsed.getEncodedAuthority();
            if (authority == null || authority.contains("@")) {
                return null;
            }

            String normalizedPath = BootstrapUrlNormalizer.normalizePath(parsed.getEncodedPath());
            Uri normalized = parsed.buildUpon()
                    .scheme(normalizedScheme)
                    .encodedPath(normalizedPath)
                    .build();
            String normalizedUrl = normalized.toString();
            Log.i(TAG, "PROOT_BOOTSTRAP URL_NORMALIZED raw=" + trimmed + " normalized=" + normalizedUrl);
            return normalizedUrl;
        } catch (Exception e) {
            return null;
        }
    }


    private boolean applyOfflineBootstrapFallback(boolean forceCurlDownload) {
        if (isBootstrapLinkValid(bootstrapFileLink)) {
            if (downloadBootstrapsCommand.isEmpty()) {
                downloadBootstrapsCommand = buildBootstrapDownloadCommand(bootstrapFileLink, forceCurlDownload);
            }
            if (TextUtils.isEmpty(downloadBootstrapsCommand)) {
                bootstrapExpectedSha256 = "";
                bootstrapExpectedSignature = "";
            }
            setSetupSource(SetupSource.OFFLINE_FALLBACK, "Using cached bootstrap link already loaded in memory.");
            return !downloadBootstrapsCommand.isEmpty();
        }

        String persistedBootstrapLink = MainSettingsManager.getLastSetupBootstrapUrl(this);
        if (isBootstrapLinkValid(persistedBootstrapLink)) {
            bootstrapFileLink = persistedBootstrapLink;
            bootstrapExpectedSha256 = "";
            bootstrapExpectedSignature = "";
            downloadBootstrapsCommand = buildBootstrapDownloadCommand(bootstrapFileLink, forceCurlDownload);
            setSetupSource(SetupSource.OFFLINE_FALLBACK, "Using persisted bootstrap URL as offline fallback.");
            runOnUiThread(() -> UIUtils.toastShort(this, getString(R.string.this_option_is_temporarily_unavailable_because_the_server_cannot_be_connected)));
            return !downloadBootstrapsCommand.isEmpty();
        }

        Log.d(TAG, "Offline fallback unavailable, source remains=" + setupSource);
        return false;
    }

    private void applyOfflineBootstrapFallback() {
        applyOfflineBootstrapFallback(false);
    }

    private void setSetupSource(SetupSource source, String reason) {
        setupSource = source;
        Log.d(TAG, "Setup source set to " + setupSource + " | " + reason);
    }

    private String withSetupSourceDiagnostic(String message) {
        return "[setupSource=" + setupSource + "] " + message;
    }

    private boolean updateBootstrapForCurrentArchitecture(HashMap<String, Object> bootstrapMap) {
        if (bootstrapMap == null) {
            return false;
        }

        String architectureKey = "";
        Object architectureConfig = null;
        ArrayList<String> attemptedKeys = new ArrayList<>();
        for (String abiCandidate : BootstrapAbiMapper.resolveCandidates(Build.SUPPORTED_ABIS)) {
            String metadataKey = BootstrapAbiMapper.architectureMetadataKey(abiCandidate);
            attemptedKeys.add(metadataKey);
            Object candidateConfig = bootstrapMap.get(metadataKey);
            if (candidateConfig != null) {
                architectureKey = metadataKey;
                architectureConfig = candidateConfig;
                break;
            }
        }
        if (architectureConfig == null) {
            Log.e(TAG, "PROOT_BOOTSTRAP ABI_RESOLUTION_FAIL metadataKeys=" + attemptedKeys);
            return false;
        }
        String resolvedBootstrapUrl;
        String resolvedSha256 = "";
        String resolvedSignature = "";

        if (architectureConfig instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> architectureMap = (Map<String, Object>) architectureConfig;
            Object bootstrapUrlObject = architectureMap.get("url");
            resolvedBootstrapUrl = bootstrapUrlObject == null ? "" : bootstrapUrlObject.toString().trim();
            Object sha256Object = architectureMap.get("sha256");
            if (sha256Object != null) {
                String candidate = sha256Object.toString().trim();
                if (SHA256_PATTERN.matcher(candidate).matches()) {
                    resolvedSha256 = candidate.toLowerCase(Locale.ROOT);
                } else if (!candidate.isEmpty()) {
                    Log.w(TAG, "AUDIT bootstrap metadata contains invalid sha256 for architecture=" + architectureKey);
                }
                Object sigObject = architectureMap.get("sig");
                resolvedSignature = sigObject == null ? "" : sigObject.toString().trim();
            } else {
                resolvedBootstrapUrl = architectureConfig.toString().trim();
            }

            if (!isBootstrapLinkValid(resolvedBootstrapUrl)) {
                Log.w(SetupFeatureCore.ABI_RESOLVE_TAG, "Invalid bootstrap URL for key=" + architectureKey + " value=" + resolvedBootstrapUrl);
                return false;
            }

            bootstrapFileLink = resolvedBootstrapUrl;
            bootstrapExpectedSha256 = resolvedSha256;
            bootstrapExpectedSignature = resolvedSignature;
            downloadBootstrapsCommand = buildBootstrapDownloadCommand(bootstrapFileLink, false);
            Log.i(SetupFeatureCore.ABI_RESOLVE_TAG, "Resolved metadata key=" + architectureKey + " origin=" + bootstrapFileLink);
            Log.i(TAG, "AUDIT bootstrap metadata resolved architecture=" + architectureKey
                    + " | hasSha256=" + (!TextUtils.isEmpty(bootstrapExpectedSha256))
                    + " | hasSig=" + (!TextUtils.isEmpty(bootstrapExpectedSignature))
                    + " | origin=" + bootstrapFileLink);
            MainSettingsManager.setLastSetupBootstrapUrl(this, bootstrapFileLink);
            return !downloadBootstrapsCommand.isEmpty();
        }

        String error = SetupFeatureCore.buildAbiResolutionError(
                "No remote bootstrap metadata entry matched the current device architecture.",
                Build.SUPPORTED_ABIS,
                attemptedKeys,
                "bootstrap"
        ) + " | Metadata keys=" + bootstrapMap.keySet();
        Log.e(SetupFeatureCore.ABI_RESOLVE_TAG, error);
        return false;
    }

    private void selectMirror() {
        ListViewBinding listViewBinding = ListViewBinding.inflate(getLayoutInflater());
        SpinnerSelectMirrorAdapter adapter =
                new SpinnerSelectMirrorAdapter(this, mirrorList);

        listViewBinding.list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(listViewBinding.getRoot())
                .create();

        listViewBinding.list.setOnItemClickListener((parent, view1, position, id) -> {
            MainSettingsManager.setSelectedMirror(SetupWizard2Activity.this, position);
            applySelectedMirror(position);

            dialog.dismiss();
        });

        listViewBinding.list.post(() -> listViewBinding.list.setSelection(MainSettingsManager.getSelectedMirror(this)));

        dialog.show();
    }

    private void applySelectedMirror(int position) {
        if (mirrorList.isEmpty()) {
            selectedMirrorCommand = "echo ";
            selectedMirrorLocation = "";
            return;
        }

        int safePosition = Math.max(0, Math.min(position, mirrorList.size() - 1));
        HashMap<String, String> item = mirrorList.get(safePosition);

        String mirrorCommandCandidate = item.get("mirror");
        String locationCandidate = item.get("location");
        if (mirrorCommandCandidate == null || locationCandidate == null) {
            Log.w(TAG, "AUDIT mirror selection fallback: null entry at index=" + safePosition);
            selectedMirrorCommand = "echo ";
            selectedMirrorLocation = "";
            return;
        }

        String expectedMirrorCommand;
        try {
            expectedMirrorCommand = buildMirrorCommandForLocation(locationCandidate);
        } catch (IllegalArgumentException invalidMirrorConfig) {
            Log.w(TAG, "AUDIT mirror selection rejected invalid internal config location=" + locationCandidate + " reason=" + invalidMirrorConfig.getMessage());
            selectedMirrorCommand = "echo ";
            selectedMirrorLocation = "";
            return;
        }

        if (!mirrorCommandCandidate.equals(expectedMirrorCommand)) {
            Log.w(TAG, "AUDIT mirror command mismatch for location=" + locationCandidate + ". Enforcing internal template.");
        }

        selectedMirrorCommand = expectedMirrorCommand;
        selectedMirrorLocation = locationCandidate;
        Log.i(TAG, "AUDIT mirror command template=printf '%s\n' <repo-main> <repo-community> <repo-edge-testing> > /etc/apk/repositories | location=" + locationCandidate);
    }

    private String buildMirrorCommandForLocation(String location) {
        switch (location) {
            case "Default":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "dl-cdn.alpinelinux.org", "/alpine");
            case "Australia":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(false, "mirror.aarnet.edu.au", "/pub/alpine");
            case "Austria":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.alwyzon.net", "/alpine");
            case "Bulgaria":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirrors.neterra.net", "/alpine");
            case "Brazil":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.uepg.br", "/alpine");
            case "Cambodia":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.sabay.com.kh", "/alpine");
            case "Canada":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.csclub.uwaterloo.ca", "/alpine");
            case "Chile":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "elmirror.cl", "/alpine");
            case "China":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirrors.tuna.tsinghua.edu.cn", "/alpine");
            case "Czech Republic":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.fel.cvut.cz", "/alpine");
            case "Denmark":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirrors.dotsrc.org", "/alpine");
            case "Finland":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.5i.fi", "/alpine");
            case "France":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirrors.ircam.fr", "/pub/alpine");
            case "Germany":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "ftp.halifax.rwth-aachen.de", "/alpine");
            case "Hong Kong":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.xtom.com.hk", "/alpine");
            case "Indonesia":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(false, "foobar.turbo.net.id", "/alpine");
            case "Iran":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.bardia.tech", "/alpine");
            case "Italy":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "alpinelinux.mirror.garr.it", "");
            case "Japan":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "repo.jing.rocks", "/alpine");
            case "Kazakhstan":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.ps.kz", "/alpine");
            case "Moldova":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.ihost.md", "/alpine");
            case "Morocco":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.marwan.ma", "/alpine");
            case "New Caledonia":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.lagoon.nc", "/alpine");
            case "New Zealand":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.2degrees.nz", "/alpine");
            case "Poland":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "ftp.icm.edu.pl", "/pub/Linux/distributions/alpine");
            case "Portugal":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.leitecastro.com", "/alpine");
            case "Romania":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirrors.hosterion.ro", "/alpinelinux");
            case "Russia":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.hyperdedic.ru", "/alpinelinux");
            case "Singapore":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.jingk.ai", "/alpine");
            case "Slovenia":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.tux.si", "/alpine");
            case "Spain":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.raiolanetworks.com", "/alpine");
            case "Sweden":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "ftp.lysator.liu.se/pub", "/alpine");
            case "Switzerland":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "pkg.adfinis.com", "/alpine");
            case "Taiwan":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.twds.com.tw", "/alpine");
            case "Thailand":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "mirror.kku.ac.th", "/alpine");
            case "The Netherlands":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "alpine.mirror.wearetriple.com", "");
            case "United Kingdom":
                return com.vectras.vm.utils.CommandUtils.createForSelectedMirror(true, "uk.alpinelinux.org", "/alpine");
            default:
                throw new IllegalArgumentException("Unknown mirror location: " + location);
        }
    }

    public static class SpinnerSelectMirrorAdapter extends BaseAdapter {

        private final ArrayList<HashMap<String, String>> data;
        private final LayoutInflater inflater;
        private final int selectedPosition;

        public SpinnerSelectMirrorAdapter(Context context, ArrayList<HashMap<String, String>> arr) {
            this.data = arr;
            this.inflater = LayoutInflater.from(context);
            this.selectedPosition = MainSettingsManager.getSelectedMirror(context);
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public HashMap<String, String> getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SetupWizard2Activity.SpinnerSelectMirrorAdapter.ViewHolder holder;

            if (convertView == null) {
                // Inflate binding only once for each new item
                SimpleLayoutListViewWithCheckBinding simpleLayoutListViewWithCheckBinding =
                        SimpleLayoutListViewWithCheckBinding.inflate(inflater, parent, false);

                // Create ViewHolder to hold binding
                holder = new SetupWizard2Activity.SpinnerSelectMirrorAdapter.ViewHolder(simpleLayoutListViewWithCheckBinding);
                convertView = simpleLayoutListViewWithCheckBinding.getRoot();
                convertView.setTag(holder);
            } else {
                // Get back the saved ViewHolder
                holder = (SetupWizard2Activity.SpinnerSelectMirrorAdapter.ViewHolder) convertView.getTag();
            }

            // Assign data
            HashMap<String, String> item = data.get(position);
            holder.simpleLayoutListViewWithCheckBinding.textview.setText(item.get("location"));
            holder.simpleLayoutListViewWithCheckBinding.ivCheck.setVisibility(position == selectedPosition ? View.VISIBLE : View.GONE);


            return convertView;
        }

        // ViewHolder holds binding for reuse
        private record ViewHolder(SimpleLayoutListViewWithCheckBinding simpleLayoutListViewWithCheckBinding) {
        }
    }

    public String getPath(Uri uri) {
        return FileUtils.getPath(this, uri);
    }

    private boolean isCompatibleBootstrapArchive(String fileName) {
        if (fileName == null) {
            return false;
        }

        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.endsWith(".tar.gz") || normalized.endsWith(".tar"))) {
            return false;
        }

        for (String abiPrefix : SetupFeatureCore.resolveBootstrapAbiCandidates()) {
            if (normalized.contains(abiPrefix)) {
                return true;
            }
        }

        return false;
    }
}
