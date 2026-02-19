package com.vectras.vm.core;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    public void platformSignatureUsesStableArchitectureContract() {
        int signature = NativeFastPath.getPlatformSignature();
        int arch = signature & 0xFF00;

        assertEquals(signature, arch);
        assertTrue(NativeFastPath.isStableArchCode(arch));
    }

    @Test
    public void stableArchitectureCodesMatchNativeContract() {
        assertEquals(0x0000, NativeFastPath.ARCH_UNKNOWN);
        assertEquals(0x0100, NativeFastPath.ARCH_ARM64);
        assertEquals(0x0200, NativeFastPath.ARCH_ARM32);
        assertEquals(0x0300, NativeFastPath.ARCH_X64);
        assertEquals(0x0400, NativeFastPath.ARCH_X86);
        assertEquals(0x0500, NativeFastPath.ARCH_RISCV64);

        assertTrue(NativeFastPath.isStableArchCode(NativeFastPath.ARCH_UNKNOWN));
        assertTrue(NativeFastPath.isStableArchCode(NativeFastPath.ARCH_ARM64));
        assertTrue(NativeFastPath.isStableArchCode(NativeFastPath.ARCH_ARM32));
        assertTrue(NativeFastPath.isStableArchCode(NativeFastPath.ARCH_X64));
        assertTrue(NativeFastPath.isStableArchCode(NativeFastPath.ARCH_X86));
        assertTrue(NativeFastPath.isStableArchCode(NativeFastPath.ARCH_RISCV64));
        assertTrue(!NativeFastPath.isStableArchCode(NativeFastPath.ARCH_RISCV32));
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


    @Test
    public void hardwareContractLayoutMapsExpectedSemantics() throws Exception {
        Method fromContract = NativeFastPath.HardwareProfile.class.getDeclaredMethod("fromHardwareContract", int[].class);
        fromContract.setAccessible(true);

        int[] contract = new int[]{
                NativeFastPath.ARCH_ARM64,
                64,
                128,
                16384,
                NativeFastPath.FEATURE_NEON,
                0xA1,
                0xB2,
                0xC3,
                64,
                4
        };

        NativeFastPath.HardwareProfile profile =
                (NativeFastPath.HardwareProfile) fromContract.invoke(null, new Object[]{contract});

        assertEquals(NativeFastPath.ARCH_ARM64, profile.signature);
        assertEquals(64, profile.pointerBits);
        assertEquals(128, profile.cacheLineBytes);
        assertEquals(16384, profile.pageBytes);
        assertEquals(NativeFastPath.FEATURE_NEON, profile.featureMask);
        assertEquals(0xA1, profile.regSignature0);
        assertEquals(0xB2, profile.regSignature1);
        assertEquals(0xC3, profile.regSignature2);
        assertEquals(64, profile.gpioWordBits);
        assertEquals(4, profile.gpioPinStride);
    }

    @Test
    public void hardwareContractRejectsZeroedJniPayload() throws Exception {
        Method fromContract = NativeFastPath.HardwareProfile.class.getDeclaredMethod("fromHardwareContract", int[].class);
        fromContract.setAccessible(true);
        Field sizeField = NativeFastPath.class.getDeclaredField("HW_CONTRACT_SIZE");
        sizeField.setAccessible(true);
        int size = sizeField.getInt(null);

        int[] contract = new int[size];
        try {
            fromContract.invoke(null, new Object[]{contract});
        } catch (InvocationTargetException ex) {
            assertTrue(ex.getCause() instanceof IllegalArgumentException);
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for zeroed contract");
    }
}
