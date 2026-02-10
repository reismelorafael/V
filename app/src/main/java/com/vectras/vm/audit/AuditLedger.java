package com.vectras.vm.audit;

import android.content.Context;

import com.vectras.vm.AppConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class AuditLedger {
    private static final long MAX_BYTES = 512 * 1024L;
    private static final String LEDGER_NAME = "audit-ledger.jsonl";
    private static final String LEDGER_ROTATED_NAME = "audit-ledger.prev.jsonl";

    public static synchronized void record(Context context, AuditEvent event) {
        try {
            File baseDir = context != null ? context.getFilesDir() : new File(AppConfig.internalDataDirPath);
            File ledger = new File(baseDir, LEDGER_NAME);
            rotateIfNeeded(ledger, new File(baseDir, LEDGER_ROTATED_NAME));
            try (FileWriter writer = new FileWriter(ledger, true)) {
                writer.write(event.toJsonLine());
                writer.write('\n');
            }
        } catch (IOException ignored) {
            // must never block main flow
        }
    }

    private static void rotateIfNeeded(File current, File rotated) {
        if (!current.exists() || current.length() < MAX_BYTES) {
            return;
        }
        if (rotated.exists()) {
            //noinspection ResultOfMethodCallIgnored
            rotated.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        current.renameTo(rotated);
    }
}
