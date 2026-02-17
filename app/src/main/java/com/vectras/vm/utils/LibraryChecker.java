package com.vectras.vm.utils;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vterm.Terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryChecker {
    public enum PackageManagerType { APK, PKG, APT, UNKNOWN }

    private final Context context;

    public LibraryChecker(Context context) {
        this.context = context;
    }

    public void checkMissingLibraries(Activity activity) {
        // List of required libraries
        PackageManagerType managerType = detectPackageManagerType();
        String[] requiredLibraries = resolveRequiredLibraries(managerType);

        // Get the list of installed packages
        isPackageInstalled(null, (output, errors) -> {
            // Split the installed packages output into an array and convert to a set for fast lookup
            Set<String> installedPackages = new HashSet<>();
            for (String installedPackage : output.split("\n")) {
                String normalizedName = normalizeInstalledPackageName(installedPackage, managerType);
                if (!normalizedName.isEmpty()) {
                    installedPackages.add(normalizedName);
                }
            }

            // List to collect missing libraries
            List<String> missingLibraries = new ArrayList<>();

            // Loop over required libraries and check if they're installed
            for (String lib : requiredLibraries) {
                String normalizedRequired = normalizeComparablePackageName(lib, managerType);
                if (!normalizedRequired.isEmpty() && !installedPackages.contains(normalizedRequired)) {
                    missingLibraries.add(lib);
                }
            }

            // Show dialog if any libraries are missing
            if (!missingLibraries.isEmpty()) {
                showMissingLibrariesDialog(activity, missingLibraries, managerType);
            } else {
                // show a dialog if all libraries are installed
                // showAllLibrariesInstalledDialog(activity);
            }
        });
    }

    // Method to show the missing libraries dialog
    private void showMissingLibrariesDialog(Activity activity, List<String> missingLibraries, PackageManagerType managerType) {
        String missingLibrariesText = String.join("\n", missingLibraries);
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("Missing Libraries")
                .setMessage("The following libraries are missing:\n\n" + missingLibrariesText)
                .setCancelable(false)
                .setPositiveButton("Install", (dialog, which) -> {
                    String installCommand;
                    if (managerType == PackageManagerType.APK) {
                        installCommand = buildApkInstallCommand(missingLibraries);
                    } else {
                        installCommand = buildInstallCommand(managerType, String.join(" ", missingLibraries));
                    }
                    new Terminal(context).executeShellCommand(installCommand, true, true, activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static String buildApkInstallCommand(List<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return "echo 'No packages requested for installation'";
        }

        StringBuilder command = new StringBuilder();
        command.append("success_count=0; skipped_count=0; failed_count=0; ")
                .append("success_list=''; skipped_list=''; failed_list=''; ");

        for (String pkg : packages) {
            String normalizedPkg = normalizeComparablePackageName(pkg, PackageManagerType.APK);
            if (normalizedPkg.isEmpty()) {
                continue;
            }
            String escapedPkg = shellSingleQuote(normalizedPkg);
            command.append("echo 'Checking package: ").append(escapedPkg).append("'; ")
                    .append("if apk search -x ").append(escapedPkg).append(" >/dev/null 2>&1; then ")
                    .append("echo 'Installing package: ").append(escapedPkg).append("'; ")
                    .append("if apk add ").append(escapedPkg).append("; then ")
                    .append("success_count=$((success_count+1)); success_list=\"$success_list ").append(escapedPkg).append("\"; ")
                    .append("echo '[INSTALLED] ").append(escapedPkg).append("'; ")
                    .append("else ")
                    .append("failed_count=$((failed_count+1)); failed_list=\"$failed_list ").append(escapedPkg).append("\"; ")
                    .append("echo '[FAILED] ").append(escapedPkg).append("'; ")
                    .append("fi; ")
                    .append("else ")
                    .append("skipped_count=$((skipped_count+1)); skipped_list=\"$skipped_list ").append(escapedPkg).append("\"; ")
                    .append("echo '[SKIPPED_NOT_FOUND] ").append(escapedPkg).append("'; ")
                    .append("fi; ");
        }

        command.append("echo '--- Installation Summary ---'; ")
                .append("echo \"Installed successfully ($success_count):${success_list:- none}\"; ")
                .append("echo \"Skipped (not found) ($skipped_count):${skipped_list:- none}\"; ")
                .append("echo \"Failed to install ($failed_count):${failed_list:- none}\"");
        return command.toString();
    }

    private static String shellSingleQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    // Method to show the "All Libraries Installed" dialog
    private void showAllLibrariesInstalledDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("All Libraries Installed")
                .setMessage("All required libraries are already installed.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static PackageManagerType detectPackageManagerType(Context context) {
        Terminal terminal = new Terminal(context);
        String output = terminal.executeShellCommandWithResult("command -v apk >/dev/null 2>&1 && echo apk || (command -v pkg >/dev/null 2>&1 && echo pkg || (command -v apt-get >/dev/null 2>&1 && echo apt))", context);
        String normalized = output == null ? "" : output.trim().toLowerCase();
        if (normalized.contains("apk")) {
            return PackageManagerType.APK;
        }
        if (normalized.contains("pkg")) {
            return PackageManagerType.PKG;
        }
        if (normalized.contains("apt")) {
            return PackageManagerType.APT;
        }
        return PackageManagerType.UNKNOWN;
    }

    private PackageManagerType detectPackageManagerType() {
        return detectPackageManagerType(context);
    }

    public static String buildInstallCommand(Context context, String packages) {
        return buildInstallCommand(detectPackageManagerType(context), packages);
    }

    public static String buildInstallCommand(PackageManagerType managerType, String packages) {
        String sanitizedPackages = packages == null ? "" : packages.trim();
        if (sanitizedPackages.isEmpty()) {
            return "echo 'No packages requested for installation'";
        }
        switch (managerType) {
            case PKG:
                return "pkg install -y " + sanitizedPackages;
            case APT:
                return "apt-get install -y " + sanitizedPackages;
            case APK:
                return "apk add " + sanitizedPackages;
            case UNKNOWN:
            default:
                return "apk add " + sanitizedPackages;
        }
    }

    private static String[] resolveRequiredLibraries(PackageManagerType managerType) {
        String required;
        switch (managerType) {
            case APK:
                required = DeviceUtils.is64bit() ? AppConfig.neededPkgsAlpine() : AppConfig.neededPkgs32bitAlpine();
                break;
            case APT:
                required = DeviceUtils.is64bit() ? AppConfig.neededPkgsDebianUbuntu() : AppConfig.neededPkgs32bitDebianUbuntu();
                break;
            case PKG:
                required = DeviceUtils.is64bit() ? AppConfig.neededPkgsTermux() : AppConfig.neededPkgs32bitTermux();
                break;
            case UNKNOWN:
            default:
                required = DeviceUtils.is64bit() ? AppConfig.neededPkgsAlpine() : AppConfig.neededPkgs32bitAlpine();
                break;
        }
        return required.trim().split("\\s+");
    }

    // Method to check if the package is installed
    public void isPackageInstalled(String packageName, Terminal.CommandCallback callback) {
        runInstalledPackageQuery(context, callback);
    }

    // Method to check if the package is installed
    public static void isPackageInstalled2(Context context, String packageName, Terminal.CommandCallback callback) {
        runInstalledPackageQuery(context, callback);
    }

    private static void runInstalledPackageQuery(Context context, Terminal.CommandCallback callback) {
        if (context == null || callback == null) {
            return;
        }

        Terminal terminal = new Terminal(context);

            PackageManagerType managerType = detectPackageManagerType(context);
            String dpkgOutput = managerType == PackageManagerType.APT
                    ? terminal.executeShellCommandWithResult("dpkg-query -W -f='${binary:Package}\n'", context)
                    : "";
        if (isUsablePackageOutput(dpkgOutput)) {
            callback.onCommandCompleted(normalizeInstalledPackageOutput(dpkgOutput, managerType), "");
            return;
        }

        String pkgOutput = managerType == PackageManagerType.PKG
                ? terminal.executeShellCommandWithResult("pkg list-installed", context)
                : "";
        if (isUsablePackageOutput(pkgOutput)) {
            callback.onCommandCompleted(normalizeInstalledPackageOutput(pkgOutput, managerType), "");
            return;
        }

        String apkOutput = managerType == PackageManagerType.APK
                ? terminal.executeShellCommandWithResult("apk info -v", context)
                : "";
        if (isUsablePackageOutput(apkOutput)) {
            callback.onCommandCompleted(normalizeInstalledPackageOutput(apkOutput, managerType), "");
            return;
        }

        callback.onCommandCompleted("", "No supported package manager detected in current distro/runtime.");
    }

    private static boolean isUsablePackageOutput(String output) {
        return output != null && !output.trim().isEmpty() && !containsShellNotFound(output);
    }

    private static String normalizeInstalledPackageOutput(String output, PackageManagerType managerType) {
        StringBuilder normalized = new StringBuilder();
        for (String line : output.split("\n")) {
            String packageName = normalizeInstalledPackageName(line, managerType);
            if (!packageName.isEmpty()) {
                normalized.append(packageName).append("\n");
            }
        }
        return normalized.toString();
    }

    private static String normalizeInstalledPackageName(String rawLine, PackageManagerType managerType) {
        return normalizeComparablePackageName(rawLine, managerType);
    }

    private static String normalizeComparablePackageName(String rawLine, PackageManagerType managerType) {
        if (rawLine == null) {
            return "";
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("WARNING:") || line.startsWith("ERROR:")) {
            return "";
        }
        line = line.replaceAll("[<>=].*$", "").trim();

        switch (managerType) {
            case APT:
                int firstSpace = line.indexOf(' ');
                if (firstSpace > 0) {
                    line = line.substring(0, firstSpace);
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    line = line.substring(0, colonIndex);
                }
                break;
            case PKG:
                int slashIndex = line.indexOf('/');
                if (slashIndex > 0) {
                    line = line.substring(0, slashIndex);
                }
                firstSpace = line.indexOf(' ');
                if (firstSpace > 0) {
                    line = line.substring(0, firstSpace);
                }
                break;
            case APK:
            case UNKNOWN:
            default:
                slashIndex = line.indexOf('/');
                if (slashIndex > 0) {
                    line = line.substring(0, slashIndex);
                }
                firstSpace = line.indexOf(' ');
                if (firstSpace > 0) {
                    line = line.substring(0, firstSpace);
                }

                Pattern alpineVersionPattern = Pattern.compile("^(.+)-\\d.*$");
                Matcher matcher = alpineVersionPattern.matcher(line);
                if (matcher.matches()) {
                    line = matcher.group(1);
                }
                break;
        }

        return line.trim();
    }

    private static boolean containsShellNotFound(String output) {
        String normalized = output.toLowerCase();
        return normalized.contains("not found")
                || normalized.contains("inaccessible or not found")
                || normalized.contains("no such file");
    }

    public void checkAndInstallXFCE4(Activity activity) {
        // XFCE4 meta-package
        String xfce4Package = "xfce4";
        PackageManagerType managerType = detectPackageManagerType();

        // Check if XFCE4 is installed
        isPackageInstalled(xfce4Package, (output, errors) -> {
            boolean isInstalled = false;

            // Check if the package exists in the installed packages output
            if (output != null) {
                Set<String> installedPackages = new HashSet<>();
                for (String installedPackage : output.split("\n")) {
                    String normalizedInstalled = normalizeComparablePackageName(installedPackage, managerType);
                    if (!normalizedInstalled.isEmpty()) {
                        installedPackages.add(normalizedInstalled);
                    }
                }

                isInstalled = installedPackages.contains(normalizeComparablePackageName(xfce4Package, managerType));
            }

            // If not installed, show a dialog to install it
            if (!isInstalled) {
                showInstallDialog(activity, xfce4Package);
            } else {
                showAlreadyInstalledDialog(activity);
            }
        });
    }

    private void showInstallDialog(Activity activity, String packageName) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("Install XFCE4")
                .setMessage("XFCE4 is not installed. Would you like to install it?")
                .setCancelable(false)
                .setPositiveButton("Install", (dialog, which) -> {
                    PackageManagerType managerType = detectPackageManagerType();
                    String installCommand = buildInstallCommand(managerType, packageName);
                    new Terminal(context).executeShellCommand(installCommand, true, true, activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAlreadyInstalledDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("XFCE4 Installed")
                .setMessage("XFCE4 is already installed on this system.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
