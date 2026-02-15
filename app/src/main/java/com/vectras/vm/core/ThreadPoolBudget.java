package com.vectras.vm.core;

/**
 * Deterministic executor budget used by VM runtime services.
 */
public final class ThreadPoolBudget {

    public enum RejectionPolicy {
        CALLER_RUNS,
        DISCARD,
        DISCARD_OLDEST,
        ABORT
    }

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final RejectionPolicy rejectionPolicy;
    private final String threadNamePrefix;

    public ThreadPoolBudget(int corePoolSize,
                            int maxPoolSize,
                            int queueCapacity,
                            RejectionPolicy rejectionPolicy,
                            String threadNamePrefix) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be > 0");
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be >= corePoolSize");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
        if (rejectionPolicy == null) {
            throw new IllegalArgumentException("rejectionPolicy == null");
        }
        String prefix = threadNamePrefix == null ? "vm-exec" : threadNamePrefix.trim();
        if (prefix.isEmpty()) {
            prefix = "vm-exec";
        }
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.rejectionPolicy = rejectionPolicy;
        this.threadNamePrefix = prefix;
    }

    public int corePoolSize() {
        return corePoolSize;
    }

    public int maxPoolSize() {
        return maxPoolSize;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public RejectionPolicy rejectionPolicy() {
        return rejectionPolicy;
    }

    public String threadNamePrefix() {
        return threadNamePrefix;
    }
}
