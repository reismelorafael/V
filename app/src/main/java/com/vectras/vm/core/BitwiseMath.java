package com.vectras.vm.core;

/**
 * BitwiseMath: Enhanced low-level bitwise and mathematical operations for performance optimization.
 * 
 * <p>This class provides optimized mathematical operations using bitwise manipulation,
 * vector/matrix operations, and geometric transformations. Designed to improve performance
 * in the Vectras VM project through low-level operations.</p>
 * 
 * <h2>Categories:</h2>
 * <ul>
 *   <li>Vector Operations: Fast vector math using bitwise tricks</li>
 *   <li>Matrix Operations: Efficient matrix transformations</li>
 *   <li>Trigonometric Approximations: Fast sine/cosine using lookup tables</li>
 *   <li>Entropy/Harmony: Information-theoretic calculations</li>
 *   <li>Spectral/Frequency: Frequency domain helpers</li>
 * </ul>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li>Low-level bitwise operations for maximum performance</li>
 *   <li>Branchless implementations where possible</li>
 *   <li>No heap allocations in hot paths</li>
 *   <li>Deterministic and reproducible results</li>
 * </ul>
 * 
 * @author Vectras Team
 * @version 1.0.0
 */
public final class BitwiseMath {

    // ========== Constants ==========
    
    /** Fixed-point precision for trigonometric operations (16 bits) */
    public static final int FIXED_POINT_BITS = 16;
    
    /** Fixed-point scale factor */
    public static final int FIXED_POINT_SCALE = 1 << FIXED_POINT_BITS;
    
    /** Pi in fixed-point representation */
    public static final int FIXED_PI = 205887;
    
    /** 2*Pi in fixed-point representation */
    public static final int FIXED_TWO_PI = 411774;
    
    /** Euler's number in fixed-point representation */
    public static final int FIXED_E = 178145;
    
    /** Golden ratio in fixed-point representation */
    public static final int FIXED_PHI = 106039;
    
    // ========== Private Constructor ==========
    
    private BitwiseMath() {
        throw new AssertionError("BitwiseMath is a utility class and cannot be instantiated");
    }

    // ========== Vector Operations (2D/3D using packed integers) ==========
    
    /**
     * Packs two 16-bit signed values into a 32-bit integer (2D vector).
     * Format: [y: bits 16-31][x: bits 0-15]
     * 
     * @param x X component (-32768 to 32767)
     * @param y Y component (-32768 to 32767)
     * @return Packed 32-bit vector
     */
    public static int packVec2(int x, int y) {
        return LowLevelAsm.asmVec2Pack(x, y);
    }
    
    /**
     * Extracts X component from packed 2D vector.
     * 
     * @param vec Packed 2D vector
     * @return X component (sign-extended)
     */
    public static int unpackVec2X(int vec) {
        return LowLevelAsm.asmVec2X(vec);
    }
    
    /**
     * Extracts Y component from packed 2D vector.
     * 
     * @param vec Packed 2D vector
     * @return Y component (sign-extended)
     */
    public static int unpackVec2Y(int vec) {
        return LowLevelAsm.asmVec2Y(vec);
    }
    
    /**
     * Adds two packed 2D vectors with saturation.
     * 
     * @param a First vector
     * @param b Second vector
     * @return Sum vector (saturated to 16-bit range)
     */
    public static int addVec2Saturate(int a, int b) {
        return LowLevelAsm.asmVec2AddSat(a, b);
    }
    
    /**
     * Computes dot product of two packed 2D vectors.
     * 
     * @param a First vector
     * @param b Second vector
     * @return Dot product (ax*bx + ay*by)
     */
    public static int dotVec2(int a, int b) {
        return LowLevelAsm.asmVec2Dot(a, b);
    }
    
    /**
     * Computes squared magnitude of packed 2D vector.
     * 
     * @param vec Packed 2D vector
     * @return Squared magnitude (x^2 + y^2)
     */
    public static int magnitudeSquaredVec2(int vec) {
        return LowLevelAsm.asmVec2Mag2(vec);
    }
    
