package com.vectras.vm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Test;

public class DeterministicRuntimeMatrixIoQuantumTest {

    @Test
    public void deriveIoQuantum_alignsWithPowerOfTwoLine_usingKernelQuantum() {
        int value = deriveIoQuantum(4096, 64, 4, 0, 5000);

        assertEquals(5056, value);
        assertEquals(0, value % 64);
    }

    @Test
    public void deriveIoQuantum_alignsWithNonPowerOfTwoLine_usingKernelQuantum() {
        int value = deriveIoQuantum(4096, 96, 4, 0, 5000);

        assertEquals(5088, value);
        assertEquals(0, value % 96);
    }

    @Test
    public void deriveIoQuantum_alignsWithNonPowerOfTwoLine_usingFallbackComputation() {
        int value = deriveIoQuantum(4096, 192, 2, 0, 0);

        assertEquals(8256, value);
        assertEquals(0, value % 192);
    }

    @Test
    public void deriveIoQuantum_fallbacksToMinimumAlignmentWhenLineIsNonPositive() {
        int value = deriveIoQuantum(4096, 0, 4, 0, 4101);

        assertEquals(4128, value);
        assertEquals(0, value % 32);
    }

    @Test
    public void deriveIoQuantum_keepsRangeAndAlignmentInvariants() {
        int[] lines = new int[] {64, 128, 96, 192};
        int[] kernelQuanta = new int[] {0, 5000};

        for (int line : lines) {
            for (int kernelQuantum : kernelQuanta) {
                int value = deriveIoQuantum(4096, line, 4, NativeFastPath.FEATURE_NEON, kernelQuantum);
                assertTrue(value >= 4096);
                assertTrue(value <= 1024 * 1024);
                assertEquals(0, value % line);
            }
        }
    }

    @Test
    public void deriveIoQuantum_extremeHighKernelQuantumAndSimd_clampsWithoutNegativeOverflow() {
        int features = NativeFastPath.FEATURE_AVX2 | NativeFastPath.FEATURE_NEON | NativeFastPath.FEATURE_SIMD;
        int value = deriveIoQuantum(4096, 256, 16, features, Integer.MAX_VALUE);

        assertEquals(1024 * 1024, value);
        assertTrue(value > 0);
        assertEquals(0, value % 256);
    }

    @Test
    public void deriveIoQuantum_extremeHighPageAndSimdFallback_staysAlignedAndNonNegative() {
        int features = NativeFastPath.FEATURE_AVX2 | NativeFastPath.FEATURE_NEON | NativeFastPath.FEATURE_SIMD;
        int value = deriveIoQuantum(Integer.MAX_VALUE, 192, 16, features, 0);

        assertEquals(1024 * 1024, value);
        assertTrue(value > 0);
        assertEquals(0, value % 192);
    }

    private static int deriveIoQuantum(int page, int line, int cores, int features, int kernelQuantum) {
        try {
            Method method = DeterministicRuntimeMatrix.class.getDeclaredMethod(
                    "deriveIoQuantum", int.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            return (Integer) method.invoke(null, page, line, cores, features, kernelQuantum);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Unable to access deriveIoQuantum", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new AssertionError("deriveIoQuantum invocation failed", cause);
        }
    }
}
