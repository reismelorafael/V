package com.vectras.vm.utils;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vterm.Terminal;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryChecker {
    private enum PackageManagerType { APK, PKG, UNKNOWN }

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
                String normalizedName = normalizeInstalledPackageName(installedPackage);
                if (!normalizedName.isEmpty()) {
                    installedPackages.add(normalizedName);
                }
            }

            // StringBuilder to collect missing libraries
            StringBuilder missingLibraries = new StringBuilder();

            // Loop over required libraries and check if they're installed
            for (String lib : requiredLibraries) {
                if (!installedPackages.contains(lib.trim())) {
                    missingLibraries.append(lib).append("\n");
                }
            }

            // Show dialog if any libraries are missing
            if (missingLibraries.toString().trim().length() > 0) {
                showMissingLibrariesDialog(activity, missingLibraries.toString(), managerType);
            } else {
                // show a dialog if all libraries are installed
                // showAllLibrariesInstalledDialog(activity);
            }
        });
    }

    // Method to show the missing libraries dialog
    private void showMissingLibrariesDialog(Activity activity, String missingLibraries, PackageManagerType managerType) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("Missing Libraries")
                .setMessage("The following libraries are missing:\n\n" + missingLibraries)
                .setCancelable(false)
                .setPositiveButton("Install", (dialog, which) -> {
                    // Create the installation command
                    String installCommandPrefix = managerType == PackageManagerType.PKG ? "pkg install -y " : "apk add ";
                    String installCommand = installCommandPrefix + missingLibraries.replace("\n", " ");
                    new Terminal(context).executeShellCommand(installCommand, true, true, activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // Method to show the "All Libraries Installed" dialog
    private void showAllLibrariesInstalledDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                .setTitle("All Libraries Installed")
                .setMessage("All required libraries are already installed.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private PackageManagerType detectPackageManagerType() {
        Terminal terminal = new Terminal(context);
        String output = terminal.executeShellCommandWithResult("command -v apk >/dev/null 2>&1 && echo apk || (command -v pkg >/dev/null 2>&1 && echo pkg)", context);
        String normalized = output == null ? "" : output.trim().toLowerCase();
        if (normalized.contains("apk")) {
            return PackageManagerType.APK;
        }
        if (normalized.contains("pkg")) {
            return PackageManagerType.PKG;
        }
        return PackageManagerType.UNKNOWN;
    }

    private static String[] resolveRequiredLibraries(PackageManagerType managerType) {
        if (managerType == PackageManagerType.PKG) {
            String required = DeviceUtils.is64bit() ? AppConfig.neededPkgsTermux() : AppConfig.neededPkgs32bitTermux();
            return required.split(" ");
        }
        String required = DeviceUtils.is64bit() ? AppConfig.neededPkgs() : AppConfig.neededPkgs32bit();
        return required.split(" ");
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

        String dpkgOutput = terminal.executeShellCommandWithResult("dpkg-query -W -f='${binary:Package}\n'", context);
        if (isUsablePackageOutput(dpkgOutput)) {
            callback.onCommandCompleted(normalizeInstalledPackageOutput(dpkgOutput), "");
            return;
        }

        String pkgOutput = terminal.executeShellCommandWithResult("pkg list-installed", context);
        if (isUsablePackageOutput(pkgOutput)) {
            callback.onCommandCompleted(normalizeInstalledPackageOutput(pkgOutput), "");
            return;
        }

        String apkOutput = terminal.executeShellCommandWithResult("apk info -v", context);
        if (isUsablePackageOutput(apkOutput)) {
            callback.onCommandCompleted(normalizeInstalledPackageOutput(apkOutput), "");
            return;
        }

        callback.onCommandCompleted("", "No supported package manager detected in current distro/runtime.");
    }

    private static boolean isUsablePackageOutput(String output) {
        return output != null && !output.trim().isEmpty() && !containsShellNotFound(output);
    }

    private static String normalizeInstalledPackageOutput(String output) {
        StringBuilder normalized = new StringBuilder();
        for (String line : output.split("\n")) {
            String packageName = normalizeInstalledPackageName(line);
            if (!packageName.isEmpty()) {
                normalized.append(packageName).append("\n");
            }
        }
        return normalized.toString();
    }

    private static String normalizeInstalledPackageName(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("WARNING:") || line.startsWith("ERROR:")) {
            return "";
        }

        int slashIndex = line.indexOf('/');
        if (slashIndex > 0) {
            line = line.substring(0, slashIndex);
        }

        int firstSpace = line.indexOf(' ');
        if (firstSpace > 0) {
            line = line.substring(0, firstSpace);
        }

        Pattern alpineVersionPattern = Pattern.compile("^(.+)-\\d.*$");
        Matcher matcher = alpineVersionPattern.matcher(line);
        if (matcher.matches()) {
            line = matcher.group(1);
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

        // Check if XFCE4 is installed
        isPackageInstalled(xfce4Package, (output, errors) -> {
            boolean isInstalled = false;

            // Check if the package exists in the installed packages output
            if (output != null) {
                Set<String> installedPackages = new HashSet<>();
                for (String installedPackage : output.split("\n")) {
                    installedPackages.add(installedPackage.trim());
                }

                isInstalled = installedPackages.contains(xfce4Package.trim());
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
                    String installCommand = "apk add " + packageName;
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
