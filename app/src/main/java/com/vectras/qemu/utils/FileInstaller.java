/*
 Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.vectras.qemu.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import android.util.Log;

import com.vectras.qemu.Config;
import com.vectras.vm.utils.UIUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dev
 */
public class FileInstaller {

    private static final Map<String, ReentrantReadWriteLock> FILE_LOCKS = new ConcurrentHashMap<>();

    private static ReentrantReadWriteLock lockForPath(String path) {
        return FILE_LOCKS.computeIfAbsent(path, key -> new ReentrantReadWriteLock());
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[8092];
        int n;
        while ((n = is.read(buf)) > 0) {
            os.write(buf, 0, n);
        }
    }

    private static void moveTempFileToDestination(File tempFile, File outputFile) throws IOException {
        Path tempPath = tempFile.toPath();
        Path outputPath = outputFile.toPath();
        try {
            Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Log.w("Installer", "event=file_install atomic_move_not_supported src=" + tempFile.getAbsolutePath() + " dest=" + outputFile.getAbsolutePath());
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanupTempFile(File tempFile, String opTag) {
        if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
            Log.w("Installer", "event=" + opTag + " temp_cleanup_failed path=" + tempFile.getAbsolutePath());
        }
    }

    public static void installFiles(Context context, boolean force) {

        Log.v("Installer", "Installing files...");
        File tmpDir = new File(Config.getBasefileDir());
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        File tmpDir1 = new File(Config.getMachineDir());
        if (!tmpDir1.exists()) {
            tmpDir1.mkdirs();
        }


        //Install base dir
        File dir = new File(Config.getBasefileDir());
        if (dir.exists() && dir.isDirectory()) {
            //don't create again
        } else if (dir.exists() && !dir.isDirectory()) {
            Log.v("Installer", "Could not create Dir, file found: " + Config.getBasefileDir());
            return;
        } else if (!dir.exists()) {
            dir.mkdir();
        }

        String destDir = Config.getBasefileDir();

        //Get each file in assets under ./roms/ and install in SDCARD
        AssetManager am = context.getResources().getAssets();
        String[] files = null;
        try {
            files = am.list("roms");
        } catch (IOException ex) {
            Logger.getLogger(FileInstaller.class.getName()).log(Level.SEVERE, null, ex);
            Log.v("Installer", "Could not install files: " + ex.getMessage());
            return;
        }

        for (int i = 0; i < files.length; i++) {
            //Log.v("Installer", "File: " + files[i]);
            String[] subfiles = null;
            try {
                subfiles = am.list("roms/" + files[i]);
            } catch (IOException ex) {
                Logger.getLogger(FileInstaller.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (subfiles != null && subfiles.length > 0) {
                //Install base dir
                File dir1 = new File(Config.getBasefileDir() + files[i]);
                if (dir1.exists() && dir1.isDirectory()) {
                    //don't create again
                } else if (dir1.exists() && !dir1.isDirectory()) {
                    Log.v("Installer", "Could not create Dir, file found: " + Config.getBasefileDir() + files[i]);
                    return;
                } else if (!dir1.exists()) {
                    dir1.mkdir();
                }
                for (int k = 0; k < subfiles.length; k++) {

                    File file = new File(destDir, files[i] + "/" + subfiles[k]);
                    if(!file.exists() || force) {
                        Log.v("Installer", "Installing file: " + file.getPath());
                        installAssetFile(context, files[i] + "/" + subfiles[k], destDir, "roms", null);
                    }
                }
            } else {
                File file = new File(destDir, files[i]);
                if(!file.exists() || force) {
                    Log.v("Installer", "Installing file: " + file.getPath());
                    installAssetFile(context, files[i], Config.getBasefileDir(), "roms", null);
                }
            }
        }
//        InputStream is = am.open(srcFile);

    }

    public static boolean installAssetFile(Context context, String srcFile,
                                           String destDir, String assetsDir, String destFile) {
        try {
            File destDirF = new File(destDir);
            if (!destDirF.exists()) {
                boolean res = destDirF.mkdirs();
                if (!res && context instanceof Activity activity) {
                    UIUtils.toastShort(activity, "Could not create directory for image");
                }
            }

            String resolvedDestFile = destFile == null ? srcFile : destFile;
            File outputFile = new File(destDirF, resolvedDestFile);
            File tempFile = new File(destDirF, resolvedDestFile + ".tmp-" + Thread.currentThread().getId());
            ReentrantReadWriteLock.WriteLock lock = lockForPath(outputFile.getAbsolutePath()).writeLock();
            lock.lock();
            try {
                AssetManager am = context.getResources().getAssets();
                try (InputStream is = am.open(assetsDir + "/" + srcFile);
                     OutputStream os = new FileOutputStream(tempFile)) {
                    copyStream(is, os);
                    os.flush();
                }
                moveTempFileToDestination(tempFile, outputFile);
                Log.i("Installer", "event=install_asset success src=" + srcFile + " dest=" + outputFile.getAbsolutePath());
            } finally {
                cleanupTempFile(tempFile, "install_asset");
                lock.unlock();
            }
            return true;
        } catch (Exception ex) {
            Log.e("Installer", "event=install_asset failure src=" + srcFile + " dest=" + destFile, ex);
            return false;
        }
    }

    public static Uri installImageTemplateToSDCard(Activity activity, String srcFile,
                                                   Uri destDir, String assetsDir, String destFile) {

        String resolvedDestFile = destFile == null ? srcFile : destFile;
        try {

            DocumentFile dir = DocumentFile.fromTreeUri(activity, destDir);
            if (dir == null) {
                Log.e("Installer", "event=install_sdcard failure reason=invalid_destination destDir=" + destDir);
                return null;
            }

            //Create the file if doesn't exist
            DocumentFile destFileF = dir.findFile(resolvedDestFile);
            if(destFileF == null) {
                destFileF = dir.createFile("application/octet-stream", resolvedDestFile);
                if (destFileF == null) {
                    Log.e("Installer", "event=install_sdcard failure reason=create_file_failed destFile=" + resolvedDestFile);
                    return null;
                }
            }
            else {
                UIUtils.toastShort(activity, "File exists, choose another filename");
                return null;
            }

            //Write to the dest
            AssetManager am = activity.getResources().getAssets(); // get the local asset manager
            try (InputStream is = am.open(assetsDir + "/" + srcFile);
                 OutputStream os = activity.getContentResolver().openOutputStream(destFileF.getUri())) {
                if (os == null) {
                    Log.e("Installer", "event=install_sdcard failure reason=open_output_stream_failed uri=" + destFileF.getUri());
                    return null;
                }
                copyStream(is, os);
                os.flush();
            }

            //success
            Log.i("Installer", "event=install_sdcard success src=" + srcFile + " destUri=" + destFileF.getUri());
            return destFileF.getUri();

        } catch (Exception ex) {
            Log.e("Installer", "event=install_sdcard failure src=" + srcFile + " destFile=" + resolvedDestFile, ex);
        }
        return null;
    }


    public static String installImageTemplateToExternalStorage(Activity activity, String srcFile,
                                                   String destDir, String assetsDir, String destFile) {

        String resolvedDestFile = destFile == null ? srcFile : destFile;
        File file = new File(destDir, resolvedDestFile);
        File tempFile = new File(destDir, resolvedDestFile + ".tmp-" + Thread.currentThread().getId());
        ReentrantReadWriteLock.WriteLock lock = lockForPath(file.getAbsolutePath()).writeLock();
        lock.lock();
        try {
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                Log.e("Installer", "event=install_external failure reason=create_parent_dir_failed dir=" + parentDir.getAbsolutePath());
                return null;
            }

            //Create the file if doesn't exist
            if (file.exists()) {
                UIUtils.toastShort(activity, "File exists, choose another filename");
                return null;
            }

            //Write to the dest
            AssetManager am = activity.getResources().getAssets(); // get the local asset manager
            try (InputStream is = am.open(assetsDir + "/" + srcFile);
                 OutputStream os = new FileOutputStream(tempFile)) {
                copyStream(is, os);
                os.flush();
            }
            moveTempFileToDestination(tempFile, file);

            //success
            Log.i("Installer", "event=install_external success src=" + srcFile + " dest=" + file.getAbsolutePath());
            return file.getAbsolutePath();

        } catch (Exception ex) {
            Log.e("Installer", "event=install_external failure src=" + srcFile + " dest=" + file.getAbsolutePath(), ex);
        } finally {
            cleanupTempFile(tempFile, "install_external");
            lock.unlock();
        }
        return null;
    }
}
