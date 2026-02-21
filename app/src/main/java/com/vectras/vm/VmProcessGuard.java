package com.vectras.vm;

import android.content.Context;
import android.util.Log;

/**
 * VmProcessGuard
 * - protege contra reentrância/lifecycle (ex.: Terminal/Termux UI)
 * - evita crash "process already bound" e deixa o app estável
 */
public final class VmProcessGuard {
    private static final String TAG = "VmProcessGuard";

    private VmProcessGuard() {}

    public static boolean tryRegister(Context context, String vmId, Process process) {
        try {
            VMManager.registerVmProcess(context, vmId, process);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "suppressed register crash vmId=" + vmId + " err=" + t.getMessage(), t);
            try {
                if (process != null && process.isAlive()) process.destroy();
            } catch (Throwable ignored) {}
            return false;
        }
    }
}
