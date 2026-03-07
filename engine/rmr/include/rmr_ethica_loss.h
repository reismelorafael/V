#ifndef RMR_ETHICA_LOSS_H
#define RMR_ETHICA_LOSS_H

#include "rmr_types.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  u32 channel_weight_q16[8];
  u32 cpu_load_q16;
  u32 mem_latency_ns;
  u32 jni_overhead_ns;
  u32 coherence_q16;
} RmR_OnsagerParams;

u32 RmR_EthicaLoss_Compute(const RmR_OnsagerParams *p);
u32 RmR_EthicaLoss_PhiEthica(const RmR_OnsagerParams *p, u32 entropy_q16);

#ifdef __cplusplus
}
#endif

#endif
