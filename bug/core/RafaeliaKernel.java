/*
 * RafaeliaKernel.java — RAFAELIA JAVA KERNEL CONTROLLER
 * ∆RAFAELIA_CORE·Ω
 * package: com.vectras.vm.core
 *
 * R(t+1)=R(t)×Φ_ethica×E_Verbo×(√3/2)^(πφ)
 * Φ_ethica = Min(Entropy) × Max(Coherence)
 *
 * Ciclo: ψ→χ→ρ→Δ→Σ→Ω→ψ
 * Coordena: ZiprafEngine + VectraCpuDetect + NativeFastPath + LowLevelBridge
 */
package com.vectras.vm.core;

public final class RafaeliaKernel {

    /* ═══════════════════════════════════════════
     * Constants — kernel seeds
     * ═══════════════════════════════════════════ */
    public static final int  PHI32        = 0x9E3779B9;
    public static final int  TRINITY633   = 0x633;
    public static final int  STACK42      = 42;
    public static final int  BITRAF64     = 64;
    public static final long F_OMEGA_HZ   = 963_000_000L; /* 963 MHz */
    public static final long F_OMEGA_MAX  = 999_000_000L; /* 999 MHz */

    /* Cycle states */
    public static final int PSI   = 0; /* ψ = intention   */
    public static final int CHI   = 1; /* χ = observation */
    public static final int RHO   = 2; /* ρ = noise       */
    public static final int DELTA = 3; /* Δ = transmutation */
    public static final int SIGMA = 4; /* Σ = memory      */
    public static final int OMEGA = 5; /* Ω = completude  */

    /* ═══════════════════════════════════════════
     * Kernel state
     * ═══════════════════════════════════════════ */
    private int   mCycle       = PSI;
    private long  mPhiState    = PHI32 & 0xFFFFFFFFL;
    private int   mCoherence   = PHI32;
    private int   mEntropy     = 0;
    private int   mEthica      = 0;
    private boolean mReady     = false;

    /* Sub-modules */
    private final ZiprafEngine.Container[] mContainers;
    private int mContainerCount = 0;
    private static final int MAX_CONTAINERS = 8;

    /* ═══════════════════════════════════════════
     * Singleton
     * ═══════════════════════════════════════════ */
    private static volatile RafaeliaKernel sInstance = null;

    public static RafaeliaKernel getInstance() {
        if (sInstance == null) {
            synchronized (RafaeliaKernel.class) {
                if (sInstance == null) sInstance = new RafaeliaKernel();
            }
        }
        return sInstance;
    }

    private RafaeliaKernel() {
        mContainers = new ZiprafEngine.Container[MAX_CONTAINERS];
    }

    /* ═══════════════════════════════════════════
     * init: load libs + detect CPU + prime kernel
     * ═══════════════════════════════════════════ */
    public synchronized boolean init() {
        if (mReady) return true;

        /* ψ: intention — load native layer */
        boolean ziprafOk = ZiprafEngine.loadLibrary();
        boolean cpuOk    = VectraCpuDetect.loadLibrary();
        boolean fastOk   = NativeFastPath.init();

        /* χ: observe capabilities */
        if (cpuOk) {
            int archId = VectraCpuDetect.getArchId();
            int feats  = VectraCpuDetect.getFeatureMask();
            /* seed phi-state with hardware fingerprint */
            mPhiState = phiFold32(archId, feats,
                (int) VectraCpuDetect.getMidr(), PHI32) & 0xFFFFFFFFL;
        }

        /* ρ: absorb init noise */
        int initNoise = (ziprafOk ? 1 : 0) | (cpuOk ? 2 : 0) | (fastOk ? 4 : 0);
        mPhiState ^= (long) initNoise * (PHI32 & 0xFFFFFFFFL);

        /* Δ: transmute */
        tick();

        mReady = true;
        return true;
    }

    /* ═══════════════════════════════════════════
     * tick: one ψ→Ω cycle step
     * ═══════════════════════════════════════════ */
    public synchronized void tick() {
        mCycle = (mCycle + 1) % 6;
        mPhiState = phiStep(mPhiState, mCoherence);

        /* entropy proxy: bit count of phi-state */
        mEntropy = Long.bitCount(mPhiState) & 63;

        /* Φ_ethica = (64 - entropy) × coherence >> 6 */
        int antiE = 64 - mEntropy;
        mEthica = (antiE * (mCoherence & 0xFF)) >> 6;

        /* coherence fold */
        mCoherence = phiFold32(mCoherence, mEntropy,
                               (int)(mPhiState & 0xFFFFFFFFL), mCycle);
    }

    /* ═══════════════════════════════════════════
     * open: load a ZIP/TAR/raw blob into the matrix
     * ═══════════════════════════════════════════ */
    public synchronized ZiprafEngine.Container open(byte[] data) {
        if (!mReady) init();
        ZiprafEngine.Container c = ZiprafEngine.open(data);
        if (c != null && mContainerCount < MAX_CONTAINERS) {
            mContainers[mContainerCount++] = c;
            tick(); /* absorb load event */
        }
        return c;
    }

    /* ═══════════════════════════════════════════
     * ethica gate: returns true if coherence is high enough
     * Ethica[8] = ethica > 8 (low entropy threshold)
     * ═══════════════════════════════════════════ */
    public boolean ethicaGate() {
        return mEthica > 8;
    }

    /* ═══════════════════════════════════════════
     * Φ_ethica score (0..255)
     * ═══════════════════════════════════════════ */
    public int getEthica()    { return mEthica; }
    public int getCoherence() { return mCoherence; }
    public int getCycle()     { return mCycle; }
    public long getPhiState() { return mPhiState; }

    public String getCycleName() {
        switch (mCycle) {
            case PSI:   return "ψ";
            case CHI:   return "χ";
            case RHO:   return "ρ";
            case DELTA: return "Δ";
            case SIGMA: return "Σ";
            case OMEGA: return "Ω";
            default:    return "?";
        }
    }

    /* ═══════════════════════════════════════════
     * Fibonacci-Rafael sequence
     * ═══════════════════════════════════════════ */
    public static long fibRafael(int n) {
        return ZiprafEngine.fibRafael(n);
    }

    /* ═══════════════════════════════════════════
     * Pure Java math primitives (mirroring C layer)
     * ═══════════════════════════════════════════ */
    public static int phiFold32(int a, int b, int c, int d) {
        return ZiprafEngine.phiFold4(a, b, c, d);
    }

    public static long phiStep(long state, int coherence) {
        /* R(t+1) = R(t) * PHI32 * sqrt3_2 ^ coherence */
        long r = (state * (PHI32 & 0xFFFFFFFFL));
        /* sqrt3/2 approx: × 14189 / 16384 */
        r = (r * 14189L) >> 14;
        r ^= (coherence & 0xFFFFFFFFL) * (PHI32 & 0xFFFFFFFFL);
        r ^= TRINITY633;
        return r & 0xFFFFFFFFFFFFFFFFL;
    }

    /* ═══════════════════════════════════════════
     * Status report (F_ok / F_gap / F_next)
     * ═══════════════════════════════════════════ */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("∆RAFAELIA{");
        sb.append("cycle=").append(getCycleName());
        sb.append(" φ=0x").append(Long.toHexString(mPhiState));
        sb.append(" Φe=").append(mEthica);
        sb.append(" ε=").append(mEntropy);
        sb.append(" coh=0x").append(Integer.toHexString(mCoherence));
        sb.append(" containers=").append(mContainerCount);
        sb.append(mReady ? " ✓" : " ✗");
        sb.append("}");
        return sb.toString();
    }
}