    /**
     * Packs three 10-bit values into a 32-bit integer (3D vector).
     * Format: [unused: 2 bits][z: 10 bits][y: 10 bits][x: 10 bits]
     * Range: -512 to 511 per component
     * 
     * @param x X component (-512 to 511)
     * @param y Y component (-512 to 511)
     * @param z Z component (-512 to 511)
     * @return Packed 30-bit vector
     */
    public static int packVec3_10bit(int x, int y, int z) {
        return ((z & 0x3FF) << 20) | ((y & 0x3FF) << 10) | (x & 0x3FF);
    }
    
    /**
     * Extracts X component from packed 3D vector (10-bit).
     * 
     * @param vec Packed 3D vector
     * @return X component (sign-extended)
     */
    public static int unpackVec3X_10bit(int vec) {
        int x = vec & 0x3FF;
        return (x & 0x200) != 0 ? (x | 0xFFFFFC00) : x; // Sign extend
    }
    
    /**
     * Extracts Y component from packed 3D vector (10-bit).
     * 
     * @param vec Packed 3D vector
     * @return Y component (sign-extended)
     */
    public static int unpackVec3Y_10bit(int vec) {
        int y = (vec >>> 10) & 0x3FF;
        return (y & 0x200) != 0 ? (y | 0xFFFFFC00) : y;
    }
    
    /**
     * Extracts Z component from packed 3D vector (10-bit).
     * 
     * @param vec Packed 3D vector
     * @return Z component (sign-extended)
     */
    public static int unpackVec3Z_10bit(int vec) {
        int z = (vec >>> 20) & 0x3FF;
        return (z & 0x200) != 0 ? (z | 0xFFFFFC00) : z;
    }

    // ========== Matrix Operations (4x4 using 16-bit elements) ==========
    
    /**
     * Computes matrix-vector product for 4x4 matrix and 4D vector.
     * Matrix is stored in row-major order as 16 shorts.
     * Vector is stored as 4 shorts.
     * 
     * @param matrix 16-element array (4x4 matrix in row-major)
     * @param vector 4-element array (input vector)
     * @param result 4-element array (output vector)
     */
    public static void matrixVectorMul4x4(short[] matrix, short[] vector, int[] result) {
        LowLevelAsm.asmMat4Mul(matrix, 0, vector, 0, result, 0);
    }
    
    /**
     * Computes transpose of 4x4 matrix in-place using XOR swaps.
     * Uses bitwise operations to swap elements without temporary storage.
     * 
     * @param matrix 16-element array to transpose
     */
    public static void matrixTranspose4x4(short[] matrix) {
        LowLevelAsm.asmMat4Transpose(matrix, 0);
    }
    
    /**
     * Computes determinant of 2x2 matrix.
     * det = a*d - b*c
     * 
     * @param a Element (0,0)
     * @param b Element (0,1)
     * @param c Element (1,0)
     * @param d Element (1,1)
     * @return Determinant value
     */
    public static int determinant2x2(int a, int b, int c, int d) {
        return a * d - b * c;
    }
    
    /**
     * Computes trace (sum of diagonal elements) of 4x4 matrix.
     * 
     * @param matrix 16-element array
     * @return Sum of diagonal elements
     */
    public static int trace4x4(short[] matrix) {
        return matrix[0] + matrix[5] + matrix[10] + matrix[15];
    }

    // ========== Fast Trigonometric Approximations ==========
    
    /**
     * Fast sine approximation using lookup table interpolation.
     * Input angle is in fixed-point format where 2*Pi = FIXED_TWO_PI.
     * 
     * @param angleFixed Angle in fixed-point (0 to FIXED_TWO_PI for full circle)
     * @return Sine value in Q15 format (-32767 to 32767)
     */
    public static int fastSineFixed(int angleFixed) {
        return LowLevelAsm.asmFastSineFixed(angleFixed);
    }
    
