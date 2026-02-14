package com.vectras.vm.core;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe ring buffer with line and byte quotas.
 */
public class BoundedStringRingBuffer {
    private final int maxLines;
    private final int maxBytes;
    private final Deque<String> lines = new ArrayDeque<>();
    private int totalBytes;

    public BoundedStringRingBuffer(int maxLines, int maxBytes) {
        this.maxLines = Math.max(1, maxLines);
        this.maxBytes = Math.max(64, maxBytes);
    }

    public synchronized void addLine(String line) {
        String safe = line == null ? "" : line;
        int size = byteCount(safe) + 1;
        if (size > maxBytes) {
            safe = trimToBytes(safe, maxBytes - 1);
            size = byteCount(safe) + 1;
        }
        lines.addLast(safe);
        totalBytes += size;
        trim();
    }

    public synchronized String snapshot() {
        StringBuilder sb = new StringBuilder(totalBytes + lines.size());
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private void trim() {
        while (lines.size() > maxLines || totalBytes > maxBytes) {
            String removed = lines.pollFirst();
            if (removed == null) return;
            totalBytes -= byteCount(removed) + 1;
        }
    }

    private static int byteCount(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String trimToBytes(String value, int maxBytes) {
        if (maxBytes <= 0) return "";
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer in = CharBuffer.wrap(value);
        ByteBuffer out = ByteBuffer.allocate(maxBytes);
        CoderResult result = encoder.encode(in, out, true);
        if (result.isError()) {
            return "";
        }
        return value.substring(0, in.position());
    }
}
