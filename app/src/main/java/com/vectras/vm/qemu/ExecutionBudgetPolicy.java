package com.vectras.vm.qemu;

import androidx.annotation.Nullable;


/**
 * Package scope: QEMU-only CPU argument tuning ("-cpu max" and "-smp" heuristics).
 * Expected call sites: com.vectras.vm.qemu launch argument builders and related QEMU profile glue.
 */
public final class ExecutionBudgetPolicy {
    private static final int THROUGHPUT_MIN_CPUS = 10;
    private static final int THROUGHPUT_MAX_CPUS = 23;

    private final boolean forceCpuMax;
    @Nullable
    private final Integer smpCpus;

    private ExecutionBudgetPolicy(boolean forceCpuMax, @Nullable Integer smpCpus) {
        this.forceCpuMax = forceCpuMax;
        this.smpCpus = smpCpus;
    }

    public static ExecutionBudgetPolicy resolve(VmProfile profile, String arch, int availableProcessors) {
        VmProfile safeProfile = profile != null ? profile : VmProfile.BALANCED;
        String safeArch = arch != null ? arch : "";

        boolean shouldForceCpuMax = "X86_64".equals(safeArch) || "I386".equals(safeArch);
        Integer smp = null;

        if (safeProfile == VmProfile.THROUGHPUT) {
            smp = resolveThroughputCpuBudget(availableProcessors);
        }

        return new ExecutionBudgetPolicy(shouldForceCpuMax, smp);
    }

    public boolean shouldForceCpuMax() {
        return forceCpuMax;
    }

    @Nullable
    public Integer smpCpus() {
        return smpCpus;
    }

    private static int resolveThroughputCpuBudget(int availableProcessors) {
        if (availableProcessors <= 1) {
            return THROUGHPUT_MIN_CPUS;
        }
        int candidate = availableProcessors - 1;
        if (candidate < THROUGHPUT_MIN_CPUS) {
            return THROUGHPUT_MIN_CPUS;
        }
        return Math.min(candidate, THROUGHPUT_MAX_CPUS);
    }
}
