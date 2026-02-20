/*
 * NativeFastPath.java — NATIVE FAST PATH API
 * ∆RAFAELIA_CORE·Ω
 * package: com.vectras.vm.core
 *
 * JNI interface to vectra_core_accel.c:
 *   nativeInit              → rmr_jni_kernel_init
 *   nativeAsmBridgeMarker   → rmr_casm_bridge_marker
 *   nativeReadHardwareContract → rmr capabilities array
 */
package com.vectras.vm.core;

public final class NativeFastPath {

    /* Hardware contract indices (mirrors VECTRA_HW_CONTRACT_SIZE=10) */
    public static final int HWC_SIGNATURE       = 0;
    public static final int HWC_POINTER_BITS    = 1;
    public static final int HWC_CACHE_LINE      = 2;
    public static final int HWC_PAGE_BYTES      = 3;
    public static final int HWC_FEATURE_MASK    = 4;
    public static final int HWC_REG_SIG_0       = 5;
    public static final int HWC_REG_SIG_1       = 6;
    public static final int HWC_REG_SIG_2       = 7;
    public static final int HWC_GPIO_WORD_BITS  = 8;
    public static final int HWC_GPIO_PIN_STRIDE = 9;

    private static boolean sLoaded = false;
    private static int[]   sHwContract = null;

    public static synchronized boolean loadLibrary() {
        if (sLoaded) return true;
        try {
            System.loadLibrary("vectra_core_accel");
            sLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            sLoaded = false;
        }
        return sLoaded;
    }

    public static synchronized boolean init() {
        if (!loadLibrary()) return false;
        int rc = nativeInit();
        return rc == 0x52414641; /* RMR_UK_NATIVE_OK_MAGIC = "RAFA" */
    }

    public static int[] getHardwareContract() {
        if (sHwContract != null) return sHwContract;
        if (!loadLibrary()) return new int[10];
        sHwContract = nativeReadHardwareContract();
        return sHwContract != null ? sHwContract : new int[10];
    }

    public static int getCacheLineBytes() {
        int[] hc = getHardwareContract();
        return hc.length > HWC_CACHE_LINE ? hc[HWC_CACHE_LINE] : 64;
    }

    public static int getFeatureMask() {
        int[] hc = getHardwareContract();
        return hc.length > HWC_FEATURE_MASK ? hc[HWC_FEATURE_MASK] : 0;
    }

    public static boolean hasHwCrc32() {
        return (getFeatureMask() & (1 << 1)) != 0;
    }

    public static boolean hasNeon() {
        return (getFeatureMask() & (1 << 0)) != 0;
    }

    public static int getAsmMarker() {
        if (!loadLibrary()) return 0;
        return nativeAsmBridgeMarker();
    }

    /* ─── Natives ─── */
    private static native int    nativeInit();
    private static native int    nativeAsmBridgeMarker();
    private static native int[]  nativeReadHardwareContract();

    private NativeFastPath() {}
}
