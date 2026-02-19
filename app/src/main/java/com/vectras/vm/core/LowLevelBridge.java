package com.vectras.vm.core;

public final class LowLevelBridge {
    private static final boolean LOADED;

    static {
        boolean ok;
        try {
            System.loadLibrary("vectra_core_accel");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        LOADED = ok;
    }

    private LowLevelBridge() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static int fold32(int a, int b, int c, int d) {
        if (!LOADED) {
            return LowLevelDeterminism.fold32Fallback(a, b, c, d);
        }
        return nativeFold32(a, b, c, d);
    }

    public static int reduceXor(byte[] data, int offset, int length) {
        if (!LOADED) {
            return LowLevelDeterminism.reduceXorFallback(data, offset, length);
        }
        return nativeReduceXor(data, offset, length);
    }

    public static int checksum32(byte[] data, int offset, int length, int seed) {
        if (!LOADED) {
            return LowLevelDeterminism.checksum32Fallback(data, offset, length, seed);
        }
        return nativeChecksum32(data, offset, length, seed);
    }

    private static native int nativeFold32(int a, int b, int c, int d);

    private static native int nativeReduceXor(byte[] data, int offset, int length);

    private static native int nativeChecksum32(byte[] data, int offset, int length, int seed);
}
