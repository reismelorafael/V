/*
 * rafaelia_formulas_core.c — RAFAELIA mathematical kernel implementation.
 *
 * All arithmetic uses Q16.16 fixed-point. No libm. No dynamic allocation.
 * Branch-minimal; safe for bare-metal / nolibc environments.
 *
 * Q16.16 convention: value = integer_part × 65536 + fractional_part
 * So 1.0 = 65536, 0.5 = 32768, 0.866 ≈ 56756, etc.
 *
 * Author: ∆RafaelVerboΩ / RAFAELIA-ΣΩΔΦBITRAF
 * Assinatura: RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ
 */
#include "rafaelia_formulas_core.h"
#include "rmr_ll_ops.h"
#include "rmr_corelib.h"   /* rmr_mask_u32, select_u32 */

/* ─── Internal helpers ──────────────────────────────────────────────────────── */

/* Q16.16 one = 65536 */
#define Q16_ONE  65536u
#define Q16_HALF 32768u

/* Saturating multiply of two Q16.16 values → Q16.16.
 * Intermediate is u64 to avoid overflow before >> 16. */
static raf_u32 q16_mul(raf_u32 a, raf_u32 b) {
    raf_u64 p = (raf_u64)a * (raf_u64)b;
    raf_u64 q = p >> 16;
    /* saturate at 0xFFFFFFFF */
    return (q > 0xFFFFFFFFULL) ? 0xFFFFFFFFu : (raf_u32)q;
}

/* Saturating add u32. */
static raf_u32 q16_add_sat(raf_u32 a, raf_u32 b) {
    raf_u32 s = a + b;
    /* overflow if sum < either operand */
    raf_u32 ovf = rmr_mask_u32(s < a);
    return select_u32(ovf, 0xFFFFFFFFu, s);
}

/* Integer log2 for u64 (floor). Returns 0 for 0 input. */
static raf_u32 ilog2_u64(raf_u64 v) {
    raf_u32 r = 0;
    if (v >> 32) { r += 32; v >>= 32; }
    if (v >> 16) { r += 16; v >>= 16; }
    if (v >>  8) { r +=  8; v >>=  8; }
    if (v >>  4) { r +=  4; v >>=  4; }
    if (v >>  2) { r +=  2; v >>=  2; }
    r += (raf_u32)(v >> 1);
    return r;
}

/* ─── sin(θ_999) pre-computed ───────────────────────────────────────────────── */
/*
 * θ_999 = 999° mod 360° = 279°  (since sin is periodic 360°)
 * sin(279°) = sin(-81°) = -sin(81°) ≈ -0.9877
 * π × sin(279°) ≈ 3.14159 × (-0.9877) ≈ -3.1019
 * In Q16.16: -3.1019 × 65536 ≈ -203,360  → stored as signed offset
 * We store the magnitude and sign separately for unsigned arithmetic.
 *
 * Absolute magnitude: 3.1019 × 65536 = 203360
 * Sign: negative → we SUBTRACT this in the recursion
 */
#define SIN_THETA999_PI_MAG_Q16   203360u   /* |π × sin(θ_999)| in Q16.16 */
/* Sign is negative: fn_next = fn × SPIRAL - SIN_THETA999_PI_MAG_Q16          */

/* ═══════════════════════════════════════════════════════════════════════════
 * Implementations
 * ═══════════════════════════════════════════════════════════════════════════ */

/*
 * [E f0.4] Φ_ethica = Min(Entropy) × Max(Coherence)
 *
 * We map "Min(Entropy)" as (Q16_ONE - entropy) clamped to [0, Q16_ONE].
 * Then multiply by coherence.
 */
raf_u32 raf_phi_ethica(raf_u32 entropy_q16, raf_u32 coherence_q16) {
    raf_u32 clamped_ent = (entropy_q16 > Q16_ONE) ? Q16_ONE : entropy_q16;
    raf_u32 min_ent     = Q16_ONE - clamped_ent;          /* 1 - entropy */
    raf_u32 clamped_coh = (coherence_q16 > Q16_ONE) ? Q16_ONE : coherence_q16;
    return q16_mul(min_ent, clamped_coh);
}

/*
 * [E f0.5] R(t+1) = R(t) × Φ_ethica × E_Verbo × SPIRAL_PI_PHI
 */
raf_u32 raf_kernel_step(raf_u32 r_t, raf_u32 entropy_q16,
                        raf_u32 coherence_q16, raf_u32 e_verbo_q16) {
    raf_u32 phi  = raf_phi_ethica(entropy_q16, coherence_q16);
    raf_u32 step = q16_mul(r_t, phi);
    step = q16_mul(step, e_verbo_q16);
    step = q16_mul(step, RAF_SPIRAL_PI_PHI_Q16);
    return step;
}

/*
 * [E f12] R_Ω = Σ_n (ψ·χ·ρ·Δ·Σ·Ω)^Φλ  (approximated as Σ saturating products)
 */
