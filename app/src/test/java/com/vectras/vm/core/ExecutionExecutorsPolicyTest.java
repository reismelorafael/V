package com.vectras.vm.core;

import org.junit.Assert;
import org.junit.Test;

public class ExecutionExecutorsPolicyTest {

    @Test
    public void defaultPolicyHasExpectedDomainNamesAndCaps() {
        ExecutionBudgetPolicy policy = ExecutionBudgetPolicy.defaults();

        Assert.assertEquals("terminal-io", policy.terminalIo().threadPrefix);
        Assert.assertEquals(64, policy.terminalIo().queueCapacity);
        Assert.assertEquals("terminal-wait", policy.terminalWait().threadPrefix);
        Assert.assertEquals(16, policy.terminalWait().queueCapacity);
        Assert.assertEquals("shell-executor", policy.shellExecutor().threadPrefix);
        Assert.assertEquals(32, policy.shellExecutor().queueCapacity);
        Assert.assertEquals("process-supervisor-qmp", policy.processSupervisorQmp().threadPrefix);
        Assert.assertEquals(16, policy.processSupervisorQmp().queueCapacity);
    }

    @Test
    public void snapshotsExposeUnifiedObservabilityFields() {
        ExecutionExecutors executors = ExecutionExecutors.get();
        ExecutionExecutors.DomainSnapshot shell = executors.shellExecutorSnapshot();

        Assert.assertEquals("shell-executor", shell.domain);
        Assert.assertTrue(shell.queueSize >= 0);
        Assert.assertTrue(shell.queueRemainingCapacity >= 0);
        Assert.assertTrue(shell.rejectedCount >= 0);
        Assert.assertTrue(shell.saturatedCount >= 0);
        Assert.assertTrue(shell.createdThreads >= 0);
    }
}
