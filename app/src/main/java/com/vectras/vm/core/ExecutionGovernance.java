package com.vectras.vm.core;

import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Camada central de governança para executores com telemetria de execução real.
 */
public final class ExecutionGovernance {

    private ExecutionGovernance() {
    }

    public static GovernedExecutor newSerialExecutor(String owner, String profileLabel) {
        int effectiveSmp = Math.max(1, Runtime.getRuntime().availableProcessors());
        int maxThreads = 1;
        int maxQueueDepth = Math.max(4, effectiveSmp * 2);
        int maxProcesses = Math.max(8, effectiveSmp * 4);

        AtomicInteger rejectionCount = new AtomicInteger(0);
        AtomicInteger callerRunsCount = new AtomicInteger(0);
        AtomicInteger submittedTasks = new AtomicInteger(0);
        AtomicInteger completedTasks = new AtomicInteger(0);
        AtomicInteger maxObservedQueueDepth = new AtomicInteger(0);
        AtomicLong totalTaskRuntimeNs = new AtomicLong(0L);

        String threadPrefix = "vectras-" + sanitize(owner);
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger next = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, threadPrefix + "-" + next.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };

        RejectedExecutionHandler callerRunsPolicy = (runnable, executor) -> {
            rejectionCount.incrementAndGet();
            callerRunsCount.incrementAndGet();
            if (!executor.isShutdown()) {
                runnable.run();
            }
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            maxThreads,
            maxThreads,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(maxQueueDepth),
            threadFactory,
            callerRunsPolicy
        ) {
            @Override
            public void execute(Runnable command) {
                submittedTasks.incrementAndGet();
                updateQueueDepth(maxObservedQueueDepth, getQueue().size());
                super.execute(wrap(command, completedTasks, totalTaskRuntimeNs));
                updateQueueDepth(maxObservedQueueDepth, getQueue().size());
            }
        };

        pool.allowCoreThreadTimeOut(false);

        return new GovernedExecutor(
            pool,
            profileLabel,
            effectiveSmp,
            maxThreads,
            maxProcesses,
            maxQueueDepth,
            rejectionCount,
            callerRunsCount,
            submittedTasks,
            completedTasks,
            maxObservedQueueDepth,
            totalTaskRuntimeNs,
            "CallerRuns"
        );
    }

    private static Runnable wrap(Runnable command,
                                 AtomicInteger completedTasks,
                                 AtomicLong totalTaskRuntimeNs) {
        return () -> {
            long start = System.nanoTime();
            try {
                command.run();
            } finally {
                totalTaskRuntimeNs.addAndGet(Math.max(0L, System.nanoTime() - start));
                completedTasks.incrementAndGet();
            }
        };
    }

    private static void updateQueueDepth(AtomicInteger maxObservedQueueDepth, int currentDepth) {
        int safeDepth = Math.max(0, currentDepth);
        while (true) {
            int prev = maxObservedQueueDepth.get();
            if (safeDepth <= prev) {
                return;
            }
            if (maxObservedQueueDepth.compareAndSet(prev, safeDepth)) {
                return;
            }
        }
    }

    private static String sanitize(String owner) {
        if (owner == null || owner.isEmpty()) {
            return "core";
        }
        return owner.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase(Locale.US);
    }

    public static final class GovernedExecutor {
        private final ExecutorService executor;
        private final String profileLabel;
        private final int effectiveSmp;
        private final int maxThreads;
        private final int maxProcesses;
        private final int maxQueueDepth;
        private final AtomicInteger rejectionCount;
        private final AtomicInteger callerRunsCount;
        private final AtomicInteger submittedTasks;
        private final AtomicInteger completedTasks;
        private final AtomicInteger maxObservedQueueDepth;
        private final AtomicLong totalTaskRuntimeNs;
        private final String rejectionPolicy;

        private GovernedExecutor(ExecutorService executor,
                                 String profileLabel,
                                 int effectiveSmp,
                                 int maxThreads,
                                 int maxProcesses,
                                 int maxQueueDepth,
                                 AtomicInteger rejectionCount,
                                 AtomicInteger callerRunsCount,
                                 AtomicInteger submittedTasks,
                                 AtomicInteger completedTasks,
                                 AtomicInteger maxObservedQueueDepth,
                                 AtomicLong totalTaskRuntimeNs,
                                 String rejectionPolicy) {
            this.executor = executor;
            this.profileLabel = profileLabel;
            this.effectiveSmp = effectiveSmp;
            this.maxThreads = maxThreads;
            this.maxProcesses = maxProcesses;
            this.maxQueueDepth = maxQueueDepth;
            this.rejectionCount = rejectionCount;
            this.callerRunsCount = callerRunsCount;
            this.submittedTasks = submittedTasks;
            this.completedTasks = completedTasks;
            this.maxObservedQueueDepth = maxObservedQueueDepth;
            this.totalTaskRuntimeNs = totalTaskRuntimeNs;
            this.rejectionPolicy = rejectionPolicy;
        }

        public ExecutorService executor() {
            return executor;
        }

        public PolicyTelemetry snapshot() {
            return new PolicyTelemetry(
                profileLabel,
                effectiveSmp,
                maxThreads,
                maxProcesses,
                maxQueueDepth,
                maxObservedQueueDepth.get(),
                rejectionCount.get(),
                callerRunsCount.get(),
                submittedTasks.get(),
                completedTasks.get(),
                totalTaskRuntimeNs.get(),
                rejectionPolicy
            );
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    public static final class PolicyTelemetry {
        public final String profileLabel;
        public final int effectiveSmp;
        public final int maxThreads;
        public final int maxProcesses;
        public final int maxQueueDepth;
        public final int maxObservedQueueDepth;
        public final int rejectionCount;
        public final int callerRunsCount;
        public final int submittedTasks;
        public final int completedTasks;
        public final long totalTaskRuntimeNs;
        public final String rejectionPolicy;

        private PolicyTelemetry(String profileLabel,
                                int effectiveSmp,
                                int maxThreads,
                                int maxProcesses,
                                int maxQueueDepth,
                                int maxObservedQueueDepth,
                                int rejectionCount,
                                int callerRunsCount,
                                int submittedTasks,
                                int completedTasks,
                                long totalTaskRuntimeNs,
                                String rejectionPolicy) {
            this.profileLabel = profileLabel;
            this.effectiveSmp = effectiveSmp;
            this.maxThreads = maxThreads;
            this.maxProcesses = maxProcesses;
            this.maxQueueDepth = maxQueueDepth;
            this.maxObservedQueueDepth = maxObservedQueueDepth;
            this.rejectionCount = rejectionCount;
            this.callerRunsCount = callerRunsCount;
            this.submittedTasks = submittedTasks;
            this.completedTasks = completedTasks;
            this.totalTaskRuntimeNs = totalTaskRuntimeNs;
            this.rejectionPolicy = rejectionPolicy;
        }

        public String compactEvidence() {
            return profileLabel + " | smp=" + effectiveSmp
                + " | q=" + maxObservedQueueDepth + "/" + maxQueueDepth
                + " | rej=" + rejectionCount
                + " | callerRuns=" + callerRunsCount;
        }
    }
}
