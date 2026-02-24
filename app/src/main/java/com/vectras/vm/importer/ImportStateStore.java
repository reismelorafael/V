package com.vectras.vm.importer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;

public class ImportStateStore {
    private static final String PREF = "import_state_store";

    private final SharedPreferences sharedPreferences;

    public ImportStateStore(@NonNull Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void saveSessionResult(@NonNull String sessionId, @NonNull JSONArray result) {
        sharedPreferences.edit().putString(sessionId, result.toString()).apply();
    }

    @NonNull
    public JSONArray getSessionResult(@NonNull String sessionId) {
        try {
            return new JSONArray(sharedPreferences.getString(sessionId, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
