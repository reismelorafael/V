#include "rmr_zipraf_core.h"

#include "bitraf.h"
#include "rmr_hw_detect.h"
#include "rmr_math_fabric.h"
#include "rmr_policy_kernel.h"

static uint64_t rmr_zipraf_abs64(int64_t v) {
  return (uint64_t)(v < 0 ? -v : v);
}

int RmR_Zipraf_TriFlow3x6(const int64_t state3[3], int64_t flow6[6]) {
  int64_t a;
  int64_t b;
  int64_t c;
  if (!state3 || !flow6) return -1;
  a = state3[0];
  b = state3[1];
  c = state3[2];
  flow6[0] = a - b;
  flow6[1] = b - a;
  flow6[2] = b - c;
  flow6[3] = c - b;
  flow6[4] = c - a;
  flow6[5] = a - c;
  return 0;
}

int RmR_Zipraf_TriCloseBase10(const int64_t flow6[6], int64_t closed3[3], uint32_t *out_coherence) {
  int64_t p0;
  int64_t p1;
  int64_t p2;
  int64_t cycle;
  uint64_t err;
  uint32_t coherence;
  if (!flow6 || !closed3 || !out_coherence) return -1;

  p0 = flow6[0] - flow6[1];
  p1 = flow6[2] - flow6[3];
  p2 = flow6[4] - flow6[5];
  closed3[0] = p0 - p2;
  closed3[1] = p1 - p0;
  closed3[2] = p2 - p1;

  cycle = flow6[0] + flow6[2] + flow6[4];
  err = rmr_zipraf_abs64(flow6[0] + flow6[1]) +
        rmr_zipraf_abs64(flow6[2] + flow6[3]) +
        rmr_zipraf_abs64(flow6[4] + flow6[5]) +
        rmr_zipraf_abs64(cycle);

  if (err >= 1023u) {
    coherence = 0u;
  } else {
    coherence = 1023u - (uint32_t)err;
  }
  *out_coherence = coherence;
  return 0;
}

static uint32_t rmr_zipraf_u32_from_u64_lo(uint64_t v) {
  return (uint32_t)(v & RMR_ZERO_ZIPRAF_U32_MASK_U64);
}

static uint32_t rmr_zipraf_u32_from_u64_hi(uint64_t v) {
  return (uint32_t)((v >> 32u) & RMR_ZERO_ZIPRAF_U32_MASK_U64);
}

int RmR_Zipraf_Execute(const RmR_ZiprafInput *in, RmR_ZiprafOutput *out) {
  RmR_HW_Info hw;
  RmR_MathFabricPlan plan;
  u32 points[RMR_MATH_POINTS];
  u32 domains[RMR_MATH_DOMAINS];
  uint64_t hash_seed;
  uint64_t signed_mix_a;
  uint64_t signed_mix_b;
  int64_t tri_state[3];
  int64_t tri_flow[6];
  int64_t tri_closed[3];
  uint32_t tri_coherence;

  if (!out) return -1;

  out->route_tag = 0u;
  out->bitraf_hash = 0u;
  out->crc32c = 0u;
  out->det_signature = 0;
  out->status_flags = RMR_ZIPRAF_STATUS_OK;

  if (!in || (!in->payload_ptr && in->payload_len != 0u)) {
    out->status_flags |= RMR_ZIPRAF_STATUS_ERR_ARG;
    return -1;
  }

  if (in->payload_len == 0u) {
    out->status_flags |= RMR_ZIPRAF_STATUS_EMPTY_PAYLOAD;
  }

  hash_seed = ((uint64_t)in->seed << 32u) ^ (uint64_t)in->trajectory_id;
  out->bitraf_hash = bitraf_hash(in->payload_ptr, in->payload_len, hash_seed);
  out->crc32c = RmR_CRC32C(in->payload_ptr, in->payload_len);

  points[0] = in->seed;
  points[1] = in->trajectory_id;
  points[2] = in->invariant_mask;
  points[3] = (uint32_t)(in->payload_len & RMR_ZERO_ZIPRAF_U32_MASK_U64);
  points[4] = (uint32_t)((in->payload_len >> 32u) & RMR_ZERO_ZIPRAF_U32_MASK_U64);
  points[5] = out->crc32c;
  points[6] = rmr_zipraf_u32_from_u64_lo(out->bitraf_hash);
  points[7] = rmr_zipraf_u32_from_u64_hi(out->bitraf_hash);
  points[8] = points[0] ^ points[1] ^ points[5] ^ points[6];

  RmR_HW_Detect(&hw);
  RmR_MathFabric_AutodetectPlan(&hw, &plan);
  RmR_MathFabric_VectorMix(&plan, points, domains);

  signed_mix_a = (uint64_t)((uint64_t)domains[0] * (uint64_t)(domains[3] | 1u));
  signed_mix_b = (uint64_t)((uint64_t)domains[1] * (uint64_t)(domains[2] | 1u));
  out->det_signature = (int64_t)(signed_mix_a - signed_mix_b);

  tri_state[0] = (int64_t)(int32_t)(points[0] ^ points[6]);
  tri_state[1] = (int64_t)(int32_t)(points[1] ^ points[7]);
  tri_state[2] = (int64_t)(int32_t)(points[5] ^ points[8]);
  if (RmR_Zipraf_TriFlow3x6(tri_state, tri_flow) == 0 &&
      RmR_Zipraf_TriCloseBase10(tri_flow, tri_closed, &tri_coherence) == 0) {
    out->det_signature ^= (int64_t)((uint64_t)(uint32_t)tri_closed[0] << 1u);
    out->det_signature ^= (int64_t)((uint64_t)(uint32_t)tri_closed[1] << 2u);
    out->det_signature ^= (int64_t)((uint64_t)(uint32_t)tri_closed[2] << 3u);
    if (tri_coherence >= RMR_ZERO_ZIPRAF_TRI_COHERENT_MIN_U32) {
      out->status_flags |= RMR_ZIPRAF_STATUS_TRI_COHERENT;
    }
  }

  out->route_tag = ((uint64_t)domains[4] << 32u) ^ (uint64_t)domains[5] ^
                   ((uint64_t)out->crc32c << 1u) ^ out->bitraf_hash ^ (uint64_t)in->trajectory_id;
  out->route_tag ^= ((uint64_t)(uint32_t)tri_flow[0] << 48u) ^ ((uint64_t)(uint32_t)tri_flow[2] << 24u);
  out->route_tag ^= ((uint64_t)tri_coherence << 12u) ^ (uint64_t)(uint32_t)tri_closed[1];

  if (((domains[6] ^ domains[7]) & in->invariant_mask) == 0u) {
    out->status_flags |= RMR_ZIPRAF_STATUS_INVARIANT_MATCH;
  }

  return 0;
}
