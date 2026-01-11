package com.vectras.vm.core;

/**
 * MathUtils: Stable mathematical implementations for the Vectras VM project.
 * 
 * <p>This class provides deterministic, tested mathematical operations that are
 * derived from code (not OCR) and validated using golden test vectors.</p>
 * 
 * <p>All implementations follow ISO 8000/9001 standards for software development
 * with comprehensive documentation and test coverage.</p>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li>All calculations are derived from code, never from OCR</li>
 *   <li>Each function has golden test vectors for validation</li>
 *   <li>Stable implementations using well-known algorithms</li>
 *   <li>Comprehensive comments explaining the mathematical basis</li>
 * </ul>
 * 
 * @author Vectras Team
 * @version 1.0.0
 */
public final class MathUtils {

    // ========== Constants with Mathematical Derivation ==========
    
    /**
     * Golden ratio (phi) = (1 + sqrt(5)) / 2
     * Used for hash mixing and distribution.
     * Derived from: https://en.wikipedia.org/wiki/Golden_ratio
     */
    public static final double GOLDEN_RATIO = 1.6180339887498948482;
    
    /**
     * Golden ratio in fixed-point 64-bit representation.
     * Calculated as: (2^64 - 1) / phi ≈ 0x9E3779B97F4A7C15
     * Used in SplitMix64 and other hash functions.
     */
    public static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    
    /**
     * Mixing constant A from SplitMix64.
     * Derived from: https://xorshift.di.unimi.it/splitmix64.c
     */
    public static final long MIX_CONST_A = 0xBF58476D1CE4E5B9L;
    
    /**
     * Mixing constant B from SplitMix64.
     */
    public static final long MIX_CONST_B = 0x94D049BB133111EBL;
    
    /**
     * CRC32C (Castagnoli) polynomial.
     * Standard polynomial: 0x1EDC6F41 (normal form)
     * Reflected polynomial: 0x82F63B78
     */
    public static final int CRC32C_POLY = 0x82F63B78;
    
    /**
     * Natural logarithm of 2 (ln(2)).
     * Used for log base conversions.
     * Math.log(2) = 0.6931471805599453
     */
    public static final double LN_2 = 0.6931471805599453;
    
    /**
     * Log base 2 of e (log2(e)).
     * Used for converting natural logs to log base 2.
     * 1 / ln(2) = 1.4426950408889634
     */
    public static final double LOG2_E = 1.4426950408889634;

    // ========== Private Constructor ==========
    
    private MathUtils() {
        throw new AssertionError("MathUtils is a utility class and cannot be instantiated");
    }

    // ========== Integer Log2 ==========
    
