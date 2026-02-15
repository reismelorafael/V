package com.vectras.vm.qemu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ExecutionBudgetPolicyTest {

    @Test
    public void throughputProfile_clampsToMinAndMaxRange() {
        ExecutionBudgetPolicy.CpuConcurrencyBudget minBudget =
                ExecutionBudgetPolicy.resolve(VmProfile.THROUGHPUT, "ARM64", 4);
        ExecutionBudgetPolicy.CpuConcurrencyBudget inRangeBudget =
                ExecutionBudgetPolicy.resolve(VmProfile.THROUGHPUT, "ARM64", 18);
        ExecutionBudgetPolicy.CpuConcurrencyBudget maxBudget =
                ExecutionBudgetPolicy.resolve(VmProfile.THROUGHPUT, "ARM64", 64);

        assertEquals(Integer.valueOf(10), minBudget.smpCpus);
        assertEquals(Integer.valueOf(17), inRangeBudget.smpCpus);
        assertEquals(Integer.valueOf(23), maxBudget.smpCpus);
    }

    @Test
    public void nonThroughputProfiles_doNotSetSmpBudget() {
        ExecutionBudgetPolicy.CpuConcurrencyBudget balanced =
                ExecutionBudgetPolicy.resolve(VmProfile.BALANCED, "X86_64", 32);
        ExecutionBudgetPolicy.CpuConcurrencyBudget lowLatency =
                ExecutionBudgetPolicy.resolve(VmProfile.LOW_LATENCY, "I386", 32);

        assertNull(balanced.smpCpus);
        assertNull(lowLatency.smpCpus);
    }

    @Test
    public void fallbackProfile_whenNull_usesBalancedBehavior() {
        ExecutionBudgetPolicy.CpuConcurrencyBudget fallback =
                ExecutionBudgetPolicy.resolve(null, "X86_64", 0);

        assertEquals("max", fallback.cpuModel);
        assertNull(fallback.smpCpus);
    }
}
