#include "rmr_casm_bridge.h"

#include <stdint.h>
#include <stdio.h>

static int expect(int cond, const char *msg) {
  if (!cond) {
    fprintf(stderr, "FAIL: %s\n", msg);
    return 1;
  }
  return 0;
}

int main(void) {
  int failed = 0;
  uint8_t sample[257];
  uint32_t i;

  for (i = 0u; i < 257u; ++i) {
    sample[i] = (uint8_t)((i * 13u) ^ (i >> 1u));
  }

  uint32_t asm_out = 0u;
  uint32_t c_out = 0u;
  uint32_t interop = RmR_CASM_XorFold32_Interop(sample, 257u, &asm_out, &c_out);
  failed += expect(interop == 0u, "C/ASM interop checksum equality");

  RmR_CASM_Report report;
  uint32_t final = RmR_CASM_XorFold32(sample, 257u, &report);

  failed += expect(final == c_out, "final equals C checksum");
  failed += expect(report.checksum == final, "report checksum matches");
  failed += expect(report.processed_bytes == 257u, "processed bytes tracked");

  if (failed != 0) {
    fprintf(stderr, "rmr_casm_bridge_selftest FAILED (%d)\n", failed);
    return 1;
  }

  printf("rmr_casm_bridge_selftest OK used_asm=%u checksum=0x%08x\n", report.used_asm, report.checksum);
  return 0;
}
