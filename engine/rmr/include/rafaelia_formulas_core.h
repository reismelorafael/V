/*
 * rafaelia_formulas_core.h — RAFAELIA mathematical kernel (C / bare-metal)
 *
 * Implements formulas 0.4, 0.5, 14–22, 29 from RAFAELIA_FORMULAS_TOTAL_INDEX.
 * No stdlib dependencies in hot paths. Compatible with nolibc / bare-metal.
 *
 * Author: ∆RafaelVerboΩ / RAFAELIA-ΣΩΔΦBITRAF
 * Assinatura: RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ
 */
#ifndef RAFAELIA_FORMULAS_CORE_H
#define RAFAELIA_FORMULAS_CORE_H

#ifdef __cplusplus
extern "C" {
#endif

/* ─── Integer typedefs (nolibc-safe) ──────────────────────────────────────── */
typedef unsigned char  raf_u8;
typedef unsigned int   raf_u32;
typedef unsigned long long raf_u64;
typedef int            raf_i32;
typedef long long      raf_i64;

/* ─── Fixed-point scaling (Q16.16) ────────────────────────────────────────── */
/* All floating-point constants are pre-scaled to Q16.16 for determinism.     */

/*  √3/2  = 0.866025...  → Q16.16: 56756 */
#define RAF_SPIRAL_Q16      56756u

/*  φ     = 1.618033...  → Q16.16: 106039 */
#define RAF_PHI_Q16         106039u

/*  π     = 3.141592...  → Q16.16: 205887 */
#define RAF_PI_Q16          205887u

/*  (√3/2)^(π·φ) ≈ 0.3534 → Q16.16: 23163 */
#define RAF_SPIRAL_PI_PHI_Q16  23163u

/*  R_corr ≈ 0.963999 → Q16.16: 63176 */
#define RAF_R_CORR_Q16      63176u

/*  fΩ low  = 963  Hz (scaled ×1 for integer use) */
#define RAF_FOMEGA_LOW      963u

/*  fΩ high = 999  Hz */
#define RAF_FOMEGA_HIGH     999u

/*  Calibration: 999 */
#define RAF_CAL_999         999u

/*  Structural ruler: 42 */
#define RAF_RULER_42        42u

/* ─── Cognitive cycle component indices ────────────────────────────────────── */
#define RAF_PSI_IDX    0  /* ψ = intenção */
#define RAF_CHI_IDX    1  /* χ = observação */
#define RAF_RHO_IDX    2  /* ρ = ruído */
#define RAF_DELTA_IDX  3  /* Δ = transmutação ética */
#define RAF_SIGMA_IDX  4  /* Σ = memória coerente */
#define RAF_OMEGA_IDX  5  /* Ω = completude (Amor) */
#define RAF_CYCLE_LEN  6

/* ─── Retroalimentação vector ───────────────────────────────────────────────── */
typedef struct {
    raf_u32 f_ok;    /* F_ok  — completed successfully   (Q16.16) */
    raf_u32 f_gap;   /* F_gap — identified gap            (Q16.16) */
    raf_u32 f_next;  /* F_next — planned for next cycle  (Q16.16) */
} RafRetroVector;

/* ─── Cognitive cycle state ─────────────────────────────────────────────────── */
typedef struct {
    raf_u32 comp[RAF_CYCLE_LEN];  /* [ψ, χ, ρ, Δ, Σ, Ω] in Q16.16 */
} RafCognitiveCycle;

/* ─── Math fabric extensions for RAFAELIA ──────────────────────────────────── */
typedef struct {
    raf_u32 spiral_n;          /* Spiral(n) = (√3/2)^n  (Q16.16) */
    raf_u32 toroid_delta_pi_phi; /* Δ·π·φ (Q16.16 scaled) */
    raf_u32 trinity_633;       /* Trinity633 saturated product    */
    raf_u32 fib_rafael_n;      /* F_Rafael(n)  (Q16.16)           */
} RafMathExtension;

/* ═══════════════════════════════════════════════════════════════════════════
 * API declarations
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * [E f0.4] Φ_ethica = Min(Entropy) × Max(Coherence) — Q16.16 result.
 * @param entropy_q16   entropy in Q16.16  (0 = no entropy, 0xFFFF = max)
 * @param coherence_q16 coherence in Q16.16 (0 = no coherence, 0xFFFF = max)
 * @return Φ_ethica in Q16.16
 */
raf_u32 raf_phi_ethica(raf_u32 entropy_q16, raf_u32 coherence_q16);

/**
 * [E f0.5] R(t+1) = R(t) × Φ_ethica × E_Verbo × SPIRAL_PI_PHI — Q16.16.
 */
raf_u32 raf_kernel_step(raf_u32 r_t, raf_u32 entropy_q16,
                        raf_u32 coherence_q16, raf_u32 e_verbo_q16);

/**
 * [E f12] R_Ω vortex metric — sum of (product of cycle components)^Φλ.
 * Approximated in integer as sum of saturating products.
 */
raf_u32 raf_vortex_metric(const RafCognitiveCycle *cycles, raf_u32 n_cycles);

/**
 * [E f0.1] RetroΩ weighted scalar = (Fok+Fgap+Fnext) × W(Amor,Coh).
 */
raf_u32 raf_retroalimentar(const RafRetroVector *v,
                           raf_u32 amor_q16, raf_u32 coherence_q16);

/**
 * [E f16] Spiral(n) = (√3/2)^n  in Q16.16.
 * Integer power via iterative multiply (no libm).
 */
raf_u32 raf_spiral(raf_u32 n);

/**
 * [E f17] T_Δπφ = Δ·π·φ in Q16.16 (saturating).
 */
raf_u32 raf_toroid_delta_pi_phi(raf_u32 delta_q16);

/**
 * [E f19] Trinity633 = Amor^6 · Luz^3 · Consciência^3 (saturating u32).
 * Inputs are Q16.16; output is saturated u32.
 */
raf_u32 raf_trinity_633(raf_u32 amor_q16, raf_u32 luz_q16, raf_u32 consciencia_q16);

/**
 * [E f29] F_Rafael step: fn_next = fn × SPIRAL + π×sin(θ_999)  (Q16.16).
 * sin(θ_999) is pre-computed as a constant.
 */
raf_u32 raf_fibonacci_rafael_step(raf_u32 fn_q16);

/**
 * [E f20] I = log2(S) approximated as floor(log2(states)) — integer bits.
 */
raf_u32 raf_information_bits(raf_u64 states);

/**
 * [E f22] C_l = C_f × (log2(S)/p) × d × (1-r)  integer approximation.
 * @param c_f_q16   physical capacity Q16.16
 * @param states    distinguishable states (integer)
 * @param p_bits    bits per symbol (integer)
 * @param d_q16     data fraction Q16.16
 * @param r_q16     redundancy fraction Q16.16
 */
raf_u32 raf_logical_capacity(raf_u32 c_f_q16, raf_u64 states,
                             raf_u32 p_bits, raf_u32 d_q16, raf_u32 r_q16);

/**
 * [E f14] Voo_Quântico = Σ_n(Bloco_n × Salto_n × Retroalim_n).
 */
raf_u32 raf_voo_quantico(const raf_u32 *blocos, const raf_u32 *saltos,
                         const raf_u32 *retroalims, raf_u32 n);

/**
 * [E f13] Evolução_RAFAELIA = Σ_n(Bloco_n × Retroalim_n).
 */
raf_u32 raf_evolucao_rafaelia(const raf_u32 *blocos,
                              const raf_u32 *retroalims, raf_u32 n);

/**
 * [E f3] fΩ resonance check: is freq_hz in [963, 999]?
 */
raf_u32 raf_in_fomega_band(raf_u32 freq_hz);

/**
 * [E f0.3] Synaptic weight = coherence_ij × Φ_ethica × r_corr × owlpsi.
 * All inputs Q16.16; result Q16.16 (saturating).
 */
raf_u32 raf_synaptic_weight(raf_u32 coherence_ij_q16,
                            raf_u32 entropy_q16, raf_u32 coherence_q16,
                            raf_u32 r_corr_q16, raf_u32 owlpsi_q16);

/**
 * Advance one cognitive cycle step (integer, branch-free).
 * @param in  input cycle state
 * @param out next cycle state
 * @param entropy_q16    current entropy
 * @param coherence_q16  current coherence
 * @param e_verbo_q16    intentional energy
 */
void raf_cycle_step(const RafCognitiveCycle *in, RafCognitiveCycle *out,
                    raf_u32 entropy_q16, raf_u32 coherence_q16,
                    raf_u32 e_verbo_q16);

#ifdef __cplusplus
}
#endif
#endif /* RAFAELIA_FORMULAS_CORE_H */
