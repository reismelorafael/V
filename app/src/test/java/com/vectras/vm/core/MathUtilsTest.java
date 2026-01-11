package com.vectras.vm.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for MathUtils class.
 * 
 * <p>These tests use golden test vectors (fixed inputs with known outputs)
 * to validate mathematical implementations. All calculations are derived
 * from code, not from OCR or external image sources.</p>
 * 
 * <p>Following ISO 8000/9001 standards for software testing.</p>
 */
public class MathUtilsTest {

    // ========== Log2 Tests ==========

    @Test
    public void log2Floor_powerOfTwo_returnsExactExponent() {
        assertEquals(0, MathUtils.log2Floor(1));
        assertEquals(1, MathUtils.log2Floor(2));
        assertEquals(2, MathUtils.log2Floor(4));
        assertEquals(3, MathUtils.log2Floor(8));
        assertEquals(10, MathUtils.log2Floor(1024));
        assertEquals(20, MathUtils.log2Floor(1 << 20));
    }

    @Test
    public void log2Floor_notPowerOfTwo_returnsFloor() {
        assertEquals(2, MathUtils.log2Floor(7));
        assertEquals(3, MathUtils.log2Floor(15));
        assertEquals(9, MathUtils.log2Floor(1023));
    }

    @Test
    public void log2Floor_maxInt_returns30() {
        assertEquals(30, MathUtils.log2Floor(Integer.MAX_VALUE));
    }

