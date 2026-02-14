package com.vectras.vm;

import com.vectras.vm.core.ProcessSupervisor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class VMManagerStopVmProcessTest {

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, ProcessSupervisor> supervisorsMap() {
        try {
            Field f = VMManager.class.getDeclaredField("SUPERVISORS");
            f.setAccessible(true);
            return (ConcurrentHashMap<String, ProcessSupervisor>) f.get(null);
        } catch (Exception e) {
            throw new AssertionError("Unable to access SUPERVISORS map", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> statesMap() {
        try {
            Field f = VMManager.class.getDeclaredField("VM_STATES");
            f.setAccessible(true);
            return (ConcurrentHashMap<String, Object>) f.get(null);
        } catch (Exception e) {
            throw new AssertionError("Unable to access VM_STATES map", e);
        }
    }

    @Before
    public void cleanState() {
        supervisorsMap().clear();
        statesMap().clear();
    }

    @Test
    public void tryMarkVmStarting_shouldDenySecondStartFlight() {
        boolean first = VMManager.tryMarkVmStarting("vm-flight");
        boolean second = VMManager.tryMarkVmStarting("vm-flight");

        Assert.assertTrue(first);
        Assert.assertFalse(second);
    }

    @Test
    public void clearVmStarting_shouldReturnStateToStopped_whenNoSupervisor() {
        Assert.assertTrue(VMManager.tryMarkVmStarting("vm-clear"));
        VMManager.clearVmStarting("vm-clear");

        Object state = statesMap().get("vm-clear");
        Assert.assertNotNull(state);
        Assert.assertEquals("STOPPED", state.toString());
    }

    @Test
    public void stopVmProcess_shouldReturnFalse_whenSupervisorIsMissing() {
        boolean stopped = VMManager.stopVmProcess(null, "vm-absent", false);
        Assert.assertFalse(stopped);
    }

    @Test
    public void stopVmProcess_shouldRemoveSupervisor_whenStopSucceeds() {
        ConcurrentHashMap<String, ProcessSupervisor> map = supervisorsMap();
        ProcessSupervisor supervisor = new FakeProcessSupervisor(true);
        map.put("vm-ok", supervisor);

        boolean stopped = VMManager.stopVmProcess(null, "vm-ok", true);

        Assert.assertTrue(stopped);
        Assert.assertFalse(map.containsKey("vm-ok"));
    }

    @Test
    public void stopVmProcess_shouldKeepSupervisor_whenStopFails() {
        ConcurrentHashMap<String, ProcessSupervisor> map = supervisorsMap();
        ProcessSupervisor supervisor = new FakeProcessSupervisor(false);
        map.put("vm-fail", supervisor);

        boolean stopped = VMManager.stopVmProcess(null, "vm-fail", true);

        Assert.assertFalse(stopped);
        Assert.assertTrue(map.containsKey("vm-fail"));
    }

    @Test
    public void registerVmProcess_shouldBeIdempotentForSameProcessInstance() {
        ConcurrentHashMap<String, ProcessSupervisor> map = supervisorsMap();
        FakeAliveProcess process = new FakeAliveProcess();

        VMManager.registerVmProcess(null, "vm-same", process);
        VMManager.registerVmProcess(null, "vm-same", process);

        Assert.assertEquals(1, map.size());
        Assert.assertEquals(0, process.destroyCount);
        Assert.assertEquals(0, process.destroyForciblyCount);
    }

    @Test
    public void registerVmProcess_shouldCleanupStaleSupervisor_whenProcessExits() throws Exception {
        ConcurrentHashMap<String, ProcessSupervisor> map = supervisorsMap();
        FakeExitingProcess process = new FakeExitingProcess();

        VMManager.registerVmProcess(null, "vm-exit", process);
        process.releaseExit();

        for (int i = 0; i < 40 && map.containsKey("vm-exit"); i++) {
            Thread.sleep(25L);
        }

        Assert.assertFalse(map.containsKey("vm-exit"));
    }

    private static final class FakeProcessSupervisor extends ProcessSupervisor {
        private final boolean result;

        FakeProcessSupervisor(boolean result) {
            super(null, "test");
            this.result = result;
        }

        @Override
        public synchronized boolean stopGracefully(boolean tryQmp) {
            return result;
        }
    }

    private static final class FakeExitingProcess extends Process {
        private final java.util.concurrent.CountDownLatch exitLatch = new java.util.concurrent.CountDownLatch(1);
        private volatile boolean alive = true;

        void releaseExit() {
            alive = false;
            exitLatch.countDown();
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int waitFor() throws InterruptedException {
            exitLatch.await();
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            boolean done = exitLatch.await(timeout, unit);
            if (done) alive = false;
            return done;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            releaseExit();
        }

        @Override
        public Process destroyForcibly() {
            releaseExit();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }

    private static final class FakeAliveProcess extends Process {
        int destroyCount = 0;
        int destroyForciblyCount = 0;

        @Override
        public java.io.OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyCount++;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCount++;
            return this;
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }
}
