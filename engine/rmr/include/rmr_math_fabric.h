#ifndef RMR_MATH_FABRIC_H
#define RMR_MATH_FABRIC_H

#include "rmr_hw_detect.h"

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

#define RMR_MATH_DOMAINS 8u
#define RMR_MATH_POINTS 9u

typedef enum {
  RMR_DOMAIN_ALGEBRA = 0,
  RMR_DOMAIN_TRIGONOMETRY = 1,
  RMR_DOMAIN_NUMBER_THEORY = 2,
  RMR_DOMAIN_GEOMETRY = 3,
  RMR_DOMAIN_LOGIC = 4,
  RMR_DOMAIN_ANALYSIS = 5,
  RMR_DOMAIN_DISCRETE = 6,
  RMR_DOMAIN_PROBABILITY = 7
} RmR_MathDomain;

typedef struct {
  u32 arch_code;
  u32 register_bits;
  u32 lane_count;
  u32 pin_stride;
  u32 cacheline_bytes;
  u32 page_bytes;
  u32 matrix_seed;
  u32 matrix[RMR_MATH_DOMAINS][RMR_MATH_POINTS];
} RmR_MathFabricPlan;

void RmR_MathFabric_AutodetectPlan(const RmR_HW_Info *hw, RmR_MathFabricPlan *out);
void RmR_MathFabric_VectorMix(const RmR_MathFabricPlan *plan,
                              const u32 in_points[RMR_MATH_POINTS],
                              u32 out_domains[RMR_MATH_DOMAINS]);

#endif
