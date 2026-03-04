package com.vectras.vm.core;

import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ponto único para orçamento de processos.
 */
public final class ProcessBudgetRegistry {
    private static final String TAG = "ProcessBudgetRegistry";
    private static final int DEFAULT_MAX_SLOTS = 24;

    private static final ProcessBudgetRegistry INSTANCE = new ProcessBudgetRegistry(resolveDefaultCapacity());

    private final Object lock = new Object();
    private final int totalSlots;
    private final AtomicInteger usedSlots = new AtomicInteger(0);
    private final AtomicLong tokenSequence = new AtomicLong(1L);
    private final HashMap<Long, SlotToken> activeTokens = new HashMap<>();
    private final HashMap<String, Integer> byFeature = new HashMap<>();
    private final HashMap<String, Integer> byTag = new HashMap<>();
    private final HashMap<String, Integer> byCaller = new HashMap<>();

    public static ProcessBudgetRegistry get() {
        return INSTANCE;
    }

    private ProcessBudgetRegistry(int totalSlots) {
        this.totalSlots = Math.max(1, totalSlots);
    }

    public SlotToken tryAcquireSlot(String feature, String tag, String caller, String vmIdOrScope) {
        final String normalizedFeature = normalize(feature, "unknown_feature");
        final String normalizedTag = normalize(tag, "unknown_tag");
        final String normalizedCaller = normalize(caller, "unknown_caller");
        final String normalizedScope = normalize(vmIdOrScope, "unknown_scope");

        synchronized (lock) {
            int currentUsed = usedSlots.get();
            if (currentUsed >= totalSlots) {
                log(normalizedFeature, normalizedTag, normalizedCaller,
                        "acquire", "result=reject reason=capacity scope=" + normalizedScope
                                + " total=" + totalSlots
                                + " used=" + currentUsed
                                + " available=" + Math.max(0, totalSlots - currentUsed));
                return null;
            }

            long tokenId = tokenSequence.getAndIncrement();
            SlotToken token = new SlotToken(tokenId, normalizedFeature, normalizedTag, normalizedCaller, normalizedScope);
            activeTokens.put(tokenId, token);
            int updated = usedSlots.incrementAndGet();
            increment(byFeature, normalizedFeature);
            increment(byTag, normalizedTag);
            increment(byCaller, normalizedCaller);

            log(normalizedFeature, normalizedTag, normalizedCaller,
                    "acquire", "result=ok scope=" + normalizedScope
                            + " token=" + tokenId
                            + " total=" + totalSlots
                            + " used=" + updated
                            + " available=" + Math.max(0, totalSlots - updated));
            return token;
        }
    }

    public void bindProcess(SlotToken token, Process process) {
        if (token == null || process == null) {
            return;
        }

        long pid = ProcessRuntimeOps.safePid(process);
        synchronized (lock) {
            SlotToken active = activeTokens.get(token.id);
            if (active == null || active != token || token.isReleased()) {
                log(token.feature, token.tag, token.caller,
                        "bind", "result=ignore reason=token_inactive token=" + token.id + " pid=" + pid);
                return;
            }

            if (!token.bindIfAbsent(process, pid)) {
                log(token.feature, token.tag, token.caller,
                        "bind", "result=idempotent token=" + token.id + " pid=" + token.boundPid);
                return;
            }

            log(token.feature, token.tag, token.caller,
                    "bind", "result=ok token=" + token.id + " pid=" + pid + " scope=" + token.vmIdOrScope);
            spawnExitWatcher(token, process);
        }
    }

