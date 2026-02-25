package com.vectras.vm.qemu;

import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class KvmProbe {

    private static final String KVM_DEVICE_PATH = "/dev/kvm";
    private static final String KVM_SYS_MODULE_PATH = "/sys/module/kvm";
    private static final String PROC_MODULES_PATH = "/proc/modules";
    private static final String PROC_CPUINFO_PATH = "/proc/cpuinfo";
    private static final Pattern TOKEN_VIRT = Pattern.compile("\\bvirt\\b");

    private KvmProbe() {
        throw new AssertionError("KvmProbe is a utility class and cannot be instantiated");
    }

    public static ProbeResult probe() {
        String abi = resolvePrimaryAbi();
        if (!isSupportedAbi(abi)) {
            return new ProbeResult(false, "unsupported host abi=" + abi);
        }

        File kvm = new File(KVM_DEVICE_PATH);
        if (!kvm.exists()) {
            return new ProbeResult(false, "missing /dev/kvm");
        }
        if (!kvm.canRead() || !kvm.canWrite()) {
            return new ProbeResult(false, "permission denied");
        }

        boolean modulePresent = new File(KVM_SYS_MODULE_PATH).exists();
        if (!modulePresent) {
            String modulesText = readTextFile(PROC_MODULES_PATH);
            modulePresent = hasKvmModule(modulesText);
        }
        if (!modulePresent) {
            return new ProbeResult(false, "kvm kernel module unavailable");
        }

        String cpuInfo = readTextFile(PROC_CPUINFO_PATH);
        if (!supportsCpuVirtualization(abi, cpuInfo)) {
            return new ProbeResult(false, "cpu virtualization flags unavailable");
        }

        return new ProbeResult(true, "ready abi=" + abi);
    }

    static String resolvePrimaryAbi() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) {
            return "unknown";
        }
        return Build.SUPPORTED_ABIS[0];
    }

    static boolean isSupportedAbi(String abi) {
        if (abi == null) {
            return false;
        }
        String normalized = abi.toLowerCase(Locale.ROOT);
        return normalized.contains("arm64") || normalized.contains("x86_64");
    }

    static boolean hasKvmModule(String modulesText) {
        if (modulesText == null || modulesText.isEmpty()) {
            return false;
        }
        String[] lines = modulesText.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("kvm ")
                    || trimmed.startsWith("kvm_intel ")
                    || trimmed.startsWith("kvm_amd ")
                    || trimmed.startsWith("kvm_arm ")
                    || trimmed.startsWith("kvm_arm64 ")) {
                return true;
            }
        }
        return false;
    }

    static boolean supportsCpuVirtualization(String abi, String cpuInfo) {
        if (cpuInfo == null || cpuInfo.isEmpty()) {
            return true;
        }
        String normalized = cpuInfo.toLowerCase(Locale.ROOT);
        if (abi != null && abi.toLowerCase(Locale.ROOT).contains("x86_64")) {
            return normalized.contains(" vmx") || normalized.contains(" svm");
        }
        if (abi != null && abi.toLowerCase(Locale.ROOT).contains("arm64")) {
            return normalized.contains(" hcr_el2")
                    || normalized.contains(" kvm")
                    || normalized.contains(" hypervisor")
                    || TOKEN_VIRT.matcher(normalized).find();
        }
        return false;
    }

    static String readTextFile(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
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
