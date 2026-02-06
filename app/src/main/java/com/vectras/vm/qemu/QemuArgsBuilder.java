package com.vectras.vm.qemu;

import android.app.Activity;

import com.vectras.qemu.MainSettingsManager;

import java.util.ArrayList;

public final class QemuArgsBuilder {

    private QemuArgsBuilder() {
        throw new AssertionError("QemuArgsBuilder is a utility class and cannot be instantiated");
    }

    public static String binaryForArch(String arch) {
        if ("I386".equals(arch)) return "qemu-system-i386";
        if ("X86_64".equals(arch)) return "qemu-system-x86_64";
        if ("ARM64".equals(arch)) return "qemu-system-aarch64";
        if ("PPC".equals(arch)) return "qemu-system-ppc";
        return "qemu-system-x86_64";
    }

    public static String resolveDriveInterface(Activity activity, String arch) {
        String ifType = MainSettingsManager.getIfType(activity);
        if (ifType == null || ifType.isEmpty()) {
            return "";
        }
        if ("PPC".equals(arch)) {
            return "";
        }
        if ("ARM64".equals(arch) || "X86_64".equals(arch) || "I386".equals(arch)) {
            return "virtio";
        }
        return ifType;
    }

    public static VmProfile resolveProfile(Activity activity, String extras) {
        String arch = MainSettingsManager.getArch(activity);
        if (extras != null && extras.contains("-icount")) {
            return VmProfile.LOW_LATENCY;
        }
        if ("ARM64".equals(arch) || "X86_64".equals(arch)) {
            return VmProfile.THROUGHPUT;
        }
        if ("I386".equals(arch)) {
            return VmProfile.FAST_BOOT;
        }
        return VmProfile.BALANCED;
    }

    public static void applyProfile(ArrayList<String> params, Activity activity, String extras) {
        VmProfile profile = resolveProfile(activity, extras);
        String arch = MainSettingsManager.getArch(activity);

        if ("X86_64".equals(arch) || "I386".equals(arch)) {
            params.add("-cpu");
            params.add("max");
        }

        switch (profile) {
            case FAST_BOOT:
                params.add("-no-reboot");
                params.add("-boot");
                params.add("strict=off");
                break;
            case LOW_LATENCY:
                params.add("-overcommit");
                params.add("cpu-pm=on");
                break;
            case THROUGHPUT:
                params.add("-smp");
                params.add("cpus=" + Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
                break;
            case BALANCED:
            default:
                break;
        }
    }

    public static void applyVirtioNet(ArrayList<String> params, String extras) {
        if (extras == null) return;
        if (extras.contains("-net") || extras.contains("-nic") || extras.contains("virtio-net")) {
            return;
        }
        params.add("-nic");
        params.add("user,model=virtio-net-pci");
    }

    public static void applyAcceleration(ArrayList<String> params) {
        KvmProbe.ProbeResult probe = KvmProbe.probe();
        if (probe.enabled) {
            params.add("-accel");
            params.add("kvm");
            params.add("-machine");
            params.add("accel=kvm:tcg");
            params.add("-name");
            params.add("KVM=ON");
        } else {
            params.add("-name");
            params.add("KVM=OFF(" + probe.reason.replace(" ", "_") + ")");
        }
    }
}
