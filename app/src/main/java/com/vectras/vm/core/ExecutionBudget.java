package com.vectras.vm.core;

/**
 * Limites de execução consolidados para um perfil de VM.
 */
public final class ExecutionBudget {

    private final int maxProcesses;
    private final int maxThreads;
    private final QemuCpuBudget qemuCpuBudget;
    private final ThreadPoolBudget threadPoolBudget;

    public ExecutionBudget(int maxProcesses,
                           int maxThreads,
                           QemuCpuBudget qemuCpuBudget,
                           ThreadPoolBudget threadPoolBudget) {
        if (maxProcesses < 1) {
            throw new IllegalArgumentException("maxProcesses must be >= 1");
        }
        if (maxThreads < 1) {
            throw new IllegalArgumentException("maxThreads must be >= 1");
        }
        if (qemuCpuBudget == null) {
            throw new IllegalArgumentException("qemuCpuBudget == null");
        }
        if (threadPoolBudget == null) {
            throw new IllegalArgumentException("threadPoolBudget == null");
        }
        this.maxProcesses = maxProcesses;
        this.maxThreads = maxThreads;
        this.qemuCpuBudget = qemuCpuBudget;
        this.threadPoolBudget = threadPoolBudget;
    }

    public int getMaxProcesses() {
        return maxProcesses;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public QemuCpuBudget getQemuCpuBudget() {
        return qemuCpuBudget;
    }

    public ThreadPoolBudget getThreadPoolBudget() {
        return threadPoolBudget;
    }
}
