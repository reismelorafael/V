package com.vectras.vm.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class BareMetalProfileTest {

    @Test
    public void testDetectArchitectureInKnownRange() {
        int arch = BareMetalProfile.detectArchitecture();
        assertTrue(arch >= BareMetalProfile.ARCH_UNKNOWN);
        assertTrue(arch <= BareMetalProfile.ARCH_RISCV64);
    }

    @Test
    public void testCapabilitiesAlwaysExposeAtomics() {
        int flags = BareMetalProfile.detectCapabilities();
        assertTrue((flags & BareMetalProfile.CAP_ATOMICS) != 0);
    }

    @Test
    public void testRecommendedValuesArePositive() {
        assertTrue(BareMetalProfile.recommendedWorkBlockBytes() > 0);
        assertTrue(BareMetalProfile.recommendedParallelism() > 0);
        assertTrue(BareMetalProfile.runtimeMemoryClassBytes() > 0);
    }
}