    @Test
    public void log2Floor_zeroOrNegative_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2Floor(0));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2Floor(-1));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2Floor(Integer.MIN_VALUE));
    }

    @Test
    public void log2FloorLong_goldenVectors() {
        assertEquals(0, MathUtils.log2Floor(1L));
        assertEquals(1, MathUtils.log2Floor(2L));
        assertEquals(62, MathUtils.log2Floor(Long.MAX_VALUE));
    }

    @Test
    public void log2Ceil_powerOfTwo_returnsExactExponent() {
        assertEquals(0, MathUtils.log2Ceil(1));
        assertEquals(1, MathUtils.log2Ceil(2));
        assertEquals(2, MathUtils.log2Ceil(4));
        assertEquals(10, MathUtils.log2Ceil(1024));
    }

    @Test
    public void log2Ceil_notPowerOfTwo_returnsCeiling() {
        assertEquals(2, MathUtils.log2Ceil(3));
        assertEquals(3, MathUtils.log2Ceil(5));
        assertEquals(11, MathUtils.log2Ceil(1025));
    }

    @Test
    public void log2Ceil_zeroOrNegative_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2Ceil(0));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2Ceil(-1));
    }

    // ========== Square Root Tests ==========

    @Test
    public void isqrt_perfectSquares_returnsExactRoot() {
        assertEquals(0, MathUtils.isqrt(0));
        assertEquals(1, MathUtils.isqrt(1));
        assertEquals(2, MathUtils.isqrt(4));
        assertEquals(3, MathUtils.isqrt(9));
        assertEquals(10, MathUtils.isqrt(100));
        assertEquals(100, MathUtils.isqrt(10000));
    }

    @Test
    public void isqrt_notPerfectSquare_returnsFloor() {
        assertEquals(2, MathUtils.isqrt(8));
        assertEquals(3, MathUtils.isqrt(10));
        assertEquals(3, MathUtils.isqrt(15));
        assertEquals(31, MathUtils.isqrt(999));
    }

    @Test
    public void isqrt_maxInt_returns46340() {
        // sqrt(Integer.MAX_VALUE) = sqrt(2147483647) ≈ 46340.95
        assertEquals(46340, MathUtils.isqrt(Integer.MAX_VALUE));
    }

    @Test
    public void isqrt_verifyResult_squareIsLessOrEqual() {
        for (int n : new int[]{7, 50, 123, 9999, 123456}) {
            int root = MathUtils.isqrt(n);
            assertTrue(root * root <= n);
            assertTrue((root + 1) * (long)(root + 1) > n);
        }
    }

    @Test
    public void isqrt_negative_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.isqrt(-1));
    }

    @Test
    public void isqrtLong_goldenVectors() {
        assertEquals(0L, MathUtils.isqrt(0L));
        assertEquals(1L, MathUtils.isqrt(1L));
        assertEquals(1000000L, MathUtils.isqrt(1000000000000L));
    }

    // ========== Power of Two Tests ==========

    @Test
    public void isPowerOfTwo_powersOfTwo_returnsTrue() {
        assertTrue(MathUtils.isPowerOfTwo(1));
        assertTrue(MathUtils.isPowerOfTwo(2));
        assertTrue(MathUtils.isPowerOfTwo(4));
        assertTrue(MathUtils.isPowerOfTwo(1024));
        assertTrue(MathUtils.isPowerOfTwo(1 << 30));
    }

    @Test
    public void isPowerOfTwo_nonPowersOfTwo_returnsFalse() {
        assertFalse(MathUtils.isPowerOfTwo(0));
        assertFalse(MathUtils.isPowerOfTwo(3));
        assertFalse(MathUtils.isPowerOfTwo(6));
        assertFalse(MathUtils.isPowerOfTwo(1023));
        assertFalse(MathUtils.isPowerOfTwo(-1));
        assertFalse(MathUtils.isPowerOfTwo(Integer.MIN_VALUE));
    }

    @Test
    public void nextPowerOfTwo_goldenVectors() {
        assertEquals(1, MathUtils.nextPowerOfTwo(0));
        assertEquals(1, MathUtils.nextPowerOfTwo(1));
        assertEquals(2, MathUtils.nextPowerOfTwo(2));
        assertEquals(4, MathUtils.nextPowerOfTwo(3));
        assertEquals(8, MathUtils.nextPowerOfTwo(5));
        assertEquals(1024, MathUtils.nextPowerOfTwo(1000));
        assertEquals(1024, MathUtils.nextPowerOfTwo(1024));
    }

    // ========== Mix64 Tests ==========

    @Test
    public void mix64_goldenVectors() {
        // Golden vectors calculated from SplitMix64 reference implementation
        assertEquals(0x0000000000000000L, MathUtils.mix64(0));
        assertEquals(0x5692161D100B05E5L, MathUtils.mix64(1));
        assertEquals(0xE220A8397B1DCDAFL, MathUtils.mix64(MathUtils.GOLDEN_GAMMA));
    }

    @Test
    public void mix64_differentInputs_produceDifferentOutputs() {
        long a = MathUtils.mix64(1);
        long b = MathUtils.mix64(2);
        long c = MathUtils.mix64(3);
        assertTrue(a != b);
        assertTrue(b != c);
        assertTrue(a != c);
    }

    @Test
    public void mix64_deterministic() {
        long input = 12345678L;
        assertEquals(MathUtils.mix64(input), MathUtils.mix64(input));
    }

    @Test
    public void mix64_unmix64_roundTrip() {
        long[] testValues = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE, 0x123456789ABCDEFL};
        for (long v : testValues) {
            assertEquals("Round trip failed for " + v, v, MathUtils.unmix64(MathUtils.mix64(v)));
        }
    }

    // ========== Popcount and Parity Tests ==========

    @Test
    public void popcount_goldenVectors() {
        assertEquals(0, MathUtils.popcount(0));
        assertEquals(1, MathUtils.popcount(1));
        assertEquals(8, MathUtils.popcount(0xFF));
        assertEquals(16, MathUtils.popcount(0xFFFF));
        assertEquals(16, MathUtils.popcount(0x55555555));
        assertEquals(32, MathUtils.popcount(-1));
    }

    @Test
    public void parity_goldenVectors() {
        assertEquals(0, MathUtils.parity(0));
        assertEquals(1, MathUtils.parity(1));
        assertEquals(0, MathUtils.parity(3));  // 2 bits
        assertEquals(1, MathUtils.parity(7));  // 3 bits
        assertEquals(0, MathUtils.parity(0xFF)); // 8 bits
        assertEquals(0, MathUtils.parity(-1));   // 32 bits
    }

    // ========== 4x4 Matrix Parity Tests ==========

    @Test
    public void idx4x4_coordinateMapping() {
        assertEquals(0, MathUtils.idx4x4(0, 0));
        assertEquals(1, MathUtils.idx4x4(1, 0));
        assertEquals(4, MathUtils.idx4x4(0, 1));
        assertEquals(15, MathUtils.idx4x4(3, 3));
    }

    @Test
    public void getBit4x4_setBit4x4_roundTrip() {
        int bits = 0;
        bits = MathUtils.setBit4x4(bits, 0, 0, 1);
        bits = MathUtils.setBit4x4(bits, 2, 1, 1);
        bits = MathUtils.setBit4x4(bits, 3, 3, 1);
        
        assertEquals(1, MathUtils.getBit4x4(bits, 0, 0));
        assertEquals(1, MathUtils.getBit4x4(bits, 2, 1));
        assertEquals(1, MathUtils.getBit4x4(bits, 3, 3));
        assertEquals(0, MathUtils.getBit4x4(bits, 1, 1));
    }

    @Test
    public void parity2D8_allZeros_returnsZero() {
        assertEquals(0x00, MathUtils.parity2D8(0x0000));
    }

    @Test
    public void parity2D8_singleBit_setsRowAndColumn() {
        // Single bit at (0,0) sets row 0 parity (bit 4) and col 0 parity (bit 0)
        assertEquals(0x11, MathUtils.parity2D8(0x0001));
        // Single bit at (1,0) sets row 0 parity (bit 4) and col 1 parity (bit 1)
        assertEquals(0x12, MathUtils.parity2D8(0x0002));
    }

    @Test
    public void parity2D8_allOnes_returnsZero() {
        // All 16 bits set: each row and column has 4 ones (even parity = 0)
        assertEquals(0x00, MathUtils.parity2D8(0xFFFF));
    }

    @Test
    public void parity2D8_alternatingPattern_returnsZero() {
        // 0xAAAA = alternating bits, each row/col has 2 ones (even parity)
        assertEquals(0x00, MathUtils.parity2D8(0xAAAA));
        // 0x5555 = inverse alternating
        assertEquals(0x00, MathUtils.parity2D8(0x5555));
    }

    @Test
    public void parity2D8_singleRow_setsColumnParities() {
        // First row all set: 0x000F = bits 0,1,2,3
        // Row 0 has 4 ones (even = 0), other rows have 0 (even = 0)
        // Each column has 1 one (odd = 1)
        assertEquals(0x0F, MathUtils.parity2D8(0x000F));
    }

    @Test
    public void syndrome_identical_returnsZero() {
        assertEquals(0, MathUtils.syndrome(0x00, 0x00));
        assertEquals(0, MathUtils.syndrome(0xFF, 0xFF));
        assertEquals(0, MathUtils.syndrome(0xAB, 0xAB));
    }

    @Test
    public void syndrome_singleBitDiff_returnsOne() {
        assertEquals(1, MathUtils.syndrome(0x00, 0x01));
        assertEquals(1, MathUtils.syndrome(0x00, 0x80));
    }

    @Test
    public void syndrome_twoBitsDiff_returnsTwo() {
        assertEquals(2, MathUtils.syndrome(0x11, 0x00));
    }

    @Test
    public void syndrome_allBitsDiff_returnsEight() {
        assertEquals(8, MathUtils.syndrome(0xFF, 0x00));
    }

    // ========== Triad Consensus Tests ==========

    @Test
    public void whoOutTriad_diskOut_cpuEqualsRam() {
        assertEquals(2, MathUtils.whoOutTriad(100, 100, 200));
    }

    @Test
    public void whoOutTriad_ramOut_cpuEqualsDisk() {
        assertEquals(1, MathUtils.whoOutTriad(100, 200, 100));
    }

    @Test
    public void whoOutTriad_cpuOut_ramEqualsDisk() {
        assertEquals(0, MathUtils.whoOutTriad(200, 100, 100));
    }

    @Test
    public void whoOutTriad_allAgree_returnsNone() {
        assertEquals(3, MathUtils.whoOutTriad(100, 100, 100));
    }

    @Test
    public void whoOutTriad_allDiffer_returnsUnknown() {
        assertEquals(3, MathUtils.whoOutTriad(1, 2, 3));
    }

    // ========== Safe Arithmetic Tests ==========

    @Test
    public void addExact_normal_returnsSum() {
        assertEquals(5, MathUtils.addExact(2, 3));
        assertEquals(0, MathUtils.addExact(-5, 5));
    }

    @Test
    public void addExact_overflow_throwsException() {
        assertThrows(ArithmeticException.class, 
            () -> MathUtils.addExact(Integer.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class, 
            () -> MathUtils.addExact(Integer.MIN_VALUE, -1));
    }

    @Test
    public void multiplyExact_normal_returnsProduct() {
        assertEquals(6, MathUtils.multiplyExact(2, 3));
        assertEquals(0, MathUtils.multiplyExact(0, 1000));
    }

    @Test
    public void multiplyExact_overflow_throwsException() {
        assertThrows(ArithmeticException.class, 
            () -> MathUtils.multiplyExact(Integer.MAX_VALUE, 2));
    }

    @Test
    public void divideExact_normal_returnsQuotient() {
        assertEquals(5, MathUtils.divideExact(10, 2));
        assertEquals(-5, MathUtils.divideExact(-10, 2));
    }

    @Test
    public void divideExact_byZero_throwsException() {
        assertThrows(ArithmeticException.class, 
            () -> MathUtils.divideExact(10, 0));
    }

    @Test
    public void divideExact_minValueByNegOne_throwsException() {
        assertThrows(ArithmeticException.class, 
            () -> MathUtils.divideExact(Integer.MIN_VALUE, -1));
    }

    // ========== Clamp Tests ==========

    @Test
    public void clamp_withinRange_returnsValue() {
        assertEquals(5, MathUtils.clamp(5, 0, 10));
    }

    @Test
    public void clamp_belowMin_returnsMin() {
        assertEquals(0, MathUtils.clamp(-5, 0, 10));
    }

    @Test
    public void clamp_aboveMax_returnsMax() {
        assertEquals(10, MathUtils.clamp(15, 0, 10));
    }

    @Test
    public void clamp_invalidRange_throwsException() {
        assertThrows(IllegalArgumentException.class, 
            () -> MathUtils.clamp(5, 10, 0));
    }

    @Test
    public void clampLong_goldenVectors() {
        assertEquals(5L, MathUtils.clamp(5L, 0L, 10L));
        assertEquals(0L, MathUtils.clamp(-5L, 0L, 10L));
        assertEquals(10L, MathUtils.clamp(15L, 0L, 10L));
    }

    // ========== Byte Array Conversion Tests ==========

    @Test
    public void longToLittleEndian_goldenVectors() {
        byte[] expected = {0x78, 0x56, 0x34, 0x12, (byte)0xEF, (byte)0xCD, (byte)0xAB, (byte)0x90};
        assertArrayEquals(expected, MathUtils.longToLittleEndian(0x90ABCDEF12345678L));
    }

    @Test
    public void littleEndianToLong_goldenVectors() {
        byte[] bytes = {0x78, 0x56, 0x34, 0x12, (byte)0xEF, (byte)0xCD, (byte)0xAB, (byte)0x90};
        assertEquals(0x90ABCDEF12345678L, MathUtils.littleEndianToLong(bytes));
    }

    @Test
    public void longConversion_roundTrip() {
        long[] testValues = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE, 0x123456789ABCDEFL};
        for (long v : testValues) {
            assertEquals(v, MathUtils.littleEndianToLong(MathUtils.longToLittleEndian(v)));
        }
    }

    @Test
    public void intToLittleEndian_goldenVectors() {
        byte[] expected = {0x78, 0x56, 0x34, 0x12};
        assertArrayEquals(expected, MathUtils.intToLittleEndian(0x12345678));
    }

    @Test
    public void littleEndianToInt_goldenVectors() {
        byte[] bytes = {0x78, 0x56, 0x34, 0x12};
        assertEquals(0x12345678, MathUtils.littleEndianToInt(bytes));
    }

    @Test
    public void intConversion_roundTrip() {
        int[] testValues = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 0x12345678};
        for (int v : testValues) {
            assertEquals(v, MathUtils.littleEndianToInt(MathUtils.intToLittleEndian(v)));
        }
    }

    @Test
    public void littleEndianToLong_wrongSize_throwsException() {
        assertThrows(IllegalArgumentException.class, 
            () -> MathUtils.littleEndianToLong(new byte[4]));
    }

    @Test
    public void littleEndianToInt_wrongSize_throwsException() {
        assertThrows(IllegalArgumentException.class, 
            () -> MathUtils.littleEndianToInt(new byte[8]));
    }

    // ========== Constants Verification ==========

    @Test
    public void goldenRatio_isCorrect() {
        // Verify golden ratio: phi = (1 + sqrt(5)) / 2
        double expected = (1.0 + Math.sqrt(5.0)) / 2.0;
        assertEquals(expected, MathUtils.GOLDEN_RATIO, 1e-15);
    }

    @Test
    public void ln2_isCorrect() {
        assertEquals(Math.log(2), MathUtils.LN_2, 1e-15);
    }

    @Test
    public void log2E_isCorrect() {
        assertEquals(1.0 / Math.log(2), MathUtils.LOG2_E, 1e-15);
    }
}
