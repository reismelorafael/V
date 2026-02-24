package com.vectras.vm.importer;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vectras.vm.AppConfig;
import com.vectras.vm.utils.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ImportSessionWorker extends Worker {

    public static final String KEY_URIS = "import_uris";
    public static final String KEY_SESSION_ID = "session_id";

    public static final String PROGRESS_FILE_INDEX = "progress_file_index";
    public static final String PROGRESS_TOTAL_FILES = "progress_total_files";
    public static final String PROGRESS_CURRENT_FILE = "progress_current_file";
    public static final String PROGRESS_FILE_PERCENT = "progress_file_percent";
    public static final String PROGRESS_TOTAL_PERCENT = "progress_total_percent";

    private static final String TAG = "ImportSessionWorker";
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>();

    static {
        ALLOWED_EXTENSIONS.add("rom");
        ALLOWED_EXTENSIONS.add("iso");
        ALLOWED_EXTENSIONS.add("qcow2");
        ALLOWED_EXTENSIONS.add("img");
        ALLOWED_EXTENSIONS.add("zip");
        ALLOWED_EXTENSIONS.add("cvbi");
    }

    public ImportSessionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String[] uriItems = getInputData().getStringArray(KEY_URIS);
        String sessionId = getInputData().getString(KEY_SESSION_ID);
        if (uriItems == null || uriItems.length == 0 || TextUtils.isEmpty(sessionId)) {
            return Result.failure();
        }

        File importDir = new File(AppConfig.importedDriveFolder);
        if (!importDir.exists() && !importDir.mkdirs()) {
            return Result.failure();
        }

        long aggregateTotalBytes = 0L;
        List<Uri> uris = new ArrayList<>();
        ContentResolver resolver = getApplicationContext().getContentResolver();
        for (String raw : uriItems) {
            Uri uri = Uri.parse(raw);
            uris.add(uri);
            try (android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
                if (pfd != null && pfd.getStatSize() > 0) {
                    aggregateTotalBytes += pfd.getStatSize();
                }
            } catch (Exception ignored) {
            }
        }

        long aggregateCopied = 0L;
        JSONArray report = new JSONArray();

        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            String originalName = FileUtils.getFileNameFromUri(getApplicationContext(), uri);
            if (TextUtils.isEmpty(originalName)) {
                originalName = "import_" + System.currentTimeMillis();
            }
            String extension = extractExtension(originalName);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                appendReport(report, uri, originalName, false, "unsupported_extension");
                continue;
            }

            File destination = buildUniqueDestination(importDir, originalName);
            boolean itemSuccess = false;
            String reason = "ok";
            long itemBytesCopied = 0L;
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(destination)) {
                if (in == null) {
                    reason = "source_open_failed";
                } else {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (isStopped()) {
                            reason = "cancelled";
                            break;
                        }
                        out.write(buffer, 0, read);
                        itemBytesCopied += read;
                        aggregateCopied += read;

                        int filePercent = (int) Math.min(100,
                                estimatePercent(itemBytesCopied, resolver, uri));
                        int totalPercent = (int) Math.min(100,
                                aggregateTotalBytes <= 0 ? 0 : ((aggregateCopied * 100) / aggregateTotalBytes));

                        setProgressAsync(new Data.Builder()
                                .putInt(PROGRESS_FILE_INDEX, i + 1)
                                .putInt(PROGRESS_TOTAL_FILES, uris.size())
                                .putString(PROGRESS_CURRENT_FILE, originalName)
                                .putInt(PROGRESS_FILE_PERCENT, filePercent)
                                .putInt(PROGRESS_TOTAL_PERCENT, totalPercent)
                                .build());
                    }

                    if (!"cancelled".equals(reason)) {
                        out.flush();
                        itemSuccess = true;
                    }
                }
            } catch (Exception e) {
                reason = "copy_failed";
                Log.e(TAG, "Copy failed for uri=" + uri, e);
            }

            if (!itemSuccess && destination.exists() && !destination.delete()) {
                Log.w(TAG, "Unable to delete partial file: " + destination.getAbsolutePath());
            }

            appendReport(report, uri, originalName, itemSuccess, reason);
            if (isStopped()) {
                persistReport(sessionId, report);
                return Result.failure();
            }
        }

        persistReport(sessionId, report);
        return Result.success(new Data.Builder().putString("session_id", sessionId).build());
    }

    private static void appendReport(JSONArray report, Uri uri, String name, boolean success, String reason) {
        try {
            JSONObject item = new JSONObject();
            item.put("uri", uri.toString());
            item.put("name", name);
            item.put("success", success);
            item.put("reason", reason);
            report.put(item);
        } catch (JSONException ignored) {
        }
    }

    private void persistReport(String sessionId, JSONArray report) {
        ImportStateStore store = new ImportStateStore(getApplicationContext());
        store.saveSessionResult(sessionId, report);
    }

    private static long estimatePercent(long copied, ContentResolver resolver, Uri uri) {
        try (android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null || pfd.getStatSize() <= 0) {
                return copied > 0 ? 100 : 0;
            }
            return (copied * 100) / pfd.getStatSize();
        } catch (Exception e) {
            return copied > 0 ? 100 : 0;
        }
    }

    private static String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx + 1 >= fileName.length()) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static File buildUniqueDestination(File importDir, String fileName) {
        File destination = new File(importDir, fileName);
        if (!destination.exists()) {
            return destination;
        }

        String extension = "";
        String base = fileName;
        int idx = fileName.lastIndexOf('.');
        if (idx > 0) {
            extension = fileName.substring(idx);
            base = fileName.substring(0, idx);
        }

        int suffix = 1;
        while (destination.exists()) {
            destination = new File(importDir, base + "_" + suffix + extension);
            suffix++;
        }
        return destination;
    }
}
