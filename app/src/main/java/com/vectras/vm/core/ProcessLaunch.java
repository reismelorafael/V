package com.vectras.vm.core;

import java.io.IOException;

/**
 * Centralized process start helper with budget-slot reservation metadata.
 */
public final class ProcessLaunch {

    private ProcessLaunch() {
        throw new AssertionError("ProcessLaunch is utility-only");
    }

    public static LaunchLease withBudget(ProcessBuilder processBuilder,
                                         String vmId,
                                         String feature,
                                         String tag,
                                         String caller) throws IOException {
        ProcessBudgetRegistry.SlotToken token = ProcessBudgetRegistry.tryAcquireSlot(feature, tag, caller, vmId);
        if (token == null) {
            throw new IOException("process_budget_full feature=" + feature + " tag=" + tag + " caller=" + caller);
        }
        try {
            Process process = processBuilder.start();
            ProcessBudgetRegistry.bindProcess(token, process);
            return new LaunchLease(process, token);
        } catch (IOException startError) {
            ProcessBudgetRegistry.releaseSlot(token, "start_failed");
            throw startError;
        } catch (RuntimeException runtimeError) {
            ProcessBudgetRegistry.releaseSlot(token, "start_runtime_failure");
            throw runtimeError;
        }
    }

    public static final class LaunchLease {
        private final Process process;
        private final ProcessBudgetRegistry.SlotToken token;

        private LaunchLease(Process process, ProcessBudgetRegistry.SlotToken token) {
            this.process = process;
            this.token = token;
        }

        public Process process() {
            return process;
        }

        public ProcessRuntimeOps.TimeoutExecutionResult waitFor(ProcessRuntimeOps.ExecutionCategory category) {
            return ProcessRuntimeOps.waitForByCategory(process, category);
        }

        public void release(String reason) {
            ProcessBudgetRegistry.releaseSlot(token, reason);
        }
    }
}
