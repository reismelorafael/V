package com.vectras.vm.core;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuração de executor para rotinas da VM.
 */
public final class ThreadPoolBudget {

    public enum RejectionPolicy {
        ABORT,
        CALLER_RUNS,
        DISCARD,
        DISCARD_OLDEST;

        public RejectedExecutionHandler toRejectedExecutionHandler() {
            switch (this) {
                case ABORT:
                    return new ThreadPoolExecutor.AbortPolicy();
                case DISCARD:
                    return new ThreadPoolExecutor.DiscardPolicy();
                case DISCARD_OLDEST:
                    return new ThreadPoolExecutor.DiscardOldestPolicy();
                case CALLER_RUNS:
                default:
                    return new ThreadPoolExecutor.CallerRunsPolicy();
            }
        }
    }

    private final int poolSize;
    private final int queueCapacity;
    private final RejectionPolicy rejectionPolicy;
    private final String threadNamePrefix;

    public ThreadPoolBudget(int poolSize,
                            int queueCapacity,
                            RejectionPolicy rejectionPolicy,
                            String threadNamePrefix) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be >= 1");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1");
        }
        if (rejectionPolicy == null) {
            throw new IllegalArgumentException("rejectionPolicy == null");
        }
        if (threadNamePrefix == null || threadNamePrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("threadNamePrefix must not be empty");
        }

        this.poolSize = poolSize;
        this.queueCapacity = queueCapacity;
        this.rejectionPolicy = rejectionPolicy;
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public RejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }
}
