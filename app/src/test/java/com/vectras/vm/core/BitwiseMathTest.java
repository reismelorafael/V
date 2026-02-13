package com.vectras.vm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for BitwiseMath class.
 * 
 * <p>Tests cover vector operations, matrix operations, trigonometric approximations,
 * entropy calculations, and utility functions.</p>
 */
public class BitwiseMathTest {

    // ========== Vector 2D Tests ==========

    @Test
    public void packVec2_unpack_roundTrip() {
        int packed = BitwiseMath.packVec2(100, -200);
        assertEquals(100, BitwiseMath.unpackVec2X(packed));
        assertEquals(-200, BitwiseMath.unpackVec2Y(packed));
    }

    @Test
    public void packVec2_maxValues() {
        int packed = BitwiseMath.packVec2(32767, -32768);
        assertEquals(32767, BitwiseMath.unpackVec2X(packed));
        assertEquals(-32768, BitwiseMath.unpackVec2Y(packed));
    }

    @Test
    public void packVec2_zeros() {
        int packed = BitwiseMath.packVec2(0, 0);
        assertEquals(0, BitwiseMath.unpackVec2X(packed));
        assertEquals(0, BitwiseMath.unpackVec2Y(packed));
    }

    @Test
    public void addVec2Saturate_normalAddition() {
        int a = BitwiseMath.packVec2(10, 20);
        int b = BitwiseMath.packVec2(5, 10);
        int result = BitwiseMath.addVec2Saturate(a, b);
        assertEquals(15, BitwiseMath.unpackVec2X(result));
        assertEquals(30, BitwiseMath.unpackVec2Y(result));
    }

    @Test
    public void addVec2Saturate_overflow() {
        int a = BitwiseMath.packVec2(30000, -30000);
        int b = BitwiseMath.packVec2(10000, -10000);
        int result = BitwiseMath.addVec2Saturate(a, b);
        assertEquals(32767, BitwiseMath.unpackVec2X(result)); // Saturated
        assertEquals(-32768, BitwiseMath.unpackVec2Y(result)); // Saturated
    }

    @Test
    public void dotVec2_goldenVectors() {
        int a = BitwiseMath.packVec2(3, 4);
        int b = BitwiseMath.packVec2(5, 6);
        // dot = 3*5 + 4*6 = 15 + 24 = 39
        assertEquals(39, BitwiseMath.dotVec2(a, b));
    }

    @Test
    public void dotVec2_perpendicular() {
        int a = BitwiseMath.packVec2(1, 0);
        int b = BitwiseMath.packVec2(0, 1);
        // Perpendicular vectors have zero dot product
        assertEquals(0, BitwiseMath.dotVec2(a, b));
    }

    @Test
    public void magnitudeSquaredVec2_34() {
        int vec = BitwiseMath.packVec2(3, 4);
        // |v|^2 = 3^2 + 4^2 = 9 + 16 = 25
        assertEquals(25, BitwiseMath.magnitudeSquaredVec2(vec));
    }

    // ========== Vector 3D (10-bit) Tests ==========

    @Test
    public void packVec3_10bit_roundTrip() {
        int packed = BitwiseMath.packVec3_10bit(100, -50, 200);
        assertEquals(100, BitwiseMath.unpackVec3X_10bit(packed));
        assertEquals(-50, BitwiseMath.unpackVec3Y_10bit(packed));
        assertEquals(200, BitwiseMath.unpackVec3Z_10bit(packed));
    }

    @Test
    public void packVec3_10bit_maxValues() {
        int packed = BitwiseMath.packVec3_10bit(511, -512, 0);
        assertEquals(511, BitwiseMath.unpackVec3X_10bit(packed));
        assertEquals(-512, BitwiseMath.unpackVec3Y_10bit(packed));
        assertEquals(0, BitwiseMath.unpackVec3Z_10bit(packed));
    }

    // ========== Matrix Tests ==========

    @Test
    public void determinant2x2_goldenVector() {
        // det([[1,2],[3,4]]) = 1*4 - 2*3 = 4 - 6 = -2
        assertEquals(-2, BitwiseMath.determinant2x2(1, 2, 3, 4));
    }

