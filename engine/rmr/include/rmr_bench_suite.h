/* rmr_bench_suite.h - suite industrial (50 testes) */
#ifndef RMR_BENCH_SUITE_H
#define RMR_BENCH_SUITE_H

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

#define RMR_BENCH_COUNT 50u

typedef struct {
  u32 score;
  u32 variance;
  u32 error_margin;
} RmR_Bench_Metric;

typedef struct {
  RmR_Bench_Metric metric[RMR_BENCH_COUNT];
  u32 total_score;
  u32 total_error;
} RmR_Bench_SuiteResult;

typedef struct {
  u32 budget_cycles;
  u32 max_iters;
  u32 stride_bytes;
  u32 matrix_n;
} RmR_Bench_Config;

void RmR_BenchSuite_Run(const RmR_Bench_Config *cfg, RmR_Bench_SuiteResult *out);

#endif
