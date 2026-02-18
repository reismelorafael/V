package com.vectras.vm.core;

/**
 * Bridge JNI opcional para rastreamento de estado de fluxo VM em memória nativa.
 */
final class VmFlowNativeBridge {
    private static final boolean AVAILABLE;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("vectra_core_accel");
            loaded = nativeVmFlowInit() == 1;
        } catch (Throwable ignored) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private VmFlowNativeBridge() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    static void mark(int vmHash, int stateOrdinal) {
        if (!AVAILABLE) return;
        nativeVmFlowMark(vmHash, stateOrdinal);
    }

    static int current(int vmHash) {
        if (!AVAILABLE) return -1;
        return nativeVmFlowCurrent(vmHash);
    }

    private static native int nativeVmFlowInit();
    private static native void nativeVmFlowMark(int vmHash, int stateOrdinal);
    private static native int nativeVmFlowCurrent(int vmHash);
}