    @Test
    public void determinant2x2_identity() {
        // det([[1,0],[0,1]]) = 1*1 - 0*0 = 1
        assertEquals(1, BitwiseMath.determinant2x2(1, 0, 0, 1));
    }

    @Test
    public void determinant2x2_singular() {
        // det([[2,4],[1,2]]) = 2*2 - 4*1 = 0
        assertEquals(0, BitwiseMath.determinant2x2(2, 4, 1, 2));
    }

    @Test
    public void trace4x4_identity() {
        short[] matrix = new short[16];
        matrix[0] = 1;
        matrix[5] = 1;
        matrix[10] = 1;
        matrix[15] = 1;
        assertEquals(4, BitwiseMath.trace4x4(matrix));
    }

    @Test
    public void matrixTranspose4x4_identity() {
        short[] matrix = new short[16];
        for (int i = 0; i < 4; i++) {
            matrix[i * 4 + i] = 1;
        }
        BitwiseMath.matrixTranspose4x4(matrix);
        // Identity matrix transpose is itself
        for (int i = 0; i < 4; i++) {
            assertEquals(1, matrix[i * 4 + i]);
        }
    }

    @Test
    public void matrixTranspose4x4_swapsElements() {
        short[] matrix = new short[16];
        matrix[1] = 10; // Element (0,1)
        matrix[4] = 20; // Element (1,0)
        
        BitwiseMath.matrixTranspose4x4(matrix);
        
        assertEquals(20, matrix[1]); // Now contains (1,0)
        assertEquals(10, matrix[4]); // Now contains (0,1)
    }

    // ========== Trigonometric Tests ==========

    @Test
    public void fastSineFixed_zero() {
        int sin0 = BitwiseMath.fastSineFixed(0);
        // sin(0) = 0
        assertTrue(Math.abs(sin0) < 500); // Allow small error
    }

    @Test
    public void fastSineFixed_piOver2() {
        int sinHalfPi = BitwiseMath.fastSineFixed(BitwiseMath.FIXED_PI >> 1);
        // sin(Pi/2) = 1 (32767 in Q15)
        assertTrue(sinHalfPi > 30000);
    }

    @Test
    public void fastSineFixed_pi() {
        int sinPi = BitwiseMath.fastSineFixed(BitwiseMath.FIXED_PI);
        // sin(Pi) = 0
        assertTrue(Math.abs(sinPi) < 3000); // Allow larger error at boundaries
    }

    @Test
    public void fastCosineFixed_zero() {
        int cos0 = BitwiseMath.fastCosineFixed(0);
        // cos(0) = 1 (32767 in Q15)
        assertTrue(cos0 > 30000);
    }

    @Test
    public void fastCosineFixed_piOver2() {
        int cosHalfPi = BitwiseMath.fastCosineFixed(BitwiseMath.FIXED_PI >> 1);
        // cos(Pi/2) = 0
        assertTrue(Math.abs(cosHalfPi) < 3000); // Allow error
    }

    @Test
    public void fastAtan2Fixed_firstQuadrant() {
        int angle = BitwiseMath.fastAtan2Fixed(1, 1);
        // atan2(1,1) = Pi/4
        int piOver4 = BitwiseMath.FIXED_PI >> 2;
        // Allow ~6 degree tolerance for approximation
        int tolerance = BitwiseMath.FIXED_PI / 30;
        assertTrue(Math.abs(angle - piOver4) < tolerance);
    }

    @Test
    public void fastAtan2Fixed_xAxis() {
        int angle = BitwiseMath.fastAtan2Fixed(0, 1);
        // atan2(0,1) = 0
        int tolerance = BitwiseMath.FIXED_PI / 30;
        assertTrue(Math.abs(angle) < tolerance);
    }

    @Test
    public void fastAtan2Fixed_yAxis() {
        int angle = BitwiseMath.fastAtan2Fixed(1, 0);
        // atan2(1,0) = Pi/2
        int piOver2 = BitwiseMath.FIXED_PI >> 1;
        int tolerance = BitwiseMath.FIXED_PI / 30;
        assertTrue(Math.abs(angle - piOver2) < tolerance);
    }

    // ========== Entropy/Harmony Tests ==========

    @Test
    public void computeHarmony_identical() {
        assertEquals(32, BitwiseMath.computeHarmony(0x12345678, 0x12345678));
    }

