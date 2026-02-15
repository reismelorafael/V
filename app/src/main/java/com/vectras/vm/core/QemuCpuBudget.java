package com.vectras.vm.core;

/**
 * Orçamento de CPU para mapear em argumento {@code -smp} do QEMU.
 */
public final class QemuCpuBudget {

    private final int sockets;
    private final int cores;
    private final int threads;
    private final int maxVcpus;

    public QemuCpuBudget(int sockets, int cores, int threads, int maxVcpus) {
        if (sockets < 1 || cores < 1 || threads < 1 || maxVcpus < 1) {
            throw new IllegalArgumentException("all cpu dimensions must be >= 1");
        }
        int logicalCpus = sockets * cores * threads;
        if (maxVcpus > logicalCpus) {
            throw new IllegalArgumentException("maxVcpus cannot exceed sockets*cores*threads");
        }
        this.sockets = sockets;
        this.cores = cores;
        this.threads = threads;
        this.maxVcpus = maxVcpus;
    }

    public int getSockets() {
        return sockets;
    }

    public int getCores() {
        return cores;
    }

    public int getThreads() {
        return threads;
    }

    public int getMaxVcpus() {
        return maxVcpus;
    }

    public String toSmpArgument() {
        return "cpus=" + maxVcpus
                + ",sockets=" + sockets
                + ",cores=" + cores
                + ",threads=" + threads
                + ",maxcpus=" + maxVcpus;
    }
}
