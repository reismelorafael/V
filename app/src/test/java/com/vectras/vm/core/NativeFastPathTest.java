package com.vectras.vm.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NativeFastPathTest {

    @Test
    public void copyBytesFallbackWorks() {
        byte[] src = new byte[128];
        byte[] dst = new byte[128];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) (i ^ 0x5A);
        }

        NativeFastPath.copyBytes(src, 0, dst, 0, src.length);
        assertArrayEquals(src, dst);
    }


    @Test
    public void copyBytesHandlesOverlapDeterministically() {
        byte[] data = new byte[32];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        NativeFastPath.copyBytes(data, 0, data, 4, 20);

        for (int i = 0; i < 20; i++) {
            assertEquals(i, data[4 + i] & 0xFF);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void copyBytesRejectsOutOfBoundsRange() {
        NativeFastPath.copyBytes(new byte[8], 4, new byte[8], 0, 8);
    }

    @Test(expected = IllegalArgumentException.class)
    public void xorChecksumRejectsOutOfBoundsRange() {
        NativeFastPath.xorChecksum(new byte[8], 2, 7);
    }

    @Test
    public void xorChecksumDeterministic() {
        byte[] data = new byte[33];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 3 + 1);
        }

        int x1 = NativeFastPath.xorChecksum(data, 0, data.length);
        int x2 = NativeFastPath.xorChecksum(data, 0, data.length);
        assertEquals(x1, x2);
    }


    @Test
    public void byteSwap32MatchesExpected() {
        assertEquals(0x78563412, NativeFastPath.byteSwap32(0x12345678));
        assertEquals(0xFFFFFFFF, NativeFastPath.byteSwap32(0xFFFFFFFF));
        assertEquals(0x00000000, NativeFastPath.byteSwap32(0x00000000));
    }

    @Test
    public void rotateOpsMatchRoundTrip() {
        int value = 0x12345678;
        int left = NativeFastPath.rotateLeft32(value, 7);
        int roundTrip = NativeFastPath.rotateRight32(left, 7);
        assertEquals(value, roundTrip);
    }

    @Test
    public void rotateOpsNormalizeDistance() {
        int value = 0x13579BDF;
        assertEquals(value, NativeFastPath.rotateLeft32(value, 32));
        assertEquals(value, NativeFastPath.rotateRight32(value, 64));
        assertEquals(NativeFastPath.rotateLeft32(value, 5), NativeFastPath.rotateLeft32(value, 37));
        assertEquals(NativeFastPath.rotateRight32(value, 11), NativeFastPath.rotateRight32(value, 43));
    }

    @Test
    public void popcountMatchesKnownValues() {
        assertEquals(0, NativeFastPath.popcount32(0));
        assertEquals(32, NativeFastPath.popcount32(-1));
        assertEquals(16, NativeFastPath.popcount32(0xF0F0F0F0));
        assertEquals(13, NativeFastPath.popcount32(0x12345678));
    }


    @Test
    public void platformSignatureHasKnownBitLayout() {
        int signature = NativeFastPath.getPlatformSignature();
        int arch = signature & 0xFF00;
        int os = signature & 0x00F0;

        assertEquals(signature, arch | os);
        assertTrue(
                arch == NativeFastPath.ARCH_UNKNOWN
                        || arch == NativeFastPath.ARCH_ARM64
                        || arch == NativeFastPath.ARCH_ARM32
                        || arch == NativeFastPath.ARCH_X64
                        || arch == NativeFastPath.ARCH_X86
                        || arch == NativeFastPath.ARCH_RISCV64
                        || arch == NativeFastPath.ARCH_RISCV32);
        assertTrue(
                os == NativeFastPath.OS_UNKNOWN
                        || os == NativeFastPath.OS_ANDROID
                        || os == NativeFastPath.OS_LINUX);
    }

    @Test
    public void featureMaskAlwaysNonNegative() {
        assertTrue(NativeFastPath.getFeatureMask() >= 0);
    }

    @Test
    public void nativeLibraryOptional() {
        boolean available = NativeFastPath.isNativeAvailable();
        assertEquals(available, NativeFastPath.isNativeAvailable());
    }
}
