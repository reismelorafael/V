#include "rmr_bench_suite.h"
#include "rmr_hw_detect.h"
#include "rmr_isorf.h"
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

static void write_json(
    const char *path,
    const RmR_Bench_SuiteResult *r,
    const RmR_HW_Info *hw,
    const RmR_ISOraf_Manifest *manifest,
    const u64 *matrix_map,
    u32 matrix_map_count) {
  FILE *f = fopen(path, "w");
  if (!f) return;
  fprintf(f, "{\n");
  fprintf(f, "  \"hw\": {\"arch\": %u, \"ptr_bits\": %u, \"little_endian\": %u},\n", hw->arch, hw->ptr_bits, hw->is_little_endian);
  fprintf(f, "  \"manifest\": {\"magic\": %llu, \"identity\": %llu, \"logical_bits\": %llu, \"physical_bits\": %llu, \"page_bits\": %u, \"page_count\": %u, \"pages_used\": %u, \"data_word_used\": %u},\n",
          (unsigned long long)manifest->magic,
          (unsigned long long)manifest->identity,
          (unsigned long long)manifest->logical_bits,
          (unsigned long long)manifest->physical_bits,
          manifest->page_bits,
          manifest->page_count,
          manifest->pages_used,
          manifest->data_word_used);
  fprintf(f, "  \"matrix_map\": [");
  for (u32 i = 0; i < matrix_map_count; ++i) {
    const char *comma = (i + 1u < matrix_map_count) ? "," : "";
    fprintf(f, "%s%llu", (i == 0u) ? "" : " ", (unsigned long long)matrix_map[i]);
    if (*comma) fprintf(f, ",");
  }
  fprintf(f, "],\n");
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
  enum { PAGE_COUNT = 24, DATA_WORDS = 24 * 64 };
  RmR_ISOraf_Page pages[PAGE_COUNT];
  u64 words[DATA_WORDS];
  RmR_ISOraf_Store isorf;
  RmR_ISOraf_Manifest manifest;
  u64 matrix_map[PAGE_COUNT];
  RmR_BenchSuite_Run(&cfg, &res);
  RmR_HW_Detect(&hw);

  RmR_ISOraf_Init(&isorf, pages, PAGE_COUNT, words, DATA_WORDS, 4096u);
  for (u64 b = 0u; b < 10u; ++b) {
    u64 idx = (b * 4096u) + (17u * b) + 7u;
    RmR_ISOraf_SetBit(&isorf, idx, 1u);
  }
  RmR_ISOraf_ExportManifest(&isorf, &manifest);
  u32 matrix_map_count = RmR_ISOraf_ExportMatrixMap(&isorf, matrix_map, PAGE_COUNT);

  write_csv(csv, &res);
  write_json(json, &res, &hw, &manifest, matrix_map, matrix_map_count);

  printf("bench_total_score=%u\n", res.total_score);
  printf("bench_total_error=%u\n", res.total_error);
  printf("bench_exec_signature=%llu\n", (unsigned long long)res.exec_signature);
  printf("isorf_identity=%llu pages_used=%u rebuild=%u\n",
         (unsigned long long)manifest.identity,
         manifest.pages_used,
         (unsigned)RmR_ISOraf_RebuildCheck(&isorf, &manifest));
  printf("csv=%s\njson=%s\n", csv, json);
  return 0;
}
