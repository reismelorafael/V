package com.vectras.vm.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provedor central de executores com orçamento e métricas por domínio.
 */
public final class ExecutionExecutors {

    public interface ObservableExecutor {
        Snapshot snapshot();
    }

    public enum Domain {
        TERMINAL_IO("terminal-io"),
        TERMINAL_WAIT("terminal-wait"),
        SHELL_EXECUTOR("shell-executor"),
        PROCESS_SUPERVISOR_QMP("process-supervisor-qmp");

        final String threadPrefix;

        Domain(String threadPrefix) {
            this.threadPrefix = threadPrefix;
        }
    }

    public static final class Snapshot {
        public final String domain;
        public final int activeThreads;
        public final int poolSize;
        public final int queuedTasks;
        public final long submittedTasks;
        public final long completedTasks;
        public final long rejectedTasks;
        public final long saturations;
        public final long createdThreads;
        public final long avgQueueLatencyMicros;

        Snapshot(String domain,
                 int activeThreads,
                 int poolSize,
                 int queuedTasks,
                 long submittedTasks,
                 long completedTasks,
                 long rejectedTasks,
                 long saturations,
                 long createdThreads,
                 long avgQueueLatencyMicros) {
            this.domain = domain;
            this.activeThreads = activeThreads;
            this.poolSize = poolSize;
            this.queuedTasks = queuedTasks;
            this.submittedTasks = submittedTasks;
            this.completedTasks = completedTasks;
            this.rejectedTasks = rejectedTasks;
            this.saturations = saturations;
            this.createdThreads = createdThreads;
            this.avgQueueLatencyMicros = avgQueueLatencyMicros;
        }
    }

    private static final class MetricThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicLong created = new AtomicLong();

        MetricThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            long id = created.incrementAndGet();
            Thread thread = new Thread(r, prefix + "-" + id);
            thread.setDaemon(true);
            return thread;
        }

        long createdCount() {
            return created.get();
        }
    }

    private static final class TimedRunnable implements Runnable {
        final Runnable delegate;
        final long enqueuedAtNs;

        TimedRunnable(Runnable delegate) {
            this.delegate = delegate;
            this.enqueuedAtNs = System.nanoTime();
        }

        @Override
        public void run() {
            delegate.run();
        }
    }

    private static final class InstrumentedExecutor extends ThreadPoolExecutor implements ObservableExecutor {
        private final String domain;
        private final MetricThreadFactory metricThreadFactory;
        private final AtomicLong submittedTasks = new AtomicLong();
        private final AtomicLong rejectedTasks = new AtomicLong();
        private final AtomicLong saturations = new AtomicLong();
        private final AtomicLong queueLatencyNs = new AtomicLong();
        private final AtomicLong queueLatencySamples = new AtomicLong();

        InstrumentedExecutor(String domain,
                             int coreThreads,
                             int maxThreads,
                             long keepAliveMs,
                             BlockingQueue<Runnable> queue,
                             MetricThreadFactory threadFactory,
                             RejectedExecutionHandler rejectedExecutionHandler) {
            super(coreThreads, maxThreads, keepAliveMs, TimeUnit.MILLISECONDS, queue, threadFactory, rejectedExecutionHandler);
            this.domain = domain;
            this.metricThreadFactory = threadFactory;
        }

        @Override
        public void execute(Runnable command) {
            submittedTasks.incrementAndGet();
            if (getQueue().remainingCapacity() == 0) {
                saturations.incrementAndGet();
            }
            super.execute(new TimedRunnable(command));
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            if (r instanceof TimedRunnable) {
                long delta = System.nanoTime() - ((TimedRunnable) r).enqueuedAtNs;
                if (delta > 0) {
                    queueLatencyNs.addAndGet(delta);
                    queueLatencySamples.incrementAndGet();
                }
            }
            super.beforeExecute(t, r);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
            return super.newTaskFor(callable);
        }

        @Override
        public Snapshot snapshot() {
            long samples = queueLatencySamples.get();
            long avgMicros = samples == 0 ? 0 : TimeUnit.NANOSECONDS.toMicros(queueLatencyNs.get() / samples);
            return new Snapshot(
                    domain,
                    getActiveCount(),
                    getPoolSize(),
                    getQueue().size(),
                    submittedTasks.get(),
                    getCompletedTaskCount(),
                    rejectedTasks.get(),
                    saturations.get(),
                    metricThreadFactory.createdCount(),
                    avgMicros
            );
        }
    }

    private static final class MetricsRejectedHandler implements RejectedExecutionHandler {
        private final InstrumentedExecutor owner;

        MetricsRejectedHandler(InstrumentedExecutor owner) {
            this.owner = owner;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            owner.rejectedTasks.incrementAndGet();
            owner.saturations.incrementAndGet();
            throw new RejectedExecutionException(owner.domain + " saturated: queue=" + executor.getQueue().size());
        }
    }

    private static volatile ExecutionExecutors INSTANCE;

    private final ExecutionBudgetPolicy policy;
    private final InstrumentedExecutor terminalIo;
    private final InstrumentedExecutor terminalWait;
    private final InstrumentedExecutor processSupervisorQmp;

    private ExecutionExecutors(ExecutionBudgetPolicy policy) {
        this.policy = policy;
        this.terminalIo = buildShared(Domain.TERMINAL_IO, policy.terminalIo());
        this.terminalWait = buildShared(Domain.TERMINAL_WAIT, policy.terminalWait());
        this.processSupervisorQmp = buildShared(Domain.PROCESS_SUPERVISOR_QMP, policy.processSupervisorQmp());
    }

    public static ExecutionExecutors get() {
        ExecutionExecutors local = INSTANCE;
        if (local == null) {
            synchronized (ExecutionExecutors.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new ExecutionExecutors(ExecutionBudgetPolicy.defaults());
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    private static InstrumentedExecutor buildShared(Domain domain, ExecutionBudgetPolicy.Budget budget) {
        MetricThreadFactory threadFactory = new MetricThreadFactory(domain.threadPrefix);
        InstrumentedExecutor[] holder = new InstrumentedExecutor[1];
        holder[0] = new InstrumentedExecutor(
                domain.threadPrefix,
                budget.coreThreads,
                budget.maxThreads,
                budget.keepAliveMs,
                new ArrayBlockingQueue<>(budget.queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        holder[0].setRejectedExecutionHandler(new MetricsRejectedHandler(holder[0]));
        return holder[0];
    }

    public ThreadPoolExecutor newShellExecutorPool() {
        return buildShared(Domain.SHELL_EXECUTOR, policy.shellExecutor());
    }

    public Future<?> submitTerminalIo(Runnable runnable) {
        return terminalIo.submit(runnable);
    }

    public Future<?> submitTerminalWait(Runnable runnable) {
        return terminalWait.submit(runnable);
    }

    public <T> Future<T> submitProcessSupervisorQmp(Callable<T> callable) {
        return processSupervisorQmp.submit(callable);
    }

    public long qmpGraceTimeoutMs() {
        return policy.qmpGraceTimeoutMs();
    }

    public Snapshot snapshot(Domain domain) {
        switch (domain) {
            case TERMINAL_IO:
                return terminalIo.snapshot();
            case TERMINAL_WAIT:
                return terminalWait.snapshot();
            case PROCESS_SUPERVISOR_QMP:
                return processSupervisorQmp.snapshot();
            default:
                throw new IllegalArgumentException("No shared executor for domain: " + domain);
        }
    }

    public static Snapshot snapshotOf(ThreadPoolExecutor executor, String domain) {
        if (executor instanceof ObservableExecutor) {
            return ((ObservableExecutor) executor).snapshot();
        }
        return new Snapshot(
                domain,
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size(),
                executor.getTaskCount(),
                executor.getCompletedTaskCount(),
                -1,
                -1,
                -1,
                -1
        );
    }
}