    /**
     * Fast cosine approximation using sine with phase shift.
     * cos(x) = sin(x + Pi/2)
     * 
     * @param angleFixed Angle in fixed-point
     * @return Cosine value in Q15 format (-32767 to 32767)
     */
    public static int fastCosineFixed(int angleFixed) {
        return LowLevelAsm.asmFastCosineFixed(angleFixed);
    }
    
    /**
     * Fast atan2 approximation using polynomial approximation.
     * Returns angle in fixed-point format.
     * 
     * @param y Y coordinate
     * @param x X coordinate
     * @return Angle in fixed-point (-FIXED_PI to FIXED_PI)
     */
    public static int fastAtan2Fixed(int y, int x) {
        if (x == 0 && y == 0) return 0;
        
        int absY = LowLevelAsm.asmAbs(y);
        int absX = LowLevelAsm.asmAbs(x);
        
        // Use angle = (Pi/4) * (y/x) for small angles
        // with correction for larger angles
        int angle;
        if (absX >= absY) {
            // |angle| <= Pi/4
            long ratio = ((long) absY << FIXED_POINT_BITS) / absX;
            angle = (int) ((ratio * (FIXED_PI >> 2)) >> FIXED_POINT_BITS);
        } else {
            // Pi/4 < |angle| <= Pi/2
            long ratio = ((long) absX << FIXED_POINT_BITS) / absY;
            angle = (FIXED_PI >> 1) - (int) ((ratio * (FIXED_PI >> 2)) >> FIXED_POINT_BITS);
        }
        
        // Adjust for quadrant
        if (x < 0) {
            angle = FIXED_PI - angle;
        }
        if (y < 0) {
            angle = -angle;
        }
        
        return angle;
    }

    // ========== Entropy and Harmony Operations ==========
    
    /**
     * Computes Shannon entropy approximation for byte distribution.
     * Uses lookup table for log2 approximation.
     * 
     * @param counts 256-element histogram of byte occurrences
     * @param totalBytes Total number of bytes
     * @return Entropy in fixed-point (bits per byte * FIXED_POINT_SCALE)
     */
    public static int computeEntropyFixed(int[] counts, int totalBytes) {
        if (totalBytes <= 0) return 0;
        
        long entropy = 0;
        for (int i = 0; i < 256; i++) {
            if (counts[i] > 0) {
                // p = counts[i] / totalBytes
                // contribution = -p * log2(p)
                // Using log2(counts[i]/totalBytes) = log2(counts[i]) - log2(totalBytes)
                long p = ((long) counts[i] << FIXED_POINT_BITS) / totalBytes;
                if (p > 0) {
                    int log2p = fastLog2Fixed((int) p);
                    entropy -= (p * log2p) >> FIXED_POINT_BITS;
                }
            }
        }
        
        return (int) entropy;
    }
    
    /**
     * Fast log2 approximation using lookup table.
     * 
     * @param x Input value (must be positive)
     * @return log2(x) in fixed-point format
     */
    public static int fastLog2Fixed(int x) {
        return LowLevelAsm.asmFastLog2Fixed(x);
    }
    
    /**
     * Computes "harmony" score between two values using XOR and popcount.
     * Higher score = more similar bit patterns.
     * 
     * @param a First value
     * @param b Second value
     * @return Harmony score (0-32, where 32 = identical)
     */
    public static int computeHarmony(int a, int b) {
        return 32 - NativeFastPath.popcount32(a ^ b);
    }
    
