package com.vectras.vm;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.Fragment.CreateImageDialogFragment;
import com.vectras.vm.main.vms.DataMainRoms;
import com.vectras.vm.databinding.ActivityVmCreatorBinding;
import com.vectras.vm.databinding.DialogProgressStyleBinding;
import com.vectras.vm.download.DownloadStateStore;
import com.vectras.vm.download.DownloadStatus;
import com.vectras.vm.main.MainActivity;
import com.vectras.vm.core.VmFlowState;
import com.vectras.vm.core.VmFlowTracker;
import com.vectras.vm.importer.ImportSessionWorker;
import com.vectras.vm.importer.ImportStateStore;
import com.vectras.vm.rafaelia.RafaeliaQemuProfile;
import com.vectras.vm.utils.DeviceUtils;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.utils.ImageUtils;
import com.vectras.vm.utils.PackageUtils;
import com.vectras.vm.utils.UIUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vectras.vm.utils.ZipUtils;
import com.vectras.vterm.Terminal;
import com.vectras.vm.utils.PermissionUtils;

public class VMCreatorActivity extends AppCompatActivity {

    private final String TAG = "VMCreatorActivity";
    private ActivityVmCreatorBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    boolean iseditparams = false;
    public String previousName = "";
    public boolean addromnowdone = false;
    private String vmID = VMManager.idGenerator();
    private int port = VMManager.startRandomPort();
    private boolean created = false;
    private String thumbnailPath = "";
    boolean modify;
    public static DataMainRoms current;
    private boolean isImportingCVBI = false;
    private AlertDialog importProgressDialog;
    private String importSessionId;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        return switch (item.getItemId()) {
            case android.R.id.home -> {
                finish();
                yield true;
            }
            default -> super.onOptionsItemSelected(item);
        };
    }


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.edgeToEdge(this);
        binding = ActivityVmCreatorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        binding.collapsingToolbarLayout.setSubtitle(MainSettingsManager.getArch(this));

        binding.btnCreate.setOnClickListener(v -> startCreateVM());

        binding.drive.setOnClickListener(v -> diskPicker.launch("*/*"));
        binding.driveField.setOnClickListener(v -> diskPicker.launch("*/*"));

        binding.driveField.setEndIconOnClickListener(v -> {
            if (Objects.requireNonNull(binding.drive.getText()).toString().isEmpty()) {
                CreateImageDialogFragment dialogFragment = new CreateImageDialogFragment();
                dialogFragment.folder = AppConfig.vmFolder + vmID + "/";
                dialogFragment.customRom = true;
                dialogFragment.filename = Objects.requireNonNull(binding.title.getText()).toString();
                dialogFragment.drive = binding.drive;
                dialogFragment.driveLayout = binding.driveField;
                dialogFragment.show(getSupportFragmentManager(), "CreateImageDialogFragment");
            } else {
                DialogUtils.threeDialog(this,
                        getString(R.string.change_hard_drive),
                        getString(R.string.do_you_want_to_change_create_or_remove),
                        getString(R.string.change), getString(R.string.remove),
                        getString(R.string.create),
                        true,
                        R.drawable.hard_drive_24px,
                        true,
                        () -> diskPicker.launch("*/*"),
                        () -> {
                            if (binding.drive.getText().toString().contains(AppConfig.vmFolder + vmID)) {
                                FileUtils.deleteDirectory(Objects.requireNonNull(binding.drive.getText()).toString());
                            }
                            binding.drive.setText("");
                            binding.driveField.setEndIconDrawable(R.drawable.round_add_24);
                        },
                        () -> {
                            if (createVMFolder(true)) {
                                CreateImageDialogFragment dialogFragment = new CreateImageDialogFragment();
                                dialogFragment.customRom = true;
                                dialogFragment.filename = Objects.requireNonNull(binding.title.getText()).toString();
                                dialogFragment.drive = binding.drive;
                                dialogFragment.driveLayout = binding.driveField;
                                dialogFragment.show(getSupportFragmentManager(), "CreateImageDialogFragment");
                            }
                        }, null);
            }
        });

        View.OnClickListener cdromClickListener = v -> isoPicker.launch("*/*");

        binding.cdrom.setOnClickListener(cdromClickListener);
        binding.cdromField.setOnClickListener(cdromClickListener);

        binding.cdromField.setEndIconOnClickListener(v -> {
            if (!Objects.requireNonNull(binding.cdrom.getText()).toString().isEmpty()) {
                binding.cdrom.setText("");
                binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_NONE);
            }
        });

        binding.addRomBtn.setOnClickListener(v -> startCreateVM());

        binding.qemu.setOnClickListener(v -> {
            iseditparams = true;
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), QemuParamsEditorActivity.class);
            intent.putExtra("content", Objects.requireNonNull(binding.qemu.getText()).toString());
            startActivity(intent);
        });

        binding.qemuField.setOnClickListener(v -> {
            iseditparams = true;
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), QemuParamsEditorActivity.class);
            intent.putExtra("content", Objects.requireNonNull(binding.qemu.getText()).toString());
            startActivity(intent);
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if (!thumbnailPath.isEmpty())
                    return;

                VMManager.setIconWithName(binding.ivIcon, Objects.requireNonNull(binding.title.getText()).toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
        binding.title.addTextChangedListener(afterTextChangedListener);
        binding.drive.addTextChangedListener(afterTextChangedListener);
        binding.qemu.addTextChangedListener(afterTextChangedListener);

        binding.ivIcon.setOnClickListener(v -> {
            if (thumbnailPath.isEmpty()) {
                thumbnailPicker.launch("image/*");
            } else {
                DialogUtils.twoDialog(this,
                        getString(R.string.change_thumbnail),
                        getString(R.string.do_you_want_to_change_or_remove),
                        getString(R.string.change), getString(R.string.remove),
                        true,
                        R.drawable.photo_24px,
                        true,
                        () -> thumbnailPicker.launch("image/*"),
                        () -> {
                            thumbnailPath = "";
                            binding.ivAddThubnail.setImageResource(R.drawable.round_add_24);
                            VMManager.setIconWithName(binding.ivIcon, Objects.requireNonNull(binding.title.getText()).toString());
                        }, null);
            }
        });

        binding.lineardisclaimer.setOnClickListener(v -> DialogUtils.oneDialog(this, getResources().getString(R.string.dont_miss_out), getResources().getString(R.string.disclaimer_when_using_rom), getResources().getString(R.string.i_agree), true, R.drawable.verified_user_24px, true, null, null));

        modify = getIntent().getBooleanExtra("MODIFY", false);
        if (modify) {
            binding.collapsingToolbarLayout.setTitle(getString(R.string.edit));
            created = true;
            binding.addRomBtn.setText(R.string.save_changes);

            if (binding != null && current != null) {
                if (current.itemName != null) binding.title.setText(current.itemName);
                if (current.itemPath != null) binding.drive.setText(current.itemPath);
                if (current.imgCdrom != null) binding.cdrom.setText(current.imgCdrom);
                if (current.itemIcon != null) thumbnailPath = current.itemIcon;
            }

            vmID = getIntent().getStringExtra("VMID");

            if (vmID == null || vmID.isEmpty()) {
                vmID = VMManager.idGenerator();
            }

            binding.qemu.setText(current.itemExtra);

            thumbnailProcessing();

            if (Objects.requireNonNull(binding.drive.getText()).toString().isEmpty()) {
                binding.driveField.setEndIconDrawable(R.drawable.round_add_24);
            }

            if (!Objects.requireNonNull(binding.cdrom.getText()).toString().isEmpty()) {
                binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                binding.cdromField.setEndIconDrawable(R.drawable.close_24px);
                changeOnClickCdrom();
            }

            previousName = current.itemName;
        } else {
            checkVMID();
            if (getIntent().hasExtra("addromnow")) {
                if (Objects.requireNonNull(getIntent().getStringExtra("romextra")).isEmpty()) {
                    setDefault();
                } else {
                    binding.qemu.setText(Objects.requireNonNull(getIntent().getStringExtra("romextra")).replace("OhnoIjustrealizeditsmidnightandIstillhavetodothis", AppConfig.vmFolder + vmID + "/"));
                }

                binding.title.setText(getIntent().getStringExtra("romname"));

                if (getIntent().hasExtra("romicon") && !Objects.requireNonNull(getIntent().getStringExtra("romicon")).isEmpty()) {
                    startProcessingThumbnail(Uri.parse(getIntent().getStringExtra("romicon")));
                }

                if (Objects.requireNonNull(getIntent().getStringExtra("romfilename")).endsWith(".cvbi")) {
                    importRom(
                            getIntent().hasExtra("romuri") ?
                                    Uri.parse(getIntent().getStringExtra("romuri")) :
                                    null, Objects.requireNonNull(getIntent().getStringExtra("rompath")),
                            Objects.requireNonNull(getIntent().getStringExtra("romfilename")));
                } else {
                    addromnowdone = true;
                    if (!Objects.requireNonNull(getIntent().getStringExtra("rompath")).isEmpty()) {
                        Uri romUri = toShareableUri(Objects.requireNonNull(getIntent().getStringExtra("rompath")));
                        if (romUri != null) {
                            selectedDiskFile(romUri, false);
                        }
                    }
                    if (!Objects.requireNonNull(getIntent().getStringExtra("addtodrive")).isEmpty()) {
                        binding.drive.setText(AppConfig.vmFolder + vmID + "/" + getIntent().getStringExtra("romfilename"));
                        if (Objects.requireNonNull(binding.drive.getText()).toString().isEmpty()) {
                            binding.driveField.setEndIconDrawable(R.drawable.round_add_24);
                        } else {
                            binding.driveField.setEndIconDrawable(R.drawable.more_vert_24px);
                        }
                    } else {
                        binding.driveField.setEndIconDrawable(R.drawable.round_add_24);
                    }
                }

            } else if (getIntent().hasExtra("importcvbinow")) {
                setDefault();
                cvbiPicker.launch(new String[]{
                        "application/octet-stream",
                        "application/zip",
                        "application/x-iso9660-image",
                        "application/x-qemu-disk",
                        "*/*"
                });
            } else {
                setDefault();
                if (MainSettingsManager.autoCreateDisk(this)) {
                    if (createVMFolder(true)) {
                        Terminal vterm = new Terminal(this);
                        vterm.executeShellCommand2("qemu-img create -f qcow2 " + AppConfig.vmFolder + vmID + "/disk.qcow2 128G", false, this);
                        binding.drive.setText(AppConfig.vmFolder + vmID + "/disk.qcow2");
                    }
                } else {
                    binding.driveField.setEndIconDrawable(R.drawable.round_add_24);
                }

            }
        }

        if (PackageUtils.getVersionCode("com.anbui.cqcm.app", this) < 735 || !FileUtils.isFileExists(AppConfig.vmFolder + vmID + "/cqcm.json")) {
            binding.opencqcm.setVisibility(View.GONE);
        } else {
            binding.opencqcm.setOnClickListener(v -> {
                if (PackageUtils.isInstalled("com.anbui.cqcm.app", this)) {
                    Intent intentcqcm = new Intent();
                    intentcqcm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intentcqcm.setComponent(new ComponentName("com.anbui.cqcm.app", "com.anbui.cqcm.app.DownloadActivity"));
                    intentcqcm.putExtra("content", FileUtils.readAFile(AppConfig.vmFolder + vmID + "/cqcm.json"));
                    intentcqcm.putExtra("vectrasVMId", vmID);
                    startActivity(intentcqcm);
                    finish();
                } else {
                    Intent intenturl = new Intent();
                    intenturl.setAction(Intent.ACTION_VIEW);
                    intenturl.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.anbui.cqcm.app"));
                    startActivity(intenturl);
                }
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        PermissionUtils.storagepermission(this, true);
        if (iseditparams) {
            iseditparams = false;
            binding.qemu.setText(QemuParamsEditorActivity.result);
        }
    }

    private void onBack() {
        if (isImportingCVBI) return;

        if (!created && !modify) {
            new Thread(() -> FileUtils.deleteDirectory(AppConfig.vmFolder + vmID)).start();
        }
        modify = false;
        finish();
    }

    public void onDestroy() {
        if (!created && !modify) {
            new Thread(() -> FileUtils.deleteDirectory(AppConfig.vmFolder + vmID)).start();
        }
        modify = false;
        super.onDestroy();
    }

    private final ActivityResultLauncher<String> thumbnailPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                startProcessingThumbnail(uri);
            });

    private final ActivityResultLauncher<String> diskPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                selectedDiskFile(uri, true);
            });

    @SuppressLint("SetTextI18n")
    private final ActivityResultLauncher<String> isoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;

                if (MainSettingsManager.copyFile(this)) {
                    DialogProgressStyleBinding dialogProgressStyleBinding = DialogProgressStyleBinding.inflate(getLayoutInflater());
                    dialogProgressStyleBinding.progressText.setText(getString(R.string.copying_file));
                    AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                            .setView(dialogProgressStyleBinding.getRoot())
                            .setCancelable(false)
                            .create();

                    progressDialog.show();

                    executor.execute(() -> {
                        try {
                            String _filename = FileUtils.getFileNameFromUri(this, uri);
                            if (_filename == null || _filename.isEmpty()) {
                                _filename = String.valueOf(System.currentTimeMillis());
                            }

                            File vmRoot = new File(AppConfig.vmFolder + vmID);
                            File safeDestination = FileUtils.resolveSafeDestinationFile(vmRoot, _filename);
                            FileUtils.copyFileFromUri(this, uri, safeDestination);

                            String finalPath = safeDestination.getPath();
                            runOnUiThread(() -> {
                                binding.cdrom.setText(finalPath);
                                binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                                binding.cdromField.setEndIconDrawable(R.drawable.close_24px);
                                changeOnClickCdrom();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> DialogUtils.oneDialog(this,
                                    getString(R.string.oops),
                                    getString(R.string.unable_to_copy_iso_file_content) + "\n" + e.getMessage(),
                                    getString(R.string.ok),
                                    true,
                                    R.drawable.warning_48px,
                                    true,
                                    null,
                                    null));
                            Log.e(TAG, "Rejected or failed ISO copy from URI: " + uri, e);
                        } finally {
                            runOnUiThread(progressDialog::dismiss);
                        }
                    });
                } else {
                    if (!FileUtils.isValidFilePath(this, FileUtils.getPath(this, uri), false)) {
                        DialogUtils.oneDialog(this,
                                getString(R.string.problem_has_been_detected),
                                getString(R.string.invalid_file_path_content),
                                getString(R.string.ok),
                                true,
                                R.drawable.warning_48px,
                                true,
                                null,
                                null);
                        return;
                    }
                    File selectedFilePath = new File(getPath(uri));
                    if (selectedFilePath.getName().endsWith(".iso")) {
                        binding.cdrom.setText(selectedFilePath.getPath());
                    } else {
                        DialogUtils.oneDialog(this,
                                getString(R.string.problem_has_been_detected),
                                getString(R.string.invalid_iso_file_path_content),
                                getString(R.string.ok),
                                true,
                                R.drawable.warning_48px,
                                true,
                                null,
                                null);
                    }
                }
            });

    private final ActivityResultLauncher<String[]> cvbiPicker =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) {
                    return;
                }
                startMultiImport(uris);
            });

    private void startMultiImport(java.util.List<Uri> uris) {
        java.util.ArrayList<Uri> filteredUris = new java.util.ArrayList<>();
        ContentResolver resolver = getContentResolver();

        for (Uri uri : uris) {
            if (uri == null) {
                continue;
            }
            if (!isAllowedImportUri(uri, resolver)) {
                continue;
            }

            takeUriPermissionSafely(uri);
            filteredUris.add(uri);
        }

        if (filteredUris.isEmpty()) {
            DialogUtils.oneDialog(this,
                    getString(R.string.problem_has_been_detected),
                    "Nenhum arquivo compatível foi selecionado. Use: .rom/.iso/.qcow2/.img/.zip/.cvbi",
                    getString(R.string.ok),
                    true,
                    R.drawable.warning_48px,
                    true,
                    null,
                    null);
            return;
        }

        importSessionId = UUID.randomUUID().toString();
        showImportProgressDialog();
        enqueueImportWorker(filteredUris, importSessionId);
    }

    private boolean isAllowedImportUri(Uri uri, ContentResolver resolver) {
        String fileName = FileUtils.getFileNameFromUri(this, uri);
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".rom")
                || lowerName.endsWith(".iso")
                || lowerName.endsWith(".qcow2")
                || lowerName.endsWith(".img")
                || lowerName.endsWith(".zip")
                || lowerName.endsWith(".cvbi")) {
            return true;
        }

        String mime = resolver.getType(uri);
        if (TextUtils.isEmpty(mime)) {
            return false;
        }
        return "application/octet-stream".equalsIgnoreCase(mime)
                || "application/zip".equalsIgnoreCase(mime)
                || "application/x-iso9660-image".equalsIgnoreCase(mime)
                || "application/x-qemu-disk".equalsIgnoreCase(mime)
                || "application/x-cd-image".equalsIgnoreCase(mime)
                || "application/x-cvbi".equalsIgnoreCase(mime)
                || "application/vnd.vectras.cvbi".equalsIgnoreCase(mime);
    }

    private void takeUriPermissionSafely(Uri uri) {
        final int readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        final int writeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            getContentResolver().takePersistableUriPermission(uri, writeFlags);
            return;
        } catch (SecurityException ignored) {
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, readFlags);
        } catch (SecurityException ignored) {
        }
    }

    private void showImportProgressDialog() {
        DialogProgressStyleBinding progressBinding = DialogProgressStyleBinding.inflate(getLayoutInflater());
        progressBinding.progressBar.setIndeterminate(false);
        progressBinding.progressBar.setMax(100);
        progressBinding.progressBar.setProgress(0);
        progressBinding.progressText.setText("Preparando importação...");

        importProgressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                .setTitle("Importando arquivos")
                .setView(progressBinding.getRoot())
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    if (!TextUtils.isEmpty(importSessionId)) {
                        WorkManager.getInstance(this).cancelUniqueWork(importSessionId);
                    }
                })
                .create();

        importProgressDialog.show();
        observeImportProgress(progressBinding);
    }

    private void enqueueImportWorker(java.util.ArrayList<Uri> uris, String sessionId) {
        String[] serialized = new String[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            serialized[i] = uris.get(i).toString();
        }

        Data input = new Data.Builder()
                .putStringArray(ImportSessionWorker.KEY_URIS, serialized)
                .putString(ImportSessionWorker.KEY_SESSION_ID, sessionId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportSessionWorker.class)
                .setInputData(input)
                .build();

        WorkManager.getInstance(this).enqueueUniqueWork(sessionId, androidx.work.ExistingWorkPolicy.REPLACE, request);
    }

    private void observeImportProgress(DialogProgressStyleBinding progressBinding) {
        if (TextUtils.isEmpty(importSessionId)) {
            return;
        }
        WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(importSessionId)
                .observe(this, workInfos -> {
                    if (workInfos == null || workInfos.isEmpty()) {
                        return;
                    }
                    WorkInfo info = workInfos.get(0);
                    Data progress = info.getProgress();
                    int fileIndex = progress.getInt(ImportSessionWorker.PROGRESS_FILE_INDEX, 0);
                    int totalFiles = progress.getInt(ImportSessionWorker.PROGRESS_TOTAL_FILES, 0);
                    String currentFile = progress.getString(ImportSessionWorker.PROGRESS_CURRENT_FILE);
                    int filePercent = progress.getInt(ImportSessionWorker.PROGRESS_FILE_PERCENT, 0);
                    int totalPercent = progress.getInt(ImportSessionWorker.PROGRESS_TOTAL_PERCENT, 0);

                    progressBinding.progressBar.setProgress(totalPercent);
                    progressBinding.progressText.setText(
                            "Arquivo " + fileIndex + "/" + totalFiles
                                    + "\n" + (currentFile == null ? "" : currentFile)
                                    + "\nProgresso arquivo: " + filePercent + "%"
                                    + "\nProgresso total: " + totalPercent + "%");

                    if (info.getState().isFinished()) {
                        if (importProgressDialog != null && importProgressDialog.isShowing()) {
                            importProgressDialog.dismiss();
                        }
                        showImportReport(importSessionId);
                    }
                });
    }

    private void showImportReport(String sessionId) {
        ImportStateStore stateStore = new ImportStateStore(this);
        org.json.JSONArray report = stateStore.getSessionResult(sessionId);
        int success = 0;
        int failed = 0;
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < report.length(); i++) {
            try {
                org.json.JSONObject item = report.getJSONObject(i);
                boolean itemSuccess = item.optBoolean("success", false);
                if (itemSuccess) {
                    success++;
                } else {
                    failed++;
                }
                details.append(item.optString("name", "arquivo"))
                        .append(" -> ")
                        .append(itemSuccess ? "sucesso" : "falha")
                        .append(" (")
                        .append(item.optString("reason", "unknown"))
                        .append(")\n");
            } catch (Exception ignored) {
            }
        }

        DialogUtils.oneDialog(this,
                "Relatório de importação",
                "Sucesso: " + success + "\nFalha: " + failed + "\n\n" + details,
                getString(R.string.ok),
                true,
                failed == 0 ? R.drawable.verified_user_24px : R.drawable.warning_48px,
                true,
                null,
                null);
    }

    private void setDefault() {
        boolean isRafLinuxEnterprise = com.vectras.vm.main.romstore.RomCatalogLabels.isRafLinuxEnterprise(
                getIntent().getStringExtra("os_family"),
                getIntent().getStringExtra("os_flavor"),
                getIntent().getStringExtra("release_channel")
        );

        String defQemuParams = isRafLinuxEnterprise
                ? RafaeliaQemuProfile.presetParams(
                DeviceUtils.is64bit(),
                MainSettingsManager.getArch(this),
                RafaeliaQemuProfile.PresetKind.COMPATIBILITY
        )
                : RafaeliaQemuProfile.defaultParams(
                DeviceUtils.is64bit(),
                MainSettingsManager.getArch(this)
        );

        if (isRafLinuxEnterprise) {
            binding.title.setText("RafLinux Enterprise");
        } else {
            binding.title.setText(getString(R.string.new_vm));
        }
        binding.qemu.setText(defQemuParams);
    }

    private void checkVMID() {
        if (FileUtils.isFileExists(AppConfig.maindirpath + "/roms/" + vmID) || vmID.isEmpty()) {
            vmID = VMManager.idGenerator();
            port = VMManager.startRandomPort();
        }
    }

    private boolean createVMFolder(boolean isShowDialog) {
        File romDir = new File(AppConfig.vmFolder + vmID);
        if (!romDir.exists()) {
            if (!romDir.mkdirs()) {
                if (isShowDialog) DialogUtils.oneDialog(this,
                        getResources().getString(R.string.oops),
                        getResources().getString(R.string.unable_to_create_the_directory_to_create_the_vm),
                        getResources().getString(R.string.ok),
                        true,
                        R.drawable.error_96px,
                        true,
                        getIntent().hasExtra("addromnow") ? this::finish : null,
                        getIntent().hasExtra("addromnow") ? this::finish : null
                );
                return false;
            }
        }
        return true;
    }

    private void startCreateVM() {
        if (Objects.requireNonNull(binding.title.getText()).toString().isEmpty()) {
            DialogUtils.oneDialog(this, getString(R.string.oops), getString(R.string.need_set_name), getString(R.string.ok), true, R.drawable.error_96px, true, null, null);
        } else {
            String _contentDialog = "";
            if (Objects.requireNonNull(binding.qemu.getText()).toString().isEmpty()) {
                _contentDialog = getResources().getString(R.string.qemu_params_is_empty);
            }

            if ((Objects.requireNonNull(binding.drive.getText()).toString().isEmpty()) && (Objects.requireNonNull(binding.cdrom.getText()).toString().isEmpty())) {
                if (!VMManager.isHaveADisk(Objects.requireNonNull(binding.qemu.getText()).toString())) {
                    if (!_contentDialog.isEmpty()) {
                        _contentDialog += "\n\n";
                    }
                    _contentDialog += getResources().getString(R.string.you_have_not_added_any_storage_devices);
                }

            }

            if (_contentDialog.isEmpty()) {
                createNewVM();
            } else {
                DialogUtils.twoDialog(this, getString(R.string.problem_has_been_detected), _contentDialog, getString(R.string.continuetext), getString(R.string.cancel), true, R.drawable.warning_48px, true,
                        this::createNewVM, null, null);
            }
        }
    }

    private void createNewVM() {
        VmFlowTracker.mark(this, vmID, VmFlowState.CREATING, "create_vm_requested", "create");
        if (FileUtils.isFileExists(AppConfig.romsdatajson)) {
            if (!VMManager.isRomsDataJsonValid(true, this)) {
                return;
            }
        } else {
            FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", "[]");
        }

        boolean isSaveCompleted;
        if (modify) {
            isSaveCompleted = VMManager.editVM(Objects.requireNonNull(binding.title.getText()).toString(),
                    thumbnailPath,
                    Objects.requireNonNull(binding.drive.getText()).toString(),
                    MainSettingsManager.getArch(this),
                    Objects.requireNonNull(binding.cdrom.getText()).toString(),
                    Objects.requireNonNull(binding.qemu.getText()).toString(),
                    getIntent().getIntExtra("POS", 0));
        } else {
            isSaveCompleted = VMManager.createNewVM(Objects.requireNonNull(binding.title.getText()).toString(),
                    thumbnailPath,
                    Objects.requireNonNull(binding.drive.getText()).toString(),
                    MainSettingsManager.getArch(this),
                    Objects.requireNonNull(binding.cdrom.getText()).toString(),
                    Objects.requireNonNull(binding.qemu.getText()).toString(), vmID, port);
        }

        if (!isSaveCompleted) {
            VmFlowTracker.mark(this, vmID, VmFlowState.ERROR, "create_vm_persist_failed", "abort");
            DialogUtils.oneDialog(
                    this,
                    getString(R.string.oops),
                    getString(R.string.unable_to_save_please_try_again_later),
                    R.drawable.error_96px);
            return;
        }

        created = true;
        VmFlowTracker.mark(this, vmID, VmFlowState.READY, "create_vm_saved", "ready");

        if (getIntent().hasExtra("addromnow")) {
            RomInfo.isFinishNow = true;
            MainActivity.isOpenHome = true;
        }

        modify = false;
        if (!MainActivity.isActivate) {
            startActivity(new Intent(this, SplashActivity.class));
        } else {
            Intent intent = new Intent();
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.setClass(this, MainActivity.class);
            startActivity(intent);
        }
        finish();
    }

    private void startProcessingThumbnail(Uri uri) {
        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_style, null);
        TextView progressText = progressView.findViewById(R.id.progress_text);
        progressText.setText(getString(R.string.just_a_sec));
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.show();

        executor.execute(() -> {
            try {
                ImageUtils.convertToPng(this, uri, AppConfig.vmFolder + vmID + "/thumbnail.png");

                thumbnailPath = AppConfig.vmFolder + vmID + "/thumbnail.png";
                runOnUiThread(this::thumbnailProcessing);
            } catch (Exception e) {
                runOnUiThread(() -> DialogUtils.oneDialog(this,
                        getString(R.string.oops),
                        getString(R.string.unable_to_process_thumbnail_content),
                        getString(R.string.ok),
                        true,
                        R.drawable.warning_48px,
                        true,
                        null,
                        null));
            } finally {
                runOnUiThread(() -> {
                    if (!isImportingCVBI && !isFinishing() && !isDestroyed()) progressDialog.dismiss();
                });
            }
        });
    }

    private void thumbnailProcessing() {
        if (!thumbnailPath.isEmpty()) {
            binding.ivAddThubnail.setImageResource(R.drawable.edit_24px);
            File imgFile = new File(thumbnailPath);

            if (imgFile.exists()) {
                Glide.with(this)
                        .load(new File(thumbnailPath))
                        .placeholder(R.drawable.ic_computer_180dp_with_padding)
                        .error(R.drawable.ic_computer_180dp_with_padding)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.ivIcon);
            } else {
                binding.ivAddThubnail.setImageResource(R.drawable.round_add_24);
                VMManager.setIconWithName(binding.ivIcon, Objects.requireNonNull(binding.title.getText()).toString());
            }
        } else {
            binding.ivAddThubnail.setImageResource(R.drawable.round_add_24);
        }
    }


    private Uri toShareableUri(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("content://")) return Uri.parse(path);
        File file = new File(path);
        if (!file.exists()) return null;
        return FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
    }
    private void selectedDiskFile(Uri _content_describer, boolean _addtodrive) {
        if (FileUtils.isValidFilePath(this, FileUtils.getPath(this, _content_describer), false)) {
            new Thread(() -> {
                File selectedFilePath = new File(getPath(_content_describer));
                runOnUiThread(() -> {
                    if (VMManager.isADiskFile(selectedFilePath.getPath())) {
                        startProcessingHardDriveFile(_content_describer, _addtodrive);
                    } else {
                        DialogUtils.twoDialog(this, getString(R.string.problem_has_been_detected), getString(R.string.file_format_is_not_supported), getResources().getString(R.string.continuetext), getResources().getString(R.string.cancel), true, R.drawable.hard_drive_24px, true,
                                () -> startProcessingHardDriveFile(_content_describer, _addtodrive), null, null);
                    }
                });
            }).start();
        } else {
            startProcessingHardDriveFile(_content_describer, _addtodrive);
        }
    }

    @SuppressLint("SetTextI18n")
    private void startProcessingHardDriveFile(Uri _content_describer, boolean _addtodrive) {
        if (MainSettingsManager.copyFile(this)) {

            if (!createVMFolder(true)) return;

            View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_style, null);
            TextView progressText = progressView.findViewById(R.id.progress_text);
            progressText.setText(getString(R.string.copying_file));
            AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                    .setView(progressView)
                    .setCancelable(false)
                    .create();

            progressDialog.show();

            executor.execute(() -> {
                try {
                    String _filename = FileUtils.getFileNameFromUri(this, _content_describer);
                    if (_filename == null || _filename.isEmpty()) {
                        _filename = String.valueOf(System.currentTimeMillis());
                    }

                    File vmRoot = new File(AppConfig.vmFolder + vmID);
                    File safeDestination = FileUtils.resolveSafeDestinationFile(vmRoot, _filename);
                    FileUtils.copyFileFromUri(this, _content_describer, safeDestination);

                    String finalPath = safeDestination.getPath();
                    runOnUiThread(() -> {
                        if (_addtodrive) {
                            binding.drive.setText(finalPath);
                            binding.driveField.setEndIconDrawable(R.drawable.more_vert_24px);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Rejected or failed hard drive copy from URI: " + _content_describer, e);
                    runOnUiThread(() -> DialogUtils.oneDialog(this,
                            getString(R.string.oops),
                            getString(R.string.unable_to_copy_hard_drive_file_content) + "\n" + e.getMessage(),
                            getString(R.string.ok),
                            true,
                            R.drawable.warning_48px,
                            true,
                            null,
                            null));
                } finally {
                    runOnUiThread(progressDialog::dismiss);
                }
            });
        } else {
            if (!FileUtils.isValidFilePath(this, FileUtils.getPath(this, _content_describer), false)) {
                DialogUtils.oneDialog(this,
                        getString(R.string.problem_has_been_detected),
                        getString(R.string.invalid_file_path_content),
                        getString(R.string.ok),
                        true,
                        R.drawable.warning_48px,
                        true,
                        null,
                        null);
                return;
            }
            File selectedFilePath = new File(getPath(_content_describer));
            binding.drive.setText(selectedFilePath.getPath());
            binding.driveField.setEndIconDrawable(R.drawable.more_vert_24px);
        }
    }


    private boolean verifyImportHashBeforeInstall(Uri fileUri, String filePath, String fileName) {
        String expectedSha256 = getIntent().getStringExtra("expectedSha256");
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            return true;
        }
        expectedSha256 = expectedSha256.trim().toLowerCase(Locale.US);
        if (!isValidSha256(expectedSha256)) {
            Log.e(TAG, "Expected SHA-256 from intent is invalid: " + expectedSha256);
            return true;
        }

        try {
            String actualSha256;
            File sourceFile = null;
            if (filePath != null && !filePath.trim().isEmpty() && FileUtils.isFileExists(filePath)) {
                sourceFile = new File(filePath);
                actualSha256 = computeSha256(new FileInputStream(sourceFile));
            } else if (fileUri != null) {
                actualSha256 = computeSha256(getContentResolver().openInputStream(fileUri));
            } else {
                onImportHashMismatch(filePath, fileName, expectedSha256, "");
                return false;
            }

            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                onImportHashMismatch(sourceFile != null ? sourceFile.getAbsolutePath() : filePath, fileName, expectedSha256, actualSha256);
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to verify SHA-256 before import", e);
            DialogUtils.oneDialog(this,
                    getString(R.string.problem_has_been_detected),
                    "Não foi possível validar a integridade do arquivo antes da importação.",
                    getString(R.string.ok),
                    true,
                    R.drawable.warning_48px,
                    true,
                    getIntent().hasExtra("addromnow") ? this::finish : null,
                    getIntent().hasExtra("addromnow") ? this::finish : null);
            return false;
        }
    }

    private void onImportHashMismatch(String filePath, String fileName, String expectedSha256, String actualSha256) {
        VmFlowTracker.mark(this, vmID, VmFlowState.ERROR, "import_hash_mismatch", "abort");

        String romStateId = getIntent().getStringExtra("romIdForDownloadState");
        if (romStateId != null && !romStateId.trim().isEmpty()) {
            new DownloadStateStore(this).updateStatus(romStateId, DownloadStatus.HASH_MISMATCH);
        }

        quarantineOrDeleteMismatchFile(filePath, fileName);

        Log.e(TAG, "HASH_MISMATCH expected=" + expectedSha256 + " actual=" + actualSha256 + " file=" + filePath);

        DialogUtils.oneDialog(this,
                getString(R.string.problem_has_been_detected),
                "Falha de integridade: o SHA-256 do arquivo não confere com o catálogo. A importação foi bloqueada.",
                getString(R.string.ok),
                true,
                R.drawable.error_96px,
                true,
                getIntent().hasExtra("addromnow") ? this::finish : null,
                getIntent().hasExtra("addromnow") ? this::finish : null);
    }

    private void quarantineOrDeleteMismatchFile(String filePath, String fileName) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        try {
            File mismatchFile = new File(filePath);
            if (!mismatchFile.exists()) {
                return;
            }
            File recycleDir = new File(AppConfig.recyclebin);
            if (!recycleDir.exists()) {
                recycleDir.mkdirs();
            }
            String safeName = (fileName == null || fileName.trim().isEmpty()) ? mismatchFile.getName() : fileName;
            File quarantineTarget = new File(recycleDir, "hash_mismatch_" + System.currentTimeMillis() + "_" + safeName);
            FileUtils.moveAFile(mismatchFile.getAbsolutePath(), quarantineTarget.getAbsolutePath());
            if (mismatchFile.exists()) {
                mismatchFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to quarantine mismatch file", e);
        }
    }

    private String computeSha256(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        if (inputStream == null) {
            throw new IOException("Input stream is null");
        }
        try (InputStream in = inputStream) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        }
    }

    private boolean isValidSha256(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.length() != 64) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lowerHex = c >= 'a' && c <= 'f';
            boolean upperHex = c >= 'A' && c <= 'F';
            if (!(digit || lowerHex || upperHex)) {
                return false;
            }
        }
        return true;
    }

    private void changeOnClickCdrom() {
        binding.cdromField.setEndIconOnClickListener(v -> {
            if (!Objects.requireNonNull(binding.cdrom.getText()).toString().isEmpty()) {
                binding.cdrom.setText("");
                binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_NONE);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void importRom(Uri fileUri, String filePath, String fileName) {
        if (!(fileName.endsWith(".cvbi") || fileName.endsWith(".cvbi.zip") || filePath.endsWith(".cvbi") || filePath.endsWith(".cvbi.zip"))) {
            VmFlowTracker.mark(this, vmID, VmFlowState.ERROR, "import_invalid_extension", "abort");
            DialogUtils.oneDialog(this,
                    getResources().getString(R.string.problem_has_been_detected),
                    getResources().getString(R.string.format_not_supported_please_select_file_with_format_cvbi),
                    getResources().getString(R.string.ok),
                    true,
                    R.drawable.error_96px,
                    true,
                    getIntent().hasExtra("addromnow") ? this::finish : null,
                    getIntent().hasExtra("addromnow") ? this::finish : null
            );
            return;
        }

        if (!verifyImportHashBeforeInstall(fileUri, filePath, fileName)) {
            return;
        }

        boolean isUseUri;

        if (filePath.isEmpty() || !FileUtils.isFileExists(filePath)) {
            if (fileUri != null && !fileUri.toString().isEmpty()) {
                isUseUri = true;
            } else {
                VmFlowTracker.mark(this, vmID, VmFlowState.ERROR, "import_source_missing", "abort");
                DialogUtils.oneDialog(this,
                        getResources().getString(R.string.oops),
                        getResources().getString(R.string.error_CR_CVBI1),
                        getResources().getString(R.string.ok),
                        true,
                        R.drawable.error_96px,
                        true,
                        getIntent().hasExtra("addromnow") ? this::finish : null,
                        getIntent().hasExtra("addromnow") ? this::finish : null
                );
                return;
            }
        } else {
            isUseUri = false;
        }

        if (!createVMFolder(false)) {
            VmFlowTracker.mark(this, vmID, VmFlowState.ERROR, "import_vm_dir_create_failed", "abort");
            DialogUtils.oneDialog(this,
                    getString(R.string.oops),
                    getString(R.string.unable_to_cvbi_file_vm_dir_content),
                    getString(R.string.ok),
                    true,
                    R.drawable.warning_48px,
                    true,
                    null,
                    null);
            return;
        }

        isImportingCVBI = true;
        VmFlowTracker.mark(this, vmID, VmFlowState.CREATING, "import_started", "extract");

        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_style, null);
        TextView progressText = progressView.findViewById(R.id.progress_text);
        progressText.setText(getString(R.string.importing) + "\n" + getString(R.string.please_stay_here));
        ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.show();

        Log.i(TAG, "importRom: Extracting from " + filePath + " to " + AppConfig.vmFolder + vmID);

        new Thread(() -> {
            boolean result = isUseUri ? ZipUtils.extract(
                    this,
                    fileUri,
                    AppConfig.vmFolder + vmID,
                    progressText,
                    progressBar
            ) : ZipUtils.extract(
                    this,
                    filePath,
                    AppConfig.vmFolder + vmID,
                    progressText,
                    progressBar
            );

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    new Thread(() -> FileUtils.deleteDirectory(AppConfig.vmFolder + vmID)).start();
                    return;
                }

                progressDialog.dismiss();

                if (result) {
                    afterExtractCVBIFile(fileName);
                } else {
                    VmFlowTracker.mark(VMCreatorActivity.this, vmID, VmFlowState.ERROR, "import_extract_failed", "abort");
                    runOnUiThread(() -> DialogUtils.oneDialog(VMCreatorActivity.this,
                            getString(R.string.oops),
                            getString(R.string.could_not_process_cvbi_file_content),
                            getString(R.string.ok),
                            true,
                            R.drawable.warning_48px,
                            true,
                            null,
                            null));
                }
            });
        }).start();

        if (Objects.requireNonNull(binding.drive.getText()).toString().isEmpty()) {
            binding.driveField.setEndIconDrawable(R.drawable.round_add_24);
        } else {
            binding.driveField.setEndIconDrawable(R.drawable.more_vert_24px);
        }
    }

    @SuppressLint("SetTextI18n")
    private void afterExtractCVBIFile(String _filename) {
        isImportingCVBI = false;
        binding.ivIcon.setEnabled(true);
        try {
            if (!FileUtils.isFileExists(AppConfig.vmFolder + vmID + "/rom-data.json")) {
                String _getDiskFile = VMManager.quickScanDiskFileInFolder(AppConfig.vmFolder + vmID);
                if (!_getDiskFile.isEmpty()) {
                    //Error code: CR_CVBI2
                    if (getIntent().hasExtra("addromnow") && !addromnowdone) {
                        addromnowdone = true;
                        if (Objects.requireNonNull(getIntent().getStringExtra("romextra")).isEmpty()) {
                            setDefault();
                            binding.drive.setText(_getDiskFile);
                        } else {
                            if (Objects.requireNonNull(getIntent().getStringExtra("romextra")).contains(Objects.requireNonNull(getIntent().getStringExtra("finalromfilename")))) {
                                binding.qemu.setText(Objects.requireNonNull(getIntent().getStringExtra("romextra")).replace(Objects.requireNonNull(getIntent().getStringExtra("finalromfilename")), "\"" + _getDiskFile + "\""));
                            } else {
                                binding.drive.setText(_getDiskFile);
                                binding.qemu.setText(Objects.requireNonNull(getIntent().getStringExtra("romextra")).replace("OhnoIjustrealizeditsmidnightandIstillhavetodothis", AppConfig.vmFolder + vmID + "/"));
                            }
                        }

                        binding.title.setText(getIntent().getStringExtra("romname"));

                        if (getIntent().hasExtra("romicon") && !Objects.requireNonNull(getIntent().getStringExtra("romicon")).isEmpty()) {
                            startProcessingThumbnail(Uri.parse(getIntent().getStringExtra("romicon")));
                        }
                    } else {
                        if (Objects.requireNonNull(binding.qemu.getText()).toString().isEmpty()) {
                            setDefault();
                        }
                        if (Objects.requireNonNull(binding.title.getText()).toString().isEmpty() || binding.title.getText().toString().equals("New VM")) {
                            binding.title.setText(_filename.replace(".cvbi", ""));
                        }
                        binding.drive.setText(_getDiskFile);
                        VMManager.setArch("X86_64", this);
                    }

                    VmFlowTracker.mark(this, vmID, VmFlowState.READY, "import_missing_metadata_fallback", "ready");
                    DialogUtils.oneDialog(this, getResources().getString(R.string.oops), getResources().getString(R.string.error_CR_CVBI2), getResources().getString(R.string.ok), true, R.drawable.warning_48px, true, null, null);
                } else {
                    //Error code: CR_CVBI3
                    VmFlowTracker.mark(this, vmID, VmFlowState.ERROR, "import_missing_metadata_no_disk", "abort");
                    if (getIntent().hasExtra("addromnow")) {
                        DialogUtils.oneDialog(this, getResources().getString(R.string.oops), getResources().getString(R.string.error_CR_CVBI3), getResources().getString(R.string.ok), true, R.drawable.error_96px, true,
                                this::finish, this::finish);
                    } else {
                        DialogUtils.oneDialog(this, getResources().getString(R.string.oops), getResources().getString(R.string.error_CR_CVBI3), getResources().getString(R.string.ok), true, R.drawable.error_96px, true, null, null);
                    }
                }
            } else {
                VmFlowTracker.mark(this, vmID, VmFlowState.READY, "import_metadata_loaded", "ready");
                JSONObject jObj = new JSONObject(FileUtils.readFromFile(this, new File(AppConfig.vmFolder + vmID + "/rom-data.json")));

                if (jObj.has("vmID")) {
                    if (!jObj.isNull("vmID")) {
                        if (!jObj.getString("vmID").isEmpty()) {
                            FileUtils.moveAFile(AppConfig.vmFolder + vmID, AppConfig.vmFolder + jObj.getString("vmID"));
                            vmID = jObj.getString("vmID");
                        }
                    }
                }

                if (jObj.has("title") && !jObj.isNull("title")) {
                    binding.title.setText(jObj.getString("title"));
                }

                if (jObj.has("drive") && !jObj.isNull("drive")) {
                    if (!jObj.getString("drive").isEmpty()) {
                        binding.drive.setText(AppConfig.vmFolder + vmID + "/" + jObj.getString("drive"));
                    }

                }

                if (jObj.has("qemu") && !jObj.isNull("qemu")) {
                    if (!jObj.getString("qemu").isEmpty()) {
                        binding.qemu.setText(jObj.getString("qemu").replace("OhnoIjustrealizeditsmidnightandIstillhavetodothis", AppConfig.vmFolder + vmID + "/"));
                    }
                }

                if (jObj.has("icon") && !jObj.isNull("icon")) {
                    binding.ivAddThubnail.setImageResource(R.drawable.edit_24px);
                    thumbnailPath = AppConfig.vmFolder + vmID + "/" + jObj.getString("icon");
                    thumbnailProcessing();
                } else {
                    binding.ivAddThubnail.setImageResource(R.drawable.round_add_24);
                    VMManager.setIconWithName(binding.ivIcon, Objects.requireNonNull(binding.title.getText()).toString());
                }

                if (jObj.has("cdrom") && !jObj.isNull("cdrom")) {
                    if (!jObj.getString("cdrom").isEmpty()) {
                        binding.cdrom.setText(AppConfig.vmFolder + vmID + "/" + jObj.getString("cdrom"));
                        binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                        binding.cdromField.setEndIconDrawable(R.drawable.close_24px);
                        changeOnClickCdrom();
                    } else {
                        binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_NONE);
                    }
                } else {
                    binding.cdromField.setEndIconMode(TextInputLayout.END_ICON_NONE);
                }

                if (jObj.has("arch") && !jObj.isNull("arch")) {
                    VMManager.setArch(jObj.getString("arch"), this);
                } else {
                    VMManager.setArch("x86_64", this);
                }

                FileUtils.moveAFile(AppConfig.vmFolder + _filename.replace(".cvbi", ""), AppConfig.vmFolder + vmID);

                if (!jObj.has("drive") && !jObj.has("cdrom") && !jObj.has("qemu")) {
                    UIUtils.oneDialog(getResources().getString(R.string.problem_has_been_detected), getResources().getString(R.string.this_rom_is_missing_too_much_information), true, false, this);
                }

                if (!jObj.has("versioncode")) {
                    UIUtils.oneDialog(getResources().getString(R.string.problem_has_been_detected), getResources().getString(R.string.this_rom_may_not_be_compatible), true, false, this);
                }

                if (jObj.has("author") && !jObj.isNull("author") && jObj.has("desc") && !jObj.isNull("desc")) {
                    DialogUtils.oneDialog(this,
                            getString(R.string.from) + " " + jObj.getString("author"),
                            jObj.getString("desc"),
                            getString(R.string.ok),
                            true,
                            R.drawable.info_24px,
                            false,
                            null,
                            null);
                }
            }
            binding.collapsingToolbarLayout.setSubtitle(MainSettingsManager.getArch(this));
        } catch (JSONException e) {
            Log.e(TAG, "afterExtractCVBIFile: " + e.getMessage());
        }
    }

    public String getPath(Uri uri) {
        return FileUtils.getPath(this, uri);
    }
}
