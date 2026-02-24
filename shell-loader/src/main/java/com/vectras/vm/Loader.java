package com.vectras.vm;

public class Loader {
    private static android.content.pm.PackageInfo getTargetPackageInfo() throws android.os.RemoteException {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return android.app.ActivityThread.getPackageManager().getPackageInfo(
                    BuildConfig.APPLICATION_ID,
                    (long) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                    0);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return android.app.ActivityThread.getPackageManager().getPackageInfo(
                    BuildConfig.APPLICATION_ID,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                    0);
        }
        return android.app.ActivityThread.getPackageManager().getPackageInfo(
                BuildConfig.APPLICATION_ID,
                android.content.pm.PackageManager.GET_SIGNATURES,
                0);
    }

    private static boolean isTrustedSignature(android.content.pm.PackageInfo targetInfo) {
        int[] expected = new int[]{BuildConfig.SIGNATURE};
        java.util.Arrays.sort(expected);

        int[] actual;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (targetInfo.signingInfo == null) {
                return false;
            }
            android.content.pm.Signature[] signatures = targetInfo.signingInfo.hasMultipleSigners()
                    ? targetInfo.signingInfo.getApkContentsSigners()
                    : targetInfo.signingInfo.getSigningCertificateHistory();
            if (signatures == null || signatures.length == 0) {
                return false;
            }
            actual = new int[signatures.length];
            for (int i = 0; i < signatures.length; i++) {
                if (signatures[i] == null) {
                    return false;
                }
                actual[i] = signatures[i].hashCode();
            }
        } else {
            if (targetInfo.signatures == null || targetInfo.signatures.length == 0) {
                return false;
            }
            actual = new int[targetInfo.signatures.length];
            for (int i = 0; i < targetInfo.signatures.length; i++) {
                if (targetInfo.signatures[i] == null) {
                    return false;
                }
                actual[i] = targetInfo.signatures[i].hashCode();
            }
        }

        java.util.Arrays.sort(actual);
        return java.util.Arrays.equals(expected, actual);
    }

    /**
     * Command-line entry point.
     * It is pretty simple.
     * 1. Check if application is installed.
     * 2. Check if target apk's signature matches stored hash to prevent running code of potentially replaced malicious apk.
     * 3. Load target apk code with `PathClassLoader` and start target's main function.
     * <p>
     * This way we can make this loader version-agnostic and keep it secure. All application logic is located in target apk.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        try {
            android.content.pm.PackageInfo targetInfo = getTargetPackageInfo();
            assert targetInfo != null : BuildConfig.packageNotInstalledErrorText.replace("ARCH", android.os.Build.SUPPORTED_ABIS[0]);
            assert isTrustedSignature(targetInfo) : BuildConfig.packageSignatureMismatchErrorText;

            android.util.Log.i(BuildConfig.logTag, "loading " + targetInfo.applicationInfo.sourceDir + "::" + BuildConfig.CLASS_ID + "::main of " + BuildConfig.APPLICATION_ID + " application (commit " + BuildConfig.COMMIT + ")");
            Class<?> targetClass = Class.forName(BuildConfig.CLASS_ID, true,
                    new dalvik.system.PathClassLoader(targetInfo.applicationInfo.sourceDir, null, ClassLoader.getSystemClassLoader()));
            targetClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (AssertionError e) {
            System.err.println(e.getMessage());
        } catch (java.lang.reflect.InvocationTargetException e) {
            e.getCause().printStackTrace(System.err);
        } catch (Throwable e) {
            android.util.Log.e(BuildConfig.logTag, "Loader error", e);
            e.printStackTrace(System.err);
        }
    }
}
