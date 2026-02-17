package com.vectras.qemu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import org.junit.Test;

public class MainVNCActivityDispatchKeyEventTest {

    @Test
    public void extractComposedText_shouldReturnCharactersForActionMultiplePayload() {
        String text = MainVNCActivity.extractComposedText(
                KeyEvent.ACTION_MULTIPLE,
                KeyEvent.KEYCODE_UNKNOWN,
                "ã",
                0
        );

        assertEquals("ã", text);
    }


    @Test
    public void extractComposedText_shouldReturnCharactersForActionMultipleEvenWithKnownKeyCode() {
        String text = MainVNCActivity.extractComposedText(
                KeyEvent.ACTION_MULTIPLE,
                KeyEvent.KEYCODE_A,
                "ê",
                0
        );

        assertEquals("ê", text);
    }

    @Test
    public void extractComposedText_shouldIgnoreCombiningAccentIntermediateKey() {
        int combiningAccentUnicode = KeyCharacterMap.COMBINING_ACCENT | '`';

        String text = MainVNCActivity.extractComposedText(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A,
                null,
                combiningAccentUnicode
        );

        assertNull(text);
    }

    @Test
    public void extractComposedText_shouldReturnUnicodeCodePointForSimpleActionDown() {
        String text = MainVNCActivity.extractComposedText(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A,
                null,
                'a'
        );

        assertEquals("a", text);
    }
}
