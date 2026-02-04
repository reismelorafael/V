/* rmr_bench.h - microbenchmarks determinísticos low-level */
#ifndef RMR_BENCH_H
#define RMR_BENCH_H

typedef unsigned char u8;
typedef unsigned int u32;

typedef struct {
  u32 alu;
  u32 mem;
  u32 branch;
  u32 matrix;
} RmR_Bench_Result;

void RmR_Bench_Run(u8 size, u8 shift, RmR_Bench_Result *out);

#endif
