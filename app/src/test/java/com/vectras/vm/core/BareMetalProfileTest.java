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

    @Test
    public void testCapabilitiesBitfieldContainsOnlyKnownBits() {
        int flags = BareMetalProfile.detectCapabilities();
        int knownMask = BareMetalProfile.CAP_ATOMICS
                | BareMetalProfile.CAP_UNALIGNED_FAST
                | BareMetalProfile.CAP_VECTOR_INT
                | BareMetalProfile.CAP_VECTOR_FP
                | BareMetalProfile.CAP_LITTLE_ENDIAN
                | BareMetalProfile.CAP_MULTI_CORE
                | BareMetalProfile.CAP_64_BIT
                | BareMetalProfile.CAP_NEON_OR_SSE
                | BareMetalProfile.CAP_AES
                | BareMetalProfile.CAP_CRC32;
        assertEquals(0, flags & ~knownMask);
    }
}
