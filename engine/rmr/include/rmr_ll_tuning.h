#ifndef RMR_LL_TUNING_H
#define RMR_LL_TUNING_H

#include <stdint.h>

#include "bitomega.h"
#include "rmr_hw_detect.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  uint32_t qemu_smp_cpus;
  uint8_t qemu_use_iothread;
  uint8_t qemu_use_direct_io;

  uint32_t policy_batch_size;
  uint32_t policy_lane_width;
  uint32_t policy_commit_quantum;

  uint32_t cti_chunk_size;
  uint32_t cti_stride;
  uint32_t cti_prefetch;
} RmR_LL_TunePlan;

typedef struct {
  bitomega_node_t nodes[16];
  uint32_t vcpu_count;
  uint32_t active_mask;
} RmR_VcpuScheduler;

void RmR_LL_ApplyTuneDefaults(const RmR_HW_Info *hw, RmR_LL_TunePlan *plan);
void RmR_VcpuScheduler_Init(RmR_VcpuScheduler *sched, uint32_t vcpu_count);
uint32_t RmR_VcpuScheduler_Next(RmR_VcpuScheduler *sched, const bitomega_ctx_t *ctx);

#ifdef __cplusplus
}
#endif

#endif
