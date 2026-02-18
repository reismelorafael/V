package com.vectras.vm.setupwizard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.vectras.vm.AppConfig;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

public class SetupFeatureCorePreflightTest {

    @Test
    public void runVmStartPreflight_vncHeadlessWithoutXterm_doesNotRequireXterm() throws Exception {
        File filesDir = createFakeFilesDir(false, true);
        Context context = mockContext(filesDir);

        SetupFeatureCore.PreflightResult result = SetupFeatureCore.runVmStartPreflight(
                context,
                "qemu-system-x86_64",
                new SetupFeatureCore.VmStartPreflightOptions("VNC", true, true)
        );

        assertTrue(result.ok);
        assertTrue(result.missingBinaries.isEmpty());
        assertFalse(result.missingBinaries.contains("xterm"));
    }

    @Test
    public void runVmStartPreflight_x11WithXterm_missingXtermFails() throws Exception {
        File filesDir = createFakeFilesDir(false, true);
        Context context = mockContext(filesDir);

        SetupFeatureCore.PreflightResult result = SetupFeatureCore.runVmStartPreflight(
                context,
                "qemu-system-x86_64",
                new SetupFeatureCore.VmStartPreflightOptions("X11", true, false)
        );

        assertFalse(result.ok);
        assertTrue(result.missingBinaries.contains("xterm"));
        assertTrue(result.uiSummary().contains("Required binaries"));
    }

    @Test
    public void uiSummary_separatesRequiredAndOptionalComponents() throws Exception {
        File filesDir = createFakeFilesDir(false, false);
        Context context = mockContext(filesDir);

        SetupFeatureCore.PreflightResult result = SetupFeatureCore.runVmStartPreflight(
                context,
                "qemu-system-x86_64",
                new SetupFeatureCore.VmStartPreflightOptions("X11", false, false)
        );

        assertFalse(result.ok);
        String summary = result.uiSummary();
        assertTrue(summary.contains("Required binaries"));
        assertTrue(summary.contains("Optional components for current mode"));
    }

    private static Context mockContext(File filesDir) {
        Context context = mock(Context.class);
        when(context.getFilesDir()).thenReturn(filesDir);
        return context;
    }

    private static File createFakeFilesDir(boolean withXterm, boolean withQemu) throws IOException {
        File filesDir = Files.createTempDirectory("preflight-test").toFile();
        if (withQemu) {
            createBinary(filesDir, "distro/usr/bin/qemu-system-x86_64");
        }
        if (withXterm) {
            createBinary(filesDir, "distro/usr/bin/xterm");
        }
        writeInstalledPackageDb(filesDir);
        return filesDir;
    }

    private static void createBinary(File filesDir, String relativePath) throws IOException {
        File file = new File(filesDir, relativePath);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir: " + parent);
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Failed to create file: " + file);
        }
    }

    private static void writeInstalledPackageDb(File filesDir) throws IOException {
        File dbFile = new File(filesDir, "distro/lib/apk/db/installed");
        File parent = dbFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir: " + parent);
        }

        Set<String> packages = new LinkedHashSet<>();
        addPkgs(packages, AppConfig.neededPkgs());
        addPkgs(packages, AppConfig.neededPkgs32bit());

        StringBuilder db = new StringBuilder();
        for (String pkg : packages) {
            db.append("P:").append(pkg).append("\n");
        }
        Files.write(dbFile.toPath(), db.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void addPkgs(Set<String> out, String rawPkgs) {
        if (rawPkgs == null || rawPkgs.trim().isEmpty()) {
            return;
        }
        String[] split = rawPkgs.trim().split("\\s+");
        for (String pkg : split) {
            out.add(pkg);
        }
    }
}