    @Test
    public void computeHarmony_oneBitDiff() {
        assertEquals(31, BitwiseMath.computeHarmony(0, 1));
    }

    @Test
    public void computeHarmony_allDifferent() {
        assertEquals(0, BitwiseMath.computeHarmony(0x00000000, 0xFFFFFFFF));
    }

    @Test
    public void computeSyntropy_ordered() {
        byte[] ordered = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        int syntropy = BitwiseMath.computeSyntropy(ordered, 0, ordered.length);
        // Ordered data should have high syntropy
        assertTrue(syntropy > BitwiseMath.FIXED_POINT_SCALE / 2);
    }

    @Test
    public void computeSyntropy_singleByte() {
        byte[] single = new byte[]{42};
        int syntropy = BitwiseMath.computeSyntropy(single, 0, 1);
        // Single byte = maximum order
        assertEquals(BitwiseMath.FIXED_POINT_SCALE, syntropy);
    }

    @Test
    public void fastLog2Fixed_powersOfTwo() {
        // Test that larger values give larger log results
        int log2_small = BitwiseMath.fastLog2Fixed(256);
        int log2_large = BitwiseMath.fastLog2Fixed(65536);
        // Larger input should give larger log value
        assertTrue(log2_large > log2_small);
    }

    @Test
    public void fastLog2Fixed_negative_returnsMinValue() {
        assertEquals(Integer.MIN_VALUE, BitwiseMath.fastLog2Fixed(-1));
    }

    @Test
    public void fastLog2Fixed_zero_returnsMinValue() {
        assertEquals(Integer.MIN_VALUE, BitwiseMath.fastLog2Fixed(0));
    }

    // ========== Utility Function Tests ==========

    @Test
    public void clampShort_withinRange() {
        assertEquals(100, BitwiseMath.clampShort(100));
    }

    @Test
    public void clampShort_overflow() {
        assertEquals(32767, BitwiseMath.clampShort(50000));
    }

    @Test
    public void clampShort_underflow() {
        assertEquals(-32768, BitwiseMath.clampShort(-50000));
    }

    @Test
    public void fastSqrt64_perfectSquares() {
        assertEquals(0, BitwiseMath.fastSqrt64(0));
        assertEquals(1, BitwiseMath.fastSqrt64(1));
        assertEquals(10, BitwiseMath.fastSqrt64(100));
        assertEquals(1000, BitwiseMath.fastSqrt64(1000000));
    }

    @Test
    public void fastSqrt64_nonPerfectSquares() {
        assertEquals(3, BitwiseMath.fastSqrt64(10)); // floor(sqrt(10)) = 3
        assertEquals(4, BitwiseMath.fastSqrt64(20)); // floor(sqrt(20)) = 4
    }

    @Test
    public void fastSqrt64_negative_throws() {
        assertThrows(IllegalArgumentException.class, () -> BitwiseMath.fastSqrt64(-1));
    }

    @Test
    public void branchlessMin_goldenVectors() {
        assertEquals(3, BitwiseMath.branchlessMin(3, 5));
        assertEquals(5, BitwiseMath.branchlessMin(10, 5));
        assertEquals(-5, BitwiseMath.branchlessMin(-5, 5));
        assertEquals(0, BitwiseMath.branchlessMin(0, 0));
    }

    @Test
    public void branchlessMax_goldenVectors() {
        assertEquals(5, BitwiseMath.branchlessMax(3, 5));
        assertEquals(10, BitwiseMath.branchlessMax(10, 5));
        assertEquals(5, BitwiseMath.branchlessMax(-5, 5));
        assertEquals(0, BitwiseMath.branchlessMax(0, 0));
    }

    @Test
    public void branchlessAbs_goldenVectors() {
        assertEquals(5, BitwiseMath.branchlessAbs(5));
        assertEquals(5, BitwiseMath.branchlessAbs(-5));
        assertEquals(0, BitwiseMath.branchlessAbs(0));
    }

    @Test
    public void branchlessSign_goldenVectors() {
        assertEquals(1, BitwiseMath.branchlessSign(5));
        assertEquals(-1, BitwiseMath.branchlessSign(-5));
        assertEquals(0, BitwiseMath.branchlessSign(0));
    }

