#include "rmr_hw_detect.h"
#include "rmr_bench.h"
#include <stdio.h>

int main(void) {
  RmR_HW_Info hw;
  RmR_Bench_Result bench;
  RmR_HW_Detect(&hw);
  RmR_Bench_Run(8u, 8u, &bench);

  printf("RAFAELIA demo_cli\n");
  printf("arch=%u ptr=%u endian=%u cycle=%u\n", hw.arch, hw.ptr_bits, hw.is_little_endian, hw.has_cycle_counter);
  printf("bench alu=%u mem=%u branch=%u matrix=%u\n", bench.alu, bench.mem, bench.branch, bench.matrix);
  return 0;
}
