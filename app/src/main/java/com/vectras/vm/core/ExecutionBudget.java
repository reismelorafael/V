package com.vectras.vm.core;

/**
 * Unified execution budget for VM orchestration.
 */
public final class ExecutionBudget {

    private final int processLimit;
    private final int threadLimit;
    private final ThreadPoolBudget threadPoolBudget;
    private final QemuCpuBudget qemuCpuBudget;

    public ExecutionBudget(int processLimit,
                           int threadLimit,
                           ThreadPoolBudget threadPoolBudget,
                           QemuCpuBudget qemuCpuBudget) {
        if (processLimit <= 0) {
            throw new IllegalArgumentException("processLimit must be > 0");
        }
        if (threadLimit <= 0) {
            throw new IllegalArgumentException("threadLimit must be > 0");
        }
        if (threadPoolBudget == null) {
            throw new IllegalArgumentException("threadPoolBudget == null");
        }
        if (qemuCpuBudget == null) {
            throw new IllegalArgumentException("qemuCpuBudget == null");
        }
        this.processLimit = processLimit;
        this.threadLimit = threadLimit;
        this.threadPoolBudget = threadPoolBudget;
        this.qemuCpuBudget = qemuCpuBudget;
    }

    public int processLimit() {
        return processLimit;
    }

    public int threadLimit() {
        return threadLimit;
    }

    public ThreadPoolBudget threadPoolBudget() {
        return threadPoolBudget;
    }

    public QemuCpuBudget qemuCpuBudget() {
        return qemuCpuBudget;
    }
}