    // ========== Morton Code Tests ==========

    @Test
    public void interleave16_deinterleave_roundTrip() {
        int x = 0x1234;
        int y = 0x5678;
        int morton = BitwiseMath.interleave16(x, y);
        assertEquals(x, BitwiseMath.deinterleaveX(morton));
        assertEquals(y, BitwiseMath.deinterleaveY(morton));
    }

    @Test
    public void interleave16_zeros() {
        assertEquals(0, BitwiseMath.interleave16(0, 0));
    }

    @Test
    public void interleave16_masksHigherBits() {
        assertEquals(BitwiseMath.interleave16(0xFFFF, 0xFFFF), BitwiseMath.interleave16(-1, -1));
    }

    @Test
    public void interleave16_xOnly() {
        int morton = BitwiseMath.interleave16(0xFFFF, 0);
        // All even bits should be set
        assertEquals(0x55555555, morton);
    }

    @Test
    public void interleave16_yOnly() {
        int morton = BitwiseMath.interleave16(0, 0xFFFF);
        // All odd bits should be set
        assertEquals(0xAAAAAAAA, morton);
    }

    // ========== Bit Rotation Tests ==========

    @Test
    public void rotateLeft_goldenVectors() {
        assertEquals(0x12345678, BitwiseMath.rotateLeft(0x12345678, 0));
        assertEquals(0x2468ACF0, BitwiseMath.rotateLeft(0x12345678, 1));
        assertEquals(0x48D159E0, BitwiseMath.rotateLeft(0x12345678, 2));
    }

    @Test
    public void rotateLeft_fullRotation() {
        assertEquals(0x12345678, BitwiseMath.rotateLeft(0x12345678, 32));
    }

    @Test
    public void rotateRight_goldenVectors() {
        assertEquals(0x12345678, BitwiseMath.rotateRight(0x12345678, 0));
        assertEquals(0x091A2B3C, BitwiseMath.rotateRight(0x12345678, 1));
    }

    @Test
    public void rotateRight_fullRotation() {
        assertEquals(0x12345678, BitwiseMath.rotateRight(0x12345678, 32));
    }

    // ========== Bit Reversal Tests ==========

    @Test
    public void reverseBits_goldenVectors() {
        assertEquals(0, BitwiseMath.reverseBits(0));
        assertEquals(0x80000000, BitwiseMath.reverseBits(1));
        assertEquals(0xFFFFFFFF, BitwiseMath.reverseBits(0xFFFFFFFF));
    }

    @Test
    public void reverseBits_doubleReverse() {
        int original = 0x12345678;
        int reversed = BitwiseMath.reverseBits(original);
        int doubleReversed = BitwiseMath.reverseBits(reversed);
        assertEquals(original, doubleReversed);
    }

    // ========== Leading/Trailing Zeros Tests ==========

    @Test
    public void countLeadingZeros_goldenVectors() {
        assertEquals(32, BitwiseMath.countLeadingZeros(0));
        assertEquals(31, BitwiseMath.countLeadingZeros(1));
        assertEquals(0, BitwiseMath.countLeadingZeros(-1));
        assertEquals(0, BitwiseMath.countLeadingZeros(0x80000000));
    }

    @Test
    public void countTrailingZeros_goldenVectors() {
        assertEquals(32, BitwiseMath.countTrailingZeros(0));
        assertEquals(0, BitwiseMath.countTrailingZeros(1));
        assertEquals(0, BitwiseMath.countTrailingZeros(-1));
        assertEquals(31, BitwiseMath.countTrailingZeros(0x80000000));
    }

    // ========== Spectral/Frequency Tests ==========

    @Test
    public void lowPassFilter_noChange() {
        // alpha = 1 means use only current value
        int result = BitwiseMath.lowPassFilter(100, 50, BitwiseMath.FIXED_POINT_SCALE);
        assertEquals(100, result);
    }

    @Test
    public void lowPassFilter_noCurrent() {
        // alpha = 0 means use only previous value
        int result = BitwiseMath.lowPassFilter(100, 50, 0);
        assertEquals(50, result);
    }

