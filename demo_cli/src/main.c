#include "rmr_hw_detect.h"
#include "rmr_bench.h"
#include "rmr_isorf.h"
#include "rmr_math_fabric.h"
#include <stdio.h>

int main(void) {
  RmR_HW_Info hw;
  RmR_Bench_Result bench;
  RmR_MathFabricPlan math_plan;
  u32 math_points[RMR_MATH_POINTS];
  u32 math_domains[RMR_MATH_DOMAINS];

  enum { PAGE_COUNT = 16, DATA_WORDS = 16 * 64 };
  RmR_ISOraf_Page pages[PAGE_COUNT];
  u64 data_words[DATA_WORDS];
  RmR_ISOraf_Store store;
  RmR_ISOraf_Manifest mf;
  u64 matrix_map[PAGE_COUNT];

  RmR_HW_Detect(&hw);
  RmR_Bench_Run(8u, 8u, &bench);
  RmR_MathFabric_AutodetectPlan(&hw, &math_plan);

  RmR_ISOraf_Init(&store, pages, PAGE_COUNT, data_words, DATA_WORDS, 4096u);
  RmR_ISOraf_SetBit(&store, 17u, 1u);
  RmR_ISOraf_SetBit(&store, 4098u, 1u);
  RmR_ISOraf_SetBit(&store, 12299u, 1u);
  RmR_ISOraf_ExportManifest(&store, &mf);
  u32 map_n = RmR_ISOraf_ExportMatrixMap(&store, matrix_map, PAGE_COUNT);

  for (u32 i = 0; i < RMR_MATH_POINTS; ++i) {
    math_points[i] = (u32)(mf.identity >> ((i & 7u) * 8u)) ^ (i * 0x9E3779B9u);
  }
  RmR_MathFabric_VectorMix(&math_plan, math_points, math_domains);

  printf("RAFAELIA demo_cli\n");
  printf("arch=%u ptr=%u endian=%u cycle=%u\n", hw.arch, hw.ptr_bits, hw.is_little_endian, hw.has_cycle_counter);
  printf("bench alu=%u mem=%u branch=%u matrix=%u\n", bench.alu, bench.mem, bench.branch, bench.matrix);
  printf("math_fabric seed=%u lanes=%u stride=%u d0=%u d7=%u\n",
         math_plan.matrix_seed, math_plan.lane_count, math_plan.pin_stride,
         math_domains[0], math_domains[7]);
  printf("isorf identity=%llu pages_used=%u logical_bits=%llu physical_bits=%llu rebuild=%u map=%u\n",
         (unsigned long long)mf.identity,
         mf.pages_used,
         (unsigned long long)mf.logical_bits,
         (unsigned long long)mf.physical_bits,
         (unsigned)RmR_ISOraf_RebuildCheck(&store, &mf),
         map_n);
  return 0;
}
