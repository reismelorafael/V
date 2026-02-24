package com.vectras.qemu.utils;

import org.junit.Assert;
import org.junit.Test;

public class RamInfoTest {

    @Test
    public void ensureMinimumVmMemoryMb_lowMemory_clampsToMinimum() {
        Assert.assertEquals(128, RamInfo.ensureMinimumVmMemoryMb(32));
    }

    @Test
    public void ensureMinimumVmMemoryMb_negativeMemory_clampsToMinimum() {
        Assert.assertEquals(128, RamInfo.ensureMinimumVmMemoryMb(-1));
    }

    @Test
    public void ensureMinimumVmMemoryMb_sufficientMemory_keepsValue() {
        Assert.assertEquals(512, RamInfo.ensureMinimumVmMemoryMb(512));
    }
}