    @Test
    public void lowPassFilter_halfway() {
        // alpha = 0.5 means average
        int alpha = BitwiseMath.FIXED_POINT_SCALE / 2;
        int result = BitwiseMath.lowPassFilter(100, 50, alpha);
        assertEquals(75, result);
    }

    @Test
    public void computeResonance_identical() {
        byte[] signal = new byte[]{10, 20, 30, 40, 50};
        int resonance = BitwiseMath.computeResonance(signal, signal, signal.length);
        // Identical signals should have maximum correlation
        assertTrue(resonance > BitwiseMath.FIXED_POINT_SCALE / 2);
    }

    @Test
    public void computeFrequencyBinEnergy_dc() {
        byte[] signal = new byte[]{100, 100, 100, 100};
        long energy = BitwiseMath.computeFrequencyBinEnergy(signal, 0, signal.length, 0);
        // DC component (bin 0) should have high energy for constant signal
        assertTrue(energy > 0);
    }

    // ========== Advanced Bitwise Operations Tests ==========

    @Test
    public void parallelBitDeposit_basic() {
        int result = BitwiseMath.parallelBitDeposit(0b1111, 0b10101010);
        assertEquals(0b10101010, result);
    }

    @Test
    public void parallelBitExtract_basic() {
        int result = BitwiseMath.parallelBitExtract(0b11110000, 0b10101010);
        assertEquals(0b1100, result);
    }

    @Test
    public void computeParity_evenBits() {
        assertEquals(0, BitwiseMath.computeParity(0b11)); // 2 bits
        assertEquals(0, BitwiseMath.computeParity(0b1111)); // 4 bits
    }

    @Test
    public void computeParity_oddBits() {
        assertEquals(1, BitwiseMath.computeParity(0b1)); // 1 bit
        assertEquals(1, BitwiseMath.computeParity(0b111)); // 3 bits
    }

    @Test
    public void nextPowerOf2_basic() {
        assertEquals(1, BitwiseMath.nextPowerOf2(0));
        assertEquals(1, BitwiseMath.nextPowerOf2(1));
        assertEquals(2, BitwiseMath.nextPowerOf2(2));
        assertEquals(4, BitwiseMath.nextPowerOf2(3));
        assertEquals(8, BitwiseMath.nextPowerOf2(5));
        assertEquals(16, BitwiseMath.nextPowerOf2(15));
        assertEquals(16, BitwiseMath.nextPowerOf2(16));
    }

    @Test
    public void isPowerOf2_validPowers() {
        assertTrue(BitwiseMath.isPowerOf2(1));
        assertTrue(BitwiseMath.isPowerOf2(2));
        assertTrue(BitwiseMath.isPowerOf2(4));
        assertTrue(BitwiseMath.isPowerOf2(1024));
    }

    @Test
    public void isPowerOf2_invalidPowers() {
        assertFalse(BitwiseMath.isPowerOf2(0));
        assertFalse(BitwiseMath.isPowerOf2(3));
        assertFalse(BitwiseMath.isPowerOf2(5));
        assertFalse(BitwiseMath.isPowerOf2(100));
    }

    @Test
    public void fastLog2_nonPositive_returnsMinValue() {
        assertEquals(Integer.MIN_VALUE, BitwiseMath.fastLog2(0));
        assertEquals(Integer.MIN_VALUE, BitwiseMath.fastLog2(-1));
    }

    @Test
    public void fastLog2_basic() {
        assertEquals(0, BitwiseMath.fastLog2(1));
        assertEquals(1, BitwiseMath.fastLog2(2));
        assertEquals(2, BitwiseMath.fastLog2(4));
        assertEquals(3, BitwiseMath.fastLog2(8));
        assertEquals(9, BitwiseMath.fastLog2(1000));
    }

    @Test
    public void sign_positive() {
        assertEquals(1, BitwiseMath.sign(42));
        assertEquals(1, BitwiseMath.sign(1));
    }

    @Test
    public void sign_negative() {
        assertEquals(-1, BitwiseMath.sign(-42));
        assertEquals(-1, BitwiseMath.sign(-1));
    }

    @Test
    public void sign_zero() {
        assertEquals(0, BitwiseMath.sign(0));
    }

