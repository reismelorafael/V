package com.vectras.vm.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vectras.vm.R;
import com.vectras.vm.utils.NotificationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DownloadWorker extends Worker {

    public static final String TAG = "DownloadWorker";
    public static final String WORK_NAME_PREFIX = "rom_download_";

    public static final String KEY_ROM_ID = "rom_id";
    public static final String KEY_URL = "download_url";
    public static final String KEY_FINAL_NAME = "final_name";
    public static final String KEY_EXPECTED_HASH = "expected_hash";

    public static final String KEY_OUTPUT_PATH = "output_path";

    public static final String DOWNLOAD_CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID_BASE = 40000;

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String romId = getInputData().getString(KEY_ROM_ID);
        String url = getInputData().getString(KEY_URL);
        String finalName = getInputData().getString(KEY_FINAL_NAME);
        String expectedHash = getInputData().getString(KEY_EXPECTED_HASH);

        if (isBlank(romId) || isBlank(url) || isBlank(finalName)) {
            Log.e(TAG, "Missing required input data");
            return Result.failure();
        }

        createDownloadChannel();
        setForegroundAsync(buildForegroundInfo(0, finalName));

        File downloadDir = getApplicationContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null && getApplicationContext().getFilesDir() != null) {
            downloadDir = new File(getApplicationContext().getFilesDir(), "downloads");
        }
        if (downloadDir == null) {
            return Result.failure();
        }

        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            Log.e(TAG, "Unable to create download directory: " + downloadDir);
            return Result.failure();
        }

        File partialFile = new File(downloadDir, finalName + ".part");
        File targetFile = new File(downloadDir, finalName);

        try {
            downloadToPartial(url, partialFile, finalName);

            if (!isBlank(expectedHash)) {
                String actualHash = sha256(partialFile);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    Log.e(TAG, "Hash mismatch for " + finalName + " expected=" + expectedHash + " actual=" + actualHash);
                    partialFile.delete();
                    return Result.failure();
                }
            }

            if (targetFile.exists() && !targetFile.delete()) {
                Log.e(TAG, "Unable to replace existing file " + targetFile);
                return Result.failure();
            }

            if (!partialFile.renameTo(targetFile)) {
                Log.e(TAG, "Unable to move partial file to final path");
                return Result.failure();
            }

            setForegroundAsync(buildForegroundInfo(100, finalName));
            Data output = new Data.Builder().putString(KEY_OUTPUT_PATH, targetFile.getAbsolutePath()).build();
            return Result.success(output);
        } catch (IOException networkError) {
            Log.e(TAG, "Download failed, will retry", networkError);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return Result.failure();
        }
    }

    private void downloadToPartial(String sourceUrl, File partialFile, String finalName) throws IOException {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("GET");
            connection.connect();

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Unexpected HTTP status: " + statusCode);
            }

            int contentLength = connection.getContentLength();
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(partialFile, false);

            byte[] buffer = new byte[8192];
            long downloaded = 0L;
            int bytesRead;
            int lastProgress = -1;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (isStopped()) {
                    throw new IOException("Work cancelled");
                }
                outputStream.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                if (contentLength > 0) {
                    int progress = (int) ((downloaded * 100L) / contentLength);
                    if (progress != lastProgress) {
                        setProgressAsync(new Data.Builder().putInt("progress", progress).build());
                        setForegroundAsync(buildForegroundInfo(progress, finalName));
                        lastProgress = progress;
                    }
                }
            }
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ForegroundInfo buildForegroundInfo(int progress, String fileName) {
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Downloading ROM")
                .setContentText(fileName)
                .setOnlyAlertOnce(true)
                .setOngoing(progress < 100)
                .setProgress(100, Math.max(0, Math.min(progress, 100)), false)
                .build();

        return new ForegroundInfo(NOTIFICATION_ID_BASE + Math.abs(getId().hashCode() % 10000), notification);
    }

    private void createDownloadChannel() {
        NotificationUtils.createChannel(
                "ROM Downloads",
                "Background ROM download progress",
                DOWNLOAD_CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW,
                getApplicationContext());
    }

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream inputStream = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            inputStream.close();
        }

        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
