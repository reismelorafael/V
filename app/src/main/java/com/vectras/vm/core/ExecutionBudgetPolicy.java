package com.vectras.vm.core;

import com.vectras.vm.qemu.VmProfile;

/**
 * Resolve orçamento determinístico para execução da VM e pools auxiliares.
 */
public final class ExecutionBudgetPolicy {

    private static final int MIN_CPU = 1;
    private static final int MAX_CPU = 64;

    private ExecutionBudgetPolicy() {
        throw new AssertionError("ExecutionBudgetPolicy is a utility class and cannot be instantiated");
    }

    public static ExecutionBudget resolve(VmProfile profile, int availableProcessors, String architecture) {
        VmProfile safeProfile = profile == null ? VmProfile.BALANCED : profile;
        String arch = architecture == null ? "UNKNOWN" : architecture;
        int normalizedCpu = clamp(availableProcessors, MIN_CPU, MAX_CPU);

        boolean lowCapacity = normalizedCpu <= 2;
        boolean legacyArch = "PPC".equals(arch) || "I386".equals(arch);
        boolean modernArch = "X86_64".equals(arch) || "ARM64".equals(arch);

        int processLimit = resolveProcessLimit(safeProfile, normalizedCpu, lowCapacity, legacyArch);
        int threadLimit = resolveThreadLimit(safeProfile, normalizedCpu, lowCapacity, modernArch);

        QemuCpuBudget qemuCpuBudget = resolveQemuCpuBudget(safeProfile, normalizedCpu, arch, lowCapacity);
        ThreadPoolBudget threadPoolBudget = resolveThreadPoolBudget(safeProfile, normalizedCpu, lowCapacity);

        return new ExecutionBudget(processLimit, threadLimit, qemuCpuBudget, threadPoolBudget);
    }

    private static int resolveProcessLimit(VmProfile profile, int cpus, boolean lowCapacity, boolean legacyArch) {
        if (lowCapacity) {
            return 2;
        }

        int budget;
        switch (profile) {
            case FAST_BOOT:
                budget = 3;
                break;
            case LOW_LATENCY:
                budget = Math.max(3, cpus / 2);
                break;
            case THROUGHPUT:
                budget = Math.max(4, (cpus / 2) + 1);
                break;
            case BALANCED:
            default:
                budget = Math.max(3, (cpus / 2));
                break;
        }

        if (legacyArch) {
            budget = Math.max(2, budget - 1);
        }
        return clamp(budget, 2, 16);
    }

    private static int resolveThreadLimit(VmProfile profile, int cpus, boolean lowCapacity, boolean modernArch) {
        if (lowCapacity) {
            return 6;
        }

        int budget;
        switch (profile) {
            case FAST_BOOT:
                budget = cpus + 1;
                break;
            case LOW_LATENCY:
                budget = (cpus * 2) + 2;
                break;
            case THROUGHPUT:
                budget = (cpus * 2) + 4;
                break;
            case BALANCED:
            default:
                budget = (cpus * 2);
                break;
        }

        if (!modernArch) {
            budget -= 1;
        }
        return clamp(budget, 6, 64);
    }

    private static QemuCpuBudget resolveQemuCpuBudget(VmProfile profile, int cpus, String arch, boolean lowCapacity) {
        if (lowCapacity) {
            return new QemuCpuBudget(1, 1, 1, 1);
        }

        int maxVcpus;
        switch (profile) {
            case FAST_BOOT:
                maxVcpus = Math.min(2, cpus);
                break;
            case LOW_LATENCY:
                maxVcpus = Math.min(Math.max(2, cpus - 1), 4);
                break;
            case THROUGHPUT:
                maxVcpus = Math.min(Math.max(2, cpus - 1), 8);
                break;
            case BALANCED:
            default:
                maxVcpus = Math.min(Math.max(2, cpus - 1), 6);
                break;
        }

        int sockets = "PPC".equals(arch) ? 1 : 1;
        int cores = Math.max(1, maxVcpus / sockets);
        int threads = 1;

        return new QemuCpuBudget(sockets, cores, threads, maxVcpus);
    }

    private static ThreadPoolBudget resolveThreadPoolBudget(VmProfile profile, int cpus, boolean lowCapacity) {
        if (lowCapacity) {
            return new ThreadPoolBudget(
                    1,
                    16,
                    ThreadPoolBudget.RejectionPolicy.CALLER_RUNS,
                    "vectras-lowcap"
            );
        }

        int poolSize;
        int queueCapacity;
        ThreadPoolBudget.RejectionPolicy rejectionPolicy;
        String threadPrefix;

        switch (profile) {
            case FAST_BOOT:
                poolSize = Math.min(2, cpus);
                queueCapacity = 24;
                rejectionPolicy = ThreadPoolBudget.RejectionPolicy.DISCARD_OLDEST;
                threadPrefix = "vectras-fastboot";
                break;
            case LOW_LATENCY:
                poolSize = Math.min(4, Math.max(2, cpus - 1));
                queueCapacity = 32;
                rejectionPolicy = ThreadPoolBudget.RejectionPolicy.CALLER_RUNS;
                threadPrefix = "vectras-latency";
                break;
            case THROUGHPUT:
                poolSize = Math.min(6, Math.max(2, cpus));
                queueCapacity = 96;
                rejectionPolicy = ThreadPoolBudget.RejectionPolicy.DISCARD_OLDEST;
                threadPrefix = "vectras-throughput";
                break;
            case BALANCED:
            default:
                poolSize = Math.min(4, Math.max(2, cpus - 1));
                queueCapacity = 64;
                rejectionPolicy = ThreadPoolBudget.RejectionPolicy.CALLER_RUNS;
                threadPrefix = "vectras-balanced";
                break;
        }

        return new ThreadPoolBudget(poolSize, queueCapacity, rejectionPolicy, threadPrefix);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