    /**
     * Computes "syntropy" (reverse entropy) indicator.
     * Measures how ordered/structured the data is.
     * 
     * @param data Input bytes
     * @param offset Start offset
     * @param length Number of bytes
     * @return Syntropy score (higher = more ordered)
     */
    public static int computeSyntropy(byte[] data, int offset, int length) {
        if (length <= 1) return FIXED_POINT_SCALE; // Maximum order
        
        // Count consecutive differences
        int totalDiff = 0;
        int maxDiff = 0;
        
        for (int i = offset + 1; i < offset + length; i++) {
            int diff = LowLevelAsm.asmAbs((data[i] & 0xFF) - (data[i - 1] & 0xFF));
            totalDiff += diff;
            if (diff > maxDiff) maxDiff = diff;
        }
        
        // Normalize: lower average difference = higher syntropy
        int avgDiff = totalDiff / (length - 1);
        int syntropy = FIXED_POINT_SCALE - ((avgDiff * FIXED_POINT_SCALE) / 256);
        
        return LowLevelAsm.asmMax(0, syntropy);
    }

    // ========== Spectral/Frequency Operations ==========
    
    /**
     * Computes energy at a specific frequency bin using discrete Fourier coefficients.
     * Uses integer arithmetic for performance.
     * 
     * @param data Input signal (8-bit samples)
     * @param offset Start offset
     * @param length Window size (should be power of 2)
     * @param binIndex Frequency bin (0 to length/2)
     * @return Energy at frequency bin
     */
    public static long computeFrequencyBinEnergy(byte[] data, int offset, int length, int binIndex) {
        long realPart = 0;
        long imagPart = 0;
        
        for (int n = 0; n < length; n++) {
            int sample = data[offset + n] & 0xFF;
            // Compute angle = 2*Pi*binIndex*n/length in fixed-point
            int angle = (binIndex * n * FIXED_TWO_PI) / length;
            
            int cosVal = fastCosineFixed(angle);
            int sinVal = fastSineFixed(angle);
            
            realPart += ((long) sample * cosVal) >> 15;
            imagPart += ((long) sample * sinVal) >> 15;
        }
        
        // Energy = real^2 + imag^2
        return (realPart * realPart + imagPart * imagPart) / length;
    }
    
    /**
     * Applies simple low-pass filter using exponential moving average.
     * Uses fixed-point arithmetic.
     * 
     * @param current Current sample (fixed-point)
     * @param previous Previous filtered value (fixed-point)
     * @param alpha Smoothing factor (0 to FIXED_POINT_SCALE)
     * @return Filtered value
     */
    public static int lowPassFilter(int current, int previous, int alpha) {
        // result = alpha * current + (1 - alpha) * previous
        long result = ((long) alpha * current + (long) (FIXED_POINT_SCALE - alpha) * previous) 
                      >> FIXED_POINT_BITS;
        return (int) result;
    }
    
    /**
     * Computes "resonance" between two signals using correlation.
     * 
     * @param signal1 First signal
     * @param signal2 Second signal
     * @param length Length to compare
     * @return Correlation coefficient in fixed-point
     */
    public static int computeResonance(byte[] signal1, byte[] signal2, int length) {
        long sum1 = 0, sum2 = 0;
        long sumSq1 = 0, sumSq2 = 0;
        long sumProduct = 0;
        
        for (int i = 0; i < length; i++) {
            int s1 = signal1[i] & 0xFF;
            int s2 = signal2[i] & 0xFF;
            
            sum1 += s1;
            sum2 += s2;
            sumSq1 += s1 * s1;
            sumSq2 += s2 * s2;
            sumProduct += s1 * s2;
        }
        
        // Pearson correlation coefficient
        long n = length;
        long numerator = n * sumProduct - sum1 * sum2;
        long denom1 = n * sumSq1 - sum1 * sum1;
        long denom2 = n * sumSq2 - sum2 * sum2;
        
        if (denom1 <= 0 || denom2 <= 0) return 0;
        
        // Approximate sqrt using Newton's method
        long denomProduct = denom1 * denom2;
        long sqrtDenom = fastSqrt64(denomProduct);
        
        if (sqrtDenom == 0) return 0;
        
        return (int) ((numerator * FIXED_POINT_SCALE) / sqrtDenom);
    }

    // ========== Utility Functions ==========
    
    /**
     * Clamps value to signed 16-bit range.
     * 
     * @param value Input value
     * @return Value clamped to -32768 to 32767
     */
    public static int clampShort(int value) {
        return LowLevelAsm.asmClampShort(value);
    }
    
