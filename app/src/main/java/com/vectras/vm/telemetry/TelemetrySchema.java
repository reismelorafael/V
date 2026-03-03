package com.vectras.vm.telemetry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public final class TelemetrySchema {
    public static final String FIELD_SEQUENCE = "sequence";
    public static final String FIELD_TICK = "tick";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_DEVICE = "device";
    public static final String FIELD_APP_VERSION = "appVersion";
    public static final String FIELD_EVENT_TYPE = "eventType";
    public static final String FIELD_STACKTRACE = "stacktrace";
    public static final String FIELD_PAYLOAD = "payload";

    private TelemetrySchema() {
    }

    public static JSONObject createCanonicalEnvelope(
            long sequence,
            long tick,
            long timestamp,
            String device,
            String appVersion,
            String eventType,
            String stacktrace,
            JSONObject payload
    ) throws JSONException {
        JSONObject normalizedPayload = payload != null ? payload : new JSONObject();
        JSONObject root = new JSONObject();
        root.put(FIELD_SEQUENCE, sequence);
        root.put(FIELD_TICK, tick);
        root.put(FIELD_TIMESTAMP, timestamp);
        root.put(FIELD_DEVICE, safeString(device));
        root.put(FIELD_APP_VERSION, safeString(appVersion));
        root.put(FIELD_EVENT_TYPE, safeString(eventType));
        if (stacktrace != null && !stacktrace.isEmpty()) {
            root.put(FIELD_STACKTRACE, stacktrace);
        }
        root.put(FIELD_PAYLOAD, normalizedPayload);
        return root;
    }

    public static boolean isValid(JSONObject event) {
        if (event == null) {
            return false;
        }
        boolean hasTimestamp = event.has(FIELD_TIMESTAMP);
        boolean hasDevice = event.has(FIELD_DEVICE);
        boolean hasVersion = event.has(FIELD_APP_VERSION);
        boolean hasType = event.has(FIELD_EVENT_TYPE);
        boolean hasStack = event.has(FIELD_STACKTRACE);
        return hasTimestamp && hasDevice && hasVersion && (hasType || hasStack);
    }

    public static JSONObject clonePayload(JSONObject source) throws JSONException {
        JSONObject copy = new JSONObject();
        if (source == null) {
            return copy;
        }
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            copy.put(key, source.opt(key));
        }
        return copy;
    }

    private static String safeString(String value) {
        return value == null ? "unknown" : value;
    }
}
