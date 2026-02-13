package com.vectras.vm;

import com.vectras.vm.core.ProcessSupervisor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

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
}
