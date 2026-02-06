package com.vectras.vm.qemu;

import android.os.Build;

import java.io.File;

public final class KvmProbe {

    private KvmProbe() {
        throw new AssertionError("KvmProbe is a utility class and cannot be instantiated");
    }

    public static ProbeResult probe() {
        File kvm = new File("/dev/kvm");
        if (!kvm.exists()) {
            return new ProbeResult(false, "missing /dev/kvm");
        }
        if (!kvm.canRead() || !kvm.canWrite()) {
            return new ProbeResult(false, "permission denied");
        }

        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0]
                : "unknown";
        if (!(abi.contains("arm64") || abi.contains("x86_64"))) {
            return new ProbeResult(false, "unsupported host abi=" + abi);
        }

        return new ProbeResult(true, "ready abi=" + abi);
    }

    public static final class ProbeResult {
        public final boolean enabled;
        public final String reason;

        private ProbeResult(boolean enabled, String reason) {
            this.enabled = enabled;
            this.reason = reason;
        }
    }
}
