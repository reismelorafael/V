package com.vectras.vm.setupwizard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.VMManager;
import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.utils.DeviceUtils;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.FileUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class SetupFeatureCore {
    public static String TAG = "SetupFeatureCore";
    public static String lastErrorLog = "";
    public static final String POST_CHECK_FAIL_PREFIX = "POST_CHECK_FAIL:";
    private static final String BOOTSTRAP_LOG_PREFIX = "PROOT_BOOTSTRAP";

    public static boolean isInstalledSystemFiles(Context context) {
        return isInstalledProot(context) && isInstalledDistro(context);
    }

    public static boolean isInstalledProot(Context context) {
        return validateProotBootstrapState(context).ok;
    }

    public static boolean isInstalledDistro(Context context) {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File busybox = new File(filesDir + "/distro/bin/busybox");
        return busybox.isFile();
    }

    public static boolean isInstalledQemu(Context context) {
        return FileUtils.isFileExists(context.getFilesDir().getAbsolutePath() + "/distro/usr/local/bin/qemu-system-x86_64") ||
                FileUtils.isFileExists(context.getFilesDir().getAbsolutePath() + "/distro/usr/bin/qemu-system-x86_64");
    }


    public static final class ProotBootstrapValidationResult {
        public final boolean ok;
        public final ArrayList<String> errors;
        public final String shellPath;

        ProotBootstrapValidationResult(boolean ok, ArrayList<String> errors, String shellPath) {
            this.ok = ok;
            this.errors = errors;
            this.shellPath = shellPath;
        }

        public String summary() {
            if (ok) return "ok";
            return String.join(",", errors);
        }
    }

    public static ProotBootstrapValidationResult validateProotBootstrapState(Context context) {
        String filesDir = context.getFilesDir().getAbsolutePath();
        ArrayList<String> errors = new ArrayList<>();

        File proot = new File(filesDir + "/usr/bin/proot");
        if (!proot.isFile()) {
            errors.add("missing-proot");
        } else if (!proot.canExecute()) {
            errors.add("proot-not-executable");
        }

        File busybox = new File(filesDir + "/distro/bin/busybox");
        if (!busybox.isFile()) {
            errors.add("missing-distro-busybox");
        } else if (!busybox.canExecute()) {
            errors.add("distro-busybox-not-executable");
        }

        File rootShell = new File(filesDir + "/distro/bin/sh");
        if (!rootShell.isFile()) {
            errors.add("missing-rootfs-shell");
        } else if (!rootShell.canExecute()) {
            errors.add("rootfs-shell-not-executable");
        }

        File tmpDir = new File(filesDir + "/usr/tmp");
        if (!tmpDir.isDirectory()) {
            errors.add("missing-proot-tmp-dir");
        } else if (!tmpDir.canWrite()) {
            errors.add("proot-tmp-not-writable");
        }

        boolean ok = errors.isEmpty();
        return new ProotBootstrapValidationResult(ok, errors, rootShell.getAbsolutePath());
    }

    public static String runProotbuildSelfCheck(Context context) {
        ProotBootstrapValidationResult validation = validateProotBootstrapState(context);
        String filesDir = context.getFilesDir().getAbsolutePath();
        if (!validation.ok) {
            String summary = "PROOT_SELF_CHECK: FAIL errors=" + validation.summary();
            Log.e(TAG, summary + " filesDir=" + filesDir);
            return summary;
        }

        return "PROOT_SELF_CHECK: OK filesDir=" + filesDir + " shell=" + validation.shellPath;
    }

    public static final class PreflightResult {
        public final boolean ok;
        public final ArrayList<String> missingBinaries;
        public final ArrayList<String> missingOptionalModeBinaries;
        public final ArrayList<String> missingPackages;

        private PreflightResult(
                boolean ok,
                ArrayList<String> missingBinaries,
                ArrayList<String> missingOptionalModeBinaries,
                ArrayList<String> missingPackages
        ) {
            this.ok = ok;
            this.missingBinaries = missingBinaries;
            this.missingOptionalModeBinaries = missingOptionalModeBinaries;
            this.missingPackages = missingPackages;
        }

        public String shortSummary() {
            if (ok) {
                return "Preflight OK";
            }
            return "missing-bin=" + missingBinaries.size() + ",missing-pkg=" + missingPackages.size();
        }

        public String uiSummary() {
            StringBuilder summary = new StringBuilder("Preflight check failed. Missing components:\n");
            if (!missingBinaries.isEmpty()) {
                summary.append("• Required binaries: ").append(joinLimited(missingBinaries)).append("\n");
            }
            if (!missingOptionalModeBinaries.isEmpty()) {
                summary.append("• Optional components for current mode: ")
                        .append(joinLimited(missingOptionalModeBinaries))
                        .append("\n");
            }
            if (!missingPackages.isEmpty()) {
                summary.append("• Required packages: ").append(joinLimited(missingPackages));
            }
            return summary.toString().trim();
        }

        public String ledgerReason() {
            return "PREFLIGHT_FAIL{" + shortSummary() + "}";
        }

        private static String joinLimited(List<String> values) {
            if (values.isEmpty()) return "none";
            int limit = Math.min(values.size(), 8);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                if (i > 0) out.append(", ");
                out.append(values.get(i));
            }
            if (values.size() > limit) {
                out.append(" (+").append(values.size() - limit).append(" more)");
            }
            return out.toString();
        }
    }

    public static final class VmStartPreflightOptions {
        public final String vmUi;
        public final boolean runWithXterm;
        public final boolean headless;

        public VmStartPreflightOptions(String vmUi, boolean runWithXterm, boolean headless) {
            this.vmUi = vmUi;
            this.runWithXterm = runWithXterm;
            this.headless = headless;
        }

        boolean shouldRequireXterm() {
            return "X11".equals(vmUi) && runWithXterm && !headless;
        }
    }

    public static final class SetupPostCheckResult {
        public final boolean ok;
        public final ArrayList<String> failedItems;

        SetupPostCheckResult(boolean ok, ArrayList<String> failedItems) {
            this.ok = ok;
            this.failedItems = failedItems;
        }

        public String technicalReason() {
            return formatPostCheckFailure(failedItems);
        }
    }

    public static String formatPostCheckFailure(List<String> failedItems) {
        StringJoiner joiner = new StringJoiner(",");
        if (failedItems != null) {
            for (String item : failedItems) {
                if (item != null && !item.trim().isEmpty()) {
                    joiner.add(item.trim());
                }
            }
        }
        String compactItems = joiner.toString();
        if (compactItems.isEmpty()) {
            compactItems = "unknown";
        }
        return POST_CHECK_FAIL_PREFIX + compactItems;
    }

    public static SetupPostCheckResult runSetupPostCheck(Context context) {
        ArrayList<String> failedItems = new ArrayList<>();
        if (!isInstalledProot(context)) {
            failedItems.add("missing-proot");
        }
        if (!isInstalledDistro(context)) {
            failedItems.add("missing-distro-busybox");
        }
        if (!isInstalledQemu(context)) {
            failedItems.add("missing-qemu-binary");
        }
        return new SetupPostCheckResult(failedItems.isEmpty(), failedItems);
    }

    public static PreflightResult runVmStartPreflight(
            Context context,
            String requiredQemuBinary,
            VmStartPreflightOptions options
    ) {
        ArrayList<String> missingBinaries = new ArrayList<>();
        ArrayList<String> missingOptionalModeBinaries = new ArrayList<>();
        ArrayList<String> missingPackages = new ArrayList<>();

        VmStartPreflightOptions resolvedOptions = options == null
                ? new VmStartPreflightOptions("", false, false)
                : options;

        if (!hasBinary(context, requiredQemuBinary)) {
            missingBinaries.add(requiredQemuBinary);
        }
        if (resolvedOptions.shouldRequireXterm()) {
            if (!hasBinary(context, "xterm")) {
                missingBinaries.add("xterm");
            }
        } else if (!hasBinary(context, "xterm")) {
            missingOptionalModeBinaries.add("xterm");
        }

        String pkgDbPath = context.getFilesDir().getAbsolutePath() + "/distro/lib/apk/db/installed";
        String pkgDb = readTextFile(pkgDbPath);
        if (pkgDb.isEmpty()) {
            missingPackages.add("apk-db-unavailable");
        } else {
            Set<String> expectedPkgs = new LinkedHashSet<>();
            expectedPkgs.addAll(parsePackageTokens(AppConfig.neededPkgs()));
            expectedPkgs.addAll(parsePackageTokens(AppConfig.neededPkgs32bit()));

            for (String pkg : expectedPkgs) {
                if (!isPkgInstalled(pkgDb, pkg)) {
                    missingPackages.add(pkg);
                }
            }
        }

        boolean ok = missingBinaries.isEmpty() && missingPackages.isEmpty();
        return new PreflightResult(ok, missingBinaries, missingOptionalModeBinaries, missingPackages);
    }

    public static void launchReinstallSetup(Context context) {
        AppConfig.needreinstallsystem = true;
        Intent intent = new Intent(context, SetupWizard2Activity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static ArrayList<String> parsePackageTokens(String rawPkgs) {
        if (rawPkgs == null || rawPkgs.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(rawPkgs.trim().split("\\s+")));
    }

    private static boolean isPkgInstalled(String pkgDb, String pkgName) {
        if (pkgDb == null || pkgName == null) {
            return false;
        }

        String normalizedPkgName = pkgName.trim();
        if (pkgDb.trim().isEmpty() || normalizedPkgName.isEmpty()) {
            return false;
        }

        String[] lines = pkgDb.split("\\R");
        for (String line : lines) {
            if (!line.startsWith("P:")) {
                continue;
            }

            String installedPkg = line.substring(2);
            if (installedPkg.equals(normalizedPkgName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasBinary(Context context, String binary) {
        if (binary == null || binary.isEmpty()) return false;
        String filesDir = context.getFilesDir().getAbsolutePath();
        String[] binSearchPaths = new String[]{
                filesDir + "/distro/usr/local/bin/" + binary,
                filesDir + "/distro/usr/bin/" + binary,
                filesDir + "/usr/bin/" + binary
        };
        for (String binPath : binSearchPaths) {
            if (FileUtils.isFileExists(binPath)) {
                return true;
            }
        }
        return false;
    }

    private static String readTextFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return "";
        }
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream in = new BufferedInputStream(fileInputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            Log.e(TAG, "readTextFile: ", e);
            return "";
        }
    }

    public static boolean startExtractSystemFiles(Context context) {
        if (isInstalledSystemFiles(context)) return true;
        lastErrorLog = "";

        String filesDir = context.getFilesDir().getAbsolutePath();
        File distroDir = new File(filesDir + "/distro");
        File binDir = new File(distroDir + "/bin");
        if (!binDir.exists()) {
            if (!isInstalledProot(context)) {
                if (!extractSystemFiles(context, "bootstrap", "")) return false;
            }

            if (isInstalledDistro(context)) return true;

            File tmpDir = new File(context.getFilesDir(), "usr/tmp");
            if (!tmpDir.isDirectory()) {
                if (tmpDir.mkdirs()) {
                    FileUtils.chmod(tmpDir, 0771);
                } else {
                    Log.e(TAG, "startExtractSystemFiles: Failed to create folder: tmp.");
                }
            }

            return extractSystemFiles(context, "alpine19", "distro");
        }
        return false;
    }

    public static boolean extractSystemFiles(Context context, String fromAsset, String extractTo) {
        String randomFileName = VMManager.startRamdomVMID();
        final String[] selectedAssetHolder = new String[1];
        String assetPath = resolveAssetPath(context, fromAsset, selectedAssetHolder);
        if (assetPath == null) {
            return false;
        }
        Log.i(TAG, BOOTSTRAP_LOG_PREFIX + " ABI_SELECTED candidates="
                + BootstrapAbiMapper.resolveCandidates(Build.SUPPORTED_ABIS)
                + " selected=" + selectedAssetHolder[0]
                + " assetPath=" + assetPath);

        final Path filesDirRealPath;
        final Path extractTargetPath;
        final Path extractedTarPath;
        try {
            filesDirRealPath = context.getFilesDir().toPath().toRealPath();
            extractTargetPath = filesDirRealPath.resolve(extractTo).normalize();
            extractedTarPath = filesDirRealPath.resolve(randomFileName + ".tar").normalize();
        } catch (IOException | InvalidPathException e) {
            lastErrorLog = "Invalid extraction path configuration: " + e;
            Log.e(TAG, lastErrorLog, e);
            return false;
        }

        if (!extractTargetPath.startsWith(filesDirRealPath) || !extractedTarPath.startsWith(filesDirRealPath)) {
            lastErrorLog = "Rejected extraction paths outside app files directory"
                    + " filesDir=" + filesDirRealPath
                    + " extractTo=" + extractTargetPath
                    + " extractedFilePath=" + extractedTarPath;
            Log.e(TAG, lastErrorLog);
            return false;
        }

        Log.i(TAG, "extractSystemFiles approved paths"
                + " filesDir=" + filesDirRealPath
                + " extractTo=" + extractTargetPath
                + " extractedFilePath=" + extractedTarPath);

        File destDir = extractTargetPath.toFile();
        if (!destDir.exists()) {
            if (!destDir.mkdirs()) {
                Log.e(TAG, "extractSystemFiles: Unable to create folder " + extractTargetPath);
            }
        }

        boolean isCompleted;

        // Step 1: Copy asset to filesDir
        isCompleted = copyAssetToFile(context, assetPath, extractedTarPath.toString());

        if (isCompleted) {
            File extractedTarFile = extractedTarPath.toFile();
            if (!extractedTarPath.toString().endsWith(".tar") || !extractedTarFile.exists() || !extractedTarFile.isFile()) {
                lastErrorLog = "Invalid tar file before extraction"
                        + " path=" + extractedTarPath
                        + " exists=" + extractedTarFile.exists()
                        + " isFile=" + extractedTarFile.isFile();
                Log.e(TAG, lastErrorLog);
                return false;
            }
        }

        // Step 2: Run tar extraction
        if (isCompleted) {
            String[] cmdline = {"tar", "xf", extractedTarPath.toString(), "-C", extractTargetPath.toString()};
            Process process = null;
            try {
                // Security note: ProcessBuilder receives each token separately. There is no shell invocation here.
                ProcessBuilder processBuilder = new ProcessBuilder(cmdline);
                processBuilder.environment().remove("LD_LIBRARY_PATH");
                process = processBuilder.start();

                StringBuilder errorOutput = new StringBuilder();
                Thread stdoutCollector = new Thread(() -> {
                    try (BufferedReader outputReader =
                                 new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = outputReader.readLine()) != null) {
                            synchronized (errorOutput) {
                                errorOutput.append(line).append("\n");
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "extractSystemFiles output collector: ", e);
                    }
                }, "setup-extract-output");
                stdoutCollector.start();

                ProcessRuntimeOps.TimeoutExecutionResult waitResult = ProcessRuntimeOps.waitForByCategory(
                        process,
                        ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION
                );

                try {
                    stdoutCollector.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lastErrorLog = "Extraction output collector interrupted ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath;
                    Log.e(TAG, lastErrorLog, e);
                    return false;
                }

                String commandSummary = formatCommand(cmdline);
                String stderrSummary;
                synchronized (errorOutput) {
                    stderrSummary = errorOutput.toString().trim();
                }
                if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.TIMEOUT) {
                    lastErrorLog = "Timeout during extraction ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath
                            + " cmd=" + commandSummary
                            + " detail=" + waitResult.message
                            + (stderrSummary.isEmpty() ? "" : " stderr=" + stderrSummary);
                    Log.e(TAG, lastErrorLog);
                    return false;
                }

                if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR) {
                    lastErrorLog = "Extraction execution error ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath
                            + " cmd=" + commandSummary
                            + " detail=" + waitResult.message
                            + (stderrSummary.isEmpty() ? "" : " stderr=" + stderrSummary);
                    Log.e(TAG, lastErrorLog);
                    return false;
                }

                if (waitResult.exitCode != 0 || !stderrSummary.isEmpty()) {
                    lastErrorLog = "Extraction failed ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath
                            + " cmd=" + commandSummary
                            + " exit=" + waitResult.exitCode
                            + (stderrSummary.isEmpty() ? "" : " stderr=" + stderrSummary);
                    Log.e(TAG, lastErrorLog);
                    return false;
                }

                if ("bootstrap".equals(fromAsset)) {
                    FileUtils.chmod(new File(context.getFilesDir(), "usr/bin/proot"), 0755);
                }
                if (fromAsset.contains("alpine")) {
                    FileUtils.chmod(new File(context.getFilesDir(), "distro/bin/busybox"), 0755);
                    setDNS(context);
                }

                ArrayList<String> extractionPostCheckFailedItems = new ArrayList<>();
                if (!extractTargetPath.toFile().exists()) {
                    extractionPostCheckFailedItems.add("missing-extract-target");
                }
                if ("bootstrap".equals(fromAsset) && !isInstalledProot(context)) {
                    extractionPostCheckFailedItems.add("missing-proot-after-bootstrap-extract");
                }
                if (fromAsset.contains("alpine") && !isInstalledDistro(context)) {
                    extractionPostCheckFailedItems.add("missing-distro-after-alpine-extract");
                }
                ProotBootstrapValidationResult validation = validateProotBootstrapState(context);
                if (!validation.ok) {
                    extractionPostCheckFailedItems.addAll(validation.errors);
                }

                if (!extractionPostCheckFailedItems.isEmpty()) {
                    lastErrorLog = formatPostCheckFailure(extractionPostCheckFailedItems);
                    Log.e(TAG, BOOTSTRAP_LOG_PREFIX + " PRECHECK_FAIL details=" + lastErrorLog);
                    return false;
                }

                Log.i(TAG, BOOTSTRAP_LOG_PREFIX + " EXTRACT_OK asset=" + assetPath + " target=" + extractTargetPath);
                return true;
            } catch (IOException e) {
                lastErrorLog = lastErrorLog.isEmpty() ? e.toString() : lastErrorLog + "\n" + e;
                Log.e(TAG, "extractSystemFiles: ", e);
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return false;
    }


    private static String resolveAssetPath(Context context, String fromAsset, String[] selectedAssetHolder) {
        List<String> candidates = BootstrapAbiMapper.resolveCandidates(Build.SUPPORTED_ABIS);
        for (String abi : candidates) {
            String assetPath = fromAsset + "/" + abi + ".tar";
            try (InputStream ignored = context.getAssets().open(assetPath)) {
                if (selectedAssetHolder != null && selectedAssetHolder.length > 0) {
                    selectedAssetHolder[0] = abi;
                }
                return assetPath;
            } catch (IOException ignored) {
            }
        }

        lastErrorLog = "Unable to resolve asset for " + fromAsset + " candidates=" + candidates;
        Log.e(TAG, BOOTSTRAP_LOG_PREFIX + " ABI_RESOLUTION_FAIL " + lastErrorLog);
        return null;
    }

    private static String formatCommand(String[] cmdline) {
        StringJoiner commandJoiner = new StringJoiner(" ");
        for (String token : cmdline) {
            commandJoiner.add(token);
        }
        return commandJoiner.toString();
    }

    public static boolean copyAssetToFile(Context context, String assetPath, String outputPath) {
        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(outputPath)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            lastErrorLog = e.toString();
            Log.e(TAG, "copyAssetToFile: ", e);
            return false;
        }
    }

    public static void setDNS(Context context) {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File rootDir = new File(filesDir + "/distro/root");
        if (!rootDir.exists()) if(!rootDir.mkdir()) Log.e(TAG, "extractSystemFiles: Unable to create folder " + filesDir + "/distro/root");

        File resolv = new File(filesDir + "/distro/etc/resolv.conf");
        if(!Objects.requireNonNull(resolv.getParentFile()).mkdirs()) Log.e(TAG, "extractSystemFiles: Unable to add DNS.");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resolv))) {
            writer.write("nameserver 1.1.1.1\n");
            writer.write("nameserver 1.0.0.1\n");
            writer.write("nameserver 8.8.8.8\n");
            writer.write("nameserver 8.8.4.4\n");
        } catch (IOException e) {
            Log.e(TAG, "extractSystemFiles: resolv: ", e);
        }
    }

    public static void checkabi(Context context) {
        if (!DeviceUtils.is64bit())
            DialogUtils.oneDialog((Activity) context,
                    context.getResources().getString(R.string.warning),
                    context.getResources().getString(R.string.cpu_not_support_64),
                    context.getString(R.string.ok),
                    true,
                    R.drawable.error_96px,
                    true,
                    null,
                    null);
    }
}
