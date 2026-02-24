package com.vectras.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StartVMCommandBuildTest {

    @Test
    public void buildCommand_shouldBuildVncWithoutTrailingSpaces() {
        List<String> params = new ArrayList<>();
        params.add("qemu-system-x86_64");
        params.add("-vnc");
        params.add("unix:/tmp/vnc.sock");

        String command = StartVM.buildCommand(params);

        assertEquals("qemu-system-x86_64 -vnc unix:/tmp/vnc.sock", command);
        assertFalse(command.contains("  "));
    }

    @Test
    public void buildCommand_shouldBuildSpiceWithoutTrailingSpaces() {
        List<String> params = new ArrayList<>();
        params.add("qemu-system-x86_64");
        params.add("-spice");
        params.add("port=6999,disable-ticketing=on");

        String command = StartVM.buildCommand(params);

        assertEquals("qemu-system-x86_64 -spice port=6999,disable-ticketing=on", command);
        assertFalse(command.contains("  "));
    }

    @Test
    public void buildCommand_shouldBuildPpcBiosAsSplitFlagAndValue() {
        List<String> params = new ArrayList<>();
        params.add("qemu-system-ppc");
        params.add("-L");
        params.add("pc-bios");

        String command = StartVM.buildCommand(params);

        assertEquals("qemu-system-ppc -L pc-bios", command);
        assertFalse(command.contains("  "));
    }

    @Test
    public void buildCommand_shouldBuildArm64UefiDrivePairs() {
        List<String> params = new ArrayList<>();
        params.add("qemu-system-aarch64");
        params.add("-drive");
        params.add("file=/base/QEMU_EFI.img,format=raw,readonly=on,if=pflash");
        params.add("-drive");
        params.add("file=/base/QEMU_VARS.img,format=raw,if=pflash");

        String command = StartVM.buildCommand(params);

        assertEquals("qemu-system-aarch64 -drive file=/base/QEMU_EFI.img,format=raw,readonly=on,if=pflash -drive file=/base/QEMU_VARS.img,format=raw,if=pflash", command);
        assertFalse(command.contains("  "));
    }

    @Test
    public void buildCommand_shouldBuildX8664UefiDrivePairs() {
        List<String> params = new ArrayList<>();
        params.add("qemu-system-x86_64");
        params.add("-drive");
        params.add("file=/base/RELEASEX64_OVMF.fd,format=raw,readonly=on,if=pflash");
        params.add("-drive");
        params.add("file=/base/RELEASEX64_OVMF_VARS.fd,format=raw,if=pflash");

        String command = StartVM.buildCommand(params);

        assertEquals("qemu-system-x86_64 -drive file=/base/RELEASEX64_OVMF.fd,format=raw,readonly=on,if=pflash -drive file=/base/RELEASEX64_OVMF_VARS.fd,format=raw,if=pflash", command);
        assertFalse(command.contains("  "));
    }

    @Test
    public void buildCommand_shouldBuildSharedFolderFatDriveAsPair() {
        List<String> params = new ArrayList<>();
        params.add("qemu-system-x86_64");
        params.add("-drive");
        params.add("index=3,media=disk,file=fat:rw:/storage/emulated/0/Android/data/pkg/SharedFolder,format=raw");

        String command = StartVM.buildCommand(params);

        assertEquals("qemu-system-x86_64 -drive index=3,media=disk,file=fat:rw:/storage/emulated/0/Android/data/pkg/SharedFolder,format=raw", command);
        assertFalse(command.contains("  "));
    }
}