    /**
     * Fast 64-bit integer square root using Newton's method.
     * 
     * @param n Input value (must be non-negative)
     * @return Floor of square root
     */
    public static long fastSqrt64(long n) {
        if (n < 0) throw new IllegalArgumentException("Cannot compute sqrt of negative number");
        if (n == 0) return 0;
        
        // Initial guess using highest bit position
        long x = 1L << ((63 - LowLevelAsm.asmLeadingZeros64(n)) / 2 + 1);
        
        // Newton's iteration
        while (true) {
            long x1 = (x + n / x) >> 1;
            if (x1 >= x) return x;
            x = x1;
        }
    }
    
    /**
     * Branchless min for integers.
     * 
     * @param a First value
     * @param b Second value
     * @return Minimum of a and b
     */
    public static int branchlessMin(int a, int b) {
        int diff = a - b;
        return b + (diff & (diff >> 31));
    }
    
    /**
     * Branchless max for integers.
     * 
     * @param a First value
     * @param b Second value
     * @return Maximum of a and b
     */
    public static int branchlessMax(int a, int b) {
        int diff = a - b;
        return a - (diff & (diff >> 31));
    }
    
    /**
     * Branchless absolute value.
     * 
     * @param x Input value
     * @return Absolute value of x
     */
    public static int branchlessAbs(int x) {
        int mask = x >> 31;
        return (x + mask) ^ mask;
    }
    
    /**
     * Branchless sign function.
     * 
     * @param x Input value
     * @return -1 if x < 0, 0 if x == 0, 1 if x > 0
     */
    public static int branchlessSign(int x) {
        return (x >> 31) | ((-x) >>> 31);
    }
    
    /**
     * Interleaves bits of two 16-bit values (Morton code / Z-order).
     * Useful for spatial indexing and cache-friendly access patterns.
     * 
     * @param x X coordinate (16 bits)
     * @param y Y coordinate (16 bits)
     * @return Interleaved 32-bit Morton code
     */
    public static int interleave16(int x, int y) {
        x &= 0xFFFF;
        y &= 0xFFFF;

        x = (x | (x << 8)) & 0x00FF00FF;
        x = (x | (x << 4)) & 0x0F0F0F0F;
        x = (x | (x << 2)) & 0x33333333;
        x = (x | (x << 1)) & 0x55555555;
        
        y = (y | (y << 8)) & 0x00FF00FF;
        y = (y | (y << 4)) & 0x0F0F0F0F;
        y = (y | (y << 2)) & 0x33333333;
        y = (y | (y << 1)) & 0x55555555;
        
        return x | (y << 1);
    }
    
    /**
     * Deinterleaves Morton code back to X coordinate.
     * 
     * @param morton Morton code
     * @return X coordinate
     */
    public static int deinterleaveX(int morton) {
        int x = morton & 0x55555555;
        x = (x | (x >> 1)) & 0x33333333;
        x = (x | (x >> 2)) & 0x0F0F0F0F;
        x = (x | (x >> 4)) & 0x00FF00FF;
        x = (x | (x >> 8)) & 0x0000FFFF;
        return x;
    }
    
    /**
     * Deinterleaves Morton code back to Y coordinate.
     * 
     * @param morton Morton code
     * @return Y coordinate
     */
    public static int deinterleaveY(int morton) {
        return deinterleaveX(morton >> 1);
    }
    
    /**
     * Rotates bits left by specified amount.
     * 
     * @param value Input value
     * @param bits Number of bits to rotate (0-31)
     * @return Rotated value
     */
    public static int rotateLeft(int value, int bits) {
        return Integer.rotateLeft(value, bits);
    }
    
    /**
     * Rotates bits right by specified amount.
     * 
     * @param value Input value
     * @param bits Number of bits to rotate (0-31)
     * @return Rotated value
     */
    public static int rotateRight(int value, int bits) {
        return Integer.rotateRight(value, bits);
    }
    
