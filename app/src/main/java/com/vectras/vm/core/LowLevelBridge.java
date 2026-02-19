package com.vectras.vm.core;

import java.lang.ThreadLocal;

public final class LowLevelBridge {
    private static final boolean LOADED;
    private static final ThreadLocal<Boolean> LAST_NATIVE_PATH = new ThreadLocal<Boolean>();

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

    public static boolean wasLastCallNative() {
        Boolean value = LAST_NATIVE_PATH.get();
        return value != null && value.booleanValue();
    }

    private static void markLastCallNative(boolean nativePath) {
        LAST_NATIVE_PATH.set(Boolean.valueOf(nativePath));
    }

    public static int fold32(int a, int b, int c, int d) {
        if (LOADED) {
            try {
                int out = nativeFold32(a, b, c, d);
                markLastCallNative(true);
                return out;
            } catch (Throwable ignored) {
                // immediate fallback to deterministic Java path
            }
        }
        markLastCallNative(false);
        return LowLevelDeterminism.fold32Fallback(a, b, c, d);
    }

    public static int reduceXor(byte[] data, int offset, int length) {
        if (LOADED) {
            try {
                int out = nativeReduceXor(data, offset, length);
                markLastCallNative(true);
                return out;
            } catch (Throwable ignored) {
                // immediate fallback to deterministic Java path
            }
        }
        markLastCallNative(false);
        return LowLevelDeterminism.reduceXorFallback(data, offset, length);
    }

    public static int checksum32(byte[] data, int offset, int length, int seed) {
        if (LOADED) {
            try {
                int out = nativeChecksum32(data, offset, length, seed);
                markLastCallNative(true);
                return out;
            } catch (Throwable ignored) {
                // immediate fallback to deterministic Java path
            }
        }
        markLastCallNative(false);
        return LowLevelDeterminism.checksum32Fallback(data, offset, length, seed);
    }

    public static int xorChecksumCompat(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            markLastCallNative(false);
            return 0;
        }
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid checksum range");
        }
        if (LOADED) {
            try {
                int out = nativeXorChecksumCompat(data, offset, length);
                markLastCallNative(true);
                return out;
            } catch (Throwable ignored) {
                // immediate fallback to deterministic Java path
            }
        }
        markLastCallNative(false);
        return LowLevelDeterminism.xorChecksumCompatFallback(data, offset, length);
    }

    public static int crc32cCompat(int initial, byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            markLastCallNative(false);
            return initial;
        }
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid crc range");
        }
        if (LOADED) {
            try {
                int out = nativeCrc32cCompat(initial, data, offset, length);
                markLastCallNative(true);
                return out;
            } catch (Throwable ignored) {
                // immediate fallback to deterministic Java path
            }
        }
        markLastCallNative(false);
        return LowLevelDeterminism.crc32cCompatFallback(initial, data, offset, length);
    }

    private static native int nativeFold32(int a, int b, int c, int d);

    private static native int nativeReduceXor(byte[] data, int offset, int length);

    private static native int nativeChecksum32(byte[] data, int offset, int length, int seed);

    private static native int nativeXorChecksumCompat(byte[] data, int offset, int length);

    private static native int nativeCrc32cCompat(int initial, byte[] data, int offset, int length);
}
