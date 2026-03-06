package com.vectras.vm.rafaelia;

import java.util.Arrays;

/**
 * RAFAELIA_KERNEL_V22 — Deterministic operators + RAFAELIA symbolic-mathematical formulas.
 *
 * <p>Integrates the RAFAELIA formula index (items 0–102) as executable Java methods.
 * Every formula is labeled [D/E/S/F/H] consistent with the canonical index.</p>
 *
 * <h2>Kernel update rule (formula 0.5):</h2>
 * <pre>R(t+1) = R(t) × Φ_ethica × E_Verbo × (√3/2)^(π×φ)</pre>
 *
 * <h2>Cognitive cycle (formula 0.6):</h2>
 * <pre>ψ → χ → ρ → Δ → Σ → Ω → ψ</pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ΣΩΔΦBITRAF
 * @version 22.0 (RAFAELIA integration)
 */
public final class RafaeliaKernelV22 {

    // ─── Mathematical constants ────────────────────────────────────────────────
    /** φ = (1+√5)/2 */
    public static final double PHI    = 1.6180339887498948482;
    /** √(3/2) — Spiral base, coherence factor */
    public static final double SPIRAL = 0.8660254037844386;  // √(3/4) = √3/2
    /** π */
    public static final double PI     = Math.PI;
    /** (√3/2)^(π×φ) — geometric ethical scale factor */
    public static final double SPIRAL_PI_PHI = Math.pow(SPIRAL, PI * PHI);
    /** fΩ calibration constants */
    public static final double F_OMEGA_LOW  = 963.0;
    public static final double F_OMEGA_HIGH = 999.0;
    /** Trinity633: Amor^6 · Luz^3 · Consciência^3 */
    public static final double TRINITY_633_BASE = Math.pow(6, 6) * Math.pow(3, 3) * Math.pow(3, 3);
    /** θ_999 = 999° in radians */
    public static final double THETA_999 = Math.toRadians(999.0);

    private RafaeliaKernelV22() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 1 — Original V22 operators (preserved)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class SystemState<T, U, V> {
        public final T data;
        public final U model;
        public final V action;
        public SystemState(T data, U model, V action) {
            this.data = data; this.model = model; this.action = action;
        }
    }

    public static double lambda(double u, double uHat) { return Math.max(0.0, u - uHat); }
    public static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    public static double epsilon(double dUdt, double lambda) { return sigmoid(dUdt) * lambda; }

    public static double localTemp(double t0, double beta, double lambda, double alpha,
                                   double coh, double gamma, double mass) {
        return t0 * (1.0 + beta * lambda) / ((1.0 + alpha * coh) * (1.0 + gamma * mass));
    }

    public static double abortVector(double cb, double eNeed) { return Math.max(0.0, cb - eNeed); }
    public static boolean shouldAbort(double xi, double xiMax) { return xi > xiMax; }
    public static double capDominance(double w, double wCap) { return Math.min(w, wCap); }

