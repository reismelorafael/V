package com.vectras.vm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provedor central de executores com observabilidade padronizada.
 */
public final class ExecutionExecutors {

    public static final class DomainSnapshot {
        public final String domain;
        public final int activeCount;
        public final int poolSize;
        public final int queueSize;
        public final int queueRemainingCapacity;
        public final long completedTaskCount;
        public final long submittedTaskCount;
        public final long saturatedCount;
        public final long rejectedCount;
        public final long queueLatencyNanosTotal;
        public final long queueLatencySamples;
        public final long createdThreads;

        DomainSnapshot(String domain,
                       int activeCount,
                       int poolSize,
                       int queueSize,
                       int queueRemainingCapacity,
                       long completedTaskCount,
                       long submittedTaskCount,
                       long saturatedCount,
                       long rejectedCount,
                       long queueLatencyNanosTotal,
                       long queueLatencySamples,
                       long createdThreads) {
            this.domain = domain;
            this.activeCount = activeCount;
            this.poolSize = poolSize;
            this.queueSize = queueSize;
            this.queueRemainingCapacity = queueRemainingCapacity;
            this.completedTaskCount = completedTaskCount;
            this.submittedTaskCount = submittedTaskCount;
            this.saturatedCount = saturatedCount;
            this.rejectedCount = rejectedCount;
            this.queueLatencyNanosTotal = queueLatencyNanosTotal;
            this.queueLatencySamples = queueLatencySamples;
            this.createdThreads = createdThreads;
        }
    }

    private static volatile ExecutionExecutors INSTANCE;

    private final DomainExecutor terminalIo;
    private final DomainExecutor terminalWait;
    private final DomainExecutor shellExecutor;
    private final DomainExecutor processSupervisorQmp;

    private ExecutionExecutors(ExecutionBudgetPolicy policy) {
        this.terminalIo = new DomainExecutor(policy.terminalIo());
        this.terminalWait = new DomainExecutor(policy.terminalWait());
        this.shellExecutor = new DomainExecutor(policy.shellExecutor());
        this.processSupervisorQmp = new DomainExecutor(policy.processSupervisorQmp());
    }

    public static ExecutionExecutors initialize(ExecutionBudgetPolicy policy) {
        ExecutionExecutors executors = new ExecutionExecutors(policy);
        INSTANCE = executors;
        return executors;
    }

    public static ExecutionExecutors get() {
        ExecutionExecutors local = INSTANCE;
        if (local == null) {
            synchronized (ExecutionExecutors.class) {
                local = INSTANCE;
                if (local == null) {
                    local = initialize(ExecutionBudgetPolicy.defaults());
                }
            }
        }
        return local;
    }

    public Future<?> submitTerminalIo(Runnable runnable) {
        return terminalIo.submit(runnable);
    }

    public Future<?> submitTerminalWait(Runnable runnable) {
        return terminalWait.submit(runnable);
    }

    public Future<?> submitShellExecutor(Runnable runnable) {
        return shellExecutor.submit(runnable);
    }

    public <T> Future<T> submitShellExecutor(Callable<T> callable) {
        return shellExecutor.submit(callable);
    }

    public <T> Future<T> submitProcessSupervisorQmp(Callable<T> callable) {
        return processSupervisorQmp.submit(callable);
    }

    public ThreadPoolExecutor shellExecutorPool() {
        return shellExecutor.executor;
    }

    public ThreadPoolExecutor processSupervisorQmpPool() {
        return processSupervisorQmp.executor;
    }

    public DomainSnapshot terminalIoSnapshot() {
        return terminalIo.snapshot();
    }

    public DomainSnapshot terminalWaitSnapshot() {
        return terminalWait.snapshot();
    }

    public DomainSnapshot shellExecutorSnapshot() {
        return shellExecutor.snapshot();
    }

    public DomainSnapshot processSupervisorQmpSnapshot() {
        return processSupervisorQmp.snapshot();
    }

    public List<DomainSnapshot> snapshotAll() {
        ArrayList<DomainSnapshot> snapshots = new ArrayList<>(4);
        snapshots.add(terminalIo.snapshot());
        snapshots.add(terminalWait.snapshot());
        snapshots.add(shellExecutor.snapshot());
        snapshots.add(processSupervisorQmp.snapshot());
        return snapshots;
    }

    static final class DomainExecutor {
        private final String domain;
        private final AtomicLong submittedTaskCount = new AtomicLong();
        private final AtomicLong saturatedCount = new AtomicLong();
        private final AtomicLong rejectedCount = new AtomicLong();
        private final AtomicLong queueLatencyNanosTotal = new AtomicLong();
        private final AtomicLong queueLatencySamples = new AtomicLong();
        private final DomainThreadFactory threadFactory;
        private final ThreadPoolExecutor executor;

        DomainExecutor(ExecutionBudgetPolicy.DomainBudget budget) {
            this.domain = budget.threadPrefix;
            this.threadFactory = new DomainThreadFactory(budget.threadPrefix);
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(budget.queueCapacity);
            RejectedExecutionHandler backpressureHandler = (r, e) -> {
                saturatedCount.incrementAndGet();
                rejectedCount.incrementAndGet();
                if (!e.isShutdown()) {
                    r.run();
                }
            };
            this.executor = new ThreadPoolExecutor(
                    budget.coreThreads,
                    budget.maxThreads,
                    budget.keepAliveMs,
                    TimeUnit.MILLISECONDS,
                    queue,
                    threadFactory,
                    backpressureHandler
            );
            this.executor.allowCoreThreadTimeOut(false);
        }

        Future<?> submit(Runnable runnable) {
            submittedTaskCount.incrementAndGet();
            return executor.submit(tracked(runnable));
        }

        <T> Future<T> submit(Callable<T> callable) {
            submittedTaskCount.incrementAndGet();
            return executor.submit(tracked(callable));
        }

        private Runnable tracked(Runnable delegate) {
            final long enqueuedNs = System.nanoTime();
            return () -> {
                queueLatencyNanosTotal.addAndGet(System.nanoTime() - enqueuedNs);
                queueLatencySamples.incrementAndGet();
                delegate.run();
            };
        }

        private <T> Callable<T> tracked(Callable<T> delegate) {
            final long enqueuedNs = System.nanoTime();
            return () -> {
                queueLatencyNanosTotal.addAndGet(System.nanoTime() - enqueuedNs);
                queueLatencySamples.incrementAndGet();
                return delegate.call();
            };
        }

        DomainSnapshot snapshot() {
            return new DomainSnapshot(
                    domain,
                    executor.getActiveCount(),
                    executor.getPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity(),
                    executor.getCompletedTaskCount(),
                    submittedTaskCount.get(),
                    saturatedCount.get(),
                    rejectedCount.get(),
                    queueLatencyNanosTotal.get(),
                    queueLatencySamples.get(),
                    threadFactory.createdCount()
            );
        }
    }

    static final class DomainThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        DomainThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }

        long createdCount() {
            return counter.get() - 1L;
        }
    }
}
