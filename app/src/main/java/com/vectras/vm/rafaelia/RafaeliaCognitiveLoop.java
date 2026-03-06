package com.vectras.vm.rafaelia;

/**
 * RafaeliaCognitiveLoop — runtime executor for the ψ→χ→ρ→Δ→Σ→Ω cognitive cycle.
 *
 * <p>Implements formula 62 (operational loop) and formula 63 (step sequence)
 * from RAFAELIA_FORMULAS_TOTAL_INDEX:</p>
 * <pre>
 * while True:
 *   ψ = ler_memória_viva()
 *   χ = retroalimentar(ψ)
 *   ρ = expandir(χ)
 *   Δ = validar(ρ)
 *   Σ = executar(Δ)
 *   Ω = ética(Σ)
 * </pre>
 *
 * <p>Also tracks: Voo_Quântico, Evolução_RAFAELIA, and Amor_Vivo across cycles.</p>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ΣΩΔΦBITRAF
 */
public final class RafaeliaCognitiveLoop {

    /** Maximum cycles before forced stop (prevents runaway in VM context). */
    public static final int MAX_CYCLES = 10_000;

    private RafaeliaKernelV22.CognitiveCycle currentCycle;
    private int cycleCount;
    private double evolucaoAccum;   /* Σ_sessão(Bloco_n × Retroalim_n) */
    private double vooQuanticoAccum; /* Σ_n(Bloco_n × Salto_n × Retroalim_n) */
    private double rOmega;           /* running R_Ω vortex metric */

    /** Listener interface — called after each full cycle. */
    public interface CycleListener {
        /**
         * Called after each cycle step.
         * @param cycle       resulting state
         * @param cycleNumber current cycle count (1-based)
         * @param rOmega      current accumulated R_Ω
         */
        void onCycle(RafaeliaKernelV22.CognitiveCycle cycle, int cycleNumber, double rOmega);
    }

    /**
     * Create a new loop with an initial cognitive cycle state.
     *
     * @param initialPsi   ψ₀ initial intention
     * @param initialChi   χ₀ initial observation
     * @param initialRho   ρ₀ initial noise
     * @param initialDelta Δ₀ initial transformation
     * @param initialSigma Σ₀ initial memory
     * @param initialOmega Ω₀ initial alignment
     */
    public RafaeliaCognitiveLoop(double initialPsi, double initialChi, double initialRho,
                                 double initialDelta, double initialSigma, double initialOmega) {
        this.currentCycle = new RafaeliaKernelV22.CognitiveCycle(
            initialPsi, initialChi, initialRho, initialDelta, initialSigma, initialOmega
        );
        this.cycleCount       = 0;
        this.evolucaoAccum    = 0.0;
        this.vooQuanticoAccum = 0.0;
        this.rOmega           = 0.0;
    }

