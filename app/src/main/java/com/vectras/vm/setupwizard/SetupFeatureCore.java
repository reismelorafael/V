package com.vectras.vm.setupwizard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.VMManager;
import com.vectras.vm.core.ProotCommandBuilder;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class SetupFeatureCore {
    public static String TAG = "SetupFeatureCore";
    public static final String ABI_RESOLVE_TAG = "SETUP_ABI_RESOLVE";
    public static String lastErrorLog = "";
    public static final String COPY_FAIL_PREFIX = "COPY_FAIL:";
    public static final String INTEGRITY_FAIL_PREFIX = "INTEGRITY_FAIL:";
    public static final String EXTRACTION_FAIL_PREFIX = "EXTRACTION_FAIL:";
    public static final String POST_CHECK_FAIL_PREFIX = "POST_CHECK_FAIL:";
    private static final String BOOTSTRAP_LOG_PREFIX = "PROOT_BOOTSTRAP";
    private static final long MIN_TAR_BYTES = 1024L;

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

    public static final class ProotSelfCheckResult {
        public final boolean ok;
        public final boolean validatorOk;
        public final boolean commandBuilt;
        public final boolean executed;
        public final int exitCode;
        public final String summary;
        public final List<String> details;

        private ProotSelfCheckResult(
                boolean ok,
                boolean validatorOk,
                boolean commandBuilt,
                boolean executed,
                int exitCode,
                String summary,
                List<String> details
        ) {
            this.ok = ok;
            this.validatorOk = validatorOk;
            this.commandBuilt = commandBuilt;
            this.executed = executed;
            this.exitCode = exitCode;
            this.summary = summary;
            this.details = Collections.unmodifiableList(new ArrayList<>(details));
        }

        public String toStructuredText() {
            StringBuilder out = new StringBuilder();
            out.append("ok=").append(ok).append("\n");
            out.append("validatorOk=").append(validatorOk).append("\n");
            out.append("commandBuilt=").append(commandBuilt).append("\n");
            out.append("executed=").append(executed).append("\n");
            out.append("exitCode=").append(exitCode).append("\n");
            out.append("summary=").append(summary == null ? "" : summary);
            for (String detail : details) {
                out.append("\n").append(detail);
            }
            return out.toString();
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

    public static boolean runProotSelfCheckLegacy(Context context) {
        if (context == null) {
            Log.w(TAG, "runProotSelfCheck: context is null");
            return false;
        }

        String prootPath = context.getFilesDir().getAbsolutePath() + "/usr/bin/proot";
        if (!FileUtils.isFileExists(prootPath)) {
            Log.w(TAG, "runProotSelfCheck: missing proot binary at " + prootPath);
            return false;
        }

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(prootPath, "--help");
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().remove("LD_LIBRARY_PATH");
            process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 4096) {
                        output.append(line).append("\n");
                    }
                }
            }

            ProcessRuntimeOps.TimeoutExecutionResult waitResult = ProcessRuntimeOps.waitForByCategory(
                    process,
                    ProcessRuntimeOps.ExecutionCategory.QUICK_QUERY
            );

            boolean ok = waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.SUCCESS
                    && waitResult.exitCode == 0;
            String outputSummary = output.toString().trim();
            if (outputSummary.length() > 1024) {
                outputSummary = outputSummary.substring(0, 1024);
            }
            if (ok) {
                Log.i(TAG, "runProotSelfCheck: success detail=" + waitResult.message
                        + (outputSummary.isEmpty() ? "" : " output=" + outputSummary));
            } else {
                Log.e(TAG, "runProotSelfCheck: failed status=" + waitResult.status
                        + " exitCode=" + waitResult.exitCode
                        + " detail=" + waitResult.message
                        + (outputSummary.isEmpty() ? "" : " output=" + outputSummary));
            }
            return ok;
        } catch (IOException e) {
            Log.e(TAG, "runProotSelfCheck: ", e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
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

    public static final class PostInstallCheckResult {
        public final boolean ok;
        public final List<String> failedItems;

        PostInstallCheckResult(boolean ok, List<String> failedItems) {
            this.ok = ok;
            this.failedItems = Collections.unmodifiableList(new ArrayList<>(failedItems));
        }

        public String summary() {
            return ok ? "ok" : formatPostCheckFailure(failedItems);
        }
    }

    public static PostInstallCheckResult runPostInstallCheck(Context context) {
        SetupPostCheckResult current = runSetupPostCheck(context);
        return new PostInstallCheckResult(current.ok, current.failedItems);
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

    public static ProotSelfCheckResult runProotSelfCheck(Context context) {
        LinkedHashMap<String, String> detailMap = new LinkedHashMap<>();
        detailMap.put("check", "proot-self-check");

        if (context == null) {
            detailMap.put("error", "context-null");
            return buildProotSelfCheckResult(false, false, false, false, -1,
                    "Proot self-check failed: context is null.", detailMap);
        }

        String filesDir = context.getFilesDir().getAbsolutePath();
        String rootfsPath = filesDir + "/distro";
        String workDir = "/root";
        String requiredQemuBinary = "qemu-system-x86_64";

        detailMap.put("filesDir", filesDir);
        detailMap.put("rootfsPath", rootfsPath);
        detailMap.put("workDir", workDir);

        ProotPrerequisiteResult prerequisiteResult = validateProotPrerequisites(context, rootfsPath, workDir);
        if (!prerequisiteResult.ok) {
            detailMap.putAll(prerequisiteResult.details);
            return buildProotSelfCheckResult(false, false, false, false, -1,
                    "Proot self-check failed prerequisites: " + prerequisiteResult.summary,
                    detailMap);
        }

        PreflightResult vmPreflight = runVmStartPreflight(
                context,
                requiredQemuBinary,
                new VmStartPreflightOptions("", false, false)
        );
        detailMap.put("vmPreflight", vmPreflight.shortSummary());
        if (!vmPreflight.ok) {
            detailMap.put("vmPreflightMissingBins", joinListForDetails(vmPreflight.missingBinaries));
            detailMap.put("vmPreflightMissingPkgs", joinListForDetails(vmPreflight.missingPackages));
            return buildProotSelfCheckResult(false, false, false, false, -1,
                    "Proot self-check blocked by VM preflight: " + vmPreflight.shortSummary(),
                    detailMap);
        }

        ProotCommandBuilder commandBuilder = new ProotCommandBuilder(context, rootfsPath, workDir)
                .setPath("/bin:/usr/bin:/sbin:/usr/sbin")
                .setTmpDir(filesDir + "/usr/tmp");

        List<String> baselineCommand = commandBuilder.buildCommand();
        detailMap.put("baselineCommand", formatCommand(baselineCommand.toArray(new String[0])));

        ProotExecAttempt versionAttempt = executeProotVersion(context, filesDir);
        detailMap.putAll(versionAttempt.details);
        if (versionAttempt.result != null && versionAttempt.result.ok) {
            return versionAttempt.result;
        }

        ProotExecAttempt fallbackAttempt = executeProotFallback(commandBuilder, detailMap);
        return fallbackAttempt.result;
    }

    private static ProotExecAttempt executeProotVersion(Context context, String filesDir) {
        LinkedHashMap<String, String> detailMap = new LinkedHashMap<>();
        String prootBin = filesDir + "/usr/bin/proot";
        detailMap.put("preferredExec", prootBin + " --version");

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(prootBin, "--version");
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            ProcessRuntimeOps.TimeoutExecutionResult waitResult = ProcessRuntimeOps.waitForByCategory(
                    process,
                    ProcessRuntimeOps.ExecutionCategory.QUICK_QUERY
            );

            detailMap.put("preferredWaitStatus", waitResult.status.name());
            detailMap.put("preferredWaitMessage", waitResult.message);
            detailMap.put("preferredTimedOut", Boolean.toString(waitResult.timedOut));
            detailMap.put("preferredExitCode", Integer.toString(waitResult.exitCode));
            detailMap.put("preferredOutput", readProcessOutput(process));

            boolean ok = waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.SUCCESS && waitResult.exitCode == 0;
            String summary = ok
                    ? "Proot self-check succeeded via preferred version probe."
                    : "Preferred proot version probe failed, falling back to dry-run.";

            ProotSelfCheckResult result = buildProotSelfCheckResult(
                    ok,
                    true,
                    true,
                    true,
                    waitResult.exitCode,
                    summary,
                    detailMap
            );
            return new ProotExecAttempt(result, detailMap);
        } catch (IOException ioException) {
            detailMap.put("preferredExecError", ioException.toString());
            ProotSelfCheckResult result = buildProotSelfCheckResult(
                    false,
                    true,
                    true,
                    false,
                    -1,
                    "Preferred proot version probe failed to start, falling back to dry-run.",
                    detailMap
            );
            return new ProotExecAttempt(result, detailMap);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private static ProotExecAttempt executeProotFallback(ProotCommandBuilder commandBuilder,
                                                          LinkedHashMap<String, String> inheritedDetails) {
        LinkedHashMap<String, String> detailMap = new LinkedHashMap<>(inheritedDetails);
        List<String> fallbackCommand = new ArrayList<>(commandBuilder.buildCommand());
        fallbackCommand.add("-c");
        fallbackCommand.add("true");
        detailMap.put("fallbackExec", formatCommand(fallbackCommand.toArray(new String[0])));

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(fallbackCommand);
            commandBuilder.applyEnvironment(processBuilder.environment());
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();

            ProcessRuntimeOps.TimeoutExecutionResult waitResult = ProcessRuntimeOps.waitForByCategory(
                    process,
                    ProcessRuntimeOps.ExecutionCategory.QUICK_QUERY
            );

            detailMap.put("fallbackWaitStatus", waitResult.status.name());
            detailMap.put("fallbackWaitMessage", waitResult.message);
            detailMap.put("fallbackTimedOut", Boolean.toString(waitResult.timedOut));
            detailMap.put("fallbackExitCode", Integer.toString(waitResult.exitCode));
            detailMap.put("fallbackOutput", readProcessOutput(process));

            boolean ok = waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.SUCCESS && waitResult.exitCode == 0;
            String summary = ok
                    ? "Proot self-check succeeded via fallback dry-run."
                    : "Proot self-check failed: fallback dry-run did not complete successfully.";

            return new ProotExecAttempt(
                    buildProotSelfCheckResult(ok, true, true, true, waitResult.exitCode, summary, detailMap),
                    detailMap
            );
        } catch (IOException ioException) {
            detailMap.put("fallbackExecError", ioException.toString());
            return new ProotExecAttempt(
                    buildProotSelfCheckResult(false, true, true, false, -1,
                            "Proot self-check failed: fallback dry-run failed to start.", detailMap),
                    detailMap
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private static ProotSelfCheckResult buildProotSelfCheckResult(boolean ok,
                                                                   boolean validatorOk,
                                                                   boolean commandBuilt,
                                                                   boolean executed,
                                                                   int exitCode,
                                                                   String summary,
                                                                   LinkedHashMap<String, String> detailMap) {
        ArrayList<String> detailLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : detailMap.entrySet()) {
            detailLines.add(entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()));
        }
        return new ProotSelfCheckResult(ok, validatorOk, commandBuilt, executed, exitCode, summary, detailLines);
    }

    private static ProotPrerequisiteResult validateProotPrerequisites(Context context,
                                                                      String rootfsPath,
                                                                      String workDir) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        ArrayList<String> failures = new ArrayList<>();

        boolean prootInstalled = isInstalledProot(context);
        boolean distroInstalled = isInstalledDistro(context);
        boolean workDirAvailable = new File(rootfsPath + workDir).isDirectory();

        details.put("prootInstalled", Boolean.toString(prootInstalled));
        details.put("distroInstalled", Boolean.toString(distroInstalled));
        details.put("workDirAvailable", Boolean.toString(workDirAvailable));

        if (!prootInstalled) {
            failures.add("missing-proot");
        }
        if (!distroInstalled) {
            failures.add("missing-distro");
        }
        if (!workDirAvailable) {
            failures.add("missing-workdir");
        }

        if (failures.isEmpty()) {
            details.put("validator", "ok");
            return new ProotPrerequisiteResult(true, "prerequisites-ok", details);
        }
        details.put("validator", "failed");
        details.put("missing", joinListForDetails(failures));
        return new ProotPrerequisiteResult(false, "missing: " + joinListForDetails(failures), details);
    }

    private static String readProcessOutput(Process process) {
        if (process == null) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int lineCount = 0;
            while ((line = bufferedReader.readLine()) != null && lineCount < 4) {
                if (lineCount > 0) {
                    output.append(" | ");
                }
                output.append(line);
                lineCount++;
            }
        } catch (IOException ignored) {
        }
        return output.toString();
    }

    private static String joinListForDetails(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add(value);
        }
        return joiner.toString();
    }

    private static final class ProotPrerequisiteResult {
        final boolean ok;
        final String summary;
        final LinkedHashMap<String, String> details;

        ProotPrerequisiteResult(boolean ok, String summary, LinkedHashMap<String, String> details) {
            this.ok = ok;
            this.summary = summary;
            this.details = details;
        }
    }

    private static final class ProotExecAttempt {
        final ProotSelfCheckResult result;
        final LinkedHashMap<String, String> details;

        ProotExecAttempt(ProotSelfCheckResult result, LinkedHashMap<String, String> details) {
            this.result = result;
            this.details = details;
        }
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

    private static ArrayList<File> buildRequiredExecutablesForExtractFlow(Context context, String fromAsset) {
        ArrayList<File> requiredExecutables = new ArrayList<>();
        if (context == null || fromAsset == null) {
            return requiredExecutables;
        }

        File filesDir = context.getFilesDir();
        if ("bootstrap".equals(fromAsset)) {
            requiredExecutables.add(new File(filesDir, "usr/bin/proot"));
        }

        if (fromAsset.contains("alpine")) {
            requiredExecutables.add(new File(filesDir, "distro/bin/busybox"));
            requiredExecutables.add(new File(filesDir, "distro/bin/sh"));
            requiredExecutables.add(new File(filesDir, "distro/usr/bin/env"));
        }

        return requiredExecutables;
    }

    private static void enforceExecutableMode(List<File> requiredExecutables, List<String> failedItems) {
        if (requiredExecutables == null || failedItems == null) {
            return;
        }

        for (File requiredExecutable : requiredExecutables) {
            if (requiredExecutable == null) {
                continue;
            }

            String normalizedPath = requiredExecutable.getPath().replace('\\', '/');
            if (!requiredExecutable.isFile()) {
                failedItems.add("missing-required-executable:" + normalizedPath);
                continue;
            }

            FileUtils.chmod(requiredExecutable, 0755);
            if (!requiredExecutable.canExecute()) {
                failedItems.add("chmod-failed:" + normalizedPath);
            }
        }
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

    @Nullable
    public static String computeSha256Hex(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "computeSha256Hex digest init: ", e);
            return null;
        }

        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream in = new BufferedInputStream(fileInputStream)) {
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        } catch (IOException e) {
            Log.e(TAG, "computeSha256Hex read: ", e);
            return null;
        }

        byte[] hash = digest.digest();
        char[] out = new char[hash.length * 2];
        final char[] hexMap = "0123456789abcdef".toCharArray();
        for (int i = 0; i < hash.length; i++) {
            int v = hash[i] & 0xFF;
            out[i * 2] = hexMap[v >>> 4];
            out[i * 2 + 1] = hexMap[v & 0x0F];
        }
        return new String(out);
    }

    public static boolean startExtractSystemFiles(Context context) {
        return startExtractSystemFiles(context, null);
    }

    public static boolean startExtractSystemFiles(Context context, @Nullable String bootstrapExpectedSha256) {
        if (isInstalledSystemFiles(context)) return true;
        lastErrorLog = "";

        String filesDir = context.getFilesDir().getAbsolutePath();
        File distroDir = new File(filesDir + "/distro");
        File binDir = new File(distroDir + "/bin");
        if (!binDir.exists()) {
            if (!isInstalledProot(context)) {
                if (!extractSystemFiles(context, "bootstrap", "", bootstrapExpectedSha256)) return false;
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
        return extractSystemFiles(context, fromAsset, extractTo, null);
    }

    public static boolean extractSystemFiles(Context context, String fromAsset, String extractTo, @Nullable String expectedSha256) {
        String randomFileName = VMManager.startRamdomVMID();
        final String[] selectedAssetHolder = new String[1];
        String assetPath = resolveAssetPath(context, fromAsset, selectedAssetHolder);
        if (assetPath == null) {
            String detail = lastErrorLog == null || lastErrorLog.trim().isEmpty()
                    ? "asset-path-resolution-failed fromAsset=" + fromAsset
                    : lastErrorLog;
            lastErrorLog = formatErrorCode(INTEGRITY_FAIL_PREFIX, detail);
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
            lastErrorLog = formatErrorCode(INTEGRITY_FAIL_PREFIX, "Invalid extraction path configuration: " + e);
            Log.e(TAG, lastErrorLog, e);
            return false;
        }

        if (!extractTargetPath.startsWith(filesDirRealPath) || !extractedTarPath.startsWith(filesDirRealPath)) {
            lastErrorLog = formatErrorCode(INTEGRITY_FAIL_PREFIX, "Rejected extraction paths outside app files directory"
                    + " filesDir=" + filesDirRealPath
                    + " extractTo=" + extractTargetPath
                    + " extractedFilePath=" + extractedTarPath);
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
        if (!isCompleted) {
            lastErrorLog = "COPY_ASSET_FAIL: Unable to copy system tar"
                    + " asset=" + assetPath
                    + " output=" + extractedTarPath;
            Log.e(TAG, lastErrorLog);
            return false;
        }

        if (isCompleted) {
            File extractedTarFile = extractedTarPath.toFile();
            if (!extractedTarPath.toString().endsWith(".tar") || !extractedTarFile.exists() || !extractedTarFile.isFile()) {
                lastErrorLog = formatErrorCode(INTEGRITY_FAIL_PREFIX, "Invalid tar file before extraction"
                        + " path=" + extractedTarPath
                        + " exists=" + extractedTarFile.exists()
                        + " isFile=" + extractedTarFile.isFile());
                Log.e(TAG, lastErrorLog);
                return false;
            }
        } else {
            String detail = lastErrorLog == null || lastErrorLog.trim().isEmpty()
                    ? "copy-asset-failed asset=" + assetPath + " output=" + extractedTarPath
                    : lastErrorLog;
            lastErrorLog = formatErrorCode(COPY_FAIL_PREFIX, detail);
            return false;
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
                final Process runningProcess = process;

                StringBuilder errorOutput = new StringBuilder();
                Thread stdoutCollector = new Thread(() -> {
                    try (BufferedReader outputReader =
                                 new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
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
                    lastErrorLog = formatErrorCode(EXTRACTION_FAIL_PREFIX, "OUTPUT_COLLECTOR_INTERRUPTED ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath);
                    Log.e(TAG, lastErrorLog, e);
                    return false;
                }

                String commandSummary = formatCommand(cmdline);
                String stderrSummary;
                synchronized (errorOutput) {
                    stderrSummary = errorOutput.toString().trim();
                }
                if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.TIMEOUT) {
                    lastErrorLog = formatErrorCode(EXTRACTION_FAIL_PREFIX, "PROCESS_TIMEOUT ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath
                            + " cmd=" + commandSummary
                            + " detail=" + waitResult.message
                            + (stderrSummary.isEmpty() ? "" : " stderr=" + stderrSummary));
                    Log.e(TAG, lastErrorLog);
                    return false;
                }

                if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR) {
                    lastErrorLog = formatErrorCode(EXTRACTION_FAIL_PREFIX, "PROCESS_EXECUTION_ERROR ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath
                            + " cmd=" + commandSummary
                            + " detail=" + waitResult.message
                            + (stderrSummary.isEmpty() ? "" : " stderr=" + stderrSummary));
                    Log.e(TAG, lastErrorLog);
                    return false;
                }

                if (waitResult.exitCode != 0 || !stderrSummary.isEmpty()) {
                    lastErrorLog = formatErrorCode(EXTRACTION_FAIL_PREFIX, "PROCESS_NON_ZERO_OR_OUTPUT_VALIDATION_FAIL ["
                            + ProcessRuntimeOps.ExecutionCategory.SETUP_EXTRACTION.name()
                            + "] asset=" + assetPath
                            + " cmd=" + commandSummary
                            + " exit=" + waitResult.exitCode
                            + (stderrSummary.isEmpty() ? "" : " stderr=" + stderrSummary));
                    Log.e(TAG, lastErrorLog);
                    return false;
                }

                ArrayList<String> extractionPostCheckFailedItems = new ArrayList<>();
                ArrayList<File> requiredExecutables = buildRequiredExecutablesForExtractFlow(context, fromAsset);
                enforceExecutableMode(requiredExecutables, extractionPostCheckFailedItems);

                if (fromAsset.contains("alpine")) {
                    setDNS(context);
                }

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
                lastErrorLog = formatErrorCode(EXTRACTION_FAIL_PREFIX, "PROCESS_EXECUTION_IO_EXCEPTION " + e);
                Log.e(TAG, "extractSystemFiles: ", e);
                return false;
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        lastErrorLog = formatErrorCode(EXTRACTION_FAIL_PREFIX,
                "UNEXPECTED_EXTRACTION_STATE asset=" + assetPath + " output=" + extractedTarPath);
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

    private static String validateExtractedTarFile(Path extractedTarPath) {
        File extractedTarFile = extractedTarPath.toFile();
        if (!extractedTarPath.toString().endsWith(".tar")) {
            return "TAR_INTEGRITY_FAIL: invalid-extension"
                    + " path=" + extractedTarPath;
        }
        if (!extractedTarFile.exists()) {
            return "TAR_INTEGRITY_FAIL: missing-file"
                    + " path=" + extractedTarPath;
        }
        if (!extractedTarFile.isFile()) {
            return "TAR_INTEGRITY_FAIL: not-regular-file"
                    + " path=" + extractedTarPath;
        }

        long tarLength = extractedTarFile.length();
        if (tarLength <= 0L) {
            return "TAR_INTEGRITY_FAIL: empty-file"
                    + " path=" + extractedTarPath
                    + " length=" + tarLength;
        }
        if (tarLength < MIN_TAR_BYTES) {
            return "TAR_INTEGRITY_FAIL: below-min-size"
                    + " path=" + extractedTarPath
                    + " length=" + tarLength
                    + " min=" + MIN_TAR_BYTES;
        }
        return null;
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
            lastErrorLog = formatErrorCode(COPY_FAIL_PREFIX,
                    "asset=" + assetPath + " output=" + outputPath + " detail=" + e);
            Log.e(TAG, "copyAssetToFile: ", e);
            return false;
        }
    }

    public static String formatErrorCode(String prefix, String detail) {
        String safePrefix = prefix == null || prefix.trim().isEmpty() ? EXTRACTION_FAIL_PREFIX : prefix.trim();
        String safeDetail = detail == null ? "unknown" : detail.trim();
        if (safeDetail.isEmpty()) {
            safeDetail = "unknown";
        }
        if (safeDetail.startsWith(safePrefix)) {
            return safeDetail;
        }
        return safePrefix + safeDetail;
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

    public static ArrayList<String> resolveBootstrapAbiCandidates() {
        LinkedHashSet<String> orderedCandidates = new LinkedHashSet<>();
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi == null) continue;
                String normalizedAbi = abi.trim().toLowerCase(Locale.ROOT);
                if (normalizedAbi.isEmpty()) continue;
                orderedCandidates.add(normalizedAbi);
                addAbiAliases(normalizedAbi, orderedCandidates);
            }
        }

        ArrayList<String> result = new ArrayList<>(orderedCandidates);
        Log.i(ABI_RESOLVE_TAG,
                "resolveBootstrapAbiCandidates supported=" + Arrays.toString(Build.SUPPORTED_ABIS)
                        + " candidates=" + result);
        return result;
    }

    public static String resolveFirstExistingAssetPath(AssetManager assetManager, String assetGroup, List<String> abiCandidates) {
        if (assetManager == null || abiCandidates == null || abiCandidates.isEmpty()) {
            return null;
        }

        for (String candidate : abiCandidates) {
            String assetPath = assetGroup + "/" + candidate + ".tar";
            try (InputStream inputStream = assetManager.open(assetPath)) {
                Log.i(ABI_RESOLVE_TAG, "Resolved asset path group=" + assetGroup + " candidate=" + candidate + " path=" + assetPath);
                return assetPath;
            } catch (IOException ignored) {
                Log.d(ABI_RESOLVE_TAG, "Asset candidate not found: " + assetPath);
            }
        }

        return null;
    }

    public static String buildAbiResolutionError(String reason, String[] supportedAbis, List<String> candidates, String assetGroup) {
        String requiredAssetKeys = assetGroup + "/<abi>.tar where <abi> is one of " + candidates;
        return reason
                + " Supported device ABIs=" + Arrays.toString(supportedAbis)
                + " | Required asset keys=" + requiredAssetKeys;
    }

    private static void addAbiAliases(String abi, LinkedHashSet<String> orderedCandidates) {
        switch (abi) {
            case "arm64-v8a":
                orderedCandidates.add("aarch64");
                break;
            case "armeabi-v7a":
                orderedCandidates.add("arm");
                orderedCandidates.add("armhf");
                break;
            case "x86_64":
                orderedCandidates.add("amd64");
                break;
            case "x86":
                orderedCandidates.add("i686");
                break;
            default:
                break;
        }
    }
}
