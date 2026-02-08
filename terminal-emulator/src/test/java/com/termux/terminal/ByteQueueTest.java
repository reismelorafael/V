package com.termux.terminal;

import junit.framework.TestCase;

public class ByteQueueTest extends TestCase {

    private static void assertArrayPrefixEquals(byte[] expected, byte[] actual, int length) {
        for (int i = 0; i < length; i++) {
            assertEquals("Mismatch at index=" + i, expected[i], actual[i]);
        }
    }

    public void testCompleteWrites() {
        ByteQueue queue = new ByteQueue(10);
        assertTrue(queue.write(new byte[]{1, 2, 3}, 0, 3));

        byte[] out = new byte[10];
        assertEquals(3, queue.read(out, true));
        assertArrayPrefixEquals(new byte[]{1, 2, 3}, out, 3);

        byte[] all = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertTrue(queue.write(all, 0, all.length));
        assertEquals(all.length, queue.read(out, true));
        assertArrayPrefixEquals(all, out, all.length);
    }

    public void testQueueWraparoundStability() {
        ByteQueue queue = new ByteQueue(10);
        byte[] orig = new byte[]{1, 2, 3, 4, 5, 6};
        byte[] read = new byte[orig.length];

        for (int i = 0; i < 20; i++) {
            assertTrue(queue.write(orig, 0, orig.length));
            assertEquals(orig.length, queue.read(read, true));
            assertArrayPrefixEquals(orig, read, orig.length);
        }
    }

    public void testReadNonBlockingOnEmptyQueue() {
        ByteQueue queue = new ByteQueue(8);
        byte[] out = new byte[4];

        assertEquals(0, queue.read(out, false));
    }

    public void testReadWriteWrapAround() {
        ByteQueue queue = new ByteQueue(8);

        assertTrue(queue.write(new byte[]{1, 2, 3, 4, 5, 6}, 0, 6));

        byte[] firstRead = new byte[4];
        assertEquals(4, queue.read(firstRead, false));
        assertEquals(1, firstRead[0]);
        assertEquals(2, firstRead[1]);
        assertEquals(3, firstRead[2]);
        assertEquals(4, firstRead[3]);

        assertTrue(queue.write(new byte[]{7, 8, 9, 10, 11, 12}, 0, 6));

        byte[] secondRead = new byte[8];
        assertEquals(8, queue.read(secondRead, false));
        assertEquals(5, secondRead[0]);
        assertEquals(6, secondRead[1]);
        assertEquals(7, secondRead[2]);
        assertEquals(8, secondRead[3]);
        assertEquals(9, secondRead[4]);
        assertEquals(10, secondRead[5]);
        assertEquals(11, secondRead[6]);
        assertEquals(12, secondRead[7]);
    }

    public void testCloseStopsFurtherReadsAndWrites() {
        ByteQueue queue = new ByteQueue(4);
        queue.close();

        assertEquals(-1, queue.read(new byte[4], false));
        assertFalse(queue.write(new byte[]{1}, 0, 1));
    }

    public void testWriteNoticesClosing() {
        ByteQueue queue = new ByteQueue(10);
        queue.close();
        assertFalse(queue.write(new byte[]{1, 2, 3}, 0, 3));
    }
}