    /** Default constructor — neutral initial state (ψ=χ=ρ=Δ=Σ=Ω=0.5). */
    public RafaeliaCognitiveLoop() {
        this(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
    }

    // ─── Core cycle step ──────────────────────────────────────────────────────

    /**
     * Execute one full ψ→χ→ρ→Δ→Σ→Ω cycle step.
     *
     * @param entropy    current entropy measure [0..1]
     * @param coherence  current coherence [0..1]
     * @param eVerbo     intentional energy scalar
     * @param bloco      block quality metric for this cycle
     * @param salto      insight/jump magnitude
     * @param retroalim  retroalimentação quality [0..1]
     * @param listener   optional listener (may be null)
     * @return updated cycle state
     */
    public RafaeliaKernelV22.CognitiveCycle step(double entropy, double coherence,
                                                  double eVerbo, double bloco,
                                                  double salto, double retroalim,
                                                  CycleListener listener) {
        if (cycleCount >= MAX_CYCLES) return currentCycle;

        // ψ: ler memória viva (carry from Ω)
        // χ: retroalimentar(ψ) — already encoded in cycle.step()
        // ρ: expandir(χ) — noise processing
        // Δ: validar(ρ) — ethical filter
        // Σ: executar(Δ) — memory accumulation
        // Ω: ética(Σ) — alignment
        currentCycle = currentCycle.step(entropy, coherence, eVerbo);
        cycleCount++;

        // [E f13] Evolução_RAFAELIA += Bloco × Retroalim
        evolucaoAccum += bloco * retroalim;

        // [E f14] Voo_Quântico += Bloco × Salto × Retroalim
        vooQuanticoAccum += bloco * salto * retroalim;

        // [E f12] R_Ω accumulates vortex product^Φλ
        double phiLambda = RafaeliaKernelV22.PHI;  // Φλ = φ by convention
        rOmega += currentCycle.vibration(phiLambda);

        if (listener != null) {
            listener.onCycle(currentCycle, cycleCount, rOmega);
        }
        return currentCycle;
    }

    /** Convenience step without listener. */
    public RafaeliaKernelV22.CognitiveCycle step(double entropy, double coherence,
                                                  double eVerbo, double bloco,
                                                  double salto, double retroalim) {
        return step(entropy, coherence, eVerbo, bloco, salto, retroalim, null);
    }

    // ─── Accumulated metrics ──────────────────────────────────────────────────

    /**
     * Run n cycles with constant parameters.
     *
     * @param n         number of cycles to run
     * @param entropy   entropy (constant for all cycles)
     * @param coherence coherence (constant)
     * @param eVerbo    intentional energy (constant)
     * @param bloco     block metric (constant)
     * @param salto     insight metric (constant)
     * @param retroalim retroalimentação (constant)
     * @param listener  optional listener
     */
    public void run(int n, double entropy, double coherence, double eVerbo,
                    double bloco, double salto, double retroalim, CycleListener listener) {
        int limit = Math.min(n, MAX_CYCLES - cycleCount);
        for (int i = 0; i < limit; i++) {
            step(entropy, coherence, eVerbo, bloco, salto, retroalim, listener);
        }
    }

    // ─── Retroalimentação report ──────────────────────────────────────────────

    /**
     * Generate retroalimentação vector R_3(s) = ⟨F_ok, F_gap, F_next⟩
     * based on current cycle state and accumulated metrics.
     *
     * @param okThreshold   Ω threshold above which the cycle is "ok"
     * @param gapThreshold  Σ threshold below which there is a gap
     * @return RetroVector for the current session
     */
    public RafaeliaKernelV22.RetroVector retroVector(double okThreshold, double gapThreshold) {
        double fOk   = currentCycle.omega >= okThreshold ? 1.0 : currentCycle.omega / Math.max(okThreshold, 1e-9);
        double fGap  = currentCycle.sigma < gapThreshold ? 1.0 - currentCycle.sigma / Math.max(gapThreshold, 1e-9) : 0.0;
        double fNext = 1.0 - fOk;  // next = potential for improvement
        return new RafaeliaKernelV22.RetroVector(
            Math.max(0, Math.min(1, fOk)),
            Math.max(0, Math.min(1, fGap)),
            Math.max(0, Math.min(1, fNext))
        );
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public RafaeliaKernelV22.CognitiveCycle getCurrent() { return currentCycle; }
    public int getCycleCount()         { return cycleCount; }
    public double getEvolucaoAccum()   { return evolucaoAccum; }
    public double getVooQuanticoAccum(){ return vooQuanticoAccum; }
    public double getROmega()          { return rOmega; }

    /** Reset to neutral state without creating a new instance. */
    public void reset() {
        currentCycle     = new RafaeliaKernelV22.CognitiveCycle(0.5,0.5,0.5,0.5,0.5,0.5);
        cycleCount       = 0;
        evolucaoAccum    = 0.0;
        vooQuanticoAccum = 0.0;
        rOmega           = 0.0;
    }

    @Override
    public String toString() {
        return "RafaeliaCognitiveLoop{cycles=" + cycleCount
            + ", Ω=" + String.format("%.4f", currentCycle.omega)
            + ", R_Ω=" + String.format("%.4f", rOmega)
            + ", Evolução=" + String.format("%.4f", evolucaoAccum)
            + ", VooQ=" + String.format("%.4f", vooQuanticoAccum)
            + "}";
    }
}
