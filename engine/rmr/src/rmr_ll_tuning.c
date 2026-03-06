#include "rmr_ll_tuning.h"
#include "zero_compat.h"

static uint32_t clamp_u32(uint32_t v, uint32_t lo, uint32_t hi) {
  if (v < lo) return lo;
  if (v > hi) return hi;
  return v;
}

void RmR_LL_ApplyTuneDefaults(const RmR_HW_Info *hw, RmR_LL_TunePlan *plan) {
  if (!plan) return;

  rmr_mem_set(plan, 0, sizeof(*plan));
  plan->qemu_smp_cpus = 2u;
  plan->qemu_use_iothread = 1u;
  plan->qemu_use_direct_io = 0u;

  plan->policy_batch_size = 4096u;
  plan->policy_lane_width = 8u;
  plan->policy_commit_quantum = 16u;

  plan->cti_chunk_size = 4096u;
  plan->cti_stride = 1u;
  plan->cti_prefetch = 256u;

  if (!hw) return;

  {
    uint32_t native_64 = (hw->word_bits >= 64u && hw->ptr_bits >= 64u) ? 1u : 0u;
    uint32_t l2_kib = hw->cache_hint_l2 / 1024u;
    uint32_t l4_mib = hw->cache_hint_l4 / (1024u * 1024u);
    uint32_t line = hw->cacheline_bytes ? hw->cacheline_bytes : 64u;

    plan->qemu_smp_cpus = native_64 ? 8u : 4u;
    if (l2_kib >= 512u) plan->qemu_smp_cpus += 2u;
    if (l4_mib >= 16u) plan->qemu_smp_cpus += 2u;
    plan->qemu_smp_cpus = clamp_u32(plan->qemu_smp_cpus, 2u, 16u);

    plan->policy_batch_size = clamp_u32(line * 128u, 2048u, 65536u);
    if (l2_kib >= 1024u) plan->policy_batch_size = clamp_u32(plan->policy_batch_size * 2u, 4096u, 131072u);

    plan->policy_lane_width = native_64 ? 16u : 8u;
    if (l4_mib >= 16u) plan->policy_lane_width = 32u;
    plan->policy_lane_width = clamp_u32(plan->policy_lane_width, 4u, 32u);

    plan->policy_commit_quantum = native_64 ? 32u : 16u;
    if (l4_mib >= 16u) plan->policy_commit_quantum = 64u;

    plan->cti_chunk_size = clamp_u32(line * 64u, 1024u, 65536u);
    plan->cti_stride = (hw->has_cycle_counter || hw->has_asm_probe) ? 2u : 1u;
    plan->cti_prefetch = clamp_u32(line * 4u, 64u, 1024u);
  }
}
