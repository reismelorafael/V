package com.vectras.vm.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class NativeFastPathTest {

    @Test
    public void copyBytesFallbackWorks() {
        byte[] src = new byte[128];
        byte[] dst = new byte[128];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) (i ^ 0x5A);
        }

        NativeFastPath.copyBytes(src, 0, dst, 0, src.length);
        assertArrayEquals(src, dst);
    }

    @Test
    public void xorChecksumDeterministic() {
        byte[] data = new byte[33];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 3 + 1);
        }

        int x1 = NativeFastPath.xorChecksum(data, 0, data.length);
        int x2 = NativeFastPath.xorChecksum(data, 0, data.length);
        assertEquals(x1, x2);
    }


    @Test
    public void popcountMatchesKnownValues() {
        assertEquals(0, NativeFastPath.popcount32(0));
        assertEquals(32, NativeFastPath.popcount32(-1));
        assertEquals(16, NativeFastPath.popcount32(0xF0F0F0F0));
        assertEquals(13, NativeFastPath.popcount32(0x12345678));
    }

    @Test
    public void nativeLibraryOptional() {
        boolean available = NativeFastPath.isNativeAvailable();
        assertEquals(available, NativeFastPath.isNativeAvailable());
    }
}
