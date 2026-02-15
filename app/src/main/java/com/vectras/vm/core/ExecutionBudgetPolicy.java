package com.vectras.vm.core;

import com.vectras.vm.qemu.VmProfile;

import java.util.Locale;

/**
 * Deterministic budget policy for process/thread envelopes, QEMU -smp and executors.
 */
public final class ExecutionBudgetPolicy {

    private ExecutionBudgetPolicy() {
        throw new AssertionError("ExecutionBudgetPolicy is a utility class and cannot be instantiated");
    }

    public static ExecutionBudget resolve(VmProfile profile, int availableProcessors, String architecture) {
        VmProfile vmProfile = profile == null ? VmProfile.BALANCED : profile;
        int hostCores = sanitizeProcessors(availableProcessors);
        int archTier = architectureTier(architecture);

        int processLimit = baseProcessLimit(vmProfile);
        int threadLimit = baseThreadLimit(vmProfile, hostCores, archTier);

        int qemuVcpus = resolveQemuVcpus(vmProfile, hostCores, archTier);
        QemuCpuBudget qemuCpuBudget = new QemuCpuBudget(qemuVcpus, 1, qemuVcpus, 1);

        int executorCorePool = clamp(Math.min(threadLimit, Math.max(1, qemuVcpus / 2 + 1)), 1, threadLimit);
        int executorMaxPool = clamp(Math.max(executorCorePool, Math.min(threadLimit, qemuVcpus + 1)), 1, threadLimit);
        int queueCapacity = resolveQueueCapacity(vmProfile, hostCores, archTier);

        ThreadPoolBudget.RejectionPolicy rejectionPolicy =
                vmProfile == VmProfile.LOW_LATENCY
                        ? ThreadPoolBudget.RejectionPolicy.DISCARD_OLDEST
                        : ThreadPoolBudget.RejectionPolicy.CALLER_RUNS;

        ThreadPoolBudget poolBudget = new ThreadPoolBudget(
                executorCorePool,
                executorMaxPool,
                queueCapacity,
                rejectionPolicy,
                "vm-" + vmProfile.name().toLowerCase(Locale.US) + "-"
        );

        return new ExecutionBudget(processLimit, threadLimit, poolBudget, qemuCpuBudget);
    }

    private static int baseProcessLimit(VmProfile profile) {
        switch (profile) {
            case FAST_BOOT:
                return 32;
            case LOW_LATENCY:
                return 40;
            case THROUGHPUT:
                return 56;
            case BALANCED:
            default:
                return 48;
        }
    }

    private static int baseThreadLimit(VmProfile profile, int hostCores, int archTier) {
        int multiplier;
        switch (profile) {
            case FAST_BOOT:
                multiplier = 4;
                break;
            case LOW_LATENCY:
                multiplier = 3;
                break;
            case THROUGHPUT:
                multiplier = 6;
                break;
            case BALANCED:
            default:
                multiplier = 5;
                break;
        }
        int tierAdjust = (archTier - 1) * 2;
        return clamp((hostCores * multiplier) + tierAdjust, 8, 96);
    }

    private static int resolveQemuVcpus(VmProfile profile, int hostCores, int archTier) {
        int reserve = hostCores <= 2 ? 1 : 2;
        int maxAlloc = Math.max(1, hostCores - reserve);

        int target;
        switch (profile) {
            case FAST_BOOT:
                target = Math.min(2, maxAlloc);
                break;
            case LOW_LATENCY:
                target = Math.min(3, maxAlloc);
                break;
            case THROUGHPUT:
                target = maxAlloc;
                break;
            case BALANCED:
            default:
                target = Math.min(Math.max(2, hostCores - 1), maxAlloc);
                break;
        }

        if (archTier == 0) {
            target = Math.min(target, 2);
        } else if (archTier == 1) {
            target = Math.min(target, 4);
        }

        return clamp(target, 1, 8);
    }

    private static int resolveQueueCapacity(VmProfile profile, int hostCores, int archTier) {
        int base;
        switch (profile) {
            case LOW_LATENCY:
                base = 12;
                break;
            case THROUGHPUT:
                base = 32;
                break;
            case FAST_BOOT:
                base = 8;
                break;
            case BALANCED:
            default:
                base = 20;
                break;
        }
        int capacity = base + (hostCores * 2) + (archTier * 2);
        return clamp(capacity, 8, 64);
    }

    private static int sanitizeProcessors(int availableProcessors) {
        if (availableProcessors <= 0) {
            return 1;
        }
        return availableProcessors;
    }

    private static int architectureTier(String architecture) {
        if (architecture == null) {
            return 0;
        }
        String arch = architecture.trim().toUpperCase();
        if ("X86_64".equals(arch) || "ARM64".equals(arch)) {
            return 2;
        }
        if ("I386".equals(arch) || "PPC".equals(arch)) {
            return 1;
        }
        return 0;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
