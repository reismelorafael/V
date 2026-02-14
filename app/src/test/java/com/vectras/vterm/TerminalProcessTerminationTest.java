package com.vectras.vterm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class TerminalProcessTerminationTest {

    @Test
    public void stopProcessWithTimeout_shouldEscalateToDestroyForciblyWhenGracefulTimeoutExpires() {
        FakeProcess process = new FakeProcess(false, true);

        boolean stopped = Terminal.stopProcessWithTimeout(process, 5, 5);

        assertTrue(stopped);
        assertTrue(process.destroyCalled);
        assertTrue(process.destroyForciblyCalled);
    }

    @Test
    public void stopProcessWithTimeout_shouldReturnFalseWhenStillAliveAfterForceTimeout() {
        FakeProcess process = new FakeProcess(false, false);

        boolean stopped = Terminal.stopProcessWithTimeout(process, 5, 5);

        assertFalse(stopped);
        assertTrue(process.destroyCalled);
        assertTrue(process.destroyForciblyCalled);
    }

    private static final class FakeProcess extends Process {
        private final boolean gracefulExit;
        private final boolean forcedExit;
        boolean destroyCalled = false;
        boolean destroyForciblyCalled = false;

        FakeProcess(boolean gracefulExit, boolean forcedExit) {
            this.gracefulExit = gracefulExit;
            this.forcedExit = forcedExit;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (destroyForciblyCalled) {
                return forcedExit;
            }
            if (destroyCalled) {
                return gracefulExit;
            }
            return false;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyForciblyCalled;
        }
    }
}
