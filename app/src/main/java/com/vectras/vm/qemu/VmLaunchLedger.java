package com.vectras.vm.qemu;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;

/**
 * VmLaunchLedger: append-only deterministic launch contract log.
 */
public final class VmLaunchLedger {

    private static final int MAX_ENV_BYTES = 4096;
    private static final int RECORD_HEADER_BYTES = 8 + 8 + 8 + 4 + 4 + 4 + 4 + 4;

    private VmLaunchLedger() {
        throw new AssertionError("VmLaunchLedger is a utility class and cannot be instantiated");
    }

    public static synchronized void append(Context context,
                                           String vmId,
                                           String profile,
                                           boolean headless,
                                           boolean kvmEnabled,
                                           String kvmReason,
                                           String env) {
        if (context == null) return;

        byte[] vmIdBytes = safeBytes(vmId);
        byte[] profileBytes = safeBytes(profile);
        byte[] reasonBytes = safeBytes(kvmReason);
        byte[] envBytes = safeBytes(env);
        if (envBytes.length > MAX_ENV_BYTES) {
            byte[] clipped = new byte[MAX_ENV_BYTES];
            for (int i = 0; i < MAX_ENV_BYTES; i++) {
                clipped[i] = envBytes[i];
            }
            envBytes = clipped;
        }

        long envMix = mix64(envBytes);
        long seq = estimateSeq(context);
        int flags = (headless ? 1 : 0) | (kvmEnabled ? 2 : 0);

        int payloadLen = 4 + vmIdBytes.length + 4 + profileBytes.length + 4 + reasonBytes.length + 4 + envBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_HEADER_BYTES + payloadLen);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0x564D4C4544474552L); // "VMLEDGER"
        buffer.putLong(seq);
        buffer.putLong(envMix);
        buffer.putInt(flags);
        buffer.putInt(vmIdBytes.length);
        buffer.putInt(profileBytes.length);
        buffer.putInt(reasonBytes.length);
        buffer.putInt(envBytes.length);
        buffer.put(vmIdBytes);
        buffer.put(profileBytes);
        buffer.put(reasonBytes);
        buffer.put(envBytes);

        CRC32C crc32c = new CRC32C();
        crc32c.update(buffer.array(), 0, buffer.position());
        int crc = (int) crc32c.getValue();

        ByteBuffer out = ByteBuffer.allocate(buffer.position() + 4).order(ByteOrder.LITTLE_ENDIAN);
        out.put(buffer.array(), 0, buffer.position());
        out.putInt(crc);

        File ledger = new File(context.getFilesDir(), "vm_launch_ledger.bin");
        try (FileOutputStream fos = new FileOutputStream(ledger, true)) {
            fos.write(out.array(), 0, out.position());
            fos.flush();
        } catch (IOException ignored) {
            // deterministic execution path should never crash on telemetry write
        }
    }

    private static long estimateSeq(Context context) {
        File ledger = new File(context.getFilesDir(), "vm_launch_ledger.bin");
        long bytes = ledger.exists() ? ledger.length() : 0L;
        if (bytes <= 0) return 0L;
        return bytes / 128L;
    }

    private static byte[] safeBytes(String s) {
        if (s == null) return new byte[0];
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static long mix64(byte[] data) {
        long x = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < data.length; i++) {
            x ^= (data[i] & 0xFFL) + 0x9E3779B97F4A7C15L + (x << 6) + (x >>> 2);
            x *= 0xBF58476D1CE4E5B9L;
            x ^= x >>> 32;
        }
        return x;
    }
}
