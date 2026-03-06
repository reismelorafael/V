package com.vectras.vm.core;

/**
 * MathUtils: Stable mathematical implementations + RAFAELIA formula extensions.
 *
 * <p>Original utilities preserved. Added RAFAELIA section with:
 * Fibonacci-Rafael, Spiral√3/2, Trinity633, ToroidΔπφ, logical capacity,
 * information-theoretic formulas (items 16,17,19,20,21,22,29 of formula index).</p>
 */
public final class MathUtils {

    // ─── Original constants ────────────────────────────────────────────────────
    public static final double GOLDEN_RATIO  = 1.6180339887498948482;
    public static final long   GOLDEN_GAMMA  = 0x9E3779B97F4A7C15L;
    public static final long   MIX_CONST_A   = 0xBF58476D1CE4E5B9L;
    public static final long   MIX_CONST_B   = 0x94D049BB133111EBL;
    public static final int    CRC32C_POLY   = 0x82F63B78;
    public static final double LN_2          = 0.6931471805599453;
    public static final double LOG2_E        = 1.4426950408889634;

    // ─── RAFAELIA constants ────────────────────────────────────────────────────
    /**
     * √3/2 — Spiral base, coherence scale factor.
     * Used in Spiral(n) = (√3/2)^n and F_Rafael recursion.
     */
    public static final double SPIRAL_SQRT3_2 = 0.8660254037844386;

    /**
     * φ = (1+√5)/2 — golden ratio (same as GOLDEN_RATIO, alias for RAFAELIA notation).
     */
    public static final double PHI = GOLDEN_RATIO;

    /**
     * (√3/2)^(π×φ) — geometric ethical scale used in kernel update rule.
     */
    public static final double SPIRAL_PI_PHI = Math.pow(SPIRAL_SQRT3_2, Math.PI * PHI);

    /**
     * θ_999 in radians — used in Fibonacci-Rafael recursion.
     */
    public static final double THETA_999_RAD = Math.toRadians(999.0);

