package com.vectras.vm.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * BareMetalProfile: deterministic hardware capability probe for low-level tuning.
 *
 * <p>Provides branch-light architecture detection and capability flags for five CPU families
 * without external dependencies.</p>
 */
public final class BareMetalProfile {

    public static final int ARCH_UNKNOWN = 0;
    public static final int ARCH_ARM32 = 1;
    public static final int ARCH_ARM64 = 2;
    public static final int ARCH_X86 = 3;
    public static final int ARCH_X86_64 = 4;
    public static final int ARCH_RISCV64 = 5;

    public static final int CAP_ATOMICS = 1;
    public static final int CAP_UNALIGNED_FAST = 1 << 1;
    public static final int CAP_VECTOR_INT = 1 << 2;
    public static final int CAP_VECTOR_FP = 1 << 3;
    public static final int CAP_LITTLE_ENDIAN = 1 << 4;
    public static final int CAP_MULTI_CORE = 1 << 5;
    public static final int CAP_64_BIT = 1 << 6;
    public static final int CAP_SIMD = 1 << 7;
    public static final int CAP_NEON_OR_SSE = CAP_SIMD;
    public static final int CAP_AES = 1 << 8;
    public static final int CAP_CRC32 = 1 << 9;

    private static final String CPU_POSSIBLE_PATH = "/sys/devices/system/cpu/possible";

    private static final Snapshot BOOT = snapshotBoot();

    private BareMetalProfile() {
        throw new AssertionError("BareMetalProfile is a utility class and cannot be instantiated");
    }

    public static int detectArchitecture() {
        return BOOT.arch;
    }

    public static int detectCapabilities() {
        return BOOT.capabilities;
    }

    public static int recommendedWorkBlockBytes() {
        return BOOT.recommendedBlockBytes;
    }

    public static int recommendedParallelism() {
        int cores = BOOT.cores;
        if (cores <= 1) return 1;
        if (cores <= 3) return 2;
        return cores - 1;
    }

    public static long runtimeMemoryClassBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory();
    }

    private static Snapshot snapshotBoot() {
        NativeFastPath.HardwareProfile hw = NativeFastPath.getHardwareProfile();
        int arch = mapArch(hw.signature);
        int cores = detectCoreCountInternal();
        int capabilities = detectCapabilitiesInternal(hw, arch, cores);
        int recommendedBlock = computeRecommendedBlockBytes(arch, cores, capabilities, hw.cacheLineBytes, hw.pageBytes);
        return new Snapshot(arch, cores, capabilities, recommendedBlock);
    }

    private static int mapArch(int signature) {
        int nativeArch = signature & 0xFF00;
        if (nativeArch == NativeFastPath.ARCH_ARM64) return ARCH_ARM64;
        if (nativeArch == NativeFastPath.ARCH_ARM32) return ARCH_ARM32;
        if (nativeArch == NativeFastPath.ARCH_X64) return ARCH_X86_64;
        if (nativeArch == NativeFastPath.ARCH_X86) return ARCH_X86;
        if (nativeArch == NativeFastPath.ARCH_RISCV64) return ARCH_RISCV64;
        return ARCH_UNKNOWN;
    }

    private static int detectCapabilitiesInternal(NativeFastPath.HardwareProfile hw, int arch, int cores) {
        int flags = CAP_ATOMICS;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            flags |= CAP_LITTLE_ENDIAN;
        }
        if (cores > 1) {
            flags |= CAP_MULTI_CORE;
        }

        int nativeFeatures = hw.featureMask;
        if ((nativeFeatures & NativeFastPath.FEATURE_SIMD) != 0) {
            flags |= CAP_SIMD;
        }
        if ((nativeFeatures & NativeFastPath.FEATURE_AES) != 0) {
            flags |= CAP_AES;
        }
        if ((nativeFeatures & NativeFastPath.FEATURE_CRC32) != 0) {
            flags |= CAP_CRC32;
        }

        if (arch == ARCH_ARM64 || arch == ARCH_X86_64 || arch == ARCH_RISCV64 || hw.pointerBits == 64) {
            flags |= CAP_64_BIT | CAP_VECTOR_INT | CAP_VECTOR_FP;
        } else if (arch == ARCH_ARM32 || arch == ARCH_X86) {
            flags |= CAP_VECTOR_INT;
            if (arch == ARCH_ARM32) {
                flags |= CAP_VECTOR_FP;
            }
        }

        if (arch == ARCH_X86 || arch == ARCH_X86_64 || arch == ARCH_ARM64) {
            flags |= CAP_UNALIGNED_FAST;
        }

        return flags;
    }

    private static int computeRecommendedBlockBytes(int arch, int cores, int caps, int cacheLine, int pageBytes) {
        int base = 4096;
        if (arch == ARCH_ARM64 || arch == ARCH_X86_64) {
            base = 16384;
        } else if (arch == ARCH_ARM32 || arch == ARCH_X86) {
            base = 8192;
        } else if (arch == ARCH_RISCV64) {
            base = 12288;
        }

        if (cores >= 8) {
            base <<= 1;
        } else if (cores <= 2) {
            base >>= 1;
        }

        if ((caps & CAP_SIMD) != 0) {
            base += 2048;
        }
        if ((caps & CAP_CRC32) != 0) {
            base += 1024;
        }

        int align = clamp(cacheLine, 32, 256);
        int minPage = clamp(pageBytes, 1024, 65536);

        while (base < minPage) {
            base <<= 1;
        }

        int rem = base & (align - 1);
        if (rem != 0) {
            base += (align - rem);
        }

        return base;
    }

    private static int detectCoreCountInternal() {
        int runtimeCores = Runtime.getRuntime().availableProcessors();
        int sysfsCores = parseCpuRange(CPU_POSSIBLE_PATH);
        if (sysfsCores > runtimeCores) {
            return sysfsCores;
        }
        return runtimeCores;
    }

    private static int parseCpuRange(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            if (line == null) {
                return -1;
            }
            int dash = line.indexOf('-');
            if (dash <= 0 || dash + 1 >= line.length()) {
                return -1;
            }
            int start = Integer.parseInt(line.substring(0, dash).trim());
            int end = Integer.parseInt(line.substring(dash + 1).trim());
            if (start < 0 || end < start) {
                return -1;
            }
            return (end - start) + 1;
        } catch (IOException | NumberFormatException ignored) {
            return -1;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static final class Snapshot {
        final int arch;
        final int cores;
        final int capabilities;
        final int recommendedBlockBytes;

        Snapshot(int arch, int cores, int capabilities, int recommendedBlockBytes) {
            this.arch = arch;
            this.cores = cores;
            this.capabilities = capabilities;
            this.recommendedBlockBytes = recommendedBlockBytes;
        }
    }
}
