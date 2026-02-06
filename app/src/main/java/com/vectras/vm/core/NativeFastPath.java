package com.vectras.vm.core;

/**
 * NativeFastPath: optional JNI acceleration hooks with deterministic Java fallback.
 *
 * <p>Designed for hot loops where JNI+SIMD can be enabled without breaking portability.
 * If native library is unavailable, branch-light Java loops are used.</p>
 */
public final class NativeFastPath {

    private static final int NATIVE_OK_MAGIC = 0x56414343;
    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("vectra_core_accel");
            loaded = (nativeInit() == NATIVE_OK_MAGIC);
        } catch (Throwable ignored) {
            loaded = false;
        }
        NATIVE_AVAILABLE = loaded;
    }

    private NativeFastPath() {
        throw new AssertionError("NativeFastPath is a utility class and cannot be instantiated");
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    public static void copyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
        if (src == null || dst == null || length <= 0) return;
        if (srcOffset < 0 || dstOffset < 0 || srcOffset + length > src.length || dstOffset + length > dst.length) {
            throw new IllegalArgumentException("Invalid copy range");
        }

        if (NATIVE_AVAILABLE && nativeCopyBytes(src, srcOffset, dst, dstOffset, length) == 0) {
            return;
        }

        int i = 0;
        int end = length & ~15;
        while (i < end) {
            dst[dstOffset + i] = src[srcOffset + i];
            dst[dstOffset + i + 1] = src[srcOffset + i + 1];
            dst[dstOffset + i + 2] = src[srcOffset + i + 2];
            dst[dstOffset + i + 3] = src[srcOffset + i + 3];
            dst[dstOffset + i + 4] = src[srcOffset + i + 4];
            dst[dstOffset + i + 5] = src[srcOffset + i + 5];
            dst[dstOffset + i + 6] = src[srcOffset + i + 6];
            dst[dstOffset + i + 7] = src[srcOffset + i + 7];
            dst[dstOffset + i + 8] = src[srcOffset + i + 8];
            dst[dstOffset + i + 9] = src[srcOffset + i + 9];
            dst[dstOffset + i + 10] = src[srcOffset + i + 10];
            dst[dstOffset + i + 11] = src[srcOffset + i + 11];
            dst[dstOffset + i + 12] = src[srcOffset + i + 12];
            dst[dstOffset + i + 13] = src[srcOffset + i + 13];
            dst[dstOffset + i + 14] = src[srcOffset + i + 14];
            dst[dstOffset + i + 15] = src[srcOffset + i + 15];
            i += 16;
        }
        while (i < length) {
            dst[dstOffset + i] = src[srcOffset + i];
            i++;
        }
    }

    public static int xorChecksum(byte[] data, int offset, int length) {
        if (data == null || length <= 0) return 0;
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid checksum range");
        }

        if (NATIVE_AVAILABLE) {
            int value = nativeXorChecksum(data, offset, length);
            if (value != Integer.MIN_VALUE) {
                return value;
            }
        }

        int x = 0;
        int i = 0;
        int end = length & ~7;
        while (i < end) {
            x ^= data[offset + i] & 0xFF;
            x ^= data[offset + i + 1] & 0xFF;
            x ^= data[offset + i + 2] & 0xFF;
            x ^= data[offset + i + 3] & 0xFF;
            x ^= data[offset + i + 4] & 0xFF;
            x ^= data[offset + i + 5] & 0xFF;
            x ^= data[offset + i + 6] & 0xFF;
            x ^= data[offset + i + 7] & 0xFF;
            i += 8;
        }
        while (i < length) {
            x ^= data[offset + i] & 0xFF;
            i++;
        }
        return x;
    }


    public static int popcount32(int x) {
        if (NATIVE_AVAILABLE) {
            int value = nativePopcount32(x);
            if (value >= 0) {
                return value;
            }
        }

        int v = x;
        v = v - ((v >>> 1) & 0x55555555);
        v = (v & 0x33333333) + ((v >>> 2) & 0x33333333);
        v = (v + (v >>> 4)) & 0x0F0F0F0F;
        v = v + (v >>> 8);
        v = v + (v >>> 16);
        return v & 0x3F;
    }

    private static native int nativeInit();

    private static native int nativeCopyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length);

    private static native int nativeXorChecksum(byte[] data, int offset, int length);

    private static native int nativePopcount32(int value);
}
