#include "rmr_zipraf_core.h"

#include "bitraf.h"
#include "rmr_hw_detect.h"
#include "rmr_math_fabric.h"
#include "rmr_policy_kernel.h"

static uint32_t rmr_zipraf_u32_from_u64_lo(uint64_t v) {
  return (uint32_t)(v & 0xFFFFFFFFu);
}

static uint32_t rmr_zipraf_u32_from_u64_hi(uint64_t v) {
  return (uint32_t)((v >> 32u) & 0xFFFFFFFFu);
}

int RmR_Zipraf_Execute(const RmR_ZiprafInput *in, RmR_ZiprafOutput *out) {
  RmR_HW_Info hw;
  RmR_MathFabricPlan plan;
  u32 points[RMR_MATH_POINTS];
  u32 domains[RMR_MATH_DOMAINS];
  uint64_t hash_seed;
  uint64_t signed_mix_a;
  uint64_t signed_mix_b;

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
  points[3] = (uint32_t)(in->payload_len & 0xFFFFFFFFu);
  points[4] = (uint32_t)((in->payload_len >> 32u) & 0xFFFFFFFFu);
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

  out->route_tag = ((uint64_t)domains[4] << 32u) ^ (uint64_t)domains[5] ^
                   ((uint64_t)out->crc32c << 1u) ^ out->bitraf_hash ^ (uint64_t)in->trajectory_id;

  if (((domains[6] ^ domains[7]) & in->invariant_mask) == 0u) {
    out->status_flags |= RMR_ZIPRAF_STATUS_INVARIANT_MATCH;
  }

  return 0;
}
