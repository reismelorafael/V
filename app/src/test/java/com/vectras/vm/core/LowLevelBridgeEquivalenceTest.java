package com.vectras.vm.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LowLevelBridgeEquivalenceTest {

    @Test
    public void fold32UsesDeterministicFallbackAndMatchesBridgeWhenNativeLoaded() {
        int[][] vectors = new int[][]{
                {0, 0, 0, 0},
                {1, 2, 3, 4},
                {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x13579BDF},
                {0x7F00FF00, 0x00FF00FF, 0xAAAAAAAA, 0x55555555},
                {0xDEADBEEF, 0xCAFEBABE, 0x10203040, 0x90807060}
        };

        for (int[] v : vectors) {
            int fallback = LowLevelDeterminism.fold32Fallback(v[0], v[1], v[2], v[3]);
            int bridged = LowLevelBridge.fold32(v[0], v[1], v[2], v[3]);
            assertEquals(fallback, bridged);
            if (!LowLevelBridge.isLoaded()) {
                assertFalse(LowLevelBridge.wasLastCallNative());
            }
        }

        if (LowLevelBridge.isLoaded()) {
            int[] probe = vectors[vectors.length - 1];
            int fallback = LowLevelDeterminism.fold32Fallback(probe[0], probe[1], probe[2], probe[3]);
            int bridged = LowLevelBridge.fold32(probe[0], probe[1], probe[2], probe[3]);
            assertTrue(LowLevelBridge.wasLastCallNative());
            assertEquals(fallback, bridged);
        }
    }

    @Test
    public void reduceXorUsesDeterministicFallbackAndMatchesBridgeWhenNativeLoaded() {
        byte[] signedBytes = new byte[]{
                (byte) 0x80, (byte) 0xFF, (byte) 0x7F, (byte) 0x00,
                (byte) 0xA5, (byte) 0x5A, (byte) 0xC3, (byte) 0x3C,
                (byte) 0xFE, (byte) 0x01
        };

        int[][] ranges = new int[][]{
                {0, 0},
                {0, 1},
                {0, signedBytes.length},
                {1, signedBytes.length - 1},
                {signedBytes.length - 1, 1},
                {2, 4}
        };

        for (int[] range : ranges) {
            int offset = range[0];
            int length = range[1];
            int fallback = LowLevelDeterminism.reduceXorFallback(signedBytes, offset, length);
            int bridged = LowLevelBridge.reduceXor(signedBytes, offset, length);
            assertEquals(fallback, bridged);
            if (!LowLevelBridge.isLoaded()) {
                assertFalse(LowLevelBridge.wasLastCallNative());
            }
        }

        assertEquals(0, LowLevelDeterminism.reduceXorFallback(new byte[0], 0, 0));
        assertEquals(0, LowLevelBridge.reduceXor(new byte[0], 0, 0));
        assertEquals(0, LowLevelDeterminism.reduceXorFallback(signedBytes, -1, 1));
        assertEquals(0, LowLevelBridge.reduceXor(signedBytes, -1, 1));

        if (LowLevelBridge.isLoaded()) {
            int fallback = LowLevelDeterminism.reduceXorFallback(signedBytes, 0, signedBytes.length);
            int bridged = LowLevelBridge.reduceXor(signedBytes, 0, signedBytes.length);
            assertTrue(LowLevelBridge.wasLastCallNative());
            assertEquals(fallback, bridged);
        }
    }

    @Test
    public void checksum32UsesDeterministicFallbackAndMatchesBridgeWhenNativeLoaded() {
        byte[] data = new byte[]{
                (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x80,
                (byte) 0xFF, (byte) 0x10, (byte) 0x20, (byte) 0x30,
                (byte) 0x40, (byte) 0x55
        };
        int[] seeds = new int[]{0, 1, -1, 0x12345678, Integer.MIN_VALUE, Integer.MAX_VALUE};
        int[][] ranges = new int[][]{
                {0, 0},
                {0, data.length},
                {1, data.length - 1},
                {data.length - 1, 1},
                {3, 4}
        };

        for (int seed : seeds) {
            for (int[] range : ranges) {
                int offset = range[0];
                int length = range[1];
                int fallback = LowLevelDeterminism.checksum32Fallback(data, offset, length, seed);
                int bridged = LowLevelBridge.checksum32(data, offset, length, seed);
                assertEquals(fallback, bridged);
                if (!LowLevelBridge.isLoaded()) {
                    assertFalse(LowLevelBridge.wasLastCallNative());
                }
            }
        }

        int invalidFallback = LowLevelDeterminism.checksum32Fallback(data, -1, 2, 0x55AA);
        int invalidBridge = LowLevelBridge.checksum32(data, -1, 2, 0x55AA);
        assertEquals(invalidFallback, invalidBridge);

        if (LowLevelBridge.isLoaded()) {
            int fallback = LowLevelDeterminism.checksum32Fallback(data, 0, data.length, 0xA5A5A5A5);
            int bridged = LowLevelBridge.checksum32(data, 0, data.length, 0xA5A5A5A5);
            assertTrue(LowLevelBridge.wasLastCallNative());
            assertEquals(fallback, bridged);
        }
    }
    @Test
    public void asmMarkerAndNativePathIntegrationForFoldAndChecksum() {
        int marker = NativeFastPath.asmBridgeMarker();
        if (LowLevelBridge.isLoaded()) {
            assertEquals(0x41534D31, marker);
        } else {
            assertEquals(0x4A564D31, marker);
        }

        int foldOut = LowLevelBridge.fold32(0x10203040, 0x55667788, 0xCAFEBABE, 0x0F1E2D3C);
        int foldFallback = LowLevelDeterminism.fold32Fallback(0x10203040, 0x55667788, 0xCAFEBABE, 0x0F1E2D3C);
        assertEquals(foldFallback, foldOut);

        byte[] payload = new byte[]{
                (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF,
                (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44
        };
        int checksumOut = LowLevelBridge.checksum32(payload, 0, payload.length, 0xA5A5A5A5);
        int checksumFallback = LowLevelDeterminism.checksum32Fallback(payload, 0, payload.length, 0xA5A5A5A5);
        assertEquals(checksumFallback, checksumOut);

        if (LowLevelBridge.isLoaded()) {
            assertTrue(LowLevelBridge.wasLastCallNative());
        } else {
            assertFalse(LowLevelBridge.wasLastCallNative());
        }
    }

}
