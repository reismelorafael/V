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

    @Before
    public void cleanState() {
        supervisorsMap().clear();
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
