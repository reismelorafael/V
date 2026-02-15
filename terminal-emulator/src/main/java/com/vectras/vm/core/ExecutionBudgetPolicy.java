package com.vectras.vm.core;

/**
 * Política central de orçamento para executores de execução.
 */
public final class ExecutionBudgetPolicy {

    public static final class DomainBudget {
        public final int coreThreads;
        public final int maxThreads;
        public final int queueCapacity;
        public final long keepAliveMs;
        public final String threadPrefix;

        public DomainBudget(int coreThreads, int maxThreads, int queueCapacity, long keepAliveMs, String threadPrefix) {
            if (coreThreads <= 0 || maxThreads <= 0 || maxThreads < coreThreads) {
                throw new IllegalArgumentException("invalid thread budget");
            }
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("queueCapacity must be > 0");
            }
            if (threadPrefix == null || threadPrefix.trim().isEmpty()) {
                throw new IllegalArgumentException("threadPrefix must be set");
            }
            this.coreThreads = coreThreads;
            this.maxThreads = maxThreads;
            this.queueCapacity = queueCapacity;
            this.keepAliveMs = Math.max(0L, keepAliveMs);
            this.threadPrefix = threadPrefix;
        }
    }

    private final DomainBudget terminalIo;
    private final DomainBudget terminalWait;
    private final DomainBudget shellExecutor;
    private final DomainBudget processSupervisorQmp;

    public ExecutionBudgetPolicy(DomainBudget terminalIo,
                                 DomainBudget terminalWait,
                                 DomainBudget shellExecutor,
                                 DomainBudget processSupervisorQmp) {
        this.terminalIo = terminalIo;
        this.terminalWait = terminalWait;
        this.shellExecutor = shellExecutor;
        this.processSupervisorQmp = processSupervisorQmp;
    }

    public static ExecutionBudgetPolicy defaults() {
        return new ExecutionBudgetPolicy(
                new DomainBudget(2, 2, 64, 0L, "terminal-io"),
                new DomainBudget(1, 1, 16, 0L, "terminal-wait"),
                new DomainBudget(2, 2, 32, 0L, "shell-executor"),
                new DomainBudget(1, 1, 16, 0L, "process-supervisor-qmp")
        );
    }

    public DomainBudget terminalIo() {
        return terminalIo;
    }

    public DomainBudget terminalWait() {
        return terminalWait;
    }

    public DomainBudget shellExecutor() {
        return shellExecutor;
    }

    public DomainBudget processSupervisorQmp() {
        return processSupervisorQmp;
    }
}
