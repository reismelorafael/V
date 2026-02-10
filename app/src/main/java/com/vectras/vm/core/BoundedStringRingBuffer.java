package com.vectras.vm.core;

import java.nio.charset.StandardCharsets;
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
        StringBuilder sb = new StringBuilder(value.length());
        int bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + charBytes > maxBytes) break;
            sb.append(c);
            bytes += charBytes;
        }
        return sb.toString();
    }
}
