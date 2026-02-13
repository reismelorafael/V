#include "rmr_hw_detect.h"
#include "rmr_math_fabric.h"
#include <stdio.h>

int main(void){
  RmR_HW_Info hw;
  RmR_MathFabricPlan p1, p2;
  u32 points[RMR_MATH_POINTS];
  u32 d1[RMR_MATH_DOMAINS];
  u32 d2[RMR_MATH_DOMAINS];
  u32 c1 = 0u;
  u32 c2 = 0u;

  RmR_HW_Detect(&hw);
  RmR_MathFabric_AutodetectPlan(&hw, &p1);
  RmR_MathFabric_AutodetectPlan(&hw, &p2);

  for (u32 i = 0; i < RMR_MATH_POINTS; ++i) {
    points[i] = ((i + 1u) * 0x11111111u) ^ p1.matrix_seed;
  }

  RmR_MathFabric_VectorMix(&p1, points, d1);
  RmR_MathFabric_VectorMix(&p2, points, d2);

  for (u32 d = 0; d < RMR_MATH_DOMAINS; ++d) {
    c1 ^= d1[d] + (d << 8);
    c2 ^= d2[d] + (d << 8);
  }

  printf("math_fabric_selftest arch=%u lanes=%u stride=%u seed=%u checksum=%u\n",
         p1.arch_code,
         p1.lane_count,
         p1.pin_stride,
         p1.matrix_seed,
         c1);

  return (c1 != 0u && c1 == c2 && p1.matrix[0][0] == p2.matrix[0][0] && p1.matrix[7][8] == p2.matrix[7][8]) ? 0 : 1;
}
