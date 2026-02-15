package com.vectras.vm.core;

/**
 * QEMU vCPU budget resolved from profile + hardware capacity.
 */
public final class QemuCpuBudget {

    private final int totalVcpus;
    private final int sockets;
    private final int cores;
    private final int threads;

    public QemuCpuBudget(int totalVcpus, int sockets, int cores, int threads) {
        if (totalVcpus <= 0) {
            throw new IllegalArgumentException("totalVcpus must be > 0");
        }
        if (sockets <= 0 || cores <= 0 || threads <= 0) {
            throw new IllegalArgumentException("sockets/cores/threads must be > 0");
        }
        this.totalVcpus = totalVcpus;
        this.sockets = sockets;
        this.cores = cores;
        this.threads = threads;
    }

    public int totalVcpus() {
        return totalVcpus;
    }

    public int sockets() {
        return sockets;
    }

    public int cores() {
        return cores;
    }

    public int threads() {
        return threads;
    }

    public String toSmpArgument() {
        return "cpus=" + totalVcpus
                + ",sockets=" + sockets
                + ",cores=" + cores
                + ",threads=" + threads;
    }
}
