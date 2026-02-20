package com.vectras.vm.download;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class DownloadStateStore {

    private static final String PREF_NAME = "download_item_state_store";
    private static final String KEY_IDS = "ids";
    private static final String KEY_PREFIX = "item_";
    private static final String KEY_RESUME_URL_PREFIX = "resume_url_";
    private static final String KEY_RESUME_HOST_PREFIX = "resume_host_";

    private final SharedPreferences sharedPreferences;

    public DownloadStateStore(@NonNull Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void upsert(@NonNull DownloadItemState state) {
        sharedPreferences.edit()
                .putString(KEY_PREFIX + state.id, toJson(state).toString())
                .putStringSet(KEY_IDS, withId(state.id))
                .apply();
    }

    @Nullable
    public synchronized DownloadItemState getById(@NonNull String id) {
        String value = sharedPreferences.getString(KEY_PREFIX + id, null);
        if (value == null) {
            return null;
        }
        try {
            return fromJson(new JSONObject(value));
        } catch (JSONException ignored) {
            return null;
        }
    }

    @NonNull
    public synchronized List<DownloadItemState> getAll() {
        Set<String> ids = sharedPreferences.getStringSet(KEY_IDS, new HashSet<>());
        List<DownloadItemState> states = new ArrayList<>();
        if (ids == null) {
            return states;
        }
        for (String id : ids) {
            DownloadItemState itemState = getById(id);
            if (itemState != null) {
                states.add(itemState);
            }
        }
        return states;
    }

    public synchronized void updateStatus(@NonNull String id, @NonNull String status) {
        DownloadItemState state = getById(id);
        if (state == null) {
            return;
        }
        state.status = DownloadStatus.sanitize(status);
        state.updatedAt = System.currentTimeMillis();
        upsert(state);
    }

    public synchronized void updateProgress(@NonNull String id, long bytesDownloaded, long totalBytes, @NonNull String status) {
        DownloadItemState state = getById(id);
        if (state == null) {
            return;
        }
        state.bytesDownloaded = bytesDownloaded;
        state.totalBytes = totalBytes;
        state.status = DownloadStatus.sanitize(status);
        state.updatedAt = System.currentTimeMillis();
        upsert(state);
    }

    public synchronized void remove(@NonNull String id) {
        Set<String> ids = sharedPreferences.getStringSet(KEY_IDS, new HashSet<>());
        Set<String> mutableIds = ids == null ? new HashSet<>() : new HashSet<>(ids);
        mutableIds.remove(id);

        sharedPreferences.edit()
                .remove(KEY_PREFIX + id)
                .putStringSet(KEY_IDS, mutableIds)
                .apply();
    }

    public synchronized void setResumeSupported(@NonNull String url, @Nullable String host, boolean supported) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_RESUME_URL_PREFIX + normalize(url), supported);
        if (host != null && !host.trim().isEmpty()) {
            editor.putBoolean(KEY_RESUME_HOST_PREFIX + normalize(host), supported);
        }
        editor.apply();
    }

    @Nullable
    public synchronized Boolean getResumeSupported(@NonNull String url, @Nullable String host) {
        String normalizedUrl = normalize(url);
        if (sharedPreferences.contains(KEY_RESUME_URL_PREFIX + normalizedUrl)) {
            return sharedPreferences.getBoolean(KEY_RESUME_URL_PREFIX + normalizedUrl, false);
        }

        if (host != null && !host.trim().isEmpty()) {
            String normalizedHost = normalize(host);
            if (sharedPreferences.contains(KEY_RESUME_HOST_PREFIX + normalizedHost)) {
                return sharedPreferences.getBoolean(KEY_RESUME_HOST_PREFIX + normalizedHost, false);
            }
        }
        return null;
    }

    @NonNull
    private Set<String> withId(@NonNull String id) {
        Set<String> ids = sharedPreferences.getStringSet(KEY_IDS, new HashSet<>());
        Set<String> mutableIds = ids == null ? new HashSet<>() : new HashSet<>(ids);
        mutableIds.add(id);
        return mutableIds;
    }

    @NonNull
    private static JSONObject toJson(@NonNull DownloadItemState state) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", state.id);
            object.put("url", state.url);
            object.put("partialPath", state.partialPath);
            object.put("finalPath", state.finalPath);
            object.put("bytesDownloaded", state.bytesDownloaded);
            object.put("totalBytes", state.totalBytes);
            object.put("expectedSha256", state.expectedSha256);
            object.put("status", state.status);
            object.put("updatedAt", state.updatedAt);
        } catch (JSONException ignored) {
        }
        return object;
    }

    @Nullable
    private static DownloadItemState fromJson(@NonNull JSONObject object) {
        String id = object.optString("id", null);
        String url = object.optString("url", null);
        if (id == null || id.trim().isEmpty() || url == null || url.trim().isEmpty()) {
            return null;
        }
        return new DownloadItemState(
                id,
                url,
                object.optString("partialPath", null),
                object.optString("finalPath", null),
                object.optLong("bytesDownloaded", 0L),
                object.optLong("totalBytes", 0L),
                object.optString("expectedSha256", null),
                object.optString("status", DownloadStatus.FAILED),
                object.optLong("updatedAt", System.currentTimeMillis())
        );
    }

    @NonNull
    public synchronized JSONArray dumpJson() {
        JSONArray array = new JSONArray();
        for (DownloadItemState state : getAll()) {
            array.put(toJson(state));
        }
        return array;
    }

    @NonNull
    private static String normalize(@NonNull String value) {
        return value.trim().toLowerCase(Locale.US);
    }
}
