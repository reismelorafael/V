#include "rmr_casm_bridge.h"
#include "rmr_lowlevel.h"

#include <stdint.h>
#include <stdio.h>

static uint32_t ref_reduce_xor(const uint8_t *data, size_t len) {
  if (!data || len == 0u) return 0u;
  uint32_t acc = 0u;
  for (size_t i = 0u; i < len; ++i) {
    const uint32_t lane = (uint32_t)data[i] << ((uint32_t)(i & 3u) * 8u);
    acc ^= lane;
    acc = rmr_lowlevel_rotl32(acc, 3u);
  }
  return acc;
}

static uint32_t ref_checksum32(const uint8_t *data, size_t len, uint32_t seed) {
  if (!data || len == 0u) return seed;
  uint32_t state = seed ^ 0xA5A5A5A5u;
  for (size_t i = 0u; i < len; ++i) {
    state ^= (uint32_t)data[i] + 0x9Eu + (uint32_t)i;
    state = rmr_lowlevel_rotl32(state, 7u);
    state *= 0x45D9F3Bu;
  }
  return state ^ (uint32_t)len;
}

static uint32_t ref_fold32(uint32_t a, uint32_t b, uint32_t c, uint32_t d) {
  uint32_t x = a ^ rmr_lowlevel_rotl32(b, 5u);
  x += 0x9E3779B9u;
  x ^= rmr_lowlevel_rotl32(c, 13u);
  x += (d ^ 0x85EBCA6Bu);
  x ^= (x >> 16u);
  x *= 0x7FEB352Du;
  x ^= (x >> 15u);
  x *= 0x846CA68Bu;
  x ^= (x >> 16u);
  return x;
}

static int expect_u32(const char *name, uint32_t lhs, uint32_t rhs) {
  if (lhs != rhs) {
    fprintf(stderr, "FAIL: %s lhs=0x%08x rhs=0x%08x\n", name, lhs, rhs);
    return 1;
  }
  return 0;
}

int main(void) {
  uint8_t sample[1024];
  for (uint32_t i = 0u; i < 1024u; ++i) {
    sample[i] = (uint8_t)((i * 29u) ^ (i >> 3u) ^ 0xA5u);
  }

  int failed = 0;
  uint32_t asm_out = 0u;
  uint32_t c_out = 0u;

  failed += expect_u32("bridge interop", RmR_CASM_XorFold32_Interop(sample, sizeof(sample), &asm_out, &c_out), 0u);
  failed += expect_u32("bridge c/asm checksum", asm_out, c_out);

  const uint32_t reduce_ref = ref_reduce_xor(sample, sizeof(sample));
  const uint32_t reduce_impl = rmr_lowlevel_reduce_xor(sample, sizeof(sample));
  failed += expect_u32("reduce equivalence", reduce_impl, reduce_ref);

  const uint32_t checksum_ref = ref_checksum32(sample, sizeof(sample), 0xD00DFEEDu);
  const uint32_t checksum_impl = rmr_lowlevel_checksum32(sample, sizeof(sample), 0xD00DFEEDu);
  failed += expect_u32("checksum equivalence", checksum_impl, checksum_ref);

  const uint32_t fold_ref = ref_fold32(0x10203040u, 0x88776655u, 0xAABBCCDDu, 0x0F1E2D3Cu);
  const uint32_t fold_impl = rmr_lowlevel_fold32(0x10203040u, 0x88776655u, 0xAABBCCDDu, 0x0F1E2D3Cu);
  failed += expect_u32("fold bridge equivalence", fold_impl, fold_ref);

  if (failed != 0) {
    fprintf(stderr, "rmr_asm_equivalence_selftest FAILED (%d)\n", failed);
    return 1;
  }

  printf("rmr_asm_equivalence_selftest OK asm=0x%08x c=0x%08x reduce=0x%08x checksum=0x%08x fold=0x%08x\n",
         asm_out, c_out, reduce_impl, checksum_impl, fold_impl);
  return 0;
}
