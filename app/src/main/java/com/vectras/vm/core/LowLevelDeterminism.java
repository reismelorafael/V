package com.vectras.vm.core;

public final class LowLevelDeterminism {
    private LowLevelDeterminism() {
    }

    static int rotl32(int v, int n) {
        int s = n & 31;
        return (v << s) | (v >>> ((32 - s) & 31));
    }

    public static int fold32Fallback(int a, int b, int c, int d) {
        int x = a ^ rotl32(b, 5);
        x += 0x9E3779B9;
        x ^= rotl32(c, 13);
        x += (d ^ 0x85EBCA6B);
        x ^= (x >>> 16);
        x *= 0x7FEB352D;
        x ^= (x >>> 15);
        x *= 0x846CA68B;
        x ^= (x >>> 16);
        return x;
    }

    public static int reduceXorFallback(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            return 0;
        }
        int acc = 0;
        for (int i = 0; i < length; i++) {
            int lane = (data[offset + i] & 0xFF) << ((i & 3) * 8);
            acc ^= lane;
            acc = rotl32(acc, 3);
        }
        return acc;
    }

    public static int checksum32Fallback(byte[] data, int offset, int length, int seed) {
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            return seed;
        }
        int state = seed ^ 0xA5A5A5A5;
        for (int i = 0; i < length; i++) {
            state ^= (data[offset + i] & 0xFF) + 0x9E + i;
            state = rotl32(state, 7);
            state *= 0x045D9F3B;
        }
        return state ^ length;
    }

    public static int xorChecksumCompatFallback(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return 0;
        }
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid checksum range");
        }

        int x = 0;
        int i = 0;
        int end = length & ~7;
        while (i < end) {
            x ^= data[offset + i] & 0xFF;
            x ^= data[offset + i + 1] & 0xFF;
            x ^= data[offset + i + 2] & 0xFF;
            x ^= data[offset + i + 3] & 0xFF;
            x ^= data[offset + i + 4] & 0xFF;
            x ^= data[offset + i + 5] & 0xFF;
            x ^= data[offset + i + 6] & 0xFF;
            x ^= data[offset + i + 7] & 0xFF;
            i += 8;
        }
        while (i < length) {
            x ^= data[offset + i] & 0xFF;
            i++;
        }
        return x;
    }

    public static int crc32cCompatFallback(int initial, byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return initial;
        }
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid crc range");
        }
        int crc = initial;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i];
            for (int b = 0; b < 8; b++) {
                int mask = -(crc & 1);
                crc = (crc >>> 1) ^ (0x82F63B78 & mask);
            }
        }
        return crc;
    }
}
