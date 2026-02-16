package com.vectras.vm.core;

import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Operações runtime de processo centralizadas para reduzir redundância,
 * manter compatibilidade entre toolchains e preservar comportamento determinístico.
 */
public final class ProcessRuntimeOps {

    private ProcessRuntimeOps() {
        throw new AssertionError("ProcessRuntimeOps is utility-only");
    }

    public static long monoMs() {
        return SystemClock.elapsedRealtime();
    }

    public static long wallMs() {
        return System.currentTimeMillis();
    }

    public static long safePid(Process process) {
        if (process == null) return -1L;
        try {
            Method method = Process.class.getMethod("pid");
            Object value = method.invoke(process);
            if (value instanceof Long) {
                long pid = (Long) value;
                if (pid > 0L) {
                    return pid;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            try {
                long reflectedPid = field.getLong(process);
                if (reflectedPid > 0L) {
                    return reflectedPid;
                }
            } finally {
                field.setAccessible(false);
            }
        } catch (Exception ignored) {
        }

        return -1L;
    }

    public static boolean isQmpAck(String result) {
        return result != null && result.contains("return");
    }
}
