package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.os.UserManager;
import android.system.Os;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;

import com.vectras.vm.R;
import com.termux.terminal.EmulatorDebug;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Install the  bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX folder below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging folder, $STAGING_PREFIX, is {@link #deleteFolder(File)} if left over from broken installation below.
 * <p/>
 * (4) The zip file is loaded from a shared library.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {

    /** Performs setup if necessary. */
    static void setupIfNeeded(final Activity activity, final Runnable whenDone) {
        //  can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        UserManager um = (UserManager) activity.getSystemService(Context.USER_SERVICE);
        boolean isPrimaryUser = um.getSerialNumberForUser(android.os.Process.myUserHandle()) == 0;
        if (!isPrimaryUser) {
            new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_not_primary_user_message)
                .setOnDismissListener(dialog -> {
                    Log.i(EmulatorDebug.LOG_TAG, "bootstrap_setup_exit: reason=not_primary_user action=finishAffinity");
                    activity.finishAffinity();
                }).setPositiveButton(android.R.string.ok, null).show();
            return;
        }

        final File PREFIX_FILE = new File(TermuxService.PREFIX_PATH);
        if (PREFIX_FILE.isDirectory()) {
            whenDone.run();
            return;
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread() {
            @Override
            public void run() {
                try {
                    final String STAGING_PREFIX_PATH = TermuxService.FILES_PATH + "usr-staging";
                    final File STAGING_PREFIX_FILE = new File(STAGING_PREFIX_PATH);

                    if (STAGING_PREFIX_FILE.exists()) {
                        deleteFolder(STAGING_PREFIX_FILE);
                    }

                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);

                    final byte[] zipBytes = loadZipBytes();
                    if (zipBytes == null || zipBytes.length == 0) {
                        final String errorMessage = "Bootstrap archive is empty or unavailable: loadZipBytes() returned null/empty data";
                        Log.e(EmulatorDebug.LOG_TAG, errorMessage);
                        throw new RuntimeException(errorMessage);
                    }

                    boolean symlinksFound = false;
                    try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                            if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                symlinksFound = true;
                                BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                                String line;
                                while ((line = symlinksReader.readLine()) != null) {
                                    String[] parts = line.split("←");
                                    if (parts.length != 2)
                                        throw new RuntimeException("Malformed symlink line: " + line);
                                    String oldPath = parts[0];
                                    File newPath;
                                    try {
                                        newPath = resolveWithinStaging(STAGING_PREFIX_FILE, parts[1]);
                                    } catch (RuntimeException e) {
                                        Log.e(EmulatorDebug.LOG_TAG, "Rejected symlink destination entry: " + line, e);
                                        throw e;
                                    }
                                    symlinks.add(Pair.create(oldPath, newPath.getAbsolutePath()));

                                    ensureDirectoryExists(newPath.getParentFile());
                                }
                            } else {
                                String zipEntryName = zipEntry.getName();
                                File targetFile;
                                try {
                                    targetFile = resolveWithinStaging(STAGING_PREFIX_FILE, zipEntryName);
                                } catch (RuntimeException e) {
                                    Log.e(EmulatorDebug.LOG_TAG, "Rejected zip entry: " + zipEntryName, e);
                                    throw e;
                                }
                                boolean isDirectory = zipEntry.isDirectory();

                                ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());

                                if (!isDirectory) {
                                    try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                        int readBytes;
                                        while ((readBytes = zipInput.read(buffer)) != -1)
                                            outStream.write(buffer, 0, readBytes);
                                    }
                                    if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") || zipEntryName.startsWith("lib/apt/methods")) {
                                        //noinspection OctalInteger
                                        Os.chmod(targetFile.getAbsolutePath(), 0700);
                                    }
                                }
                            }
                        }
                    }

                    if (!symlinksFound) {
                        final String errorMessage = "Bootstrap archive is invalid: SYMLINKS.txt entry was not found";
                        Log.e(EmulatorDebug.LOG_TAG, errorMessage);
                        throw new RuntimeException(errorMessage);
                    }

                    if (symlinks.isEmpty())
                        throw new RuntimeException("Bootstrap archive contains SYMLINKS.txt but no symlink definitions");
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }

                    if (!STAGING_PREFIX_FILE.renameTo(PREFIX_FILE)) {
                        throw new RuntimeException("Unable to rename staging folder");
                    }

                    activity.runOnUiThread(whenDone);
                } catch (final Exception e) {
                    Log.e(EmulatorDebug.LOG_TAG, "Bootstrap error", e);
                    activity.runOnUiThread(() -> {
                        try {
                            new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body)
                                .setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                                    dialog.dismiss();
                                    activity.finish();
                                }).setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                                    dialog.dismiss();
                                    TermuxInstaller.setupIfNeeded(activity, whenDone);
                                }).show();
                        } catch (WindowManager.BadTokenException e1) {
                            // Activity already dismissed - ignore.
                        }
                    });
                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }

    private static void ensureDirectoryExists(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create directory: " + directory.getAbsolutePath());
        }
    }

    static File resolveWithinStaging(File stagingRoot, String relativePath) {
        try {
            File path = new File(relativePath);
            if (path.isAbsolute()) {
                throw new RuntimeException("Absolute path is not allowed in bootstrap archive: " + relativePath);
            }

            File target = new File(stagingRoot, relativePath);
            String stagingRootCanonicalPath = stagingRoot.getCanonicalPath();
            String targetCanonicalPath = target.getCanonicalPath();
            String stagingRootWithSeparator = stagingRootCanonicalPath + File.separator;
            if (!targetCanonicalPath.equals(stagingRootCanonicalPath) && !targetCanonicalPath.startsWith(stagingRootWithSeparator)) {
                throw new RuntimeException("Path escapes bootstrap staging root: " + relativePath + " -> " + targetCanonicalPath);
            }
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve bootstrap path: " + relativePath, e);
        }
    }

    public static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        //System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    public static native byte[] getZip();

    /** Delete a folder and all its content or throw. Don't follow symlinks. */
    static void deleteFolder(File fileOrDirectory) throws IOException {
        if (fileOrDirectory.getCanonicalPath().equals(fileOrDirectory.getAbsolutePath()) && fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteFolder(child);
                }
            }
        }

        if (!fileOrDirectory.delete()) {
            throw new RuntimeException("Unable to delete " + (fileOrDirectory.isDirectory() ? "directory " : "file ") + fileOrDirectory.getAbsolutePath());
        }
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        new Thread() {
            public void run() {
                try {
                    File storageDir = new File(TermuxService.HOME_PATH, "storage");

                    if (storageDir.exists()) {
                        try {
                            deleteFolder(storageDir);
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "Could not delete old $HOME/storage, " + e.getMessage());
                            return;
                        }
                    }

                    if (!storageDir.mkdirs()) {
                        Log.e(LOG_TAG, "Unable to mkdirs() for $HOME/storage");
                        return;
                    }

                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                    final File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length > 1) {
                        for (int i = 1; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "external-" + i;
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error setting up link", e);
                }
            }
        }.start();
    }

}
