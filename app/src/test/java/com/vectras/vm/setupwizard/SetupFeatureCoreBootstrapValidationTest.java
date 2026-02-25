package com.vectras.vm.setupwizard;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class SetupFeatureCoreBootstrapValidationTest {

    @Test
    public void validationFailsWhenBootstrapFilesMissing() {
        Context context = RuntimeEnvironment.getApplication();
        SetupFeatureCore.ProotBootstrapValidationResult result = SetupFeatureCore.validateProotBootstrapState(context);
        Assert.assertFalse(result.ok);
        Assert.assertTrue(result.errors.contains("missing-proot") || result.errors.contains("proot-not-executable"));
    }

    @Test
    public void validationSucceedsWhenRequiredFilesPresent() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File filesDir = context.getFilesDir();
        File proot = new File(filesDir, "usr/bin/proot");
        File busybox = new File(filesDir, "distro/bin/busybox");
        File shell = new File(filesDir, "distro/bin/sh");
        File tmp = new File(filesDir, "usr/tmp");

        proot.getParentFile().mkdirs();
        busybox.getParentFile().mkdirs();
        shell.getParentFile().mkdirs();
        tmp.mkdirs();

        proot.createNewFile();
        busybox.createNewFile();
        shell.createNewFile();
        proot.setExecutable(true, true);
        busybox.setExecutable(true, true);
        shell.setExecutable(true, true);

        SetupFeatureCore.ProotBootstrapValidationResult result = SetupFeatureCore.validateProotBootstrapState(context);
        Assert.assertTrue(result.summary(), result.ok);
    }
}