    /**
     * Computes floor(log2(n)) for positive integers.
     * 
     * <p>Algorithm: Uses bit scanning to find the highest set bit position.</p>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>log2Floor(1) = 0</li>
     *   <li>log2Floor(2) = 1</li>
     *   <li>log2Floor(4) = 2</li>
     *   <li>log2Floor(7) = 2</li>
     *   <li>log2Floor(8) = 3</li>
     *   <li>log2Floor(1024) = 10</li>
     *   <li>log2Floor(Integer.MAX_VALUE) = 30</li>
     * </ul>
     * 
     * @param n positive integer (must be > 0)
     * @return floor(log2(n))
     * @throws IllegalArgumentException if n <= 0
     */
    public static int log2Floor(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("log2Floor requires positive input, got: " + n);
        }
        // 31 - numberOfLeadingZeros gives position of highest set bit
        return 31 - Integer.numberOfLeadingZeros(n);
    }
    
    /**
     * Computes floor(log2(n)) for positive long integers.
     * 
     * @param n positive long (must be > 0)
     * @return floor(log2(n))
     * @throws IllegalArgumentException if n <= 0
     */
    public static int log2Floor(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("log2Floor requires positive input, got: " + n);
        }
        return 63 - Long.numberOfLeadingZeros(n);
    }
    
    /**
     * Computes ceiling(log2(n)) for positive integers.
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>log2Ceil(1) = 0</li>
     *   <li>log2Ceil(2) = 1</li>
     *   <li>log2Ceil(3) = 2</li>
     *   <li>log2Ceil(4) = 2</li>
     *   <li>log2Ceil(5) = 3</li>
     *   <li>log2Ceil(1024) = 10</li>
     *   <li>log2Ceil(1025) = 11</li>
     * </ul>
     * 
     * @param n positive integer (must be > 0)
     * @return ceiling(log2(n))
     * @throws IllegalArgumentException if n <= 0
     */
    public static int log2Ceil(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("log2Ceil requires positive input, got: " + n);
        }
        if (n == 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    // ========== Integer Square Root ==========
    
    /**
     * Computes floor(sqrt(n)) for non-negative integers using Newton's method.
     * 
     * <p>Algorithm: Newton-Raphson iteration with integer arithmetic.</p>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>isqrt(0) = 0</li>
     *   <li>isqrt(1) = 1</li>
     *   <li>isqrt(4) = 2</li>
     *   <li>isqrt(8) = 2</li>
     *   <li>isqrt(9) = 3</li>
     *   <li>isqrt(100) = 10</li>
     *   <li>isqrt(Integer.MAX_VALUE) = 46340</li>
     * </ul>
     * 
     * @param n non-negative integer (must be >= 0)
     * @return floor(sqrt(n))
     * @throws IllegalArgumentException if n < 0
     */
    public static int isqrt(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("isqrt requires non-negative input, got: " + n);
        }
        if (n == 0) return 0;
        if (n == 1) return 1;
        
        // Use long arithmetic to avoid overflow
        // Newton-Raphson iteration: x_{n+1} = (x_n + n/x_n) / 2
        long nLong = n;
        long x = nLong;
        long y = (x + 1) / 2;
        while (y < x) {
            x = y;
            y = (x + nLong / x) / 2;
        }
        return (int) x;
    }
    
    /**
     * Computes floor(sqrt(n)) for non-negative long integers.
     * 
     * @param n non-negative long (must be >= 0)
     * @return floor(sqrt(n))
     * @throws IllegalArgumentException if n < 0
     */
    public static long isqrt(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("isqrt requires non-negative input, got: " + n);
        }
        if (n == 0) return 0;
        
        long x = n;
        long y = (x + 1) / 2;
        while (y < x) {
            x = y;
            y = (x + n / x) / 2;
        }
        return x;
    }

    // ========== Power of Two Utilities ==========
    
    /**
     * Checks if n is a power of two.
     * 
     * <p>Algorithm: A positive number n is a power of two if and only if
     * n has exactly one bit set, which means (n & (n-1)) == 0.</p>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>isPowerOfTwo(0) = false</li>
     *   <li>isPowerOfTwo(1) = true</li>
     *   <li>isPowerOfTwo(2) = true</li>
     *   <li>isPowerOfTwo(3) = false</li>
     *   <li>isPowerOfTwo(4) = true</li>
     *   <li>isPowerOfTwo(1024) = true</li>
     *   <li>isPowerOfTwo(1023) = false</li>
     * </ul>
     * 
     * @param n the number to check
     * @return true if n is a positive power of two
     */
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Rounds up to the next power of two.
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>nextPowerOfTwo(0) = 1</li>
     *   <li>nextPowerOfTwo(1) = 1</li>
     *   <li>nextPowerOfTwo(2) = 2</li>
     *   <li>nextPowerOfTwo(3) = 4</li>
     *   <li>nextPowerOfTwo(5) = 8</li>
     *   <li>nextPowerOfTwo(1000) = 1024</li>
     * </ul>
     * 
     * @param n non-negative integer
     * @return smallest power of two >= n
     */
    public static int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    // ========== 64-bit Mixing Functions ==========
    
    /**
     * SplitMix64 hash mixing function.
     * 
     * <p>This is a high-quality 64-bit mixer that produces well-distributed
     * output from any input. Used in random number generation and hashing.</p>
     * 
     * <p>Reference: https://xorshift.di.unimi.it/splitmix64.c</p>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>mix64(0) = 0x0000000000000000L</li>
     *   <li>mix64(1) = 0x5692161D100B05E5L</li>
     *   <li>mix64(GOLDEN_GAMMA) = 0xE220A8397B1DCDAFL</li>
     * </ul>
     * 
     * @param x input value
     * @return mixed 64-bit value
     */
    public static long mix64(long x) {
        x ^= (x >>> 30);
        x *= MIX_CONST_A;
        x ^= (x >>> 27);
        x *= MIX_CONST_B;
        x ^= (x >>> 31);
        return x;
    }
    
    /**
     * Inverse of mix64 for reversibility testing.
     * 
     * @param x mixed value
     * @return original value
     */
    public static long unmix64(long x) {
        // Inverse of mix64
        x ^= (x >>> 31) ^ (x >>> 62);
        x *= 0x319642B2D24D8EC3L; // Inverse of MIX_CONST_B
        x ^= (x >>> 27) ^ (x >>> 54);
        x *= 0x96DE1B173F119089L; // Inverse of MIX_CONST_A
        x ^= (x >>> 30) ^ (x >>> 60);
        return x;
    }

    // ========== Popcount and Parity ==========
    
    /**
     * Computes population count (number of 1-bits) in an integer.
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>popcount(0) = 0</li>
     *   <li>popcount(1) = 1</li>
     *   <li>popcount(0xFF) = 8</li>
     *   <li>popcount(0xFFFF) = 16</li>
     *   <li>popcount(0x55555555) = 16</li>
     *   <li>popcount(-1) = 32</li>
     * </ul>
     * 
     * @param n integer value
     * @return number of 1-bits
     */
    public static int popcount(int n) {
        return Integer.bitCount(n);
    }
    
    /**
     * Computes parity (XOR of all bits) of an integer.
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>parity(0) = 0</li>
     *   <li>parity(1) = 1</li>
     *   <li>parity(3) = 0 (two 1-bits)</li>
     *   <li>parity(7) = 1 (three 1-bits)</li>
     *   <li>parity(0xFF) = 0 (eight 1-bits)</li>
     * </ul>
     * 
     * @param n integer value
     * @return 0 if even parity, 1 if odd parity
     */
    public static int parity(int n) {
        return Integer.bitCount(n) & 1;
    }

    // ========== 4x4 Matrix Parity (2D ECC-lite) ==========
    
    /**
     * Computes index in a 4x4 matrix given x,y coordinates.
     * 
     * <p>Coordinate mapping: idx = (y << 2) | x</p>
     * 
     * @param x column (0-3)
     * @param y row (0-3)
     * @return bit index (0-15)
     */
    public static int idx4x4(int x, int y) {
        return (y << 2) | x;
    }
    
    /**
     * Gets a bit from a 16-bit value representing a 4x4 matrix.
     * 
     * @param bits16 16-bit value
     * @param x column (0-3)
     * @param y row (0-3)
     * @return bit value (0 or 1)
     */
    public static int getBit4x4(int bits16, int x, int y) {
        return (bits16 >>> idx4x4(x, y)) & 1;
    }
    
    /**
     * Sets a bit in a 16-bit value representing a 4x4 matrix.
     * 
     * @param bits16 16-bit value
     * @param x column (0-3)
     * @param y row (0-3)
     * @param v bit value (0 or 1)
     * @return updated 16-bit value
     */
    public static int setBit4x4(int bits16, int x, int y, int v) {
        int i = idx4x4(x, y);
        int mask = 1 << i;
        return (v == 0) ? (bits16 & ~mask) : (bits16 | mask);
    }
    
    /**
     * Computes 2D parity for a 4x4 matrix (8 bits total).
     * 
     * <p>Output format: [row3, row2, row1, row0, col3, col2, col1, col0]</p>
     * <ul>
     *   <li>Bits 0-3: Column parities (even parity)</li>
     *   <li>Bits 4-7: Row parities (even parity)</li>
     * </ul>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>parity2D8(0x0000) = 0x00</li>
     *   <li>parity2D8(0x0001) = 0x11 (row0=1, col0=1)</li>
     *   <li>parity2D8(0xFFFF) = 0x00 (all rows/cols have even parity)</li>
     *   <li>parity2D8(0xAAAA) = 0x00 (alternating pattern)</li>
     *   <li>parity2D8(0x5555) = 0x00 (inverse alternating)</li>
     * </ul>
     * 
     * @param bits16 16-bit value representing 4x4 matrix
     * @return 8-bit parity (4 row + 4 column)
     */
    public static int parity2D8(int bits16) {
        int parity = 0;
        
        // Compute row parities (bits 4-7)
        for (int y = 0; y < 4; y++) {
            int rowParity = 0;
            for (int x = 0; x < 4; x++) {
                rowParity ^= getBit4x4(bits16, x, y);
            }
            parity |= (rowParity & 1) << (y + 4);
        }
        
        // Compute column parities (bits 0-3)
        for (int x = 0; x < 4; x++) {
            int colParity = 0;
            for (int y = 0; y < 4; y++) {
                colParity ^= getBit4x4(bits16, x, y);
            }
            parity |= (colParity & 1) << x;
        }
        
        return parity & 0xFF;
    }
    
    /**
     * Computes syndrome (difference count) between stored and computed parity.
     * 
     * <p>The syndrome indicates how many parity bits differ, which can be used
     * for error detection in the 4x4 matrix.</p>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>syndrome(0x00, 0x00) = 0</li>
     *   <li>syndrome(0x11, 0x00) = 2</li>
     *   <li>syndrome(0xFF, 0x00) = 8</li>
     * </ul>
     * 
     * @param storedParity8 stored parity value
     * @param computedParity8 computed parity value
     * @return number of differing bits (0-8)
     */
    public static int syndrome(int storedParity8, int computedParity8) {
        int diff = (storedParity8 ^ computedParity8) & 0xFF;
        return Integer.bitCount(diff);
    }

    // ========== Triad Consensus (2-of-3) ==========
    
    /**
     * Determines which component is "out" in a 2-of-3 consensus.
     * 
     * <p>This implements a simple fault detection where if two components
     * agree and one differs, the differing component is identified.</p>
     * 
     * <p>Return values:</p>
     * <ul>
     *   <li>0 = CPU is out (RAM == DISK)</li>
     *   <li>1 = RAM is out (CPU == DISK)</li>
     *   <li>2 = DISK is out (CPU == RAM)</li>
     *   <li>3 = NONE/UNKNOWN (all agree or all differ)</li>
     * </ul>
     * 
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>whoOutTriad(100, 100, 200) = 2 (DISK)</li>
     *   <li>whoOutTriad(100, 200, 100) = 1 (RAM)</li>
     *   <li>whoOutTriad(200, 100, 100) = 0 (CPU)</li>
     *   <li>whoOutTriad(100, 100, 100) = 3 (NONE)</li>
     *   <li>whoOutTriad(1, 2, 3) = 3 (UNKNOWN)</li>
     * </ul>
     * 
     * @param cpu CPU state value
     * @param ram RAM state value
     * @param disk DISK state value
     * @return identifier of the out-of-sync component (0-3)
     */
    public static int whoOutTriad(long cpu, long ram, long disk) {
        if (cpu == ram && cpu != disk) return 2; // DISK out
        if (cpu == disk && cpu != ram) return 1; // RAM out
        if (ram == disk && ram != cpu) return 0; // CPU out
        return 3; // NONE or UNKNOWN
    }

    // ========== Safe Arithmetic ==========
    
    /**
     * Adds two integers with overflow check.
     * 
     * @param a first operand
     * @param b second operand
     * @return sum
     * @throws ArithmeticException if overflow occurs
     */
    public static int addExact(int a, int b) {
        return Math.addExact(a, b);
    }
    
    /**
     * Multiplies two integers with overflow check.
     * 
     * @param a first operand
     * @param b second operand
     * @return product
     * @throws ArithmeticException if overflow occurs
     */
    public static int multiplyExact(int a, int b) {
        return Math.multiplyExact(a, b);
    }
    
    /**
     * Safe division that handles edge cases.
     * 
     * @param dividend the dividend
     * @param divisor the divisor
     * @return quotient
     * @throws ArithmeticException if divisor is zero
     */
    public static int divideExact(int dividend, int divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("Division by zero");
        }
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            throw new ArithmeticException("Integer overflow in division");
        }
        return dividend / divisor;
    }

    // ========== Clamping Functions ==========
    
    /**
     * Clamps a value to a range.
     * 
     * @param value the value to clamp
     * @param min minimum bound
     * @param max maximum bound
     * @return clamped value
     */
    public static int clamp(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamps a value to a range.
     * 
     * @param value the value to clamp
     * @param min minimum bound
     * @param max maximum bound
     * @return clamped value
     */
    public static long clamp(long value, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        return Math.max(min, Math.min(max, value));
    }

    // ========== Byte Array Utilities ==========
    
    /**
     * Converts a long to little-endian byte array.
     * 
     * @param value the long value
     * @return 8-byte array in little-endian order
     */
    public static byte[] longToLittleEndian(long value) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
        return bytes;
    }
    
    /**
     * Converts a little-endian byte array to long.
     * 
     * @param bytes 8-byte array in little-endian order
     * @return the long value
     */
    public static long littleEndianToLong(byte[] bytes) {
        if (bytes.length != 8) {
            throw new IllegalArgumentException("Expected 8 bytes, got " + bytes.length);
        }
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) (bytes[i] & 0xFF)) << (8 * i);
        }
        return value;
    }
    
    /**
     * Converts an int to little-endian byte array.
     * 
     * @param value the int value
     * @return 4-byte array in little-endian order
     */
    public static byte[] intToLittleEndian(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
        return bytes;
    }
    
    /**
     * Converts a little-endian byte array to int.
     * 
     * @param bytes 4-byte array in little-endian order
     * @return the int value
     */
    public static int littleEndianToInt(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Expected 4 bytes, got " + bytes.length);
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value |= (bytes[i] & 0xFF) << (8 * i);
        }
        return value;
    }
}
