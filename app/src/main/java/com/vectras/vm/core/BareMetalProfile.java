package com.vectras.vm.core;

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

    private BareMetalProfile() {
        throw new AssertionError("BareMetalProfile is a utility class and cannot be instantiated");
    }

    public static int detectArchitecture() {
        String arch = normalizedArch();
        if (contains(arch, "aarch64") || contains(arch, "arm64")) {
            return ARCH_ARM64;
        }
        if (contains(arch, "arm") || contains(arch, "armeabi")) {
            return ARCH_ARM32;
        }
        if (contains(arch, "x86_64") || contains(arch, "amd64")) {
            return ARCH_X86_64;
        }
        if (contains(arch, "x86") || contains(arch, "i386") || contains(arch, "i686")) {
            return ARCH_X86;
        }
        if (contains(arch, "riscv64")) {
            return ARCH_RISCV64;
        }
        return ARCH_UNKNOWN;
    }

    public static int detectCapabilities() {
        int arch = detectArchitecture();
        int flags = CAP_ATOMICS;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            flags |= CAP_LITTLE_ENDIAN;
        }
        if (Runtime.getRuntime().availableProcessors() > 1) {
            flags |= CAP_MULTI_CORE;
        }

        if (arch == ARCH_ARM64 || arch == ARCH_X86_64 || arch == ARCH_RISCV64) {
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

    public static int recommendedWorkBlockBytes() {
        int arch = detectArchitecture();
        int cores = Runtime.getRuntime().availableProcessors();

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

        return base;
    }

    public static int recommendedParallelism() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores <= 1) return 1;
        if (cores <= 3) return 2;
        return cores - 1;
    }

    public static long runtimeMemoryClassBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory();
    }

    private static String normalizedArch() {
        String arch = System.getProperty("os.arch");
        if (arch == null) return "";
        return arch.toLowerCase();
    }

    private static boolean contains(String text, String key) {
        return text.indexOf(key) >= 0;
    }
}
