/*
 * LowLevelBridge.java — LOW LEVEL BRIDGE API
 * ∆RAFAELIA_CORE·Ω
 * package: com.vectras.vm.core
 *
 * JNI interface to lowlevel_bridge.c:
 *   nativeFold32         → rmr_lowlevel_fold32
 *   nativeReduceXor      → rmr_lowlevel_reduce_xor
 *   nativeChecksum32     → rmr_lowlevel_checksum32
 *   nativeCrc32cCompat   → rmr_lowlevel_crc32c_hw
 *   nativeXorChecksumCompat
 */
package com.vectras.vm.core;

public final class LowLevelBridge {

    private static boolean sLoaded = false;

    public static synchronized boolean loadLibrary() {
        if (sLoaded) return true;
        try {
            System.loadLibrary("vectra_core_accel");
            sLoaded = true;
        } catch (UnsatisfiedLinkError ignored) {}
        return sLoaded;
    }

    /* ─── phi fold ─── */
    public static int fold32(int a, int b, int c, int d) {
        if (sLoaded) return nativeFold32(a, b, c, d);
        /* SW fallback */
        return ZiprafEngine.phiFold4(a, b, c, d);
    }

    /* ─── reduce XOR ─── */
    public static int reduceXor(byte[] data, int offset, int length) {
        if (data == null || length == 0) return 0;
        if (sLoaded) return nativeReduceXor(data, offset, length);
        int x = 0;
        for (int i = offset; i < offset + length && i < data.length; i++)
            x ^= data[i] & 0xFF;
        return x;
    }

    /* ─── checksum32 ─── */
    public static int checksum32(byte[] data, int offset, int length, int seed) {
        if (data == null || length == 0) return seed;
        if (sLoaded) return nativeChecksum32(data, offset, length, seed);
        /* SW: simple additive */
        int s = seed;
        for (int i = offset; i < offset + length && i < data.length; i++)
            s = s * ZiprafEngine.PHI32 + (data[i] & 0xFF);
        return s;
    }

    /* ─── CRC32C ─── */
    public static int crc32c(int initial, byte[] data, int offset, int length) {
        if (data == null || length == 0) return initial;
        if (sLoaded) return nativeCrc32cCompat(initial, data, offset, length);
        return ZiprafEngine.computeCrc32cSw(initial, data, offset, length);
    }

    /* ─── Natives ─── */
    public static native int nativeFold32(int a, int b, int c, int d);
    public static native int nativeReduceXor(byte[] data, int offset, int length);
    public static native int nativeChecksum32(byte[] data, int offset, int length, int seed);
    public static native int nativeXorChecksumCompat(byte[] data, int offset, int length);
    public static native int nativeCrc32cCompat(int initial, byte[] data, int offset, int length);

    private LowLevelBridge() {}
}
