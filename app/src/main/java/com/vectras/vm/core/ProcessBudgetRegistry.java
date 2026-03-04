package com.vectras.vm.core;

import android.os.Build;
import android.util.Log;

import com.vectras.vm.BuildConfig;

/**
 * Runtime process-budget resolver for VM supervision caps.
 * Priority: System property override > BuildConfig > hardcoded safety default.
 */
public final class ProcessBudgetRegistry {

    private static final String TAG = "ProcessBudgetRegistry";
    private static final String PROP_PROCESS_BUDGET = "vectras.process_budget_max";
    private static final String PROP_DEBUG_PROCESS_BUDGET = "debug.vectras.process_budget_max";

    private static final int HARDCODED_DEFAULT_LEGACY = 9;
    private static final int HARDCODED_DEFAULT_ANDROID15 = 6;
    private static final int HARD_MIN = 1;
    private static final int HARD_MAX = 32;

    private static final int RESOLVED_MAX;
    private static final String RESOLVED_SOURCE;

    static {
        Resolution resolution = resolveInternal();
        RESOLVED_MAX = resolution.max;
        RESOLVED_SOURCE = resolution.source;
        Log.i(TAG, "init sdk=" + Build.VERSION.SDK_INT
                + " resolvedMax=" + RESOLVED_MAX
                + " source=" + RESOLVED_SOURCE);
    }

    private ProcessBudgetRegistry() {
        throw new AssertionError("ProcessBudgetRegistry is a utility class and cannot be instantiated");
    }

    public static int getMaxSupervisedVmProcesses() {
        return RESOLVED_MAX;
    }

    public static String getResolvedSource() {
        return RESOLVED_SOURCE;
    }

    private static Resolution resolveInternal() {
        Integer fromProperty = parsePropertyOverride();
        if (fromProperty != null) {
            return new Resolution(clamp(fromProperty), "property");
        }

        int fromBuild = Build.VERSION.SDK_INT >= 35
                ? BuildConfig.PROCESS_BUDGET_MAX_ANDROID15
                : BuildConfig.PROCESS_BUDGET_MAX_DEFAULT;
        if (fromBuild > 0) {
            return new Resolution(clamp(fromBuild), "build");
        }

        int fromDefault = Build.VERSION.SDK_INT >= 35
                ? HARDCODED_DEFAULT_ANDROID15
                : HARDCODED_DEFAULT_LEGACY;
        return new Resolution(clamp(fromDefault), "default");
    }

    private static Integer parsePropertyOverride() {
        Integer debugValue = parseIntProperty(PROP_DEBUG_PROCESS_BUDGET);
        if (debugValue != null) {
            return debugValue;
        }
        return parseIntProperty(PROP_PROCESS_BUDGET);
    }

    private static Integer parseIntProperty(String key) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            Log.w(TAG, "Ignoring invalid property " + key + "=" + raw);
            return null;
        }
    }

    private static int clamp(int value) {
        if (value < HARD_MIN) {
            return HARD_MIN;
        }
        if (value > HARD_MAX) {
            return HARD_MAX;
        }
        return value;
    }

    private static final class Resolution {
        final int max;
        final String source;

        Resolution(int max, String source) {
            this.max = max;
            this.source = source;
        }
    }
}