    @Test
    public void xorSwap_basic() {
        int[] arr = {10, 20, 30};
        BitwiseMath.xorSwap(arr, 0, 2);
        assertEquals(30, arr[0]);
        assertEquals(20, arr[1]);
        assertEquals(10, arr[2]);
    }

    @Test
    public void xorSwap_sameIndex() {
        int[] arr = {10, 20, 30};
        BitwiseMath.xorSwap(arr, 1, 1);
        assertEquals(10, arr[0]);
        assertEquals(20, arr[1]);
        assertEquals(30, arr[2]);
    }

    @Test
    public void binaryToGray_basic() {
        assertEquals(0, BitwiseMath.binaryToGray(0));
        assertEquals(1, BitwiseMath.binaryToGray(1));
        assertEquals(3, BitwiseMath.binaryToGray(2));
        assertEquals(2, BitwiseMath.binaryToGray(3));
    }

    @Test
    public void grayToBinary_basic() {
        assertEquals(0, BitwiseMath.grayToBinary(0));
        assertEquals(1, BitwiseMath.grayToBinary(1));
        assertEquals(2, BitwiseMath.grayToBinary(3));
        assertEquals(3, BitwiseMath.grayToBinary(2));
    }

    @Test
    public void grayCode_roundTrip() {
        for (int i = 0; i < 100; i++) {
            int gray = BitwiseMath.binaryToGray(i);
            int binary = BitwiseMath.grayToBinary(gray);
            assertEquals(i, binary);
        }
    }

    @Test
    public void interleave3D_basic() {
        int morton = BitwiseMath.interleave3D(1, 2, 3);
        assertTrue(morton > 0);
        
        // Test zeros
        assertEquals(0, BitwiseMath.interleave3D(0, 0, 0));
    }

    @Test
    public void multiplyBy10_basic() {
        assertEquals(0, BitwiseMath.multiplyBy10(0));
        assertEquals(10, BitwiseMath.multiplyBy10(1));
        assertEquals(50, BitwiseMath.multiplyBy10(5));
        assertEquals(100, BitwiseMath.multiplyBy10(10));
        assertEquals(1000, BitwiseMath.multiplyBy10(100));
    }

    @Test
    public void divideBy10_basic() {
        assertEquals(0, BitwiseMath.divideBy10(0));
        assertEquals(0, BitwiseMath.divideBy10(9));
        assertEquals(1, BitwiseMath.divideBy10(10));
        assertEquals(5, BitwiseMath.divideBy10(50));
        assertEquals(10, BitwiseMath.divideBy10(100));
        assertEquals(100, BitwiseMath.divideBy10(1000));
    }

    @Test
    public void multiplyDivideBy10_roundTrip() {
        for (int i = 0; i < 100; i++) {
            int multiplied = BitwiseMath.multiplyBy10(i);
            int divided = BitwiseMath.divideBy10(multiplied);
            assertEquals(i, divided);
        }
    }

    @Test
    public void hammingDistance_identical() {
        assertEquals(0, BitwiseMath.hammingDistance(42, 42));
        assertEquals(0, BitwiseMath.hammingDistance(0, 0));
    }

    @Test
    public void hammingDistance_different() {
        assertEquals(1, BitwiseMath.hammingDistance(0b1000, 0b0000));
        assertEquals(2, BitwiseMath.hammingDistance(0b1100, 0b0000));
        assertEquals(32, BitwiseMath.hammingDistance(0xFFFFFFFF, 0));
    }

    @Test
    public void fastAbs_positive() {
        assertEquals(42, BitwiseMath.fastAbs(42));
        assertEquals(1, BitwiseMath.fastAbs(1));
    }

    @Test
    public void fastAbs_negative() {
        assertEquals(42, BitwiseMath.fastAbs(-42));
        assertEquals(1, BitwiseMath.fastAbs(-1));
    }

    @Test
    public void fastAbs_zero() {
        assertEquals(0, BitwiseMath.fastAbs(0));
    }

    @Test
    public void conditionalMove_true() {
        assertEquals(100, BitwiseMath.conditionalMove(1, 100, 200));
        assertEquals(100, BitwiseMath.conditionalMove(42, 100, 200));
    }

    @Test
    public void conditionalMove_false() {
        assertEquals(200, BitwiseMath.conditionalMove(0, 100, 200));
    }
}
