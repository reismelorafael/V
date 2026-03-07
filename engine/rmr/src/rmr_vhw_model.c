#include "rmr_vhw_model.h"

#include "zero.h"
#include "zero_compat.h"

void RmR_VHW_Build(RmR_VirtualHWModel *model, u8 guest_arch_id) {
  u32 native = 0u;
  if (!model) return;
  rmr_mem_set(model, 0u, sizeof(*model));

  RmR_HW_Detect(&model->host_hw);
  RmR_MathFabric_AutodetectPlan(&model->host_hw, &model->fabric);

  if (model->host_hw.arch == RMR_ZERO_HW_ARCH_ARM64_U32 && guest_arch_id == RMR_ZERO_HW_ARCH_X86_64_U32) {
    model->map[model->map_count++] = (RmR_FeatureMap){0x0001u, 0x0010u, 0u, 1u};
    model->map[model->map_count++] = (RmR_FeatureMap){0x0002u, 0x0020u, 0u, 1u};
    model->map[model->map_count++] = (RmR_FeatureMap){0x0003u, 0x0030u, 0u, 1u};
    model->map[model->map_count++] = (RmR_FeatureMap){0x0004u, 0x0040u, 15u, 1u};
  }

  for (u32 i = 0u; i < model->map_count; ++i) {
    if (model->map[i].available && model->map[i].overhead_percent <= 5u) native += 1u;
  }

  model->native_percent = (model->map_count == 0u) ? 0u : (native * 100u) / model->map_count;
  model->emulated_percent = 100u - model->native_percent;
}
