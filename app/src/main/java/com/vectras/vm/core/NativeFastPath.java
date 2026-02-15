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
    private static final boolean ARENA_AVAILABLE;

    private static final int HW_CONTRACT_SIGNATURE = 0;
    private static final int HW_CONTRACT_POINTER_BITS = 1;
    private static final int HW_CONTRACT_CACHE_LINE = 2;
    private static final int HW_CONTRACT_PAGE_SIZE = 3;
    private static final int HW_CONTRACT_FEATURES = 4;
    private static final int HW_CONTRACT_SIZE = 5;

    private static final int KERNEL_CONTRACT_SIGNATURE = 0;
    private static final int KERNEL_CONTRACT_POINTER_BITS = 1;
    private static final int KERNEL_CONTRACT_CACHE_LINE = 2;
    private static final int KERNEL_CONTRACT_PAGE_SIZE = 3;
    private static final int KERNEL_CONTRACT_FEATURES = 4;
    private static final int KERNEL_CONTRACT_CPU_CORES = 5;
    private static final int KERNEL_CONTRACT_ARENA_BYTES = 6;
    private static final int KERNEL_CONTRACT_IO_QUANTUM = 7;
    private static final int KERNEL_CONTRACT_SIZE = 8;

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
        ARENA_AVAILABLE = loaded;
        if (loaded) {
            nativeCoreInit(0x56454354);
        }
        BOOT_PROFILE = detectHardwareProfile();
    }

    private NativeFastPath() {
        throw new AssertionError("NativeFastPath is a utility class and cannot be instantiated");
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    public static boolean isArenaAvailable() {
        return ARENA_AVAILABLE;
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

    public static KernelUnitProfile readKernelUnitProfile() {
        if (NATIVE_AVAILABLE) {
            int[] contract = nativeReadKernelUnitContract();
            if (contract != null && contract.length == KERNEL_CONTRACT_SIZE) {
                int signature = contract[KERNEL_CONTRACT_SIGNATURE];
                int pointerBits = normalizePointerBits(contract[KERNEL_CONTRACT_POINTER_BITS], signature);
                int cacheLine = normalizeCacheLine(contract[KERNEL_CONTRACT_CACHE_LINE]);
                int pageBytes = normalizePageSize(contract[KERNEL_CONTRACT_PAGE_SIZE]);
                int featureMask = normalizeFeatureMask(contract[KERNEL_CONTRACT_FEATURES]);
                int cpuCores = contract[KERNEL_CONTRACT_CPU_CORES] <= 0 ? 1 : contract[KERNEL_CONTRACT_CPU_CORES];
                int arenaBytes = contract[KERNEL_CONTRACT_ARENA_BYTES] <= 0 ? 0 : contract[KERNEL_CONTRACT_ARENA_BYTES];
                int ioQuantum = contract[KERNEL_CONTRACT_IO_QUANTUM];
                if (ioQuantum <= 0) {
                    ioQuantum = cacheLine * 64;
                }
                return new KernelUnitProfile(signature, pointerBits, cacheLine, pageBytes, featureMask, cpuCores, arenaBytes, ioQuantum);
            }
        }

        int signature = BOOT_PROFILE.signature;
        int cacheLine = BOOT_PROFILE.cacheLineBytes;
        int pageBytes = BOOT_PROFILE.pageBytes;
        int ioQuantum = cacheLine * 64;
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new KernelUnitProfile(signature, BOOT_PROFILE.pointerBits, cacheLine, pageBytes, BOOT_PROFILE.featureMask, cores, 0, ioQuantum);
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

        // Explicit fallback path when JNI arena acceleration is not available.
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

        // Explicit fallback path when JNI arena acceleration is not available.
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

    public static int allocArena(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Arena size must be > 0");
        }
        if (!ARENA_AVAILABLE) {
            return 0;
        }
        int handle = nativeAllocArena(bytes);
        return handle > 0 ? handle : 0;
    }

    public static boolean freeArena(int handle) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeFreeArena(handle) == 0;
    }

    public static boolean copyArena(int srcHandle, int srcOffset, int dstHandle, int dstOffset, int length) {
        if (srcHandle <= 0 || dstHandle <= 0) {
            throw new IllegalArgumentException("Arena handles must be > 0");
        }
        if (srcOffset < 0 || dstOffset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena copy offsets/length must be >= 0");
        }
        if (length == 0) {
            return true;
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeArenaCopy(srcHandle, srcOffset, dstHandle, dstOffset, length) == 0;
    }

    public static int xorChecksumArena(int handle, int offset, int length) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena checksum offset/length must be >= 0");
        }
        if (length == 0) {
            return 0;
        }
        if (!ARENA_AVAILABLE) {
            return 0;
        }
        return nativeArenaXorChecksum(handle, offset, length);
    }

    public static boolean fillArena(int handle, int offset, int length, int value) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena fill offset/length must be >= 0");
        }
        if ((value & ~0xFF) != 0) {
            throw new IllegalArgumentException("Arena fill value must be in [0, 255]");
        }
        if (length == 0) {
            return true;
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeArenaFill(handle, offset, length, value) == 0;
    }

    public static boolean writeArena(int handle, int offset, byte[] src, int srcOffset, int length) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (src == null) {
            throw new IllegalArgumentException("Source buffer must not be null");
        }
        if (offset < 0 || srcOffset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena write offsets/length must be >= 0");
        }
        if (srcOffset + length > src.length) {
            throw new IllegalArgumentException("Source range exceeds buffer length");
        }
        if (length == 0) {
            return true;
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeArenaWrite(handle, offset, src, srcOffset, length) == 0;
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

    public static int vec2Pack(int x, int y) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Pack(x, y);
        }
        return ((y & 0xFFFF) << 16) | (x & 0xFFFF);
    }

    public static int vec2X(int vec) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2X(vec);
        }
        return (short) (vec & 0xFFFF);
    }

    public static int vec2Y(int vec) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Y(vec);
        }
        return (short) (vec >>> 16);
    }

    public static int vec2AddSat(int a, int b) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2AddSat(a, b);
        }
        int ax = vec2X(a);
        int ay = vec2Y(a);
        int bx = vec2X(b);
        int by = vec2Y(b);
        return vec2Pack(clamp16(ax + bx), clamp16(ay + by));
    }

    public static int vec2Dot(int a, int b) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Dot(a, b);
        }
        int ax = vec2X(a);
        int ay = vec2Y(a);
        int bx = vec2X(b);
        int by = vec2Y(b);
        return ax * bx + ay * by;
    }

    public static int vec2Mag2(int vec) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Mag2(vec);
        }
        return vec2Dot(vec, vec);
    }


    public static int coreIngest(byte[] payload) {
        if (!NATIVE_AVAILABLE || payload == null) {
            return Integer.MIN_VALUE;
        }
        return nativeCoreIngest(payload);
    }

    public static int coreProcess(int a, int b, int mode) {
        if (!NATIVE_AVAILABLE) {
            return Integer.MIN_VALUE;
        }
        return nativeCoreProcess(a, b, mode);
    }

    public static int coreRoute(long cpuCycles, long storageReadBytes, long storageWriteBytes,
                                long inputBytes, long outputBytes,
                                long m00, long m01, long m10, long m11) {
        if (!NATIVE_AVAILABLE) {
            return -1;
        }
        return nativeCoreRoute(cpuCycles, storageReadBytes, storageWriteBytes, inputBytes, outputBytes, m00, m01, m10, m11);
    }

    public static boolean coreVerify(byte[] payload, int expected) {
        if (!NATIVE_AVAILABLE || payload == null) {
            return false;
        }
        return nativeCoreVerify(payload, expected) == 1;
    }

    public static long[] coreAudit() {
        if (!NATIVE_AVAILABLE) {
            return null;
        }
        return nativeCoreAudit();
    }

    public static int[] readUnifiedCapabilities() {
        if (!NATIVE_AVAILABLE) {
            return null;
        }
        return nativeReadUnifiedCapabilities();
    }

    public static void coreShutdown() {
        if (NATIVE_AVAILABLE) {
            nativeCoreShutdown();
        }
    }

    private static int clamp16(int value) {
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return value;
    }

    public static final class KernelUnitProfile {
        public final int signature;
        public final int pointerBits;
        public final int cacheLineBytes;
        public final int pageBytes;
        public final int featureMask;
        public final int cpuCores;
        public final int arenaBytes;
        public final int ioQuantumBytes;

        KernelUnitProfile(int signature, int pointerBits, int cacheLineBytes, int pageBytes,
                          int featureMask, int cpuCores, int arenaBytes, int ioQuantumBytes) {
            this.signature = signature;
            this.pointerBits = pointerBits;
            this.cacheLineBytes = cacheLineBytes;
            this.pageBytes = pageBytes;
            this.featureMask = featureMask;
            this.cpuCores = cpuCores;
            this.arenaBytes = arenaBytes;
            this.ioQuantumBytes = ioQuantumBytes;
        }
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

    private static native int[] nativeReadKernelUnitContract();

    private static native int nativeCopyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length);

    private static native int nativeXorChecksum(byte[] data, int offset, int length);

    private static native int nativePopcount32(int value);

    private static native int nativeByteSwap32(int value);

    private static native int nativeRotateLeft32(int value, int distance);

    private static native int nativeRotateRight32(int value, int distance);

    private static native int nativeVec2Pack(int x, int y);

    private static native int nativeVec2X(int vec);

    private static native int nativeVec2Y(int vec);

    private static native int nativeVec2AddSat(int a, int b);

    private static native int nativeVec2Dot(int a, int b);

    private static native int nativeVec2Mag2(int vec);

    private static native int nativeAllocArena(int bytes);

    private static native int nativeFreeArena(int handle);

    private static native int nativeArenaCopy(int srcHandle, int srcOffset, int dstHandle, int dstOffset, int length);

    private static native int nativeArenaXorChecksum(int handle, int offset, int length);

    private static native int nativeArenaFill(int handle, int offset, int length, int value);

    private static native int nativeArenaWrite(int handle, int offset, byte[] src, int srcOffset, int length);


    private static native int nativeCoreInit(int seed);

    private static native int nativeCoreShutdown();

    private static native int nativeCoreIngest(byte[] payload);

    private static native int nativeCoreProcess(int a, int b, int mode);

    private static native int nativeCoreRoute(long cpuCycles, long storageReadBytes, long storageWriteBytes,
                                              long inputBytes, long outputBytes,
                                              long m00, long m01, long m10, long m11);

    private static native int nativeCoreVerify(byte[] payload, int expected);

    private static native long[] nativeCoreAudit();

    private static native int[] nativeReadUnifiedCapabilities();

    private static native int nativePlatformSignature();

    private static native int nativeFeatureMask();

    private static native int nativePointerBits();

    private static native int nativeCacheLineBytes();

    private static native int nativePageBytes();
}
