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
import com.vectras.vm.utils.JSONUtils;
import com.vectras.vm.utils.LibraryChecker;
import com.vectras.vm.utils.ListUtils;
import com.vectras.vm.utils.PermissionUtils;
import com.vectras.vm.utils.TarUtils;
import com.vectras.vm.utils.UIUtils;
import com.vectras.vterm.Terminal;
import com.vectras.vterm.TerminalBottomSheetDialog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SetupWizard2Activity extends AppCompatActivity {
    private static final String TAG = "SetupWizard2Activity";
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
    String selectedMirrorCommand = "echo ";
    String selectedMirrorLocation = "";
    String downloadBootstrapsCommand = "";
    String tarPath = "";
    String progressText ="0%";
    boolean isSystemUpdateMode = false;
    boolean isExecutingCommand = false;
    boolean isLibProotError = false;
    boolean aria2Error = false;
    boolean isServerError = false;
    boolean isNotEnoughStorageSpace = false;
    boolean isCustomSetupMode = false;
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

        HashMap<String, String> item = mirrorList.get(MainSettingsManager.getSelectedMirror(this));
        selectedMirrorCommand = Objects.requireNonNull(item.get("mirror"));
        selectedMirrorLocation = Objects.requireNonNull(item.get("location"));

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
                DialogUtils.twoDialog(SetupWizard2Activity.this, getString(R.string.oops),
                        getString(R.string.this_option_is_temporarily_unavailable_because_the_server_cannot_be_connected),
                        getString(R.string.try_again),
                        getString(R.string.ok),
                        true, R.drawable.warning_48px,
                        true,
                        this::getDataForStandardSetup,
                        null,
                        null);
            } else {
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

        binding.btnTryAgain.setOnClickListener(v -> {
            if (isSystemUpdateMode) {
                uiController(STEP_SYSTEM_UPDATE);
                binding.btnSkipSystemUpdate.setVisibility(View.GONE);
            } else if (isLibProotError) {
                Intent intent = new Intent();
                intent.setAction(ACTION_VIEW);
                intent.setData(Uri.parse(AppConfig.community));
                startActivity(intent);
            } else if (SetupFeatureCore.isInstalledSystemFiles(this)) {
                getDataForStandardSetup();
            } else {
                extractSystemFiles();
            }
        });

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

        if (getIntent().hasExtra("action")) {
            if (getIntent().getIntExtra("action", -1) == ACTION_SYSTEM_UPDATE) {
                isSystemUpdateMode = true;
                uiController(STEP_SYSTEM_UPDATE);
            }
        }
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
                binding.btnTryAgain.setText(getString(R.string.join_our_community));
            } else if (isLibProotError) {
                binding.ivErrorLarge.setImageResource(R.drawable.error_96px);
                binding.tvErrorTitle.setText(getString(R.string.vectras_vm_cannot_run_on_this_device));
                binding.tvErrorSubtitle.setText(getString(R.string.a_serious_problem_has_occurred));
                binding.btnTryAgain.setText(getString(R.string.join_our_community));
            } else if (isServerError || aria2Error) {
                binding.ivErrorLarge.setImageResource(R.drawable.android_wifi_3_bar_alert_100px);
                binding.tvErrorTitle.setText(getString(R.string.unable_to_connect_to_server));
                binding.tvErrorSubtitle.setText(getString(R.string.check_your_internet_connection));
            } else {
                binding.ivErrorLarge.setImageResource(R.drawable.error_96px);
                binding.tvErrorTitle.setText(getString(R.string.something_went_wrong));
                binding.tvErrorSubtitle.setText(getString(R.string.the_setup_could_not_be_completed_and_below_is_the_log));
            }
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
                    boolean result = SetupFeatureCore.startExtractSystemFiles(this);

                    runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (result) {
                            getDataForStandardSetup();
                        } else {
                            uiController(STEP_ERROR, getString(R.string.system_files_installation_failed_content) + (!SetupFeatureCore.lastErrorLog.isEmpty() ? "\n\n" + SetupFeatureCore.lastErrorLog : ""));
                        }
                    }, 1000));
                }).start();
            });
        });
    }

    private void getDataForStandardSetup() {
        uiController(STEP_GETTING_DATA);

        RequestNetwork net = new RequestNetwork(this);
        RequestNetwork.RequestListener _net_request_listener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if (JSONUtils.isValidFromString(response)) {
                    HashMap<String, Object> mmap;
                    mmap = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {
                    }.getType());
                    if (mmap.containsKey("aarch64") && mmap.containsKey("armhf") && mmap.containsKey("amd64") && mmap.containsKey("x86")) {
                        if (DeviceUtils.isArm()) {
                            bootstrapFileLink = Objects.requireNonNull(mmap.get(DeviceUtils.is64bit() ? "aarch64" : "armhf")).toString();
                        } else {
                            bootstrapFileLink = Objects.requireNonNull(mmap.get(DeviceUtils.is64bit() ? "amd64" : "x86")).toString();
                        }
                        downloadBootstrapsCommand = " aria2c -x 4 --async-dns=false --disable-ipv6 --check-certificate=false -o setup.tar.gz " + bootstrapFileLink;
                    }
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isSystemUpdateMode) {
                        startSetup();
                    } else {
                        uiController(STEP_SETUP_OPTIONS);
                    }
                }, 1000);
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> uiController(STEP_SETUP_OPTIONS), 1000);
            }
        };

        net.startRequestNetwork(RequestNetworkController.GET, AppConfig.bootstrapfileslink, "", _net_request_listener);
    }

    private void startSetup() {
        uiController(STEP_INSTALLING_PACKAGES);

        new Thread(() -> {
            if (isCustomSetupMode) {
                runOnUiThread(() -> appendTextAndScroll(" | " + getString(R.string.checking)));

                try {
                    if (!TarUtils.isAllowExtract(tarPath)) {
                        runOnUiThread(() -> uiController(STEP_ERROR, getString(R.string.this_bootstrap_file_is_invalid)));
                        return;
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> uiController(STEP_ERROR, e.toString()));
                    return;
                }
            }

            runOnUiThread(() -> {
                logs = "";
                progressText = "";
                aria2Error = false;
                isServerError = false;
                String vncPassword = MainSettingsManager.getVncExternalPassword(this);
                if (vncPassword == null || vncPassword.isEmpty()) {
                    vncPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    MainSettingsManager.setVncExternalPassword(this, vncPassword);
                }
                String escapedVncPassword = vncPassword.replace("'", "'\\''");
                LibraryChecker.PackageManagerType managerType = LibraryChecker.detectPackageManagerType(this);
                String requiredPackages = resolveRequiredPackages(managerType);
                String updateCommand = resolveUpdateCommand(managerType);
                String installCommand = LibraryChecker.buildInstallCommand(managerType, requiredPackages);

            //   # PackageManagerType packageManagerType = detectPackageManagerType();
           //   #  String updateCommand = resolveUpdateCommand(packageManagerType);
            // #   String installCommand = resolveInstallCommand(packageManagerType);
           // #    String requiredPackages = resolveRequiredPackages(packageManagerType);

                String cmd = selectedMirrorCommand + ";" +
                        " set -e;" +
                        " echo \"Starting setup...\";" +
                        " " + updateCommand + ";" +
                        " echo \"Installing packages...\";" +
                        " " + installCommand + ";" +
                        " echo \"Downloading Qemu...\";";

                if (isCustomSetupMode) {
                    cmd += " tar -xzvf " + tarPath + " -C /;" +
                            " rm " + tarPath + ";" +
                            " chmod 775 /usr/local/bin/*;";
                } else {
                    if (FileUtils.isFileExists(getFilesDir().getAbsolutePath() + "/distro/root/setup.tar.gz"))
                        FileUtils.deleteDirectory(getFilesDir().getAbsolutePath() + "/distro/root/setup.tar.gz");

                    cmd += downloadBootstrapsCommand + ";" +
                            " echo \"Installing Qemu...\";" +
                            " tar -xzvf setup.tar.gz -C /;" +
                            " rm setup.tar.gz;" +
                            " chmod 775 /usr/local/bin/*;";
                }

                cmd += " echo \"Just a sec...\";" +
                        " echo export TMPDIR=/tmp >> /etc/profile;" +
                        " mkdir -p $TMPDIR/pulse;" +
                        " echo export PULSE_SERVER=127.0.0.1 >> /etc/profile;" +
                        " mkdir -p ~/.vnc && printf '%s\n' '" + escapedVncPassword + "' '" + escapedVncPassword + "' | vncpasswd -f > ~/.vnc/passwd && chmod 0600 ~/.vnc/passwd;" +
                        " echo \"Installation successful! xssFjnj58Id\"";

                executeShellCommand(cmd);
            });
        }).start();
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
                    String abi = Build.SUPPORTED_ABIS[0];
                    if (FileUtils.getFileNameFromUri(this, uri).endsWith(abi + ".tar.gz")) {
                        uiController(STEP_INSTALLING_PACKAGES);
                        new Thread(() -> {
                            try {
                                FileUtils.copyFileFromUri(this, uri, tarPath);
                                runOnUiThread(() -> {
                                    isCustomSetupMode = true;
                                    startSetup();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> uiController(STEP_ERROR, getString(R.string.the_file_could_not_be_processed_content)));
                            }
                        }).start();
                    } else {
                        DialogUtils.oneDialog(this,
                                getString(R.string.invalid_file),
                                getString(R.string.please_select) + " vectras-vm-" + abi + ".tar.gz.",
                                getResources().getString(R.string.ok),
                                true,
                                R.drawable.warning_48px,
                                true,
                                null,
                                null);
                    }
                }
            });

    public void executeShellCommand(String userCommand) {
        isExecutingCommand = true;
        new Thread(() -> {
            try {
                // Set up the process builder to start PRoot with environmental variables and commands
                ProcessBuilder processBuilder = new ProcessBuilder();

                // Adjust these environment variables as necessary for your app
                String filesDir = getFilesDir().getAbsolutePath();

                String tmpDirPath = getFilesDir().getAbsolutePath() + "/usr/tmp";

                ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(this, filesDir + "/distro", "/root")
                        .setPath("/bin:/usr/bin:/sbin:/usr/sbin")
                        .setTmpDir(tmpDirPath);
                prootCommandBuilder.applyEnvironment(processBuilder.environment());
                processBuilder.command(prootCommandBuilder.buildCommand());
                Process process = processBuilder.start();
                // Get the input and output streams of the process
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                // Send user command to PRoot
                writer.write(userCommand);
                writer.newLine();
                writer.flush();
                writer.close();

                // Read the input stream for the output of the command
                String line;
                while ((line = reader.readLine()) != null) {
                    final String outputLine = line;
                    runOnUiThread(() -> appendTextAndScroll(outputLine + "\n"));
                }

                // Read any errors from the error stream
                while ((line = errorReader.readLine()) != null) {
                    final String errorLine = line;
                    runOnUiThread(() -> appendTextAndScroll(errorLine + "\n"));
                }

                // Clean up
                reader.close();
                errorReader.close();

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
                    Log.e(TAG, timeoutMessage);
                    runOnUiThread(() -> {
                        appendTextAndScroll("Error: " + timeoutMessage + "\n");
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
                    Log.e(TAG, operationErrorMessage);
                    runOnUiThread(() -> {
                        appendTextAndScroll("Error: " + operationErrorMessage + "\n");
                        uiController(STEP_ERROR, logs);
                    });
                    return;
                }

                int exitValue = result.exitCode;
                if (exitValue != 0) {
                    isExecutingCommand = false;
                    if (aria2Error && downloadBootstrapsCommand.contains("aria2c")) {
                        runOnUiThread(() -> {
                            downloadBootstrapsCommand = " curl -o setup.tar.gz -L " + bootstrapFileLink;
                            startSetup();
                        });
                    } else {
                        runOnUiThread(() -> {
                            String toastMessage = "Command failed with exit code: " + exitValue;
                            appendTextAndScroll("Error: " + toastMessage + "\n");
                            uiController(STEP_ERROR, logs);
                        });
                    }
                }
            } catch (IOException e) {
                isExecutingCommand = false;
                // Handle exceptions by printing the stack trace in the terminal output
                final String errorMessage = e.getMessage();
                Log.e(TAG, "executeShellCommand IO error: " + errorMessage, e);
                runOnUiThread(() -> {
                    appendTextAndScroll("Error: " + errorMessage + "\n");
                    uiController(STEP_ERROR, logs);
                });
            }
        }).start(); // Execute the command in a separate thread to prevent blocking the UI thread
    }

    @SuppressLint("SetTextI18n")
    private void appendTextAndScroll(String newLog) {
        logs += newLog;

        if (newLog.contains("xssFjnj58Id")) {
            isExecutingCommand = false;
            MainSettingsManager.setStandardSetupVersion(this, AppConfig.standardSetupVersion);
            MainSettingsManager.setsetUpWithManualSetupBefore(this, isCustomSetupMode);
            uiController(STEP_PATERON);
            if (isSystemUpdateMode) {
                uiControllerFinalSteps(STEP_FINISH);
            } else {
                uiControllerFinalSteps(STEP_PATERON);
            }
        } else if (newLog.contains("libproot.so --help") || newLog.contains("/bin/sh: can't fork:")) {
            isLibProotError = true;
        } else if (newLog.contains("not complete: /root/setup.tar.gz")) {
            aria2Error = true;
        } else if (newLog.contains("temporary error")) {
            isServerError = true;
        }

        if (newLog.contains("Starting setup...")) {
            progressText = "5% | ";
        } else if (newLog.contains("fetch http")) {
            progressText = "10% | ";
        } else if (newLog.contains("Installing packages...")) {
            progressText = "20% | ";
        } else if (newLog.contains("(50/")) {
            progressText = "25% | ";
        } else if (newLog.contains("100/")) {
            progressText = "30% | ";
        } else if (newLog.contains("150/")) {
            progressText = "35% | ";
        } else if (newLog.contains("200/")) {
            progressText = "40% | ";
        } else if (newLog.contains("250/")) {
            progressText = "50% | ";
        } else if (newLog.contains("300/")) {
            progressText = "60% | ";
        } else if (newLog.contains("325/")) {
            progressText = "65% | ";
        } else if (newLog.contains("350/")) {
            progressText = "68% | ";
        } else if (newLog.contains("375/")) {
            progressText = "69% | ";
        } else if (newLog.contains("Downloading Qemu...") || newLog.contains("tar -xzvf ")) {
            progressText = "70% | ";
        } else if (newLog.contains("Installing Qemu...")) {
            progressText = "75% | ";
        } else if (newLog.contains("qemu-system")) {
            progressText = "80% | ";
        } else if (newLog.contains("Just a sec...")) {
            progressText = "95% | ";
        }

        binding.tvLastestCommandResult.setText(progressText + newLog);
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
            HashMap<String, String> item = mirrorList.get(position);
            selectedMirrorCommand = Objects.requireNonNull(item.get("mirror"));
            selectedMirrorLocation = Objects.requireNonNull(item.get("location"));
            MainSettingsManager.setSelectedMirror(SetupWizard2Activity.this, position);

            dialog.dismiss();
        });

        listViewBinding.list.post(() -> listViewBinding.list.setSelection(MainSettingsManager.getSelectedMirror(this)));

        dialog.show();
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
}
