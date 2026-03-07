#include "rmr_ethica_loss.h"

#include "rafaelia_formulas_core.h"

u32 RmR_EthicaLoss_Compute(const RmR_OnsagerParams *p) {
  u32 channel_loss = 0u;
  u64 transfer_loss;
  if (!p) return 0xFFFFFFFFu;

  for (u32 b = 0u; b < 8u; ++b) {
    u64 hb_rho = ((u64)p->channel_weight_q16[b] * (u64)p->cpu_load_q16) >> 16u;
    u32 rm = p->mem_latency_ns ? p->mem_latency_ns : 1u;
    channel_loss += (u32)(hb_rho / rm);
  }

  transfer_loss = ((u64)p->jni_overhead_ns * (u64)(65536u - p->coherence_q16)) >> 16u;
  return channel_loss + (u32)transfer_loss;
}

u32 RmR_EthicaLoss_PhiEthica(const RmR_OnsagerParams *p, u32 entropy_q16) {
  u32 loss = RmR_EthicaLoss_Compute(p);
  u32 coherence_q16 = (loss < 65536u) ? (65536u - loss) : 0u;
  return raf_phi_ethica(entropy_q16, coherence_q16);
}
