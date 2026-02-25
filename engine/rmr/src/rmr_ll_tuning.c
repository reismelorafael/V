#include "rmr_ll_tuning.h"
#if defined(RMR_JNI_BUILD) && RMR_JNI_BUILD
#include <string.h>
#else
#include "rmr_baremetal_compat.h" /* baremetal memset substitute */
#endif

/* BUG FIX baremetal: string.h removido */

static uint32_t clamp_u32(uint32_t v, uint32_t lo, uint32_t hi) {
  if (v < lo) return lo;
  if (v > hi) return hi;
  return v;
}

void RmR_LL_ApplyTuneDefaults(const RmR_HW_Info *hw, RmR_LL_TunePlan *plan) {
  if (!plan) return;

  memset(plan, 0, sizeof(*plan));
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
    uint32_t line = hw->cacheline_bytes ? hw->cacheline_bytes : 64u;

    plan->qemu_smp_cpus = native_64 ? 8u : 4u;
    if (hw->arch == 4u || hw->arch == 2u) plan->qemu_smp_cpus += 2u;
    if (l2_kib >= 1024u) plan->qemu_smp_cpus += 2u;
    plan->qemu_smp_cpus = clamp_u32(plan->qemu_smp_cpus, 2u, 16u);

    plan->qemu_use_iothread = (l2_kib >= 256u) ? 1u : 0u;
    plan->qemu_use_direct_io = (l2_kib >= 512u && hw->mem_bus_bits >= 32u) ? 1u : 0u;

    plan->policy_lane_width = native_64 ? 16u : 8u;
    if (line >= 128u) plan->policy_lane_width += 8u;
    plan->policy_lane_width = clamp_u32(plan->policy_lane_width, 4u, 32u);

    plan->policy_batch_size = line * plan->policy_lane_width;
    if (hw->cache_hint_l1 > 0u && hw->cache_hint_l1 < plan->policy_batch_size) {
      plan->policy_batch_size = hw->cache_hint_l1;
    }
    plan->policy_batch_size = clamp_u32(plan->policy_batch_size, 512u, 65536u);

    plan->policy_commit_quantum = (hw->cache_hint_l3 >= (2u * 1024u * 1024u)) ? 64u : 24u;
    if (!native_64) plan->policy_commit_quantum = 12u;

    plan->cti_chunk_size = clamp_u32(line * 64u, 1024u, 65536u);
    if (hw->page_bytes >= 4096u) plan->cti_chunk_size = clamp_u32(hw->page_bytes, 1024u, 65536u);
    plan->cti_stride = (hw->gpio_pin_stride > 1u) ? clamp_u32(hw->gpio_pin_stride, 1u, 64u) : 1u;
    plan->cti_prefetch = clamp_u32(line * 4u, 64u, 2048u);
  }
}
