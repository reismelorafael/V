package com.vectras.vm.core;

import org.junit.Assert;
import org.junit.Test;

public class BoundedStringRingBufferTest {
    @Test
    public void shouldKeepLatestLinesAndBoundSize() {
        BoundedStringRingBuffer buffer = new BoundedStringRingBuffer(3, 30);
        buffer.addLine("11111");
        buffer.addLine("22222");
        buffer.addLine("33333");
        buffer.addLine("44444");

        String snapshot = buffer.snapshot();
        Assert.assertFalse(snapshot.contains("11111"));
        Assert.assertTrue(snapshot.contains("44444"));
    }
}