raf_u32 raf_vortex_metric(const RafCognitiveCycle *cycles, raf_u32 n_cycles) {
    raf_u32 sum = 0;
    raf_u32 i, c;
    for (i = 0; i < n_cycles; i++) {
        raf_u32 prod = Q16_ONE;
        for (c = 0; c < RAF_CYCLE_LEN; c++) {
            prod = q16_mul(prod, cycles[i].comp[c]);
        }
        sum = q16_add_sat(sum, prod);
    }
    return sum;
}

/*
 * [E f0.1] RetroΩ = (Fok + Fgap + Fnext) × W(Amor, Coh)
 * W = geometric mean ≈ sqrt(amor × coh). Approximated as mul + sqrt via Newton.
 */
raf_u32 raf_retroalimentar(const RafRetroVector *v,
                           raf_u32 amor_q16, raf_u32 coherence_q16) {
    raf_u32 total = q16_add_sat(v->f_ok, q16_add_sat(v->f_gap, v->f_next));
    /* W = sqrt(amor × coh) */
    raf_u64 w64 = q16_mul(amor_q16, coherence_q16); /* Q16.16 product */
    /* Integer sqrt of w64 (Q16.16 → √ gives Q8.8, but we keep Q16.16 scale) */
    raf_u64 s = w64, t = (s + 1) >> 1;
    while (t < s) { s = t; t = (s + w64 / (s | 1)) >> 1; }
    raf_u32 w = (raf_u32)(s & 0xFFFFFFFFu);
    return q16_mul(total, w);
}

/*
 * [E f16] Spiral(n) = (√3/2)^n  — iterated Q16.16 multiply.
 */
raf_u32 raf_spiral(raf_u32 n) {
    raf_u32 result = Q16_ONE;
    raf_u32 i;
    for (i = 0; i < n; i++) {
        result = q16_mul(result, RAF_SPIRAL_Q16);
    }
    return result;
}

/*
 * [E f17] T_Δπφ = Δ·π·φ
 */
raf_u32 raf_toroid_delta_pi_phi(raf_u32 delta_q16) {
    raf_u32 d_pi  = q16_mul(delta_q16, RAF_PI_Q16);
    return q16_mul(d_pi, RAF_PHI_Q16);
}

/*
 * [E f19] Trinity633 = Amor^6 · Luz^3 · Consciência^3  (saturating).
 */
raf_u32 raf_trinity_633(raf_u32 amor_q16, raf_u32 luz_q16, raf_u32 consciencia_q16) {
    raf_u32 a2 = q16_mul(amor_q16, amor_q16);
    raf_u32 a4 = q16_mul(a2, a2);
    raf_u32 a6 = q16_mul(a4, a2);
    raf_u32 l2 = q16_mul(luz_q16, luz_q16);
    raf_u32 l3 = q16_mul(l2, luz_q16);
    raf_u32 c2 = q16_mul(consciencia_q16, consciencia_q16);
    raf_u32 c3 = q16_mul(c2, consciencia_q16);
    return q16_mul(q16_mul(a6, l3), c3);
}

/*
 * [E f29] F_Rafael(n+1) = F_Rafael(n)×(√3/2) + π×sin(θ_999)
 *
 * Since π×sin(279°) < 0, we compute:
 *   fn_next = fn × SPIRAL_Q16/65536  −  SIN_THETA999_PI_MAG_Q16
 *
 * If the subtraction would underflow (fn×SPIRAL < magnitude), result is 0.
 */
raf_u32 raf_fibonacci_rafael_step(raf_u32 fn_q16) {
    raf_u32 scaled = q16_mul(fn_q16, RAF_SPIRAL_Q16);
    raf_u32 sub = SIN_THETA999_PI_MAG_Q16;
    /* underflow guard */
    raf_u32 underflow = rmr_mask_u32(scaled < sub);
    return select_u32(underflow, 0u, scaled - sub);
}

/*
 * [E f20] I = log2(S) — integer bits.
 */
raf_u32 raf_information_bits(raf_u64 states) {
    if (states == 0) return 0u;
    return ilog2_u64(states);
}

/*
 * [E f22] C_l = C_f × (log2(S)/p) × d × (1-r)  — integer approximation.
 */
raf_u32 raf_logical_capacity(raf_u32 c_f_q16, raf_u64 states,
                             raf_u32 p_bits, raf_u32 d_q16, raf_u32 r_q16) {
    if (p_bits == 0 || states == 0) return 0u;
    raf_u32 log2s = ilog2_u64(states);           /* integer log2(S) */
    /* log2s/p → Q16.16: (log2s << 16) / p */
    raf_u64 ratio = ((raf_u64)log2s << 16) / p_bits;
    raf_u32 ratio32 = (ratio > 0xFFFFFFFFULL) ? 0xFFFFFFFFu : (raf_u32)ratio;
    /* 1 - r */
    raf_u32 one_minus_r = (r_q16 > Q16_ONE) ? 0u : (Q16_ONE - r_q16);
    raf_u32 step = q16_mul(c_f_q16, ratio32);
    step = q16_mul(step, d_q16);
    step = q16_mul(step, one_minus_r);
    return step;
}