    /**
     * Reverses bits in a 32-bit integer.
     * 
     * @param value Input value
     * @return Value with bits reversed
     */
    public static int reverseBits(int value) {
        value = ((value & 0xFFFF0000) >>> 16) | ((value & 0x0000FFFF) << 16);
        value = ((value & 0xFF00FF00) >>> 8) | ((value & 0x00FF00FF) << 8);
        value = ((value & 0xF0F0F0F0) >>> 4) | ((value & 0x0F0F0F0F) << 4);
        value = ((value & 0xCCCCCCCC) >>> 2) | ((value & 0x33333333) << 2);
        value = ((value & 0xAAAAAAAA) >>> 1) | ((value & 0x55555555) << 1);
        return value;
    }
    
    /**
     * Counts leading zeros in 32-bit integer (branchless).
     * 
     * @param x Input value
     * @return Number of leading zero bits (0-32)
     */
    public static int countLeadingZeros(int x) {
        return LowLevelAsm.asmLeadingZeros32(x);
    }
    
    /**
     * Counts trailing zeros in 32-bit integer.
     * 
     * @param x Input value
     * @return Number of trailing zero bits (0-32)
     */
    public static int countTrailingZeros(int x) {
        return LowLevelAsm.asmTrailingZeros32(x);
    }
    
    // ========== Advanced Bitwise Operations ==========
    
    /**
     * Parallel bit deposit (PDEP-like operation).
     * Deposits bits from source according to mask.
     * 
     * @param source Source bits
     * @param mask Bit mask
     * @return Deposited result
     */
    public static int parallelBitDeposit(int source, int mask) {
        int result = 0;

        for (int sourceBits = source; mask != 0; sourceBits >>>= 1) {
            int lowestMaskBit = mask & -mask;
            if ((sourceBits & 1) != 0) {
                result |= lowestMaskBit;
            }
            mask ^= lowestMaskBit;
        }

        return result;
    }
    
    /**
     * Parallel bit extract (PEXT-like operation).
     * Extracts bits according to mask into contiguous result.
     * 
     * @param source Source bits
     * @param mask Bit mask
     * @return Extracted result
     */
    public static int parallelBitExtract(int source, int mask) {
        int result = 0;
        int resultBit = 0;

        while (mask != 0) {
            int lowestMaskBit = mask & -mask;
            if ((source & lowestMaskBit) != 0) {
                result |= (1 << resultBit);
            }
            resultBit++;
            mask ^= lowestMaskBit;
        }

        return result;
    }
    
    /**
     * Computes parity of all bits (even/odd).
     * 
     * @param x Input value
     * @return 1 if odd number of 1-bits, 0 if even
     */
    public static int computeParity(int x) {
        x ^= x >>> 16;
        x ^= x >>> 8;
        x ^= x >>> 4;
        x ^= x >>> 2;
        x ^= x >>> 1;
        return x & 1;
    }
    
    /**
     * Finds next power of 2 greater than or equal to x.
     * 
     * @param x Input value
     * @return Next power of 2
     */
    public static int nextPowerOf2(int x) {
        if (x <= 0) return 1;
        x--;
        x |= x >>> 1;
        x |= x >>> 2;
        x |= x >>> 4;
        x |= x >>> 8;
        x |= x >>> 16;
        return x + 1;
    }
    
    /**
     * Checks if x is a power of 2.
     * 
     * @param x Input value
     * @return true if power of 2
     */
    public static boolean isPowerOf2(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }
    
    /**
     * Fast log2 approximation (integer).
     * 
     * @param x Input value (must be positive)
     * @return Floor(log2(x))
     */
    public static int fastLog2(int x) {
        if (x <= 0) return Integer.MIN_VALUE;
        return 31 - LowLevelAsm.asmLeadingZeros32(x);
    }
    
