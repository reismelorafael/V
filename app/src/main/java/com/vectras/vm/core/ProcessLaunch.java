package com.vectras.vm.core;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central helper for process launches guarded by {@link ProcessBudgetRegistry}.
 */
public final class ProcessLaunch {

    private ProcessLaunch() {
    }

    public interface Starter {
        Process start() throws IOException;
    }

    public static LaunchTicket withBudget(String feature,
                                          String tag,
                                          String caller,
                                          long timeoutMs,
                                          Starter starter) throws IOException {
        ProcessBudgetRegistry.SlotToken token = ProcessBudgetRegistry.tryAcquireSlot(feature, tag, caller, null);
        if (token == null) {
            throw new IOException("slot limit reached for " + diagnosticPrefix(feature, tag, caller));
        }

        try {
            Process process = starter.start();
            ProcessBudgetRegistry.bindProcess(token, process);
            return new LaunchTicket(feature, tag, caller, timeoutMs, token, process);
        } catch (IOException | RuntimeException e) {
            ProcessBudgetRegistry.releaseSlot(token, "start_failed");
            throw e;
        }
    }

    public static String diagnosticPrefix(String feature, String tag, String caller) {
        return "feature=" + feature + " tag=" + tag + " caller=" + caller;
    }

    public static final class LaunchTicket {
        private final String feature;
        private final String tag;
        private final String caller;
        private final long timeoutMs;
        private final ProcessBudgetRegistry.SlotToken token;
        private final Process process;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private LaunchTicket(String feature,
                             String tag,
                             String caller,
                             long timeoutMs,
                             ProcessBudgetRegistry.SlotToken token,
                             Process process) {
            this.feature = feature;
            this.tag = tag;
            this.caller = caller;
            this.timeoutMs = timeoutMs;
            this.token = token;
            this.process = process;
        }

        public Process process() {
            return process;
        }

        public long timeoutMs() {
            return timeoutMs;
        }

        public String diagnosticPrefix() {
            return ProcessLaunch.diagnosticPrefix(feature, tag, caller);
        }

        public void release(String reason) {
            if (released.compareAndSet(false, true)) {
                ProcessBudgetRegistry.releaseSlot(token, reason);
            }
        }
    }
}

