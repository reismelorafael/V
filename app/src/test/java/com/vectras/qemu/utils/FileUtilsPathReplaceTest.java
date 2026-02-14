package com.vectras.qemu.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FileUtilsPathReplaceTest {

    @Test
    public void getFilenameFromPath_shouldDecodeTokenizedSeparatorsWithoutRegexSideEffects() {
        String filename = FileUtils.getFilenameFromPath("storage%2Femulated%2F0%2FVMs%2Fdisk%5B01%5D.qcow2");
        assertEquals("disk%5B01%5D.qcow2", filename);
    }

    @Test
    public void convertAndUnconvertDocumentFilePath_shouldRoundTripDeterministically() {
        String original = "content://com.android.externalstorage.documents/document/primary%3AVMs%2Fdisk.qcow2";
        String converted = FileUtils.convertDocumentFilePath(original);
        String restored = FileUtils.unconvertDocumentFilePath(converted);

        assertEquals(original, restored);
    }
}
