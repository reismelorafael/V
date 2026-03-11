package com.vectras.vm.core;

/**
 * Política central de orçamento de execução por domínio.
 */
public final class ExecutionBudgetPolicy {

    public static final class Budget {
        public final int coreThreads;
        public final int maxThreads;
        public final int queueCapacity;
        public final long keepAliveMs;

        Budget(int coreThreads, int maxThreads, int queueCapacity, long keepAliveMs) {
            if (coreThreads <= 0) {
                throw new IllegalArgumentException("coreThreads must be > 0");
            }
            if (maxThreads <= 0) {
                throw new IllegalArgumentException("maxThreads must be > 0");
            }
            if (maxThreads < coreThreads) {
                throw new IllegalArgumentException("maxThreads must be >= coreThreads");
            }
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("queueCapacity must be > 0");
            }
            if (keepAliveMs < 0L) {
                throw new IllegalArgumentException("keepAliveMs must be >= 0");
            }
            this.coreThreads = coreThreads;
            this.maxThreads = maxThreads;
            this.queueCapacity = queueCapacity;
            this.keepAliveMs = keepAliveMs;
        }
    }

    private final Budget terminalIo;
    private final Budget terminalWait;
    private final Budget shellExecutor;
    private final Budget processSupervisorQmp;
    private final long qmpGraceTimeoutMs;

    private ExecutionBudgetPolicy(Budget terminalIo,
                                  Budget terminalWait,
                                  Budget shellExecutor,
                                  Budget processSupervisorQmp,
                                  long qmpGraceTimeoutMs) {
        this.terminalIo = terminalIo;
        this.terminalWait = terminalWait;
        this.shellExecutor = shellExecutor;
        this.processSupervisorQmp = processSupervisorQmp;
        this.qmpGraceTimeoutMs = qmpGraceTimeoutMs;
    }

    public static ExecutionBudgetPolicy defaults() {
        return new ExecutionBudgetPolicy(
                new Budget(2, 2, 64, 0L),
                new Budget(1, 1, 32, 0L),
                new Budget(2, 2, 32, 0L),
                new Budget(1, 1, 16, 0L),
                1_200L
        );
    }

    public Budget terminalIo() {
        return terminalIo;
    }

    public Budget terminalWait() {
        return terminalWait;
    }

    public Budget shellExecutor() {
        return shellExecutor;
    }

    public Budget processSupervisorQmp() {
        return processSupervisorQmp;
    }

    public long qmpGraceTimeoutMs() {
        return qmpGraceTimeoutMs;
    }
}
