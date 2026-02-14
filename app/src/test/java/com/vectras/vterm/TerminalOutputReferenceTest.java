package com.vectras.vterm;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

public class TerminalOutputReferenceTest {

    @Test
    public void shouldReturnOutputTextWhenAtomicReferenceHasBuffer() {
        AtomicReference<StringBuilder> output = new AtomicReference<>(new StringBuilder("vm ready"));

        String finalText = Terminal.resolveFinalOutputText(output, "ignored error");

        Assert.assertEquals("vm ready", finalText);
    }

    @Test
    public void shouldFallbackToErrorsWhenOutputReferenceIsNull() {
        AtomicReference<StringBuilder> output = new AtomicReference<>(null);

        String finalText = Terminal.resolveFinalOutputText(output, "execution failed");

        Assert.assertEquals("execution failed", finalText);
    }

    @Test
    public void shouldUseShortExecutionOutputAsFinalDisplayedText() {
        Process fake = new FakeProcess("line1\nline2\n", "", 0);
        AtomicReference<StringBuilder> output = new AtomicReference<>(Terminal.streamLog("", fake, true));

        String finalText = Terminal.resolveFinalOutputText(output, "");

        Assert.assertTrue(finalText.contains("line1"));
        Assert.assertTrue(finalText.contains("Execution finished successfully."));
    }

    private static class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private final int exitCode;

        FakeProcess(String stdout, String stderr, int exitCode) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes());
            this.stderr = new ByteArrayInputStream(stderr.getBytes());
            this.exitCode = exitCode;
        }

        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return stderr; }
        @Override public int waitFor() { return exitCode; }
        @Override public int exitValue() { return exitCode; }
        @Override public void destroy() {}
        @Override public boolean isAlive() { return false; }
    }
}
