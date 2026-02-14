package com.vectras.vm.core;

import java.util.Locale;

/**
 * NativeFastPath: optional JNI acceleration hooks with deterministic Java fallback.
 *
 * <p>Designed for hot loops where JNI+SIMD can be enabled without breaking portability.
 * If native library is unavailable, branch-light Java loops are used.</p>
 */
public final class NativeFastPath {

    private static final int NATIVE_OK_MAGIC = 0x56414343;
    private static final boolean NATIVE_AVAILABLE;

    private static final int HW_CONTRACT_SIGNATURE = 0;
    private static final int HW_CONTRACT_POINTER_BITS = 1;
    private static final int HW_CONTRACT_CACHE_LINE = 2;
    private static final int HW_CONTRACT_PAGE_SIZE = 3;
    private static final int HW_CONTRACT_FEATURES = 4;
    private static final int HW_CONTRACT_SIZE = 5;

    public static final int ARCH_UNKNOWN = 0x0000;
    public static final int ARCH_ARM64 = 0x0100;
    public static final int ARCH_ARM32 = 0x0200;
    public static final int ARCH_X64 = 0x0300;
    public static final int ARCH_X86 = 0x0400;
    public static final int ARCH_RISCV64 = 0x0500;
    public static final int ARCH_RISCV32 = 0x0600;

    public static final int OS_UNKNOWN = 0x0000;
    public static final int OS_ANDROID = 0x0010;
    public static final int OS_LINUX = 0x0020;

    public static final int FEATURE_NEON = 1 << 0;
    public static final int FEATURE_AES = 1 << 1;
    public static final int FEATURE_CRC32 = 1 << 2;
    public static final int FEATURE_POPCNT = 1 << 3;
    public static final int FEATURE_SSE42 = 1 << 4;
    public static final int FEATURE_AVX2 = 1 << 5;
    public static final int FEATURE_SIMD = 1 << 6;