/*
 * [E f14] Voo_Quântico = Σ_n(Bloco_n × Salto_n × Retroalim_n).
 */
raf_u32 raf_voo_quantico(const raf_u32 *blocos, const raf_u32 *saltos,
                         const raf_u32 *retroalims, raf_u32 n) {
    raf_u32 sum = 0, i;
    for (i = 0; i < n; i++) {
        raf_u32 t = q16_mul(blocos[i], saltos[i]);
        t = q16_mul(t, retroalims[i]);
        sum = q16_add_sat(sum, t);
    }
    return sum;
}

/*
 * [E f13] Evolução_RAFAELIA = Σ_n(Bloco_n × Retroalim_n).
 */
raf_u32 raf_evolucao_rafaelia(const raf_u32 *blocos,
                              const raf_u32 *retroalims, raf_u32 n) {
    raf_u32 sum = 0, i;
    for (i = 0; i < n; i++) {
        sum = q16_add_sat(sum, q16_mul(blocos[i], retroalims[i]));
    }
    return sum;
}

/*
 * [E f3] fΩ resonance check.
 */
raf_u32 raf_in_fomega_band(raf_u32 freq_hz) {
    raf_u32 in_low  = rmr_mask_u32(freq_hz >= RAF_FOMEGA_LOW);
    raf_u32 in_high = rmr_mask_u32(freq_hz <= RAF_FOMEGA_HIGH);
    return in_low & in_high;
}

/*
 * [E f0.3] Synaptic weight = coherence_ij × Φ_ethica × r_corr × owlpsi.
 */
raf_u32 raf_synaptic_weight(raf_u32 coherence_ij_q16,
                            raf_u32 entropy_q16, raf_u32 coherence_q16,
                            raf_u32 r_corr_q16, raf_u32 owlpsi_q16) {
    raf_u32 phi  = raf_phi_ethica(entropy_q16, coherence_q16);
    raf_u32 step = q16_mul(coherence_ij_q16, phi);
    step = q16_mul(step, r_corr_q16);
    step = q16_mul(step, owlpsi_q16);
    return step;
}

/*
 * Advance one cognitive cycle (branch-free, integer only).
 *
 * Rules (integer analogues):
 *   Δ_next = Δ × Φ_ethica
 *   Σ_next = Σ + E_Verbo × Δ_next         (saturating)
 *   Ω_next = (Ω + Φ_ethica) / 2            (average toward coherence)
 *   ψ_next = Ω_next                         (ψ feeds from Ω)
 *   χ_next = |χ - ρ|                        (χ sharpens over noise)
 *   ρ_next = ρ × (Q16_ONE - Φ_ethica) >> 16 (ρ diminishes)
 */
void raf_cycle_step(const RafCognitiveCycle *in, RafCognitiveCycle *out,
                    raf_u32 entropy_q16, raf_u32 coherence_q16,
                    raf_u32 e_verbo_q16) {
    raf_u32 phi = raf_phi_ethica(entropy_q16, coherence_q16);

    raf_u32 psi   = in->comp[RAF_PSI_IDX];
    raf_u32 chi   = in->comp[RAF_CHI_IDX];
    raf_u32 rho   = in->comp[RAF_RHO_IDX];
    raf_u32 delta = in->comp[RAF_DELTA_IDX];
    raf_u32 sigma = in->comp[RAF_SIGMA_IDX];
    raf_u32 omega = in->comp[RAF_OMEGA_IDX];

    raf_u32 new_delta = q16_mul(delta, phi);
    raf_u32 new_sigma = q16_add_sat(sigma, q16_mul(e_verbo_q16, new_delta));
    raf_u32 new_omega = (omega >> 1) + (phi >> 1);          /* (Ω+Φ)/2 */
    raf_u32 new_psi   = new_omega;
    raf_u32 chi_minus_rho = (chi >= rho) ? (chi - rho) : (rho - chi);
    raf_u32 new_chi   = chi_minus_rho;
    raf_u32 one_minus_phi = (phi > Q16_ONE) ? 0u : (Q16_ONE - phi);
    raf_u32 new_rho   = q16_mul(rho, one_minus_phi);

    (void)psi;  /* psi feeds back → new_psi already set */

    out->comp[RAF_PSI_IDX]   = new_psi;
    out->comp[RAF_CHI_IDX]   = new_chi;
    out->comp[RAF_RHO_IDX]   = new_rho;
    out->comp[RAF_DELTA_IDX] = new_delta;
    out->comp[RAF_SIGMA_IDX] = new_sigma;
    out->comp[RAF_OMEGA_IDX] = new_omega;
}
