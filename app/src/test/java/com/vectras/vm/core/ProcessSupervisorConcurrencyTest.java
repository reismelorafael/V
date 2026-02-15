package com.vectras.vm.core;

import com.vectras.vm.AppConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class ProcessSupervisorConcurrencyTest {

    @Before
    public void configureAuditPath() {
        AppConfig.internalDataDirPath = System.getProperty("java.io.tmpdir") + "/vectras-test-audit/";
    }

    @Test
    public void qmpExecutorUsesSingleThreadBudget() {
        Assert.assertEquals(1, ProcessSupervisor.getQmpExecutorMaxThreadsForTests());
        Assert.assertEquals("process-supervisor-qmp", ProcessSupervisor.getQmpExecutorSnapshot().domain);
    }

    @Test
    public void stopGracefullyKeepsTimeoutBehaviorWhenQmpHangs() {
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-stop-regression",
                () -> {
                    try {
                        Thread.sleep(5_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "{\"return\": {}}";
                },
                (from, to, cause, action, stallMs, droppedLogs, bytes) -> { },
                new ProcessSupervisor.Clock() {
                    private long mono = 1000L;
                    private long wall = 1_700_000_000_000L;

                    @Override
                    public long monoMs() {
                        return mono++;
                    }

                    @Override
                    public long wallMs() {
                        return wall++;
                    }
                }
        );

        FakeProcess process = new FakeProcess(false, true);
        supervisor.bindProcess(process);

        long startNs = System.nanoTime();
        boolean stopped = supervisor.stopGracefully(true);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        Assert.assertTrue(stopped);
        Assert.assertEquals(ProcessSupervisor.State.STOP, supervisor.getState());
        Assert.assertEquals(1, process.destroyCount);
        Assert.assertEquals(0, process.destroyForciblyCount);
        Assert.assertTrue(elapsedMs < 3_500L);
    }

    private static final class FakeProcess extends Process {
        private final boolean[] waitResults;
        int destroyCount;
        int destroyForciblyCount;
        int waitIndex;

        FakeProcess(boolean... waitResults) {
            this.waitResults = waitResults;
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getErrorStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (waitIndex < waitResults.length) {
                return waitResults[waitIndex++];
            }
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
