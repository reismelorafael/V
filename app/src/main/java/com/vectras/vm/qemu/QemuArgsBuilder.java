package com.vectras.vm.qemu;

import android.app.Activity;

import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.core.ExecutionBudget;
import com.vectras.vm.core.CoreExecutionBudgetPolicy;

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
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (availableProcessors < 1) {
            availableProcessors = 1;
        }

        ExecutionBudget budget = CoreExecutionBudgetPolicy.resolve(profile, availableProcessors, arch);

        if (budget.getQemuCpuBudget().getMaxVcpus() > 0) {
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
                break;
            default:
                break;
        }

        int cpus = budget.getQemuCpuBudget().getMaxVcpus();
        if (cpus > 1) {
            params.add("-smp");
            params.add("cpus=" + cpus);
        }
    }


    public static void applyVirtioStorageHints(ArrayList<String> params, String arch, String ifType, String extras) {
        if (!"ARM64".equals(arch) && !"X86_64".equals(arch) && !"I386".equals(arch)) {
            return;
        }
        if (!"virtio".equals(ifType)) {
            return;
        }

        if (extras != null && (extras.contains("virtio-scsi") || extras.contains("iothread,id=ioth0"))) {
            return;
        }

        params.add("-object");
        params.add("iothread,id=ioth0");
        params.add("-device");
        params.add("virtio-scsi-pci,id=scsi0,iothread=ioth0");
        params.add("-device");
        params.add("virtio-rng-pci");
    }

    public static void applyVirtioNet(ArrayList<String> params, String extras) {
        if (extras == null) return;
        if (extras.contains("-net") || extras.contains("-nic") || extras.contains("virtio-net")) {
            return;
        }
        params.add("-nic");
        params.add("user,model=virtio-net-pci");
    }

    public static KvmProbe.ProbeResult applyAcceleration(ArrayList<String> params, String extras) {
        KvmProbe.ProbeResult probe = KvmProbe.probe();
        if (hasAccelerationInExtras(extras)) {
            params.add("-name");
            params.add("KVM=EXTERNAL");
            return probe;
        }

        if (probe.enabled) {
            params.add("-accel");
            params.add("kvm");
            if (!hasMachineDeclaration(params)) {
                params.add("-machine");
                params.add("accel=kvm:tcg,kernel-irqchip=on");
            }
            params.add("-name");
            params.add("KVM=ON");
        } else {
            params.add("-accel");
            params.add("tcg,thread=multi");
            params.add("-name");
            params.add("KVM=OFF(" + probe.reason.replace(" ", "_") + ")");
        }
        return probe;
    }

    private static boolean hasAccelerationInExtras(String extras) {
        if (extras == null || extras.isEmpty()) {
            return false;
        }
        return extras.contains("-accel")
                || extras.contains("accel=")
                || extras.contains("-enable-kvm");
    }

    private static boolean hasMachineDeclaration(ArrayList<String> params) {
        for (int i = 0; i < params.size(); i++) {
            String p = params.get(i);
            if ("-machine".equals(p) || "-M".equals(p)) {
                return true;
            }
        }
        return false;
    }
}
