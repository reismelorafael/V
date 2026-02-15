package com.vectras.vm.qemu;

import org.junit.Assert;
import org.junit.Test;

public class ExecutionBudgetPolicyTest {

    @Test
    public void throughput_shouldClampCpuBudget_between10And23() {
        ExecutionBudgetPolicy low = ExecutionBudgetPolicy.resolve(VmProfile.THROUGHPUT, "ARM64", 2);
        ExecutionBudgetPolicy mid = ExecutionBudgetPolicy.resolve(VmProfile.THROUGHPUT, "ARM64", 16);
        ExecutionBudgetPolicy high = ExecutionBudgetPolicy.resolve(VmProfile.THROUGHPUT, "ARM64", 48);

        Assert.assertEquals(Integer.valueOf(10), low.smpCpus());
        Assert.assertEquals(Integer.valueOf(15), mid.smpCpus());
        Assert.assertEquals(Integer.valueOf(23), high.smpCpus());
    }

    @Test
    public void nonThroughputProfiles_shouldNotEmitSmpBudget() {
        ExecutionBudgetPolicy fastBoot = ExecutionBudgetPolicy.resolve(VmProfile.FAST_BOOT, "X86_64", 32);
        ExecutionBudgetPolicy lowLatency = ExecutionBudgetPolicy.resolve(VmProfile.LOW_LATENCY, "ARM64", 32);
        ExecutionBudgetPolicy balanced = ExecutionBudgetPolicy.resolve(VmProfile.BALANCED, "PPC", 32);

        Assert.assertNull(fastBoot.smpCpus());
        Assert.assertNull(lowLatency.smpCpus());
        Assert.assertNull(balanced.smpCpus());
    }

    @Test
    public void fallback_shouldDefaultToBalancedAndNoCpuMax_forUnknownArch() {
        ExecutionBudgetPolicy fallback = ExecutionBudgetPolicy.resolve(null, null, 0);

        Assert.assertFalse(fallback.shouldForceCpuMax());
        Assert.assertNull(fallback.smpCpus());
    }

    @Test
    public void x86Architectures_shouldForceCpuMax() {
        ExecutionBudgetPolicy x86_64 = ExecutionBudgetPolicy.resolve(VmProfile.BALANCED, "X86_64", 8);
        ExecutionBudgetPolicy i386 = ExecutionBudgetPolicy.resolve(VmProfile.BALANCED, "I386", 8);
        ExecutionBudgetPolicy arm64 = ExecutionBudgetPolicy.resolve(VmProfile.BALANCED, "ARM64", 8);

        Assert.assertTrue(x86_64.shouldForceCpuMax());
        Assert.assertTrue(i386.shouldForceCpuMax());
        Assert.assertFalse(arm64.shouldForceCpuMax());
    }
}
