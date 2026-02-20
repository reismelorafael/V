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
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
    private static final long PROGRESS_PERSIST_INTERVAL_MS = 1000L;

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

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
        long existingPartialBytes = partialFile.exists() ? partialFile.length() : 0L;
        DownloadStateStore stateStore = new DownloadStateStore(getApplicationContext());
        stateStore.upsert(new DownloadItemState(
                romId,
                url,
                partialFile.getAbsolutePath(),
                targetFile.getAbsolutePath(),
                existingPartialBytes,
                0L,
                expectedHash,
                DownloadStatus.RUNNING,
                System.currentTimeMillis()
        ));

        try {
            DownloadResult downloadResult = downloadToPartial(romId, url, partialFile, finalName, stateStore);

            if (!isBlank(expectedHash)) {
                String actualHash = sha256(partialFile);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    Log.e(TAG, "Hash mismatch for " + finalName + " expected=" + expectedHash + " actual=" + actualHash);
                    partialFile.delete();
                    stateStore.updateStatus(romId, DownloadStatus.HASH_MISMATCH);
                    return Result.failure();
                }
            }

            if (targetFile.exists() && !targetFile.delete()) {
                Log.e(TAG, "Unable to replace existing file " + targetFile);
                stateStore.updateStatus(romId, DownloadStatus.FAILED);
                return Result.failure();
            }

            if (!partialFile.renameTo(targetFile)) {
                Log.e(TAG, "Unable to move partial file to final path");
                stateStore.updateStatus(romId, DownloadStatus.FAILED);
                return Result.failure();
            }

            long finalSize = targetFile.length();
            if (finalSize != downloadResult.downloadedBytes) {
                Log.e(TAG, "Final file size differs from downloaded bytes for " + finalName + " downloaded=" + downloadResult.downloadedBytes + " actual=" + finalSize);
                targetFile.delete();
                stateStore.updateStatus(romId, DownloadStatus.FAILED);
                return Result.failure();
            }
            if (downloadResult.totalBytes > 0 && finalSize != downloadResult.totalBytes) {
                Log.e(TAG, "Final size mismatch for " + finalName + " expected=" + downloadResult.totalBytes + " actual=" + finalSize);
                targetFile.delete();
                stateStore.updateStatus(romId, DownloadStatus.FAILED);
                return Result.failure();
            }

            setForegroundAsync(buildForegroundInfo(100, finalName));
            stateStore.updateProgress(romId, finalSize, finalSize, DownloadStatus.COMPLETED);
            Data output = new Data.Builder().putString(KEY_OUTPUT_PATH, targetFile.getAbsolutePath()).build();
            return Result.success(output);
        } catch (IOException networkError) {
            String message = networkError.getMessage();
            if (message != null && message.contains("Work cancelled")) {
                stateStore.updateStatus(romId, DownloadStatus.CANCELED);
                return Result.failure();
            }
            Log.e(TAG, "Download failed, will retry", networkError);
            stateStore.updateStatus(romId, DownloadStatus.FAILED);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            stateStore.updateStatus(romId, DownloadStatus.FAILED);
            return Result.failure();
        }
    }

    private DownloadResult downloadToPartial(String romId,
                                             String sourceUrl,
                                             File partialFile,
                                             String finalName,
                                             DownloadStateStore stateStore) throws IOException {
        String host = extractHost(sourceUrl);
        long partialOffset = partialFile.exists() ? partialFile.length() : 0L;
        Boolean cachedResumeSupport = stateStore.getResumeSupported(sourceUrl, host);
        boolean tryResume = partialOffset > 0L && !Boolean.FALSE.equals(cachedResumeSupport);

        for (int attempt = 0; attempt < 2; attempt++) {
            long currentOffset = partialFile.exists() ? partialFile.length() : 0L;

            Request.Builder requestBuilder = new Request.Builder().url(sourceUrl).get();
            if (tryResume && currentOffset > 0L) {
                requestBuilder.header("Range", "bytes=" + currentOffset + "-");
            }

            try (Response response = HTTP_CLIENT.newCall(requestBuilder.build()).execute()) {
                int statusCode = response.code();

                if (tryResume && currentOffset > 0L) {
                    String contentRange = response.header("Content-Range");
                    boolean coherentPartial = statusCode == 206 && isCoherentContentRange(contentRange, currentOffset);
                    if (coherentPartial) {
                        stateStore.setResumeSupported(sourceUrl, host, true);
                        return streamResponseToFile(romId, response, partialFile, finalName, stateStore, true, currentOffset, contentRange);
                    }

                    if (statusCode == 200 || statusCode == 416 || statusCode == 206) {
                        stateStore.setResumeSupported(sourceUrl, host, false);
                        truncatePartialFile(partialFile);
                        stateStore.updateProgress(romId, 0L, 0L, DownloadStatus.RUNNING);
                        tryResume = false;
                        continue;
                    }
                }

                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("Unexpected HTTP status: " + statusCode);
                }

                if (tryResume) {
                    stateStore.setResumeSupported(sourceUrl, host, false);
                    truncatePartialFile(partialFile);
                    stateStore.updateProgress(romId, 0L, 0L, DownloadStatus.RUNNING);
                }
                return streamResponseToFile(romId, response, partialFile, finalName, stateStore, false, 0L, null);
            }
        }

        throw new IOException("Unable to start download after resume fallback");
    }

    private DownloadResult streamResponseToFile(String romId,
                                                Response response,
                                                File partialFile,
                                                String finalName,
                                                DownloadStateStore stateStore,
                                                boolean append,
                                                long startOffset,
                                                String contentRangeHeader) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Empty response body");
        }

        long responseContentLength = body.contentLength();
        long totalBytes;
        if (append) {
            long totalFromRange = extractTotalBytesFromContentRange(contentRangeHeader);
            if (totalFromRange > 0L) {
                totalBytes = totalFromRange;
            } else if (responseContentLength > 0L) {
                totalBytes = startOffset + responseContentLength;
            } else {
                totalBytes = -1L;
            }
        } else {
            totalBytes = responseContentLength > 0L ? responseContentLength : -1L;
        }

        long downloaded = append ? startOffset : 0L;
        long writtenInThisResponse = 0L;
        int lastProgress = -1;
        long lastPersistAt = 0L;

        try (InputStream inputStream = body.byteStream();
             FileOutputStream outputStream = new FileOutputStream(partialFile, append)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (isStopped()) {
                    stateStore.updateStatus(romId, DownloadStatus.CANCELED);
                    throw new IOException("Work cancelled");
                }
                outputStream.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                writtenInThisResponse += bytesRead;

                if (totalBytes > 0L) {
                    int progress = (int) ((downloaded * 100L) / totalBytes);
                    if (progress != lastProgress) {
                        setProgressAsync(new Data.Builder().putInt("progress", progress).build());
                        setForegroundAsync(buildForegroundInfo(progress, finalName));
                        lastProgress = progress;
                    }
                }

                long now = System.currentTimeMillis();
                if (now - lastPersistAt >= PROGRESS_PERSIST_INTERVAL_MS) {
                    stateStore.updateProgress(romId, downloaded, totalBytes, DownloadStatus.RUNNING);
                    lastPersistAt = now;
                }
            }
            outputStream.flush();
        }

        if (responseContentLength >= 0L && writtenInThisResponse != responseContentLength) {
            throw new IOException("Incomplete download body: expected=" + responseContentLength + " actual=" + writtenInThisResponse);
        }

        long fileBytes = partialFile.length();
        if (fileBytes != downloaded) {
            throw new IOException("Mismatch between tracked bytes and file length: tracked=" + downloaded + " file=" + fileBytes);
        }
        if (totalBytes > 0L && fileBytes != totalBytes) {
            throw new IOException("Incomplete file size: expected=" + totalBytes + " actual=" + fileBytes);
        }

        stateStore.updateProgress(romId, downloaded, totalBytes > 0L ? totalBytes : downloaded, DownloadStatus.RUNNING);
        return new DownloadResult(downloaded, totalBytes > 0L ? totalBytes : downloaded);
    }

    private static boolean isCoherentContentRange(String header, long expectedOffset) {
        if (isBlank(header)) {
            return false;
        }
        int firstSpace = header.indexOf(' ');
        int slash = header.indexOf('/');
        int dash = header.indexOf('-');
        if (firstSpace < 0 || dash < 0 || slash < 0 || dash < firstSpace) {
            return false;
        }

        String unit = header.substring(0, firstSpace).trim();
        if (!"bytes".equalsIgnoreCase(unit)) {
            return false;
        }

        String startPart = header.substring(firstSpace + 1, dash).trim();
        try {
            long start = Long.parseLong(startPart);
            return start == expectedOffset;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static long extractTotalBytesFromContentRange(String header) {
        if (isBlank(header)) {
            return -1L;
        }
        int slash = header.indexOf('/');
        if (slash < 0 || slash + 1 >= header.length()) {
            return -1L;
        }
        String totalPart = header.substring(slash + 1).trim();
        if ("*".equals(totalPart)) {
            return -1L;
        }
        try {
            return Long.parseLong(totalPart);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static void truncatePartialFile(File partialFile) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(partialFile, false);
        outputStream.close();
    }

    private static String extractHost(@NonNull String sourceUrl) {
        try {
            return new URL(sourceUrl).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    static DownloadItemState buildInitialState(@NonNull Context context,
                                               @NonNull String romId,
                                               @NonNull String url,
                                               @NonNull String finalName,
                                               String expectedHash) {
        File downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null && context.getFilesDir() != null) {
            downloadDir = new File(context.getFilesDir(), "downloads");
        }
        File partialFile = downloadDir == null ? null : new File(downloadDir, finalName + ".part");
        File targetFile = downloadDir == null ? null : new File(downloadDir, finalName);
        return new DownloadItemState(
                romId,
                url,
                partialFile == null ? null : partialFile.getAbsolutePath(),
                targetFile == null ? null : targetFile.getAbsolutePath(),
                0L,
                0L,
                expectedHash,
                DownloadStatus.QUEUED,
                System.currentTimeMillis()
        );
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

    private static final class DownloadResult {
        final long downloadedBytes;
        final long totalBytes;

        DownloadResult(long downloadedBytes, long totalBytes) {
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
        }
    }
}
