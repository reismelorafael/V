package com.vectras.vm.crashtracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.vectras.vm.AppConfig;
import com.vectras.vm.utils.FileUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CrashHandlerTest {

    @Test
    public void uncaughtException_withNullDefaultHandler_persistsLogAndUsesExplicitFallback() {
        Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        String originalLogPath = AppConfig.lastCrashLogPath;

        File crashFile = new File(
                System.getProperty("java.io.tmpdir"),
                "vectras-crash-" + UUID.randomUUID() + "/lastcrash.txt");

        try {
            AppConfig.lastCrashLogPath = crashFile.getAbsolutePath();
            Thread.setDefaultUncaughtExceptionHandler(null);

            Context context = RuntimeEnvironment.getApplication();
            TestCrashHandler handler = new TestCrashHandler(context);

            IllegalStateException crash = new IllegalStateException("boom-null-default");
            handler.uncaughtException(Thread.currentThread(), crash);

            assertTrue(crashFile.exists());
            String content = FileUtils.readAFile(crashFile.getAbsolutePath());
            assertTrue(content.contains("boom-null-default"));
            assertTrue(handler.terminateCalled);
            assertTrue(handler.exitCalled);
            assertEquals(10, handler.exitCode);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler);
            AppConfig.lastCrashLogPath = originalLogPath;
            deleteParentDir(crashFile);
        }
    }

    @Test
    public void uncaughtException_withDefaultHandler_forwardsWithoutFallbackTermination() {
        Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        String originalLogPath = AppConfig.lastCrashLogPath;

        AtomicBoolean delegated = new AtomicBoolean(false);
        Thread.UncaughtExceptionHandler fakeDefault = (thread, throwable) -> delegated.set(true);

        File crashFile = new File(
                System.getProperty("java.io.tmpdir"),
                "vectras-crash-" + UUID.randomUUID() + "/lastcrash.txt");

        try {
            AppConfig.lastCrashLogPath = crashFile.getAbsolutePath();
            Thread.setDefaultUncaughtExceptionHandler(fakeDefault);

            Context context = RuntimeEnvironment.getApplication();
            TestCrashHandler handler = new TestCrashHandler(context);
            handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom-with-default"));

            assertTrue(delegated.get());
            assertFalse(handler.terminateCalled);
            assertFalse(handler.exitCalled);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler);
            AppConfig.lastCrashLogPath = originalLogPath;
            deleteParentDir(crashFile);
        }
    }

    private static void deleteParentDir(File file) {
        File parent = file.getParentFile();
        if (parent == null || !parent.exists()) {
            return;
        }

        File[] children = parent.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!child.delete()) {
                    child.deleteOnExit();
                }
            }
        }

        if (!parent.delete()) {
            parent.deleteOnExit();
        }
    }

    private static final class TestCrashHandler extends CrashHandler {
        boolean terminateCalled;
        boolean exitCalled;
        int exitCode;

        TestCrashHandler(Context context) {
            super(context);
        }

        @Override
        void terminateProcess(int pid) {
            terminateCalled = true;
        }

        @Override
        void exitProcess(int code) {
            exitCalled = true;
            exitCode = code;
        }
    }
}