    /**
     * Branchless sign function.
     * 
     * @param x Input value
     * @return -1 if x < 0, 0 if x == 0, 1 if x > 0
     */
    public static int sign(int x) {
        return (x >> 31) | (-x >>> 31);
    }
    
    /**
     * Swaps two values without temporary variable.
     * Modifies array in-place at indices i and j.
     * 
     * @param arr Array
     * @param i First index
     * @param j Second index
     */
    public static void xorSwap(int[] arr, int i, int j) {
        if (i != j && arr != null && i >= 0 && j >= 0 && i < arr.length && j < arr.length) {
            arr[i] ^= arr[j];
            arr[j] ^= arr[i];
            arr[i] ^= arr[j];
        }
    }
    
    /**
     * Gray code encoding (binary to Gray).
     * 
     * @param binary Binary value
     * @return Gray code
     */
    public static int binaryToGray(int binary) {
        return binary ^ (binary >>> 1);
    }
    
    /**
     * Gray code decoding (Gray to binary).
     * 
     * @param gray Gray code value
     * @return Binary value
     */
    public static int grayToBinary(int gray) {
        int binary = gray;
        for (int shift = 1; shift < 32; shift <<= 1) {
            binary ^= binary >>> shift;
        }
        return binary;
    }
    
    /**
     * Bit interleaving for 3D Morton codes (Z-order curve).
     * 
     * @param x X coordinate (10 bits)
     * @param y Y coordinate (10 bits)
     * @param z Z coordinate (10 bits)
     * @return 30-bit Morton code
     */
    public static int interleave3D(int x, int y, int z) {
        x &= 0x3FF;
        y &= 0x3FF;
        z &= 0x3FF;

        x = (x | (x << 16)) & 0x030000FF;
        x = (x | (x << 8)) & 0x0300F00F;
        x = (x | (x << 4)) & 0x030C30C3;
        x = (x | (x << 2)) & 0x09249249;
        
        y = (y | (y << 16)) & 0x030000FF;
        y = (y | (y << 8)) & 0x0300F00F;
        y = (y | (y << 4)) & 0x030C30C3;
        y = (y | (y << 2)) & 0x09249249;
        
        z = (z | (z << 16)) & 0x030000FF;
        z = (z | (z << 8)) & 0x0300F00F;
        z = (z | (z << 4)) & 0x030C30C3;
        z = (z | (z << 2)) & 0x09249249;
        
        return x | (y << 1) | (z << 2);
    }
    
    /**
     * Fast multiplication by 10 using shifts and adds.
     * 
     * @param x Input value
     * @return x * 10
     */
    public static int multiplyBy10(int x) {
        return (x << 3) + (x << 1); // x*8 + x*2 = x*10
    }
    
    /**
     * Fast division by 10 using multiplication and shift.
     * Works for non-negative integers up to ~2^31/10.
     * 
     * @param x Input value (non-negative)
     * @return x / 10
     */
    public static int divideBy10(int x) {
        // Magic number: ceil(2^35 / 10)
        long q = ((x * 0xCCCCCCCDL) >>> 35);
        return (int) q;
    }
    
    /**
     * Computes Hamming distance between two integers.
     * Number of bit positions where they differ.
     * 
     * @param x First value
     * @param y Second value
     * @return Hamming distance
     */
    public static int hammingDistance(int x, int y) {
        return NativeFastPath.popcount32(x ^ y);
    }
    
    /**
     * Fast absolute value without branching.
     * 
     * @param x Input value
     * @return |x|
     */
    public static int fastAbs(int x) {
        int mask = x >> 31;
        return (x + mask) ^ mask;
    }
    
    /**
     * Branchless conditional move.
     * Returns a if condition is true (non-zero), b otherwise.
     * 
     * @param condition Condition (0 = false, non-zero = true)
     * @param a Value if true
     * @param b Value if false
     * @return Selected value
     */
    public static int conditionalMove(int condition, int a, int b) {
        int mask = -(condition != 0 ? 1 : 0);
        return (mask & a) | (~mask & b);
    }
}
