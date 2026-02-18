package com.vectras.vm.core;

import com.vectras.vm.AppConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessSupervisorFailoverTest {


    @Before
    public void configureAuditPath() {
        AppConfig.internalDataDirPath = System.getProperty("java.io.tmpdir") + "/vectras-test-audit/";
    }

    @Test
    public void stopGracefully_qmpSuccess_transitionsToStopWithoutTermKill() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        FakeProcess process = new FakeProcess(true, 1001L);
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-qmp",
                () -> "{\"return\": {}}",
                sink,
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

        supervisor.bindProcess(process);
        boolean stopped = supervisor.stopGracefully(true);

        Assert.assertTrue(stopped);
        Assert.assertEquals(ProcessSupervisor.State.STOP, supervisor.getState());
        Assert.assertEquals(0, process.destroyCount);
        Assert.assertEquals(0, process.destroyForciblyCount);
        Assert.assertTrue(sink.containsPrefix("RUN->STOP:qmp_shutdown:qmp"));
        Assert.assertTrue(sink.hasNonNegativeStall("RUN->STOP:qmp_shutdown:qmp"));
    }

    @Test
    public void stopGracefully_qmpTimeout_thenTermSuccess() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        FakeProcess process = new FakeProcess(false, true);
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-term",
                () -> "{\"return\": {}}",
                sink,
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

        supervisor.bindProcess(process);
        boolean stopped = supervisor.stopGracefully(true);

        Assert.assertTrue(stopped);
        Assert.assertEquals(ProcessSupervisor.State.STOP, supervisor.getState());
        Assert.assertEquals(1, process.destroyCount);
        Assert.assertEquals(0, process.destroyForciblyCount);
        Assert.assertTrue(sink.containsPrefix("RUN->FAILOVER:qmp_timeout:term_kill"));
        Assert.assertTrue(sink.containsPrefix("FAILOVER->STOP:term_success:term"));
        Assert.assertTrue(sink.hasNonNegativeStall("FAILOVER->STOP:term_success:term"));
    }


    @Test
    public void stopGracefully_qmpHang_timesOutAndFallsBackToTermKill() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        FakeProcess process = new FakeProcess(false, true);
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-qmp-hang",
                () -> {
                    try {
                        Thread.sleep(5_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "{\"return\": {}}";
                },
                sink,
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

        supervisor.bindProcess(process);
        long startNs = System.nanoTime();
        boolean stopped = supervisor.stopGracefully(true);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        Assert.assertTrue(stopped);
        Assert.assertEquals(ProcessSupervisor.State.STOP, supervisor.getState());
        Assert.assertEquals(1, process.destroyCount);
        Assert.assertEquals(0, process.destroyForciblyCount);
        Assert.assertTrue(sink.containsPrefix("RUN->FAILOVER:qmp_timeout:term_kill"));
        Assert.assertTrue(sink.containsPrefix("FAILOVER->STOP:term_success:term"));
        Assert.assertTrue("stop should failover quickly when QMP hangs", elapsedMs < 3_500L);
    }
    @Test
    public void stopGracefully_termTimeout_thenKill() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        FakeProcess process = new FakeProcess(false, true);
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-kill",
                () -> null,
                sink,
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

        supervisor.bindProcess(process);
        boolean stopped = supervisor.stopGracefully(false);

        Assert.assertTrue(stopped);
        Assert.assertEquals(1, process.destroyCount);
        Assert.assertEquals(1, process.destroyForciblyCount);
        Assert.assertTrue(sink.containsPrefix("RUN->FAILOVER:no_qmp:term_kill"));
        Assert.assertTrue(sink.containsPrefix("FAILOVER->STOP:kill_success:kill"));
        Assert.assertTrue(sink.hasNonNegativeStall("FAILOVER->STOP:kill_success:kill"));
    }


    @Test
    public void bindProcess_allowsIdempotentRebindSameInstance() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-bind-idempotent",
                command -> "error:no_qmp",
                sink,
                new ProcessSupervisor.Clock() {
                    @Override
                    public long monoMs() {
                        return 100L;
                    }

                    @Override
                    public long wallMs() {
                        return 200L;
                    }
                }
        );

        FakeProcess process = new FakeProcess(true, 1001L);
        supervisor.bindProcess(process);
        supervisor.bindProcess(process);

        Assert.assertEquals(ProcessSupervisor.State.RUN, supervisor.getState());
        Assert.assertTrue(supervisor.isBoundTo(process));
        Assert.assertEquals(2, sink.size());
    }


    @Test
    public void bindProcess_allowsIdempotentRebindForSamePidAlias() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-bind-pid",
                command -> "error:no_qmp",
                sink,
                new ProcessSupervisor.Clock() {
                    @Override
                    public long monoMs() {
                        return 300L;
                    }

                    @Override
                    public long wallMs() {
                        return 400L;
                    }
                }
        );

        FakeProcess first = new FakeProcess(true, 4242L);
        FakeProcess alias = new FakeProcess(true, 4242L);
        supervisor.bindProcess(first);
        supervisor.bindProcess(alias);

        Assert.assertEquals(ProcessSupervisor.State.RUN, supervisor.getState());
        Assert.assertTrue(supervisor.isBoundTo(first));
        Assert.assertFalse(supervisor.isBoundTo(alias));
        Assert.assertEquals(2, sink.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void bindProcess_rejectsNull() {
        ProcessSupervisor supervisor = new ProcessSupervisor(null, "vm-null");
        supervisor.bindProcess(null);
    }

    @Test
    public void bindProcess_rebindsWhenAlreadyBoundAndCurrentProcessAlive() {
        RecordingTransitionSink sink = new RecordingTransitionSink();
        ProcessSupervisor supervisor = new ProcessSupervisor(
                null,
                "vm-bind",
                command -> "error:no_qmp",
                sink,
                new ProcessSupervisor.Clock() {
                    @Override
                    public long monoMs() {
                        return 500L;
                    }

                    @Override
                    public long wallMs() {
                        return 600L;
                    }
                }
        );

        FakeProcess previous = new FakeProcess(true, 1002L);
        FakeProcess replacement = new FakeProcess(true, 1003L);

        supervisor.bindProcess(previous);
        supervisor.bindProcess(replacement);

        Assert.assertEquals(1, previous.destroyCount);
        Assert.assertEquals(0, previous.destroyForciblyCount);
        Assert.assertEquals(ProcessSupervisor.State.RUN, supervisor.getState());
        Assert.assertTrue(supervisor.isBoundTo(replacement));
        Assert.assertFalse(supervisor.isBoundTo(previous));
        Assert.assertTrue(sink.containsPrefix("RUN->FAILOVER:no_qmp:term_kill"));
        Assert.assertTrue(sink.containsPrefix("FAILOVER->STOP:term_success:term"));
        Assert.assertEquals(6, sink.size());
    }

    private static final class RecordingTransitionSink implements ProcessSupervisor.TransitionSink {
        private final List<String> transitions = new ArrayList<>();

        @Override
        public void onTransition(ProcessSupervisor.State from,
                                 ProcessSupervisor.State to,
                                 String cause,
                                 String action,
                                 long stallMs,
                                 int droppedLogs,
                                 long bytes) {
            transitions.add(from.name() + "->" + to.name() + ":" + cause + ":" + action + ":" + stallMs);
        }

        boolean containsPrefix(String prefix) {
            for (String t : transitions) {
                if (t.startsWith(prefix + ":")) {
                    return true;
                }
            }
            return false;
        }

        boolean hasNonNegativeStall(String prefix) {
            for (String t : transitions) {
                if (t.startsWith(prefix + ":")) {
                    String[] parts = t.split(":");
                    if (parts.length >= 4) {
                        return Long.parseLong(parts[3]) >= 0L;
                    }
                }
            }
            return false;
        }

        int size() {
            return transitions.size();
        }
    }

    private static final class FakeProcess extends Process {
        private final List<Boolean> waitResults;
        private final long pid;
        private int waitIndex = 0;
        int destroyCount = 0;
        int destroyForciblyCount = 0;

        FakeProcess(Boolean... waitResults) {
            this(1234L, waitResults);
        }

        FakeProcess(boolean waitResult, long pid) {
            this(pid, waitResult);
        }

        FakeProcess(long pid, Boolean... waitResults) {
            this.waitResults = Arrays.asList(waitResults);
            this.pid = pid;
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
            if (waitIndex < waitResults.size()) {
                return waitResults.get(waitIndex++);
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

        @Override
        public long pid() {
            return pid;
        }
    }
}
