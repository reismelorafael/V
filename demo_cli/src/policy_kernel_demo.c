#include "rmr_policy_kernel.h"

#include <stdio.h>
#include <stdlib.h>

static void usage(const char *bin) {
  printf("uso: %s <input> <output> <audit.log> [chunk_size] [xor_hex] [stride] [cpu_ok] [ram_ok] [disk_ok]\n", bin);
}

int main(int argc, char **argv) {
  if (argc < 4) {
    usage(argv[0]);
    return 2;
  }

  RmR_PipelineConfig cfg;
  cfg.chunk_size = (argc > 4) ? (size_t)strtoull(argv[4], NULL, 10) : 4096u;
  cfg.mutation_xor = (argc > 5) ? (unsigned char)strtoul(argv[5], NULL, 16) : 0xA5u;
  cfg.mutation_stride = (argc > 6) ? (unsigned int)strtoul(argv[6], NULL, 10) : 31u;
  cfg.triad.cpu_ok = (argc > 7) ? (unsigned char)strtoul(argv[7], NULL, 10) : 1u;
  cfg.triad.ram_ok = (argc > 8) ? (unsigned char)strtoul(argv[8], NULL, 10) : 1u;
  cfg.triad.disk_ok = (argc > 9) ? (unsigned char)strtoul(argv[9], NULL, 10) : 1u;

  RmR_AuditSummary summary;
  int rc = RmR_RunPolicyPipeline(argv[1], argv[2], argv[3], &cfg, &summary);

  printf("pipeline_rc=%d planned=%u applied=%u diff=%u verified=%u verify_failures=%u\n",
         rc,
         summary.chunks_planned,
         summary.chunks_applied,
         summary.chunks_diff,
         summary.chunks_verified,
         summary.verify_failures);
  return (rc < 0) ? 1 : 0;
}
