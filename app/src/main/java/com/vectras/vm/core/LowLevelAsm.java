package com.vectras.vm.core;

/**
 * LowLevelAsm: low-level helpers with ASM-style naming and flat-array access.
 *
 * <p>Designed to minimize GC and overhead by operating on primitive arrays
 * with explicit offsets, avoiding object allocations and named wrappers.</p>
 */
public final class LowLevelAsm {

    // ========== Fixed-point constants ==========
    public static final int FIXED_POINT_BITS = 16;
    public static final int FIXED_POINT_SCALE = 1 << FIXED_POINT_BITS;
    public static final int FIXED_PI = 205887;
    public static final int FIXED_TWO_PI = 411774;

    private static final int SINE_TABLE_SIZE = 256;
    private static final int[] DEBRUIJN_TZ_INDEX_32 = new int[] {
        0, 1, 28, 2, 29, 14, 24, 3,
        30, 22, 20, 15, 25, 17, 4, 8,
        31, 27, 13, 23, 21, 19, 16, 7,
        26, 12, 18, 6, 11, 5, 10, 9
    };
    private static final short[] SINE_TABLE = new short[] {
        0, 201, 402, 603, 804, 1005, 1206, 1407, 1608, 1809, 2009, 2210, 2410, 2611, 2811, 3012,
        3212, 3412, 3612, 3811, 4011, 4210, 4410, 4609, 4808, 5007, 5205, 5404, 5602, 5800, 5998, 6195,
        6393, 6590, 6786, 6983, 7179, 7375, 7571, 7767, 7962, 8157, 8351, 8545, 8739, 8933, 9126, 9319,
        9512, 9704, 9896, 10087, 10278, 10469, 10659, 10849, 11039, 11228, 11417, 11605, 11793, 11980, 12167, 12353,
        12539, 12725, 12910, 13094, 13279, 13462, 13645, 13828, 14010, 14191, 14372, 14553, 14732, 14912, 15090, 15269,
        15446, 15623, 15800, 15976, 16151, 16325, 16499, 16673, 16846, 17018, 17189, 17360, 17530, 17700, 17869, 18037,
        18204, 18371, 18537, 18703, 18868, 19032, 19195, 19357, 19519, 19680, 19841, 20000, 20159, 20317, 20475, 20631,
        20787, 20942, 21096, 21250, 21403, 21554, 21705, 21856, 22005, 22154, 22301, 22448, 22594, 22739, 22884, 23027,
        23170, 23311, 23452, 23592, 23731, 23870, 24007, 24143, 24279, 24413, 24547, 24680, 24811, 24942, 25072, 25201,
        25329, 25456, 25582, 25708, 25832, 25955, 26077, 26198, 26319, 26438, 26556, 26674, 26790, 26905, 27019, 27133,
        27245, 27356, 27466, 27575, 27683, 27790, 27896, 28001, 28105, 28208, 28310, 28411, 28510, 28609, 28706, 28803,
        28898, 28992, 29085, 29177, 29268, 29358, 29447, 29534, 29621, 29706, 29791, 29874, 29956, 30037, 30117, 30195,
        30273, 30349, 30424, 30498, 30571, 30643, 30714, 30783, 30852, 30919, 30985, 31050, 31113, 31176, 31237, 31297,
        31356, 31414, 31470, 31526, 31580, 31633, 31685, 31736, 31785, 31833, 31880, 31926, 31971, 32014, 32057, 32098,
        32137, 32176, 32213, 32250, 32285, 32318, 32351, 32382, 32412, 32441, 32469, 32495, 32521, 32545, 32567, 32589,
        32609, 32628, 32646, 32663, 32678, 32692, 32705, 32717, 32728, 32737, 32745, 32752, 32757, 32761, 32765, 32766
    };
    private static final byte[] LOG2_TABLE = new byte[] {
        0, 0, 16, 25, 32, 37, 41, 45, 48, 51, 53, 55, 57, 59, 61, 63,
        64, 65, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 79,
        80, 81, 81, 82, 83, 83, 84, 85, 85, 86, 86, 87, 87, 88, 88, 89,
        89, 90, 90, 91, 91, 92, 92, 93, 93, 93, 94, 94, 95, 95, 95, 96,
        96, 96, 97, 97, 97, 98, 98, 98, 99, 99, 99, 100, 100, 100, 101, 101,
        101, 101, 102, 102, 102, 103, 103, 103, 103, 104, 104, 104, 104, 105, 105, 105,
        105, 106, 106, 106, 106, 107, 107, 107, 107, 107, 108, 108, 108, 108, 109, 109,
        109, 109, 109, 110, 110, 110, 110, 110, 111, 111, 111, 111, 111, 111, 112, 112,
        112, 112, 112, 113, 113, 113, 113, 113, 113, 114, 114, 114, 114, 114, 114, 115,
        115, 115, 115, 115, 115, 116, 116, 116, 116, 116, 116, 116, 117, 117, 117, 117,
        117, 117, 117, 118, 118, 118, 118, 118, 118, 118, 119, 119, 119, 119, 119, 119,
        119, 119, 120, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 121,
        121, 121, 122, 122, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123,
        123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125,
        125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128
    };