    private static final HardwareProfile BOOT_PROFILE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("vectra_core_accel");
            loaded = (nativeInit() == NATIVE_OK_MAGIC);
        } catch (Throwable ignored) {
            loaded = false;
        }
        NATIVE_AVAILABLE = loaded;
        BOOT_PROFILE = detectHardwareProfile();
    }

    private NativeFastPath() {
        throw new AssertionError("NativeFastPath is a utility class and cannot be instantiated");
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    static HardwareProfile getHardwareProfile() {
        return BOOT_PROFILE;
    }

    public static int getPlatformSignature() {
        return BOOT_PROFILE.signature;
    }

    public static int getPointerBits() {
        return BOOT_PROFILE.pointerBits;
    }

    public static int getNativeCacheLineBytes() {
        return BOOT_PROFILE.cacheLineBytes;
    }

    public static int getNativePageBytes() {
        return BOOT_PROFILE.pageBytes;
    }

    public static int getFeatureMask() {
        return BOOT_PROFILE.featureMask;
    }

    private static HardwareProfile detectHardwareProfile() {
        if (NATIVE_AVAILABLE) {
            int[] contract = nativeReadHardwareContract();
            if (contract != null && contract.length == HW_CONTRACT_SIZE) {
                int signature = contract[HW_CONTRACT_SIGNATURE];
                int pointerBits = normalizePointerBits(contract[HW_CONTRACT_POINTER_BITS], signature);
                int cacheLine = normalizeCacheLine(contract[HW_CONTRACT_CACHE_LINE]);
                int pageBytes = normalizePageSize(contract[HW_CONTRACT_PAGE_SIZE]);
                int featureMask = normalizeFeatureMask(contract[HW_CONTRACT_FEATURES]);
                return new HardwareProfile(signature, pointerBits, cacheLine, pageBytes, featureMask);
            }
        }

        int signature = javaPlatformSignature();
        int pointerBits = normalizePointerBits(0, signature);
        int cacheLine = 64;
        int pageBytes = 4096;
        int featureMask = normalizeFeatureMask(javaFeatureMask(signature));
        return new HardwareProfile(signature, pointerBits, cacheLine, pageBytes, featureMask);
    }

    private static int javaPlatformSignature() {
        int arch = ARCH_UNKNOWN;
        String abi = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (abi.contains("aarch64") || abi.contains("arm64")) {
            arch = ARCH_ARM64;
        } else if (abi.startsWith("arm")) {
            arch = ARCH_ARM32;
        } else if (abi.contains("x86_64") || abi.contains("amd64")) {
            arch = ARCH_X64;
        } else if (abi.contains("x86")) {
            arch = ARCH_X86;
        } else if (abi.contains("riscv64")) {
            arch = ARCH_RISCV64;
        } else if (abi.contains("riscv32")) {
            arch = ARCH_RISCV32;
        }

        int os = OS_UNKNOWN;
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("android")) {
            os = OS_ANDROID;
        } else if (osName.contains("linux")) {
            os = OS_LINUX;
        }

        return arch | os;
    }

    private static int javaFeatureMask(int signature) {
        int features = 0;
        int arch = signature & 0xFF00;

        if (arch == ARCH_ARM64 || arch == ARCH_ARM32) {
            features |= FEATURE_NEON;
            features |= FEATURE_POPCNT;
        } else if (arch == ARCH_X64 || arch == ARCH_X86) {
            features |= FEATURE_POPCNT;
        }
        return features;
    }

    private static int normalizePointerBits(int pointerBits, int signature) {
        if (pointerBits == 32 || pointerBits == 64) {
            return pointerBits;
        }

        int arch = signature & 0xFF00;
        if (arch == ARCH_ARM64 || arch == ARCH_X64 || arch == ARCH_RISCV64) {
            return 64;
        }
        if (arch == ARCH_ARM32 || arch == ARCH_X86 || arch == ARCH_RISCV32) {
            return 32;
        }
        String model = System.getProperty("sun.arch.data.model", "");
        if ("64".equals(model)) {
            return 64;
        }
        return 32;
    }

    private static int normalizeCacheLine(int line) {
        if (line < 32) {
            return 32;
        }
        if (line > 256) {
            return 256;
        }
        return line;
    }

    private static int normalizePageSize(int page) {
        if (page < 1024) {
            return 1024;
        }
        if (page > 65536) {
            return 65536;
        }
        return page;
    }

    private static int normalizeFeatureMask(int features) {
        int mask = features;
        if ((mask & (FEATURE_NEON | FEATURE_SSE42 | FEATURE_AVX2)) != 0) {
            mask |= FEATURE_SIMD;
        }
        return mask;
    }

    public static void copyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
        if (src == null || dst == null || length <= 0) return;
        if (srcOffset < 0 || dstOffset < 0 || srcOffset + length > src.length || dstOffset + length > dst.length) {
            throw new IllegalArgumentException("Invalid copy range");
        }

        if (NATIVE_AVAILABLE && nativeCopyBytes(src, srcOffset, dst, dstOffset, length) == 0) {
            return;
        }

        if (src == dst && dstOffset > srcOffset && dstOffset < srcOffset + length) {
            for (int i = length - 1; i >= 0; i--) {
                dst[dstOffset + i] = src[srcOffset + i];
            }
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


    public static int byteSwap32(int x) {
        if (NATIVE_AVAILABLE) {
            return nativeByteSwap32(x);
        }
        return (x >>> 24)
                | ((x >>> 8) & 0x0000FF00)
                | ((x << 8) & 0x00FF0000)
                | (x << 24);
    }

    public static int rotateLeft32(int x, int distance) {
        if (NATIVE_AVAILABLE) {
            return nativeRotateLeft32(x, distance);
        }
        int d = distance & 31;
        return (x << d) | (x >>> ((32 - d) & 31));
    }

    public static int rotateRight32(int x, int distance) {
        if (NATIVE_AVAILABLE) {
            return nativeRotateRight32(x, distance);
        }
        int d = distance & 31;
        return (x >>> d) | (x << ((32 - d) & 31));
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

    static final class HardwareProfile {
        final int signature;
        final int pointerBits;
        final int cacheLineBytes;
        final int pageBytes;
        final int featureMask;

        HardwareProfile(int signature, int pointerBits, int cacheLineBytes, int pageBytes, int featureMask) {
            this.signature = signature;
            this.pointerBits = pointerBits;
            this.cacheLineBytes = cacheLineBytes;
            this.pageBytes = pageBytes;
            this.featureMask = featureMask;
        }
    }

    private static native int nativeInit();

    private static native int[] nativeReadHardwareContract();

    private static native int nativeCopyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length);

    private static native int nativeXorChecksum(byte[] data, int offset, int length);

    private static native int nativePopcount32(int value);

    private static native int nativeByteSwap32(int value);

    private static native int nativeRotateLeft32(int value, int distance);

    private static native int nativeRotateRight32(int value, int distance);

    private static native int nativePlatformSignature();

    private static native int nativeFeatureMask();

    private static native int nativePointerBits();

    private static native int nativeCacheLineBytes();

    private static native int nativePageBytes();
}