    private MathUtils() {
        throw new AssertionError("MathUtils is a utility class");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ORIGINAL METHODS (preserved)
    // ═══════════════════════════════════════════════════════════════════════════

    public static int log2Floor(int n) {
        if (n <= 0) throw new IllegalArgumentException("log2Floor requires positive input, got: " + n);
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    public static int log2Floor(long n) {
        if (n <= 0) throw new IllegalArgumentException("log2Floor requires positive input, got: " + n);
        return 63 - Long.numberOfLeadingZeros(n);
    }

    public static int log2Ceil(int n) {
        if (n <= 0) throw new IllegalArgumentException("log2Ceil requires positive input, got: " + n);
        if (n == 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    public static int isqrt(int n) {
        if (n < 0) throw new IllegalArgumentException("isqrt requires non-negative input, got: " + n);
        if (n == 0) return 0; if (n == 1) return 1;
        long nL = n, x = nL, y = (x + 1) / 2;
        while (y < x) { x = y; y = (x + nL / x) / 2; }
        return (int) x;
    }

    public static long isqrt(long n) {
        if (n < 0) throw new IllegalArgumentException("isqrt requires non-negative input, got: " + n);
        if (n == 0) return 0;
        long x = n, y = (x + 1) / 2;
        while (y < x) { x = y; y = (x + n / x) / 2; }
        return x;
    }

    public static boolean isPowerOfTwo(int n) { return n > 0 && (n & (n - 1)) == 0; }

    public static int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        n--; n |= n >> 1; n |= n >> 2; n |= n >> 4; n |= n >> 8; n |= n >> 16;
        return n + 1;
    }

    public static long mix64(long x) {
        x ^= (x >>> 30); x *= MIX_CONST_A;
        x ^= (x >>> 27); x *= MIX_CONST_B;
        x ^= (x >>> 31); return x;
    }

    public static long unmix64(long x) {
        x ^= (x >>> 31) ^ (x >>> 62); x *= 0x319642B2D24D8EC3L;
        x ^= (x >>> 27) ^ (x >>> 54); x *= 0x96DE1B173F119089L;
        x ^= (x >>> 30) ^ (x >>> 60); return x;
    }

    public static int popcount(int n) { return Integer.bitCount(n); }
    public static int parity(int n)   { return Integer.bitCount(n) & 1; }

    public static int idx4x4(int x, int y)              { return (y << 2) | x; }
    public static int getBit4x4(int b, int x, int y)    { return (b >>> idx4x4(x, y)) & 1; }
    public static int setBit4x4(int b, int x, int y, int v) {
        int i = idx4x4(x, y); int m = 1 << i;
        return (v == 0) ? (b & ~m) : (b | m);
    }

    public static int parity2D8(int bits16) {
        int p = 0;
        for (int y = 0; y < 4; y++) {
            int rp = 0;
            for (int x = 0; x < 4; x++) rp ^= getBit4x4(bits16, x, y);
            p |= (rp & 1) << (y + 4);
        }
        for (int x = 0; x < 4; x++) {
            int cp = 0;
            for (int y = 0; y < 4; y++) cp ^= getBit4x4(bits16, x, y);
            p |= (cp & 1) << x;
        }
        return p & 0xFF;
    }

    public static int syndrome(int stored, int computed) {
        return Integer.bitCount((stored ^ computed) & 0xFF);
    }

    public static int whoOutTriad(long cpu, long ram, long disk) {
        if (cpu == ram && cpu != disk) return 2;
        if (cpu == disk && cpu != ram) return 1;
        if (ram == disk && ram != cpu) return 0;
        return 3;
    }

    public static int addExact(int a, int b)        { return Math.addExact(a, b); }
    public static int multiplyExact(int a, int b)   { return Math.multiplyExact(a, b); }

    public static int divideExact(int dividend, int divisor) {
        if (divisor == 0) throw new ArithmeticException("Division by zero");
        if (dividend == Integer.MIN_VALUE && divisor == -1) throw new ArithmeticException("overflow");
        return dividend / divisor;
    }

    public static int  clamp(int  v, int  min, int  max) {
        if (min > max) throw new IllegalArgumentException("min>max");
        return Math.max(min, Math.min(max, v));
    }
    public static long clamp(long v, long min, long max) {
        if (min > max) throw new IllegalArgumentException("min>max");
        return Math.max(min, Math.min(max, v));
    }

    public static byte[] longToLittleEndian(long v) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) b[i] = (byte)((v >>> (8*i)) & 0xFF);
        return b;
    }

    public static long littleEndianToLong(byte[] bytes) {
        if (bytes.length != 8) throw new IllegalArgumentException("Expected 8 bytes");
        long v = 0;
        for (int i = 0; i < 8; i++) v |= ((long)(bytes[i] & 0xFF)) << (8*i);
        return v;
    }

    public static byte[] intToLittleEndian(int v) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) b[i] = (byte)((v >>> (8*i)) & 0xFF);
        return b;
    }

    public static int littleEndianToInt(byte[] bytes) {
        if (bytes.length != 4) throw new IllegalArgumentException("Expected 4 bytes");
        int v = 0;
        for (int i = 0; i < 4; i++) v |= (bytes[i] & 0xFF) << (8*i);
        return v;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RAFAELIA EXTENSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    // ─── Fibonacci-Rafael (formula 29) ────────────────────────────────────────

    /**
     * [E f29] F_Rafael(n+1) = F_Rafael(n)×(√3/2) + π×sin(θ_999)
     * One step of the modified Fibonacci for fractal coherence.
     *
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>fibRafaelStep(0.0) ≈ π×sin(θ_999) ≈ -2.8576</li>
     *   <li>fibRafaelStep(1.0) ≈ 0.866 + π×sin(θ_999)</li>
     * </ul>
     */
    public static double fibRafaelStep(double fn) {
        return fn * SPIRAL_SQRT3_2 + Math.PI * Math.sin(THETA_999_RAD);
    }

    /**
     * Generate F_Rafael sequence of given length starting from seed.
     *
     * @param seed   initial value F_Rafael(0)
     * @param length number of terms to generate
     * @return sequence array
     */
    public static double[] fibRafaelSequence(double seed, int length) {
        if (length <= 0) return new double[0];
        double[] seq = new double[length];
        seq[0] = seed;
        for (int i = 1; i < length; i++) seq[i] = fibRafaelStep(seq[i - 1]);
        return seq;
    }

    // ─── Spiral (formula 16) ──────────────────────────────────────────────────

    /**
     * [E f16] Spiral(n) = (√3/2)^n — coherence spiral for fractal hierarchy.
     *
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>spiral(0) = 1.0</li>
     *   <li>spiral(1) = 0.866...</li>
     *   <li>spiral(2) ≈ 0.750</li>
     *   <li>spiral(10) ≈ 0.237</li>
     * </ul>
     */
    public static double spiral(int n) {
        return Math.pow(SPIRAL_SQRT3_2, n);
    }

    // ─── Toroide (formula 17) ────────────────────────────────────────────────

    /**
     * [E f17] T_Δπφ = Δ·π·φ — toroidal energy parameter.
     *
     * @param delta transformation magnitude
     * @return toroidal parameter
     */
    public static double toroidDeltaPiPhi(double delta) {
        return delta * Math.PI * PHI;
    }

    // ─── Trinity633 (formula 19) ─────────────────────────────────────────────

    /**
     * [E f19] Trinity633 = Amor^6 · Luz^3 · Consciência^3
     *
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>trinity633(1,1,1) = 1.0</li>
     *   <li>trinity633(2,1,1) = 64.0 (2^6)</li>
     * </ul>
     */
    public static double trinity633(double amor, double luz, double consciencia) {
        return Math.pow(amor, 6) * Math.pow(luz, 3) * Math.pow(consciencia, 3);
    }

    // ─── Information capacity (formulas 20–22) ───────────────────────────────

    /**
     * [E f20] I = log2(S) — information content for S distinguishable states.
     *
     * @param states number of distinguishable states (must be > 0)
     * @return information in bits
     */
    public static double informationBits(long states) {
        if (states <= 0) return 0.0;
        return Math.log(states) / LN_2;
    }

    /**
     * [E f21] I_total = (N/b)·log2(Q)
     * Total information for a block of N physical bits with b bits/cell and Q valid states.
     *
     * @param physicalBits  N — number of physical bits
     * @param bitsPerCell   b — bits per physical cell
     * @param validStates   Q — valid logical states per cell
     * @return total information in bits
     */
    public static double informationTotal(long physicalBits, int bitsPerCell, long validStates) {
        if (bitsPerCell <= 0 || validStates <= 0) return 0.0;
        return ((double) physicalBits / bitsPerCell) * (Math.log(validStates) / LN_2);
    }

    /**
     * [E f22] C_l = C_f · (log2(S)/p) · d · (1 - r)
     * Logical capacity with redundancy correction.
     *
     * <p>Golden test vectors:</p>
     * <ul>
     *   <li>logicalCapacity(1024, 256, 1, 1.0, 0.0) ≈ 8192  (8 bits/symbol, no redundancy)</li>
     *   <li>logicalCapacity(1024, 256, 1, 1.0, 0.5) ≈ 4096  (50% redundancy)</li>
     * </ul>
     *
     * @param physCapacity physical capacity (C_f)
     * @param states       distinguishable states per symbol (S)
     * @param bitsPerSymbol bits per symbol (p)
     * @param dataFraction  data fraction d ∈ [0,1]
     * @param redundancy    redundancy fraction r ∈ [0,1]
     * @return logical capacity
     */
    public static double logicalCapacity(double physCapacity, long states,
                                         double bitsPerSymbol, double dataFraction,
                                         double redundancy) {
        if (bitsPerSymbol == 0 || states <= 0) return 0.0;
        double log2S = Math.log(states) / LN_2;
        return physCapacity * (log2S / bitsPerSymbol) * dataFraction * (1.0 - redundancy);
    }

    // ─── Entropy / Coherence helpers ─────────────────────────────────────────

    /**
     * Normalised Shannon entropy of a probability distribution.
     * Returns value in [0,1] where 1 = maximum entropy.
     *
     * @param probs probability array (should sum to 1)
     * @return normalised entropy
     */
    public static double shannonEntropy(double[] probs) {
        if (probs == null || probs.length == 0) return 0.0;
        double h = 0.0;
        for (double p : probs) {
            if (p > 0) h -= p * (Math.log(p) / LN_2);
        }
        double maxH = Math.log(probs.length) / LN_2;
        return maxH > 0 ? h / maxH : 0.0;
    }

    /**
     * Coherence as complement of entropy: coherence = 1 - entropy.
     */
    public static double coherenceFromEntropy(double normalisedEntropy) {
        return Math.max(0.0, Math.min(1.0, 1.0 - normalisedEntropy));
    }

    // ─── Régua (ruler) constants (formulas 23–27) ────────────────────────────

    /** [E f23] Angular ladder: 6→12→24→48 */
    public static final int[] ANGULAR_LADDER = {6, 12, 24, 48};

    /** [E f24] Structural ruler: 42 */
    public static final int RULER_42 = 42;

    /** [E f25] Prime-spectral ladder: 30→210→2310→30030→... */
    public static final int[] PRIME_SPECTRAL = {30, 210, 2310, 30030};

    /** [E f26] Calibration constant: 999 */
    public static final int CALIBRATION_999 = 999;

    /** [E f27] Geometric scale: (√3/2)^(π·φ) */
    public static final double GEO_SCALE = SPIRAL_PI_PHI;
}
