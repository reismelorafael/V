package com.vectras.vm;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;

import org.junit.Assert;
import org.junit.Test;
import org.robolectric.annotation.Config;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LoaderSignatureVerificationTest {
    private static String sha256Hex(byte[] input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        char[] out = new char[digest.length * 2];
        final char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return new String(out);
    }

    @Test
    @Config(sdk = 27)
    public void isTrustedSignature_acceptsExpectedSignatures_withOrderNormalization() throws Exception {
        Signature signerA = new Signature(new byte[]{1, 2, 3, 4});
        Signature signerB = new Signature(new byte[]{5, 6, 7, 8});

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.signatures = new Signature[]{signerB, signerA};

        List<String> expected = new ArrayList<>(Arrays.asList(
                sha256Hex(signerA.toByteArray()),
                sha256Hex(signerB.toByteArray())
        ));
        Collections.sort(expected);

        Assert.assertTrue(Loader.isTrustedSignature(packageInfo, Collections.unmodifiableList(expected)));
    }

    @Test
    @Config(sdk = 27)
    public void isTrustedSignature_rejectsDivergentSignature() throws Exception {
        Signature signer = new Signature(new byte[]{10, 11, 12, 13});

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.signatures = new Signature[]{signer};

        List<String> expected = Collections.singletonList(sha256Hex(new byte[]{99, 100, 101}));

        Assert.assertFalse(Loader.isTrustedSignature(packageInfo, expected));
    }

    @Test
    @Config(sdk = 27)
    public void getSecurityValidationError_returnsNotInstalledMessage_whenTargetPackageMissing() {
        Assert.assertEquals(
                BuildConfig.packageNotInstalledErrorText.replace("ARCH", android.os.Build.SUPPORTED_ABIS[0]),
                Loader.getSecurityValidationError(null, Collections.singletonList("expected"))
        );
    }

    @Test
    @Config(sdk = 27)
    public void getSecurityValidationError_returnsSignatureMismatchMessage_whenSignatureIsUntrusted() throws Exception {
        Signature signer = new Signature(new byte[]{42, 43, 44, 45});

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.signatures = new Signature[]{signer};

        List<String> expected = Collections.singletonList(sha256Hex(new byte[]{9, 9, 9, 9}));

        Assert.assertEquals(
                BuildConfig.packageSignatureMismatchErrorText,
                Loader.getSecurityValidationError(packageInfo, expected)
        );
    }

    @Test
    @Config(sdk = 27)
    public void getSecurityValidationError_returnsNull_whenSignatureIsTrusted() throws Exception {
        Signature signer = new Signature(new byte[]{7, 7, 7, 7});

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.signatures = new Signature[]{signer};

        List<String> expected = Collections.singletonList(sha256Hex(signer.toByteArray()));

        Assert.assertNull(Loader.getSecurityValidationError(packageInfo, expected));
    }
}
