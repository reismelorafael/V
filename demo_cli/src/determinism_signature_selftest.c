#include "rmr_bench_suite.h"
#include <stdio.h>

int main(void) {
  RmR_Bench_Config cfg;
  cfg.budget_cycles = 0u;
  cfg.max_iters = 2048u;
  cfg.stride_bytes = 4u;
  cfg.matrix_n = 6u;

  RmR_Bench_SuiteResult a;
  RmR_Bench_SuiteResult b;
  RmR_BenchSuite_Run(&cfg, &a);
  RmR_BenchSuite_Run(&cfg, &b);

  if (a.exec_signature != b.exec_signature) {
    printf("FAIL exec_signature mismatch a=%llu b=%llu\n",
           (unsigned long long)a.exec_signature,
           (unsigned long long)b.exec_signature);
    return 1;
  }

  for (u32 i = 0u; i < RMR_BENCH_COUNT; ++i) {
    const RmR_Bench_Metric *ma = &a.metric[i];
    const RmR_Bench_Metric *mb = &b.metric[i];
    if (ma->stage_seed != mb->stage_seed ||
        ma->tune_plan != mb->tune_plan ||
        ma->path_id != mb->path_id ||
        ma->output_checksum != mb->output_checksum ||
        ma->stage_signature != mb->stage_signature) {
      printf("FAIL stage signature mismatch idx=%u\n", i);
      return 1;
    }
  }

  printf("OK determinism signature selftest exec_signature=%llu\n",
         (unsigned long long)a.exec_signature);
  return 0;
}