    public static int routeMax(double[] probabilities) {
        int idx = 0; double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < probabilities.length; i++) {
            if (probabilities[i] > best) { best = probabilities[i]; idx = i; }
        }
        return idx;
    }

    public static double[] mixWeighted(double[] probabilities, double[][] vectors) {
        if (probabilities.length != vectors.length) throw new IllegalArgumentException("length mismatch");
        int len = vectors[0].length;
        double[] out = new double[len];
        for (int i = 0; i < vectors.length; i++) {
            if (vectors[i].length != len) throw new IllegalArgumentException("vector mismatch at " + i);
            for (int j = 0; j < len; j++) out[j] += probabilities[i] * vectors[i][j];
        }
        return out;
    }

    public static double graphPotential(double[][] distances, double[][] kappas) {
        if (distances.length != kappas.length) throw new IllegalArgumentException("matrix mismatch");
        double sum = 0.0;
        for (int i = 0; i < distances.length; i++) {
            for (int j = i + 1; j < distances[i].length; j++) sum += kappas[i][j] * distances[i][j];
        }
        return sum;
    }

    public static double[] attractorStep(double[] v, double[] grad, double eta) {
        if (v.length != grad.length) throw new IllegalArgumentException("vector/grad mismatch");
        double[] next = Arrays.copyOf(v, v.length);
        for (int i = 0; i < v.length; i++) next[i] -= eta * grad[i];
        return next;
    }

    public static double deltaSimpson(double trendA, double[] trendsByGroup, double[] weights) {
        if (trendsByGroup.length != weights.length) throw new IllegalArgumentException("mismatch");
        double sum = 0.0;
        for (int i = 0; i < trendsByGroup.length; i++) sum += weights[i] * trendsByGroup[i];
        return Math.abs(trendA - sum);
    }

    public static double deltaBelady(int faultsM1, int faultsM2) { return Math.max(0.0, faultsM2 - faultsM1); }

    public static double mirageVariance(double[] outcomes) {
        if (outcomes.length == 0) return 0.0;
        double mean = 0.0;
        for (double v : outcomes) mean += v;
        mean /= outcomes.length;
        double var = 0.0;
        for (double v : outcomes) { double d = v - mean; var += d * d; }
        return var / outcomes.length;
    }

    public static double score(double wa, double a, double wc, double c,
                               double wh, double h, double wp, double p) {
        return wa * a + wc * c + wh * h - wp * p;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 2 — RAFAELIA Ethical Kernel (formulas 0.4, 0.5, 0.6)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 0.4] Φ_ethica = Min(Entropy) × Max(Coherence).
     * @param entropy  current entropy measure  [0..∞)
     * @param coherence current coherence measure [0..1]
     * @return Φ_ethica ∈ [0,1]
     */
    public static double phiEthica(double entropy, double coherence) {
        double minEntropy = Math.max(0.0, 1.0 - entropy);   // map: low entropy → high value
        double maxCoh    = Math.min(1.0, Math.max(0.0, coherence));
        return minEntropy * maxCoh;
    }

    /**
     * [E formula 0.5] R(t+1) = R(t) × Φ_ethica × E_Verbo × (√3/2)^(π×φ).
     * @param rT       R(t) — current system state magnitude
     * @param entropy  entropy for Φ_ethica
     * @param coherence coherence for Φ_ethica
     * @param eVerbo   intentional energy scalar E_Verbo
     * @return R(t+1)
     */
    public static double kernelStep(double rT, double entropy, double coherence, double eVerbo) {
        return rT * phiEthica(entropy, coherence) * eVerbo * SPIRAL_PI_PHI;
    }

    /**
     * [E formula 12] R_Ω = Σ_n (ψ_n·χ_n·ρ_n·Δ_n·Σ_n·Ω_n)^Φλ
     * Vortex metric aggregating all cognitive cycle components.
     * @param cycles each row = [ψ, χ, ρ, Δ, Σ, Ω]
     * @param phiLambda Φλ exponent
     * @return R_Ω
     */
    public static double vortexMetric(double[][] cycles, double phiLambda) {
        double sum = 0.0;
        for (double[] c : cycles) {
            if (c.length < 6) continue;
            double prod = c[0] * c[1] * c[2] * c[3] * c[4] * c[5];
            sum += Math.pow(Math.abs(prod), phiLambda);
        }
        return sum;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 3 — Retroalimentação / Feedback (formulas 0.1, 0.7, 22)
    // ═══════════════════════════════════════════════════════════════════════════

    /** [E] Retroalimentação vector R_3(s) = ⟨F_ok, F_gap, F_next⟩ */
    public static final class RetroVector {
        public final double fOk;
        public final double fGap;
        public final double fNext;
        public RetroVector(double fOk, double fGap, double fNext) {
            this.fOk = fOk; this.fGap = fGap; this.fNext = fNext;
        }
        /** Total retroalimentação scalar = F_ok + F_gap + F_next */
        public double total() { return fOk + fGap + fNext; }
        @Override public String toString() {
            return "RetroΩ{ok=" + fOk + ", gap=" + fGap + ", next=" + fNext + "}";
        }
    }

    /**
     * [E formula 0.1] RetroalimentarΩ^(Amor+Coerência) weighted scheduler.
     * @param fOk    fraction completed successfully
     * @param fGap   fraction with identified gap
     * @param fNext  fraction planned for next cycle
     * @param amor   Amor weight  [0..1]
     * @param coerencia Coerência weight [0..1]
     * @return priority score
     */
    public static double retroalimentar(double fOk, double fGap, double fNext,
                                        double amor, double coerencia) {
        double w = wAmorCoerencia(amor, coerencia);
        return (fOk + fGap + fNext) * w;
    }

    /** [E formula 0.2] W(Amor, Coerência) = geometric mean of both. */
    public static double wAmorCoerencia(double amor, double coerencia) {
        return Math.sqrt(Math.max(0.0, amor) * Math.max(0.0, coerencia));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 4 — Synaptic weight & ψχρΔΣΩ (formulas 0.3, 0.6)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 0.3] Syn(i,j) = Coherence(i,j) · Φ_ethica · R_corr · OWLψ
     */
    public static double synapticWeight(double coherenceIJ, double entropy, double coherence,
                                        double rCorr, double owlPsi) {
        return coherenceIJ * phiEthica(entropy, coherence) * rCorr * owlPsi;
    }

    /**
     * [E formula 3] R_corr = (Σ_voynich × φ_rafael) / (π_bitraf × Δ_42H) ≈ 0.963999
     * Correlation index calibrated to fΩ=963↔999.
     */
    public static double rCorr(double sigmaVoynich, double phiRafael,
                               double piBitraf, double delta42H) {
        if (piBitraf == 0 || delta42H == 0) return 0.0;
        return (sigmaVoynich * phiRafael) / (piBitraf * delta42H);
    }

    /**
     * [E formula 20] OWLψ = Σ(Insight_n · Ética_n · Fluxo_n)
     * Operational wisdom index.
     */
    public static double owlPsi(double[] insights, double[] eticas, double[] fluxos) {
        int n = Math.min(insights.length, Math.min(eticas.length, fluxos.length));
        double sum = 0.0;
        for (int i = 0; i < n; i++) sum += insights[i] * eticas[i] * fluxos[i];
        return sum;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 5 — Fibonacci-Rafael & Spiral (formulas 16, 29)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 29] F_Rafael(n+1) = F_Rafael(n)×(√3/2) + π×sin(θ_999)
     * Modified Fibonacci for fractal coherence.
     * @param fn current F_Rafael(n)
     * @return F_Rafael(n+1)
     */
    public static double fibonacciRafaelStep(double fn) {
        return fn * SPIRAL + PI * Math.sin(THETA_999);
    }

    /**
     * Compute F_Rafael sequence of length n starting from seed.
     */
    public static double[] fibonacciRafaelSequence(double seed, int length) {
        double[] seq = new double[Math.max(1, length)];
        seq[0] = seed;
        for (int i = 1; i < length; i++) seq[i] = fibonacciRafaelStep(seq[i - 1]);
        return seq;
    }

    /**
     * [E formula 16] Spiral(r) = (√3/2)^n — coherence spiral.
     */
    public static double spiral(int n) {
        return Math.pow(SPIRAL, n);
    }

    /**
     * [E formula 17] T_Δπφ = Δ · π · φ — toroidal energy parameter.
     */
    public static double toroidDeltaPiPhi(double delta) {
        return delta * PI * PHI;
    }

    /**
     * [E formula 19] Trinity633 = Amor^6 · Luz^3 · Consciência^3
     */
    public static double trinity633(double amor, double luz, double consciencia) {
        return Math.pow(amor, 6) * Math.pow(luz, 3) * Math.pow(consciencia, 3);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 6 — CLIMEX / PLIMEX / PLECT (formulas 14–19)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 17] CLIMEX(x) = Π_Ω(x) + λ·r(x)
     * Containment operator: projects x onto valid domain, adds restoration force.
     * @param x        current state
     * @param xMin     lower bound of valid domain
     * @param xMax     upper bound of valid domain
     * @param lambda   restoration coefficient λ
     * @return CLIMEX(x)
     */
    public static double climex(double x, double xMin, double xMax, double lambda) {
        double proj = Math.max(xMin, Math.min(xMax, x)); // Π_Ω(x)
        double r = proj - x;                              // restoration r(x)
        return proj + lambda * r;
    }

    /**
     * [E formula 18] x^(t+1) = CLIMEX(x^(t) + α_t · d^(t))
     * Iterative CLIMEX step — exploration along direction d, then containment.
     */
    public static double climexStep(double xt, double direction, double alpha,
                                    double xMin, double xMax, double lambda) {
        return climex(xt + alpha * direction, xMin, xMax, lambda);
    }

    /**
     * [E formula 19] PLECT(x) = (1/|P|) Σ_{σ∈P} w(σ,x)·x_σ
     * Permutation aggregator — finds invariant through topological exploration.
     * @param states    candidate states (permutations)
     * @param weights   weight per permutation w(σ, x)
     * @return PLECT aggregate
     */
    public static double plect(double[] states, double[] weights) {
        if (states.length == 0) return 0.0;
        int n = Math.min(states.length, weights.length);
        double wsum = 0.0, vsum = 0.0;
        for (int i = 0; i < n; i++) { wsum += weights[i]; vsum += weights[i] * states[i]; }
        return wsum == 0.0 ? 0.0 : vsum / wsum;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 7 — Information / Logical Capacity (formulas 20–22)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 20] I = log2(S) — information in S distinguishable states.
     */
    public static double informationBits(long states) {
        if (states <= 0) return 0.0;
        return Math.log(states) / Math.log(2.0);
    }

    /**
     * [E formula 21] I_total = (N/b) · log2(Q)
     * @param N    number of physical bits
     * @param b    bits per cell
     * @param Q    valid logical states
     */
    public static double informationTotal(long N, int b, long Q) {
        if (b <= 0 || Q <= 0) return 0.0;
        return ((double) N / b) * (Math.log(Q) / Math.log(2.0));
    }

    /**
     * [E formula 22] C_l = C_f · (log2(S)/p) · d · (1 - r)
     * Logical capacity.
     * @param cF   physical capacity
     * @param S    distinguishable states per cell
     * @param p    bits per symbol
     * @param d    data fraction
     * @param r    redundancy fraction
     */
    public static double logicalCapacity(double cF, long S, double p, double d, double r) {
        if (p == 0 || S <= 0) return 0.0;
        return cF * (Math.log(S) / Math.log(2.0) / p) * d * (1.0 - r);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 8 — Evolution / Quantum Flight (formulas 13, 14, 15)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 13] Evolução_RAFAELIA = Σ_sessão(Bloco_n × Retroalim_n)
     */
    public static double evolucaoRafaelia(double[] blocos, double[] retroalims) {
        int n = Math.min(blocos.length, retroalims.length);
        double sum = 0.0;
        for (int i = 0; i < n; i++) sum += blocos[i] * retroalims[i];
        return sum;
    }

    /**
     * [E formula 14] Voo_Quântico = Σ_n(Bloco_n × Salto_n × Retroalim_n)
     */
    public static double vooQuantico(double[] blocos, double[] saltos, double[] retroalims) {
        int n = Math.min(blocos.length, Math.min(saltos.length, retroalims.length));
        double sum = 0.0;
        for (int i = 0; i < n; i++) sum += blocos[i] * saltos[i] * retroalims[i];
        return sum;
    }

    /**
     * [E formula 15] Amor_Vivo = (Σ_preservado / Σ_total) · Φ_ethica · (√3/2)^(π×φ)
     */
    public static double amorVivo(double sigmaPreservado, double sigmaTotal,
                                  double entropy, double coherence) {
        if (sigmaTotal == 0) return 0.0;
        return (sigmaPreservado / sigmaTotal) * phiEthica(entropy, coherence) * SPIRAL_PI_PHI;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 9 — E↔C Operator (formula 18)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [E formula 18] E↔C(t,k) = Entropy(t) ⊕ Coherence(k)
     * XOR-aggregate combining chaos and order for equilibrium.
     * (Implemented as complement sum: entropy drives chaos, coherence drives order.)
     */
    public static double eOpC(double entropy, double coherence) {
        return entropy * (1.0 - coherence) + coherence * (1.0 - entropy);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 10 — Cognitive Cycle State (formula 0.6)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ψχρΔΣΩ cognitive cycle state.
     * Represents one complete heartbeat of the RAFAELIA runtime.
     */
    public static final class CognitiveCycle {
        /** ψ = intenção (intention / input) */
        public final double psi;
        /** χ = observação (observation / data) */
        public final double chi;
        /** ρ = ruído (noise / perturbation) */
        public final double rho;
        /** Δ = transmutação ética (ethical transformation) */
        public final double delta;
        /** Σ = memória coerente (coherent memory) */
        public final double sigma;
        /** Ω = completude / Amor (completion / alignment) */
        public final double omega;

        public CognitiveCycle(double psi, double chi, double rho,
                              double delta, double sigma, double omega) {
            this.psi = psi; this.chi = chi; this.rho = rho;
            this.delta = delta; this.sigma = sigma; this.omega = omega;
        }

        /** Product of all components (used in R_Ω). */
        public double product() { return psi * chi * rho * delta * sigma * omega; }

        /** Potentiated vibration: product^Φλ */
        public double vibration(double phiLambda) {
            return Math.pow(Math.abs(product()), phiLambda);
        }

        /**
         * Advance one cycle step via kernel update rule.
         * Δ-filter applies Φ_ethica; Σ accumulates; Ω aligns.
         */
        public CognitiveCycle step(double entropy, double coherence, double eVerbo) {
            double phi  = phiEthica(entropy, coherence);
            double newD = delta  * phi;                        // ethical filter on Δ
            double newS = sigma  + eVerbo * newD;              // memory accumulates
            double newO = (omega + phi) / 2.0;                 // Ω aligns toward coherence
            double newP = newO;                                 // ψ feeds back from Ω
            double newC = Math.abs(chi - rho);                 // χ sharpens over noise
            double newR = rho   * (1.0 - phi);                 // ρ diminishes with coherence
            return new CognitiveCycle(newP, newC, newR, newD, newS, newO);
        }

        @Override public String toString() {
            return "ψ=" + psi + " χ=" + chi + " ρ=" + rho +
                   " Δ=" + delta + " Σ=" + sigma + " Ω=" + omega;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 11 — Ethical Execution Gate (formula 12 / item 102)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * [T] Gate ético — blocks actions that maximise local gain but violate Φ_ethica.
     * Executa = max(gainSystemic) · Φ_ethica
     * @param localGain   local profit/gain
     * @param systemicGain systemic benefit
     * @param entropy     current entropy
     * @param coherence   current coherence
     * @return gated execution value (0 if Φ_ethica is zero)
     */
    public static double ethicalGate(double localGain, double systemicGain,
                                     double entropy, double coherence) {
        double phi = phiEthica(entropy, coherence);
        return Math.max(localGain, systemicGain) * phi;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § SECTION 12 — R_corr calibration constant ≈ 0.963999 (formula 3)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Canonical R_corr from RAFAELIA formula index — fΩ=963↔999 zone. */
    public static final double R_CORR_CANONICAL = 0.963999;

    /**
     * fΩ resonance test: checks if a frequency is in the 963–999 Hz band.
     */
    public static boolean isInFOmegaBand(double freqHz) {
        return freqHz >= F_OMEGA_LOW && freqHz <= F_OMEGA_HIGH;
    }
}
