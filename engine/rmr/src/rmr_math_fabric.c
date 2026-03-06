#include "rmr_math_fabric.h"
#include "rmr_ll_ops.h"

static u32 rmr_rotl32(u32 x, u32 n){
  n &= 31u;
  return (x << n) | (x >> ((32u - n) & 31u));
}

static u32 rmr_mix32(u32 x){
  x ^= x >> 16;
  x *= 0x7FEB352Du;
  x ^= x >> 15;
  x *= 0x846CA68Bu;
  x ^= x >> 16;
  return x;
}

static u32 rmr_arch_lane_count(u32 arch, u32 ptr_bits){
  u32 is_wide = rmr_mask_u32((arch == 2u) || (arch == 4u) || (arch == 9u));
  u32 is_mid = rmr_mask_u32((arch == 1u) || (arch == 3u) || (arch == 5u) || (arch == 7u) || (arch == 8u));
  u32 ptr_fallback = select_u32(rmr_mask_u32(ptr_bits >= 64u), 4u, 2u);
  u32 lanes = select_u32(is_mid, 4u, ptr_fallback);
  return select_u32(is_wide, 8u, lanes);
}

void RmR_MathFabric_AutodetectPlan(const RmR_HW_Info *hw, RmR_MathFabricPlan *out){
  if (!out) return;

  out->arch_code = hw ? hw->arch : 0u;
  out->register_bits = hw ? hw->word_bits : 32u;
  out->cacheline_bytes = hw ? hw->cacheline_bytes : 64u;
  out->page_bytes = hw ? hw->page_bytes : 4096u;
  out->pin_stride = select_u32(rmr_mask_u32(out->cacheline_bytes >= 64u),
                               (out->cacheline_bytes >> 3),
                               8u);
  out->lane_count = rmr_arch_lane_count(out->arch_code, hw ? hw->ptr_bits : 32u);

  out->matrix_seed = 0xB16B00B5u
    ^ (out->arch_code << 19)
    ^ (out->register_bits << 11)
    ^ (out->lane_count << 7)
    ^ out->cacheline_bytes
    ^ (out->page_bytes >> 5);

  for (u32 d = 0; d < RMR_MATH_DOMAINS; ++d){
    for (u32 p = 0; p < RMR_MATH_POINTS; ++p){
      u32 lane = (d * RMR_MATH_POINTS) + p + 1u;
      u32 pin = lane * out->pin_stride;
      u32 m = out->matrix_seed ^ (d << 24) ^ (p << 16) ^ pin;
      u32 entropy = hw ? (hw->mem_bus_bits ^ hw->align_bytes ^ hw->is_little_endian) : 0x5Au;
      out->matrix[d][p] = (rmr_mix32(m ^ entropy) & 0x0000FFFFu) + 1u;
    }
  }
}

void RmR_MathFabric_VectorMix(const RmR_MathFabricPlan *plan,
                              const u32 in_points[RMR_MATH_POINTS],
                              u32 out_domains[RMR_MATH_DOMAINS]){
  if (!plan || !in_points || !out_domains) return;

  for (u32 d = 0; d < RMR_MATH_DOMAINS; ++d){
    u32 acc = plan->matrix_seed ^ (d << 13);
    for (u32 p = 0; p < RMR_MATH_POINTS; ++p){
      u32 weight = plan->matrix[d][p];
      u32 sample = in_points[p] ^ (p * 0x9E3779B9u);
      acc ^= rmr_mix32(sample * weight + (plan->pin_stride * (p + 1u)));
      acc = rmr_rotl32(acc, (p + d + plan->lane_count) & 31u);
    }
    out_domains[d] = acc;
  }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * RAFAELIA Math Fabric Extensions
 * ═══════════════════════════════════════════════════════════════════════════ */
static u32 rmr_raf_q16_mul(u32 a, u32 b) {
  unsigned long long p = (unsigned long long)a * (unsigned long long)b;
  unsigned long long q = p >> 16;
  return (q > 0xFFFFFFFFULL) ? 0xFFFFFFFFu : (u32)q;
}

void RmR_MathFabric_RafaeliaExtend(RmR_MathFabricRafaeliaExt *out) {
  if (!out) return;
  out->spiral_q16 = 56756u;
  out->phi_q16 = 106039u;
  out->pi_q16 = 205887u;
  out->spiral_pi_phi_q16 = 23163u;
  out->r_corr_q16 = 63176u;
  out->theta_999_sin_pi_q16 = 203360u;
  out->fomega_low = 963u;
  out->fomega_high = 999u;
  out->ruler_42 = 42u;
  out->calibration_999 = 999u;
}

u32 RmR_MathFabric_Spiral(const RmR_MathFabricRafaeliaExt *ext, u32 n) {
  u32 result = 65536u;
  if (!ext) return result;
  for (u32 i = 0; i < n; i++) {
    result = rmr_raf_q16_mul(result, ext->spiral_q16);
  }
  return result;
}

u32 RmR_MathFabric_FibRafaelStep(const RmR_MathFabricRafaeliaExt *ext, u32 fn_q16) {
  if (!ext) return 0u;
  u32 scaled = rmr_raf_q16_mul(fn_q16, ext->spiral_q16);
  u32 sub = ext->theta_999_sin_pi_q16;
  return (scaled < sub) ? 0u : (scaled - sub);
}
