#include "rmr_casm_bridge.h"
#include "rmr_lowlevel.h"
#include "rmr_unified_kernel.h"

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

static int expect_i32(const char *name, int lhs, int rhs) {
  if (lhs != rhs) {
    fprintf(stderr, "FAIL: %s lhs=%d rhs=%d\n", name, lhs, rhs);
    return 1;
  }
  return 0;
}

static int unified_arena_overflow_boundaries(void) {
  RmR_UnifiedKernel kernel = {0};
  uint8_t arena[64] = {0};
  uint8_t src[8] = {1u, 2u, 3u, 4u, 5u, 6u, 7u, 8u};
  uint32_t handle = 0u;
  uint32_t checksum = 0u;
  int failed = 0;

  kernel.initialized = 1u;
  kernel.arena_base = arena;
  kernel.arena_capacity = 64u;

  kernel.slots[0].in_use = 1u;
  kernel.slots[0].offset = UINT32_MAX - 7u;
  kernel.slots[0].size = 16u;
  failed += expect_i32("arena alloc detects slot end overflow",
                       RmR_UnifiedKernel_ArenaAlloc(&kernel, 1u, &handle),
                       RMR_KERNEL_ERR_STATE);

  kernel.slots[0].offset = 60u;
  kernel.slots[0].size = 4u;
  failed += expect_i32("arena alloc uses capacity-end guard",
                       RmR_UnifiedKernel_ArenaAlloc(&kernel, 1u, &handle),
                       RMR_UK_OK);

  kernel.slots[0].in_use = 1u;
  kernel.slots[0].offset = 0u;
  kernel.slots[0].size = 8u;
  kernel.slots[1].in_use = 1u;
  kernel.slots[1].offset = 8u;
  kernel.slots[1].size = 8u;
  failed += expect_i32("arena copy source offset overflow-safe arg check",
                       RmR_UnifiedKernel_ArenaCopy(&kernel, 1u, UINT32_MAX, 2u, 0u, 2u),
                       RMR_KERNEL_ERR_ARG);

  kernel.slots[0].offset = UINT32_MAX;
  failed += expect_i32("arena copy source pointer composition overflow",
                       RmR_UnifiedKernel_ArenaCopy(&kernel, 1u, 1u, 2u, 0u, 1u),
                       RMR_KERNEL_ERR_STATE);

  kernel.slots[0].offset = UINT32_MAX;
  kernel.slots[0].size = 8u;
  failed += expect_i32("arena xor checksum pointer composition overflow",
                       RmR_UnifiedKernel_ArenaXorChecksum(&kernel, 1u, 1u, 1u, &checksum),
                       RMR_KERNEL_ERR_STATE);

  failed += expect_i32("arena fill pointer composition overflow",
                       RmR_UnifiedKernel_ArenaFill(&kernel, 1u, 1u, 1u, 0x5Au),
                       RMR_KERNEL_ERR_STATE);

  failed += expect_i32("arena write pointer composition overflow",
                       RmR_UnifiedKernel_ArenaWrite(&kernel, 1u, 1u, src, 1u),
                       RMR_KERNEL_ERR_STATE);

  kernel.slots[0].offset = 0u;
  kernel.slots[0].size = 8u;
  failed += expect_i32("arena write exact end boundary",
                       RmR_UnifiedKernel_ArenaWrite(&kernel, 1u, 7u, src, 1u),
                       RMR_UK_OK);
  failed += expect_u32("arena write exact end value", arena[7], src[0]);

  failed += expect_i32("arena xor checksum exact end boundary",
                       RmR_UnifiedKernel_ArenaXorChecksum(&kernel, 1u, 7u, 1u, &checksum),
                       RMR_UK_OK);
  failed += expect_u32("arena xor checksum exact end value", checksum, src[0]);

  return failed;
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

  failed += unified_arena_overflow_boundaries();

  if (failed != 0) {
    fprintf(stderr, "rmr_asm_equivalence_selftest FAILED (%d)\n", failed);
    return 1;
  }

  printf("rmr_asm_equivalence_selftest OK asm=0x%08x c=0x%08x reduce=0x%08x checksum=0x%08x fold=0x%08x\n",
         asm_out, c_out, reduce_impl, checksum_impl, fold_impl);
  return 0;
}
