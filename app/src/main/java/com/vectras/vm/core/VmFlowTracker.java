package com.vectras.vm.core;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.vectras.vm.audit.AuditEvent;
import com.vectras.vm.audit.AuditLedger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracker canônico de estados de fluxo da VM, com trilha em AuditLedger.
 */
public final class VmFlowTracker {
    private static final String TAG = "VmFlowTracker";
    private static final ConcurrentHashMap<String, VmFlowState> STATES = new ConcurrentHashMap<>();

    private VmFlowTracker() {
    }

    public static void mark(Context context,
                            String vmId,
                            VmFlowState to,
                            String causeCode,
                            String actionTaken) {
        if (to == null) {
            return;
        }

        String key = normalizeVmId(vmId);
        VmFlowState from = STATES.getOrDefault(key, VmFlowState.IDLE);
        STATES.put(key, to);

        AuditLedger.record(context, new AuditEvent(
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis(),
                key,
                from.name(),
                to.name(),
                safe(causeCode, "flow_update"),
                0,
                0,
                0,
                safe(actionTaken, "track")
        ));

        Log.i(TAG, key + " " + from + " -> " + to + " cause=" + safe(causeCode, "flow_update"));
    }

    public static VmFlowState current(String vmId) {
        return STATES.getOrDefault(normalizeVmId(vmId), VmFlowState.IDLE);
    }

    private static String normalizeVmId(String vmId) {
        if (vmId == null) return "unknown";
        String normalized = vmId.trim();
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
