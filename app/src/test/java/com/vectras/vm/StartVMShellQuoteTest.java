package com.vectras.vm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StartVMShellQuoteTest {

    @Test
    public void shellQuote_pathWithApostropheAndSpace_isEscapedForSingleQuoteShell() {
        String path = "/storage/emulated/0/My VM/O'Reilly.iso";

        assertEquals("'/storage/emulated/0/My VM/O'\"'\"'Reilly.iso'", StartVM.shellQuote(path));
    }

    @Test
    public void shellQuote_plainPath_keepsBackwardCompatibleWrapping() {
        String path = "/data/local/tmp/disk.qcow2";

        assertEquals("'/data/local/tmp/disk.qcow2'", StartVM.shellQuote(path));
    }
}
