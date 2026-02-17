package com.vectras.vm.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.junit.Test;

import java.lang.reflect.Method;

public class FileUtilsOpenModeTest {

    @Test
    public void resolveContentOpenMode_shouldHonorSupportedModesAndFallbackSafely() {
        assertEquals("r", FileUtils.resolveContentOpenMode("content://disk.qcow2", "r"));
        assertEquals("r", FileUtils.resolveContentOpenMode("content://disk.iso", "rw"));
        assertEquals("w", FileUtils.resolveContentOpenMode("content://disk.qcow2", "w"));
        assertEquals("rw", FileUtils.resolveContentOpenMode("content://disk.qcow2", "rw"));
        assertEquals("wt", FileUtils.resolveContentOpenMode("content://disk.qcow2", "wt"));
        assertEquals("wa", FileUtils.resolveContentOpenMode("content://disk.qcow2", "wa"));

        assertEquals("rw", FileUtils.resolveContentOpenMode("content://disk.qcow2", null));
        assertEquals("rw", FileUtils.resolveContentOpenMode("content://disk.qcow2", "   "));
        assertEquals("rw", FileUtils.resolveContentOpenMode("content://disk.qcow2", "invalid"));
    }

    @Test
    public void resolveParcelOpenMode_shouldRespectIsoAndReadOnlyBackendMode() {
        assertEquals(ParcelFileDescriptor.MODE_READ_ONLY,
                FileUtils.resolveParcelOpenMode("/storage/emulated/0/vm/disk.ISO", "rw"));
        assertEquals(ParcelFileDescriptor.MODE_READ_ONLY,
                FileUtils.resolveParcelOpenMode("/storage/emulated/0/vm/disk.qcow2", "r"));
        assertEquals(ParcelFileDescriptor.MODE_READ_WRITE,
                FileUtils.resolveParcelOpenMode("/storage/emulated/0/vm/disk.qcow2", "rw"));
    }

    @Test
    public void normalizeBackendMode_shouldTrimAndLowercase() throws Exception {
        Method method = FileUtils.class.getDeclaredMethod("normalizeBackendMode", String.class);
        method.setAccessible(true);

        assertEquals("rw", method.invoke(null, " RW "));
        assertEquals("wa", method.invoke(null, "Wa"));
        assertEquals("", method.invoke(null, (Object) null));
    }

    @Test
    public void isIsoPath_shouldBeCaseInsensitive() throws Exception {
        Method method = FileUtils.class.getDeclaredMethod("isIsoPath", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, "/storage/emulated/0/vm/disk.ISO"));
        assertTrue((Boolean) method.invoke(null, "content://disk.iSo"));
        assertFalse((Boolean) method.invoke(null, "/storage/emulated/0/vm/disk.qcow2"));
        assertFalse((Boolean) method.invoke(null, (Object) null));
    }


    @Test
    public void fileValid_multipleContentUriValidations_shouldNotGrowFdMap() throws Exception {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);

        ParcelFileDescriptor pfd = mock(ParcelFileDescriptor.class);
        when(resolver.openFileDescriptor(any(Uri.class), anyString())).thenReturn(pfd);

        FileUtils.fds.clear();
        int initialSize = FileUtils.fds.size();

        for (int i = 0; i < 20; i++) {
            assertTrue(FileUtils.fileValid(context, "content://disk.qcow2", "rw"));
        }

        assertEquals(initialSize, FileUtils.fds.size());
        verify(resolver, times(20)).openFileDescriptor(Uri.parse("content://disk.qcow2"), "rw");
        verify(pfd, times(20)).close();
    }

    @Test
    public void getFd_overloadWithoutBackendMode_shouldUseNullBackendModeDefaults() throws Exception {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);

        Uri uri = Uri.parse("content://disk.qcow2");
        File tempFile = File.createTempFile("vectras-fd", ".img");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE);
        when(resolver.openFileDescriptor(uri, "rw")).thenReturn(pfd);

        int fdWithoutMode = FileUtils.get_fd(context, "content://disk.qcow2");
        int fdWithNullMode = FileUtils.get_fd(context, "content://disk.qcow2", null);

        assertTrue(fdWithoutMode > 0);
        assertEquals(fdWithoutMode, fdWithNullMode);
        verify(resolver, times(2)).openFileDescriptor(uri, "rw");

        pfd.close();
    }
}
