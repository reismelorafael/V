#include "rmr_bench_suite.h"
#include "rmr_hw_detect.h"
#include <stdio.h>
#include <stdlib.h>

static void write_csv(const char *path, const RmR_Bench_SuiteResult *r) {
  FILE *f = fopen(path, "w");
  if (!f) return;
  fprintf(f, "metric,score,variance,error_margin\n");
  for (unsigned i = 0; i < RMR_BENCH_COUNT; ++i) {
    fprintf(f, "%u,%u,%u,%u\n", i, r->metric[i].score, r->metric[i].variance, r->metric[i].error_margin);
  }
  fprintf(f, "total,%u,0,%u\n", r->total_score, r->total_error);
  fclose(f);
}

static void write_json(const char *path, const RmR_Bench_SuiteResult *r, const RmR_HW_Info *hw) {
  FILE *f = fopen(path, "w");
  if (!f) return;
  fprintf(f, "{\n");
  fprintf(f, "  \"hw\": {\"arch\": %u, \"ptr_bits\": %u, \"little_endian\": %u},\n", hw->arch, hw->ptr_bits, hw->is_little_endian);
  fprintf(f, "  \"total_score\": %u,\n", r->total_score);
  fprintf(f, "  \"total_error\": %u,\n", r->total_error);
  fprintf(f, "  \"metrics\": [\n");
  for (unsigned i = 0; i < RMR_BENCH_COUNT; ++i) {
    const char *comma = (i + 1u < RMR_BENCH_COUNT) ? "," : "";
    fprintf(f, "    {\"id\": %u, \"score\": %u, \"variance\": %u, \"error_margin\": %u}%s\n",
            i, r->metric[i].score, r->metric[i].variance, r->metric[i].error_margin, comma);
  }
  fprintf(f, "  ]\n");
  fprintf(f, "}\n");
  fclose(f);
}

int main(int argc, char **argv) {
  const char *csv = (argc > 1) ? argv[1] : "bench/results/latest.csv";
  const char *json = (argc > 2) ? argv[2] : "bench/results/latest.json";

  RmR_Bench_Config cfg = {0u, 2048u, 4u, 6u};
  RmR_Bench_SuiteResult res;
  RmR_HW_Info hw;
  RmR_BenchSuite_Run(&cfg, &res);
  RmR_HW_Detect(&hw);

  write_csv(csv, &res);
  write_json(json, &res, &hw);

  printf("bench_total_score=%u\n", res.total_score);
  printf("bench_total_error=%u\n", res.total_error);
  printf("csv=%s\njson=%s\n", csv, json);
  return 0;
}