    private LowLevelAsm() {
        throw new AssertionError("LowLevelAsm is a utility class and cannot be instantiated");
    }

    // ========== Vec2 packed ops ==========

    public static int asmVec2Pack(int x, int y) {
        return ((y & 0xFFFF) << 16) | (x & 0xFFFF);
    }

    public static int asmVec2X(int vec) {
        return (short) (vec & 0xFFFF);
    }

    public static int asmVec2Y(int vec) {
        return (short) (vec >>> 16);
    }

    public static int asmVec2AddSat(int a, int b) {
        int ax = asmVec2X(a);
        int ay = asmVec2Y(a);
        int bx = asmVec2X(b);
        int by = asmVec2Y(b);
        return asmVec2Pack(asmClampShort(ax + bx), asmClampShort(ay + by));
    }

    public static int asmVec2Dot(int a, int b) {
        int ax = asmVec2X(a);
        int ay = asmVec2Y(a);
        int bx = asmVec2X(b);
        int by = asmVec2Y(b);
        return ax * bx + ay * by;
    }

    public static int asmVec2Mag2(int vec) {
        return asmVec2Dot(vec, vec);
    }

    // ========== Matrix ops with offsets ==========

    public static void asmMat4Mul(short[] m, int mOffset, short[] v, int vOffset, int[] out, int outOffset) {
        final int v0 = v[vOffset];
        final int v1 = v[vOffset + 1];
        final int v2 = v[vOffset + 2];
        final int v3 = v[vOffset + 3];

        int row = mOffset;
        int sum0 = m[row] * v0;
        sum0 += m[row + 1] * v1;
        sum0 += m[row + 2] * v2;
        sum0 += m[row + 3] * v3;

        row += 4;
        int sum1 = m[row] * v0;
        sum1 += m[row + 1] * v1;
        sum1 += m[row + 2] * v2;
        sum1 += m[row + 3] * v3;

        row += 4;
        int sum2 = m[row] * v0;
        sum2 += m[row + 1] * v1;
        sum2 += m[row + 2] * v2;
        sum2 += m[row + 3] * v3;

        row += 4;
        int sum3 = m[row] * v0;
        sum3 += m[row + 1] * v1;
        sum3 += m[row + 2] * v2;
        sum3 += m[row + 3] * v3;

        out[outOffset] = sum0 >> FIXED_POINT_BITS;
        out[outOffset + 1] = sum1 >> FIXED_POINT_BITS;
        out[outOffset + 2] = sum2 >> FIXED_POINT_BITS;
        out[outOffset + 3] = sum3 >> FIXED_POINT_BITS;
    }

    public static void asmMat4Transpose(short[] m, int offset) {
        swap(m, offset + 1, offset + 4);
        swap(m, offset + 2, offset + 8);
        swap(m, offset + 3, offset + 12);
        swap(m, offset + 6, offset + 9);
        swap(m, offset + 7, offset + 13);
        swap(m, offset + 11, offset + 14);
    }

