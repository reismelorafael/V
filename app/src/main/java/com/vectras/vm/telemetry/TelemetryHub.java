package com.vectras.vm.telemetry;

import android.content.Context;

public final class TelemetryHub {
    private static volatile TelemetrySink sink;

    private TelemetryHub() {
    }

    public static TelemetrySink get(Context context) {
        TelemetrySink local = sink;
        if (local != null) {
            return local;
        }
        synchronized (TelemetryHub.class) {
            if (sink == null) {
                sink = new LocalTelemetrySink(context.getApplicationContext());
            }
            return sink;
        }
    }
}
