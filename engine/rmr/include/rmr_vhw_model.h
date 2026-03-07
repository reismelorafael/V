#ifndef RMR_VHW_MODEL_H
#define RMR_VHW_MODEL_H

#include "rmr_hw_detect.h"
#include "rmr_math_fabric.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  u32 guest_feature_id;
  u32 host_impl_id;
  u32 overhead_percent;
  u8 available;
} RmR_FeatureMap;

typedef struct {
  RmR_HW_Info host_hw;
  RmR_MathFabricPlan fabric;
  RmR_FeatureMap map[64];
  u32 map_count;
  u32 native_percent;
  u32 emulated_percent;
} RmR_VirtualHWModel;

void RmR_VHW_Build(RmR_VirtualHWModel *model, u8 guest_arch_id);

#ifdef __cplusplus
}
#endif

#endif
