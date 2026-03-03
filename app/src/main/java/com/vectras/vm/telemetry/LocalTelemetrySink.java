package com.vectras.vm.telemetry;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;
import java.nio.charset.StandardCharsets;

final class LocalTelemetrySink implements TelemetrySink {
    private static final String TAG = "LocalTelemetrySink";
    private static final int BITSTACK_MAGIC = 0x54454C45; // TELE
    private static final int BITSTACK_HEADER_BYTES = 32;
    private static final String CURSOR_FILE = "export.cursor";

    private final ReentrantLock lock = new ReentrantLock();
    private final File rootDir;
    private final File jsonlFile;
    private final File bitstackFile;
    private final TelemetryClock clock;
    private final String device;
    private final String appVersion;

    LocalTelemetrySink(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        rootDir = new File(appContext.getFilesDir(), "telemetry");
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        jsonlFile = new File(rootDir, "events.jsonl");
        bitstackFile = new File(rootDir, "events.bitstack");
        clock = new TelemetryClock(appContext);
        device = String.format(Locale.US, "%s/%s (%s)", Build.MANUFACTURER, Build.MODEL, Build.DEVICE);
        appVersion = resolveVersion(appContext);
    }

    @Override
    public void publish(TelemetryRecord record) {
        lock.lock();
        try {
            long seq = clock.nextSequence();
            long tick = clock.nextTick();
            long timestamp = System.currentTimeMillis();

            JSONObject event = TelemetrySchema.createCanonicalEnvelope(
                    seq,
                    tick,
                    timestamp,
                    device,
                    appVersion,
                    record != null ? record.getEventType() : "unknown",
                    record != null ? record.getStacktrace() : null,
                    record != null ? record.getPayloadCopy() : new JSONObject()
            );
            if (!TelemetrySchema.isValid(event)) {
                Log.w(TAG, "Dropping invalid telemetry event");
                return;
            }
            appendJsonl(event);
            appendBitstack(event.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to persist telemetry event", t);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public File exportDeterministicBatch(int maxEvents) {
        lock.lock();
        try {
            if (!jsonlFile.exists()) {
                return null;
            }
            int batchSize = Math.max(1, maxEvents);
            long cursor = readCursor();
            List<String> selected = new ArrayList<>();
            long minSeq = Long.MAX_VALUE;
            long maxSeq = cursor;

            try (BufferedReader reader = new BufferedReader(new FileReader(jsonlFile))) {
                String line;
                while ((line = reader.readLine()) != null && selected.size() < batchSize) {
                    JSONObject event = new JSONObject(line);
                    long seq = event.optLong(TelemetrySchema.FIELD_SEQUENCE, -1L);
                    if (seq <= cursor) {
                        continue;
                    }
                    selected.add(line);
                    minSeq = Math.min(minSeq, seq);
                    maxSeq = Math.max(maxSeq, seq);
                }
            }

            if (selected.isEmpty()) {
                return null;
            }

            File exportDir = new File(rootDir, "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File out = new File(exportDir, String.format(Locale.US, "batch_%020d_%020d.jsonl", minSeq, maxSeq));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(out, false))) {
                for (String line : selected) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            writeCursor(maxSeq);
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to export deterministic telemetry batch", t);
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void appendJsonl(JSONObject event) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonlFile, true))) {
            writer.write(event.toString());
            writer.newLine();
        }
    }

    private void appendBitstack(byte[] payload) throws IOException {
        CRC32 crc32 = new CRC32();
        crc32.update(payload);

        long seq = 0;
        long tick = 0;
        try {
            JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            seq = json.optLong(TelemetrySchema.FIELD_SEQUENCE, 0L);
            tick = json.optLong(TelemetrySchema.FIELD_TICK, 0L);
        } catch (JSONException ignored) {
        }

        ByteBuffer header = ByteBuffer.allocate(BITSTACK_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(BITSTACK_MAGIC);
        header.putInt(payload.length);
        header.putLong(seq);
        header.putLong(tick);
        header.putInt((int) crc32.getValue());
        header.putInt(0);

        try (FileOutputStream fos = new FileOutputStream(bitstackFile, true)) {
            fos.write(header.array());
            fos.write(payload);
        }
    }

    private long readCursor() {
        File file = new File(rootDir, CURSOR_FILE);
        if (!file.exists()) {
            return 0L;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? 0L : Long.parseLong(line.trim());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private void writeCursor(long seq) throws IOException {
        File file = new File(rootDir, CURSOR_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(Long.toString(seq));
        }
    }

    private String resolveVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            return String.format(Locale.US, "%s (%d)", info.versionName, code);
        } catch (Throwable e) {
            return "unknown";
        }
    }
}
