package com.vectras.vm.telemetry;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicLong;

final class TelemetryClock {
    private static final String PREFS = "telemetry_clock";
    private static final String KEY_SEQ = "seq";
    private static final String KEY_TICK = "tick";

    private final SharedPreferences prefs;
    private final AtomicLong seq;
    private final AtomicLong tick;

    TelemetryClock(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seq = new AtomicLong(Math.max(0L, prefs.getLong(KEY_SEQ, 0L)));
        tick = new AtomicLong(Math.max(0L, prefs.getLong(KEY_TICK, 0L)));
    }

    long nextSequence() {
        long value = seq.incrementAndGet();
        persist();
        return value;
    }

    long nextTick() {
        long value = tick.incrementAndGet();
        persist();
        return value;
    }

    private void persist() {
        prefs.edit().putLong(KEY_SEQ, seq.get()).putLong(KEY_TICK, tick.get()).apply();
    }
}
