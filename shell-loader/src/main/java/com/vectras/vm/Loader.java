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

    private static java.util.List<String> expectedSignatureDigests() {
        if (BuildConfig.SIGNATURE_DIGESTS_SHA256 == null || BuildConfig.SIGNATURE_DIGESTS_SHA256.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.List<String> digests = new java.util.ArrayList<>();
        String[] split = BuildConfig.SIGNATURE_DIGESTS_SHA256.split(",");
        for (String digest : split) {
            if (digest == null) {
                continue;
            }
            String normalized = digest.trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalized.isEmpty()) {
                digests.add(normalized);
            }
        }
        java.util.Collections.sort(digests);
        return java.util.Collections.unmodifiableList(digests);
    }

    private static String sha256Hex(byte[] input) {
        try {
            java.security.MessageDigest messageDigest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(input);
            char[] out = new char[digest.length * 2];
            final char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                out[i * 2] = hex[v >>> 4];
                out[i * 2 + 1] = hex[v & 0x0F];
            }
            return new String(out);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static java.util.List<String> normalizeSignatureDigests(android.content.pm.Signature[] signatures) {
        if (signatures == null || signatures.length == 0) {
            return java.util.Collections.emptyList();
        }

        java.util.List<String> digests = new java.util.ArrayList<>(signatures.length);
        for (android.content.pm.Signature signature : signatures) {
            if (signature == null) {
                return java.util.Collections.emptyList();
            }
            digests.add(sha256Hex(signature.toByteArray()));
        }

        java.util.Collections.sort(digests);
        return digests;
    }

    static boolean isTrustedSignature(android.content.pm.PackageInfo targetInfo) {
        return isTrustedSignature(targetInfo, expectedSignatureDigests());
    }

    static boolean isTrustedSignature(android.content.pm.PackageInfo targetInfo, java.util.List<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return false;
        }

        java.util.List<String> actual;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (targetInfo.signingInfo == null) {
                return false;
            }
            android.content.pm.Signature[] signatures = targetInfo.signingInfo.hasMultipleSigners()
                    ? targetInfo.signingInfo.getApkContentsSigners()
                    : targetInfo.signingInfo.getSigningCertificateHistory();
            actual = normalizeSignatureDigests(signatures);
        } else {
            actual = normalizeSignatureDigests(targetInfo.signatures);
        }

        return !actual.isEmpty() && expected.equals(actual);
    }

    static String getSecurityValidationError(android.content.pm.PackageInfo targetInfo) {
        return getSecurityValidationError(targetInfo, expectedSignatureDigests());
    }

    static String getSecurityValidationError(android.content.pm.PackageInfo targetInfo, java.util.List<String> expected) {
        if (targetInfo == null) {
            return BuildConfig.packageNotInstalledErrorText.replace("ARCH", android.os.Build.SUPPORTED_ABIS[0]);
        }
        if (!isTrustedSignature(targetInfo, expected)) {
            return BuildConfig.packageSignatureMismatchErrorText;
        }
        return null;
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
            String validationError = getSecurityValidationError(targetInfo);
            if (validationError != null) {
                System.err.println(validationError);
                return;
            }

            android.util.Log.i(BuildConfig.logTag, "loading " + targetInfo.applicationInfo.sourceDir + "::" + BuildConfig.CLASS_ID + "::main of " + BuildConfig.APPLICATION_ID + " application (commit " + BuildConfig.COMMIT + ")");
            Class<?> targetClass = Class.forName(BuildConfig.CLASS_ID, true,
                    new dalvik.system.PathClassLoader(targetInfo.applicationInfo.sourceDir, null, ClassLoader.getSystemClassLoader()));
            targetClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            e.getCause().printStackTrace(System.err);
        } catch (Throwable e) {
            android.util.Log.e(BuildConfig.logTag, "Loader error", e);
            e.printStackTrace(System.err);
        }
    }
}
