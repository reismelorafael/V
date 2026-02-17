package com.vectras.vm.core;

import android.content.Context;

import com.termux.app.TermuxService;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProotCommandBuilderTest {

    @Test
    public void buildCommandShouldUseExpectedProotArgumentsAndBinds() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getFilesDir()).thenReturn(new File("/data/user/0/com.vectras.vm/files"));

        ProotCommandBuilder builder = new ProotCommandBuilder(context, "/data/user/0/com.vectras.vm/files/distro", "/root");

        List<String> command = builder.buildCommand();

        Assert.assertEquals(TermuxService.PREFIX_PATH + "/bin/proot", command.get(0));
        Assert.assertTrue(command.contains("--kill-on-exit"));
        Assert.assertTrue(command.contains("--link2symlink"));
        Assert.assertTrue(command.contains("-0"));

        assertHasPair(command, "-r", "/data/user/0/com.vectras.vm/files/distro");
        assertHasPair(command, "-w", "/root");
        assertHasPair(command, "-b", "/dev");
        assertHasPair(command, "-b", "/proc");
        assertHasPair(command, "-b", "/sys");
        assertHasPair(command, "-b", "/sdcard");
        assertHasPair(command, "-b", "/storage");
        assertHasPair(command, "-b", "/data");
        assertHasPair(command, "-b", "/data/user/0/com.vectras.vm/files/distro/root:/dev/shm");
        assertHasPair(command, "-b", "/data/user/0/com.vectras.vm/files/usr/tmp:/tmp");

        Assert.assertEquals("/bin/sh", command.get(command.size() - 2));
        Assert.assertEquals("--login", command.get(command.size() - 1));
    }

    @Test
    public void applyEnvironmentShouldPopulateExpectedKeys() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getFilesDir()).thenReturn(new File("/ignored/by/setFilesDirPath"));

        ProotCommandBuilder builder = new ProotCommandBuilder(context, "/rootfs", "/root")
                .setFilesDirPath("/custom/files")
                .setHome("/root")
                .setUser("root")
                .setTmpDir("/tmp")
                .setDisplay(":0")
                .setPulseServer("127.0.0.1")
                .setXdgRuntimeDir("/tmp")
                .setPath("/bin:/usr/bin")
                .setSdlVideoDriver("x11");

        Map<String, String> environment = new HashMap<>();
        builder.applyEnvironment(environment);

        Assert.assertEquals("/custom/files/usr/tmp", environment.get("PROOT_TMP_DIR"));
        Assert.assertEquals("/root", environment.get("HOME"));
        Assert.assertEquals("root", environment.get("USER"));
        Assert.assertEquals("xterm-256color", environment.get("TERM"));
        Assert.assertEquals("/tmp", environment.get("TMPDIR"));
        Assert.assertEquals("/bin/sh", environment.get("SHELL"));
        Assert.assertEquals(":0", environment.get("DISPLAY"));
        Assert.assertEquals("127.0.0.1", environment.get("PULSE_SERVER"));
        Assert.assertEquals("/tmp", environment.get("XDG_RUNTIME_DIR"));
        Assert.assertEquals("/bin:/usr/bin", environment.get("PATH"));
        Assert.assertEquals("x11", environment.get("SDL_VIDEODRIVER"));
    }

    private static void assertHasPair(List<String> values, String key, String expectedValue) {
        for (int i = 0; i < values.size() - 1; i++) {
            if (key.equals(values.get(i)) && expectedValue.equals(values.get(i + 1))) {
                return;
            }
        }
        Assert.fail("Expected pair not found: " + key + " " + expectedValue);
    }
}
