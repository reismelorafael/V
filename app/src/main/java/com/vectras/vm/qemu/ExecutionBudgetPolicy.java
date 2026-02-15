package com.vectras.vm.qemu;

public final class ExecutionBudgetPolicy {

    static final int THROUGHPUT_MIN_CPUS = 10;
    static final int THROUGHPUT_MAX_CPUS = 23;
    static final int RESERVED_HOST_CPUS = 1;

    private ExecutionBudgetPolicy() {
        throw new AssertionError("ExecutionBudgetPolicy is a utility class and cannot be instantiated");
    }

    public static CpuConcurrencyBudget resolve(VmProfile profile, String arch) {
        return resolve(profile, arch, Runtime.getRuntime().availableProcessors());
    }

    static CpuConcurrencyBudget resolve(VmProfile profile, String arch, int availableProcessors) {
        VmProfile resolvedProfile = profile != null ? profile : VmProfile.BALANCED;
        String cpuModel = shouldUseMaxCpu(arch) ? "max" : null;
        Integer smpCpus = null;

        if (resolvedProfile == VmProfile.THROUGHPUT) {
            smpCpus = clampThroughputCpus(availableProcessors - RESERVED_HOST_CPUS);
        }

        return new CpuConcurrencyBudget(cpuModel, smpCpus);
    }

    private static boolean shouldUseMaxCpu(String arch) {
        return "X86_64".equals(arch) || "I386".equals(arch);
    }

    private static int clampThroughputCpus(int requestedCpus) {
        if (requestedCpus < THROUGHPUT_MIN_CPUS) {
            return THROUGHPUT_MIN_CPUS;
        }
        if (requestedCpus > THROUGHPUT_MAX_CPUS) {
            return THROUGHPUT_MAX_CPUS;
        }
        return requestedCpus;
    }

    public static final class CpuConcurrencyBudget {
        public final String cpuModel;
        public final Integer smpCpus;

        CpuConcurrencyBudget(String cpuModel, Integer smpCpus) {
            this.cpuModel = cpuModel;
            this.smpCpus = smpCpus;
        }
    }
}