    private static void swap(short[] m, int i, int j) {
        short tmp = m[i];
        m[i] = m[j];
        m[j] = tmp;
    }

    // ========== Fast trig/log ==========

    public static int asmFastSineFixed(int angleFixed) {
        int angle = angleFixed % FIXED_TWO_PI;
        if (angle < 0) angle += FIXED_TWO_PI;

        int quadrant = (angle * 4) / FIXED_TWO_PI;
        int tableAngle;
        boolean negate = false;

        switch (quadrant) {
            case 0:
                tableAngle = (angle * SINE_TABLE_SIZE * 2) / FIXED_PI;
                break;
            case 1:
                tableAngle = ((FIXED_PI - angle) * SINE_TABLE_SIZE * 2) / FIXED_PI;
                break;
            case 2:
                tableAngle = ((angle - FIXED_PI) * SINE_TABLE_SIZE * 2) / FIXED_PI;
                negate = true;
                break;
            default:
                tableAngle = ((FIXED_TWO_PI - angle) * SINE_TABLE_SIZE * 2) / FIXED_PI;
                negate = true;
                break;
        }

        if (tableAngle < 0) tableAngle = 0;
        if (tableAngle >= SINE_TABLE_SIZE) tableAngle = SINE_TABLE_SIZE - 1;
        int result = SINE_TABLE[tableAngle];
        return negate ? -result : result;
    }

    public static int asmFastCosineFixed(int angleFixed) {
        return asmFastSineFixed(angleFixed + (FIXED_PI >> 1));
    }

    public static int asmFastLog2Fixed(int x) {
        if (x <= 0) return 0x80000000;

        int msb = 31 - asmLeadingZeros32(x);
        int tableIdx;
        if (msb >= 8) {
            tableIdx = (x >>> (msb - 7)) & 0xFF;
        } else {
            tableIdx = (x << (7 - msb)) & 0xFF;
        }

        int intPart = (msb - FIXED_POINT_BITS) << FIXED_POINT_BITS;
        int fracPart = (LOG2_TABLE[tableIdx] & 0xFF) << (FIXED_POINT_BITS - 4);
        return intPart + fracPart;
    }

    // ========== Small helpers ==========

    public static int asmClampShort(int value) {
        if (value < Short.MIN_VALUE) return Short.MIN_VALUE;
        if (value > Short.MAX_VALUE) return Short.MAX_VALUE;
        return value;
    }

    public static int asmAbs(int x) {
        int mask = x >> 31;
        return (x + mask) ^ mask;
    }

    public static int asmMax(int a, int b) {
        int diff = a - b;
        return a - (diff & (diff >> 31));
    }

    public static int asmMin(int a, int b) {
        int diff = a - b;
        return b + (diff & (diff >> 31));
    }

    public static int asmBitCount(int x) {
        x = x - ((x >>> 1) & 0x55555555);
        x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
        x = (x + (x >>> 4)) & 0x0F0F0F0F;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        return x & 0x3F;
    }

    public static int asmLeadingZeros32(int x) {
        if (x == 0) return 32;
        int n = 1;
        int v = x;
        if ((v >>> 16) == 0) {
            n += 16;
            v <<= 16;
        }
        if ((v >>> 24) == 0) {
            n += 8;
            v <<= 8;
        }
        if ((v >>> 28) == 0) {
            n += 4;
            v <<= 4;
        }
        if ((v >>> 30) == 0) {
            n += 2;
            v <<= 2;
        }
        return n - (v >>> 31);
    }

    public static int asmTrailingZeros32(int x) {
        if (x == 0) return 32;
        int isolated = x & -x;
        int index = (isolated * 0x077CB531) >>> 27;
        return DEBRUIJN_TZ_INDEX_32[index];
    }

    public static int asmLeadingZeros64(long x) {
        if (x == 0L) return 64;
        int high = (int) (x >>> 32);
        if (high != 0) {
            return asmLeadingZeros32(high);
        }
        return 32 + asmLeadingZeros32((int) x);
    }
}
