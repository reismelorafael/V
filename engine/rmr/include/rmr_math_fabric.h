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

/* ─── RAFAELIA Math Fabric Extensions (formula index §16,17,19,29) ────────── */
#define RMR_DOMAIN_SPIRAL     RMR_DOMAIN_GEOMETRY
#define RMR_DOMAIN_TOROID     RMR_DOMAIN_ANALYSIS
#define RMR_DOMAIN_FIBONACCI  RMR_DOMAIN_NUMBER_THEORY
#define RMR_DOMAIN_TRINITY    RMR_DOMAIN_ALGEBRA
#define RMR_DOMAIN_ETHICA     RMR_DOMAIN_LOGIC
#define RMR_DOMAIN_RETRO      RMR_DOMAIN_DISCRETE

typedef struct {
  u32 spiral_q16;
  u32 phi_q16;
  u32 pi_q16;
  u32 spiral_pi_phi_q16;
  u32 r_corr_q16;
  u32 theta_999_sin_pi_q16;
  u32 fomega_low;
  u32 fomega_high;
  u32 ruler_42;
  u32 calibration_999;
} RmR_MathFabricRafaeliaExt;

void RmR_MathFabric_RafaeliaExtend(RmR_MathFabricRafaeliaExt *out);
u32 RmR_MathFabric_Spiral(const RmR_MathFabricRafaeliaExt *ext, u32 n);
u32 RmR_MathFabric_FibRafaelStep(const RmR_MathFabricRafaeliaExt *ext, u32 fn_q16);

#endif
