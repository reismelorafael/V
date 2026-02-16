package com.vectras.vm.qemu;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KvmProbeTest {

    @Test
    public void supportedAbi_allowsArm64AndX8664() {
        assertTrue(KvmProbe.isSupportedAbi("arm64-v8a"));
        assertTrue(KvmProbe.isSupportedAbi("x86_64"));
        assertFalse(KvmProbe.isSupportedAbi("armeabi-v7a"));
    }

    @Test
    public void hasKvmModule_detectsCoreAndVendorModules() {
        String modules = "kvm 123 0 - Live 0x0\n"
                + "kvm_intel 456 0 - Live 0x0\n";
        assertTrue(KvmProbe.hasKvmModule(modules));
        assertFalse(KvmProbe.hasKvmModule("binder 1 0 - Live 0x0\n"));
    }

    @Test
    public void supportsCpuVirtualization_detectsX86Flags() {
        assertTrue(KvmProbe.supportsCpuVirtualization("x86_64", "flags : fpu vmx aes"));
        assertTrue(KvmProbe.supportsCpuVirtualization("x86_64", "flags : fpu svm aes"));
        assertFalse(KvmProbe.supportsCpuVirtualization("x86_64", "flags : fpu aes"));
    }

    @Test
    public void supportsCpuVirtualization_detectsArm64Hints() {
        assertTrue(KvmProbe.supportsCpuVirtualization("arm64-v8a", "Features : fp asimd hypervisor"));
        assertTrue(KvmProbe.supportsCpuVirtualization("arm64-v8a", "Features : fp asimd virt"));
        assertFalse(KvmProbe.supportsCpuVirtualization("arm64-v8a", "Features : fp asimd"));
    }
}