    public void releaseSlot(SlotToken token, String reason) {
        if (token == null) {
            return;
        }

        if (!token.markReleased()) {
            log(token.feature, token.tag, token.caller,
                    "release", "result=idempotent token=" + token.id + " reason=" + normalize(reason, "none"));
            return;
        }

        synchronized (lock) {
            SlotToken removed = activeTokens.remove(token.id);
            if (removed == null) {
                log(token.feature, token.tag, token.caller,
                        "release", "result=idempotent token=" + token.id + " reason=" + normalize(reason, "none") + " active=0");
                return;
            }

            int updated = Math.max(0, usedSlots.decrementAndGet());
            decrement(byFeature, token.feature);
            decrement(byTag, token.tag);
            decrement(byCaller, token.caller);

            log(token.feature, token.tag, token.caller,
                    "release", "result=ok token=" + token.id
                            + " pid=" + token.boundPid
                            + " reason=" + normalize(reason, "none")
                            + " total=" + totalSlots
                            + " used=" + updated
                            + " available=" + Math.max(0, totalSlots - updated));
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            int used = usedSlots.get();
            return new Snapshot(
                    totalSlots,
                    used,
                    Math.max(0, totalSlots - used),
                    toUnmodifiableCopy(byFeature),
                    toUnmodifiableCopy(byTag),
                    toUnmodifiableCopy(byCaller)
            );
        }
    }

    private static int resolveDefaultCapacity() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int adaptive = 16 + (cpus >= 8 ? 4 : 0) + (cpus >= 12 ? 4 : 0);
        return Math.max(8, Math.min(32, Math.max(DEFAULT_MAX_SLOTS, adaptive)));
    }

    private static String normalize(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static void increment(HashMap<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private static void decrement(HashMap<String, Integer> map, String key) {
        Integer current = map.get(key);
        if (current == null) return;
        if (current <= 1) {
            map.remove(key);
        } else {
            map.put(key, current - 1);
        }
    }

    private static Map<String, Integer> toUnmodifiableCopy(HashMap<String, Integer> source) {
        return Collections.unmodifiableMap(new HashMap<>(source));
    }

    private static void log(String feature, String tag, String caller, String action, String tail) {
        Log.i(TAG, "feature=" + feature + " tag=" + tag + " caller=" + caller + " action=" + action + " " + tail);
    }

    private void spawnExitWatcher(SlotToken token, Process process) {
        Thread watcher = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                releaseSlot(token, "waitFor_exit");
            }
        }, "proc-budget-watch-" + token.id);
        watcher.setDaemon(true);
        watcher.start();
    }

    public static final class Snapshot {
        public final int total;
        public final int inUse;
        public final int available;
        public final Map<String, Integer> byFeature;
        public final Map<String, Integer> byTag;
        public final Map<String, Integer> byCaller;

        Snapshot(int total,
                 int inUse,
                 int available,
                 Map<String, Integer> byFeature,
                 Map<String, Integer> byTag,
                 Map<String, Integer> byCaller) {
            this.total = total;
            this.inUse = inUse;
            this.available = available;
            this.byFeature = byFeature;
            this.byTag = byTag;
            this.byCaller = byCaller;
        }
    }

    public static final class SlotToken {
        private final long id;
        private final String feature;
        private final String tag;
        private final String caller;
        private final String vmIdOrScope;
        private final long acquiredAtMs;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private volatile Process boundProcess;
        private volatile long boundPid = -1L;

        private SlotToken(long id, String feature, String tag, String caller, String vmIdOrScope) {
            this.id = id;
            this.feature = feature;
            this.tag = tag;
            this.caller = caller;
            this.vmIdOrScope = vmIdOrScope;
            this.acquiredAtMs = ProcessRuntimeOps.wallMs();
        }

        private boolean bindIfAbsent(Process process, long pid) {
            if (boundProcess == process) {
                if (boundPid <= 0 && pid > 0) {
                    boundPid = pid;
                }
                return false;
            }
            if (boundProcess != null) {
                return false;
            }
            this.boundProcess = process;
            this.boundPid = pid;
            return true;
        }

        private boolean markReleased() {
            return released.compareAndSet(false, true);
        }

        private boolean isReleased() {
            return released.get();
        }

        public long getId() {
            return id;
        }

        public String getFeature() {
            return feature;
        }

        public String getTag() {
            return tag;
        }

        public String getCaller() {
            return caller;
        }

        public String getVmIdOrScope() {
            return vmIdOrScope;
        }

        public long getAcquiredAtMs() {
            return acquiredAtMs;
        }

        public long getBoundPid() {
            return boundPid;
        }
    }
}
