#include "rmr_casm_bridge.h"

#if defined(__x86_64__)
uint32_t rmr_casm_xor_fold32_x86_64(const uint8_t *data, size_t size);
#define RMR_CASM_HAS_ASM 1
#else
#define RMR_CASM_HAS_ASM 0
#endif

static uint32_t rmr_casm_mix_tail(uint32_t acc, const uint8_t *data, size_t start, size_t size) {
  size_t i = start;
  while (i < size) {
    uint32_t v = (uint32_t)data[i];
    uint32_t shift = (uint32_t)((i & 3u) * 8u);
    acc ^= (v << shift);
    ++i;
  }
  return acc;
}

uint32_t RmR_CASM_XorFold32_C(const uint8_t *data, size_t size) {
  if (!data || size == 0u) return 0u;

  uint32_t acc = 0u;
  size_t i = 0u;
  size_t limit = size & ~(size_t)3u;

  while (i < limit) {
    uint32_t lane = 0u;
    lane |= (uint32_t)data[i + 0u];
    lane |= (uint32_t)data[i + 1u] << 8u;
    lane |= (uint32_t)data[i + 2u] << 16u;
    lane |= (uint32_t)data[i + 3u] << 24u;
    acc ^= lane;
    i += 4u;
  }

  return rmr_casm_mix_tail(acc, data, limit, size);
}

uint32_t RmR_CASM_XorFold32(const uint8_t *data, size_t size, RmR_CASM_Report *report) {
  uint32_t checksum = 0u;
  uint32_t used_asm = 0u;

#if RMR_CASM_HAS_ASM
  if (data && size != 0u) {
    checksum = rmr_casm_xor_fold32_x86_64(data, size);
    used_asm = 1u;
  }
#endif

  if (used_asm == 0u) {
    checksum = RmR_CASM_XorFold32_C(data, size);
  }

  if (report) {
    report->used_asm = used_asm;
    report->processed_bytes = (uint32_t)(size & 0xFFFFFFFFu);
    report->checksum = checksum;
  }
  return checksum;
}

uint32_t RmR_CASM_XorFold32_Interop(const uint8_t *data, size_t size, uint32_t *asm_out, uint32_t *c_out) {
  uint32_t csum_c = RmR_CASM_XorFold32_C(data, size);
  uint32_t csum_asm = csum_c;

#if RMR_CASM_HAS_ASM
  if (data && size != 0u) {
    csum_asm = rmr_casm_xor_fold32_x86_64(data, size);
  }
#endif

  if (asm_out) *asm_out = csum_asm;
  if (c_out) *c_out = csum_c;

  return (csum_asm == csum_c) ? 0u : 1u;
}
