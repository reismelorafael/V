package com.vectras.vm.telemetry;

import org.json.JSONException;
import org.json.JSONObject;

public final class TelemetryRecord {
    private final String eventType;
    private final String stacktrace;
    private final JSONObject payload;

    private TelemetryRecord(String eventType, String stacktrace, JSONObject payload) {
        this.eventType = eventType;
        this.stacktrace = stacktrace;
        this.payload = payload;
    }

    public static TelemetryRecord event(String eventType, JSONObject payload) {
        return new TelemetryRecord(eventType, null, payload != null ? payload : new JSONObject());
    }

    public static TelemetryRecord crash(String eventType, Throwable throwable, JSONObject payload) {
        String stack = throwable == null ? "" : android.util.Log.getStackTraceString(throwable);
        return new TelemetryRecord(eventType, stack, payload != null ? payload : new JSONObject());
    }

    public static TelemetryRecord crash(String eventType, String stacktrace, JSONObject payload) {
        return new TelemetryRecord(eventType, stacktrace, payload != null ? payload : new JSONObject());
    }

    public String getEventType() {
        return eventType;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public JSONObject getPayloadCopy() {
        try {
            return TelemetrySchema.clonePayload(payload);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
