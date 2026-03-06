#include "rmr_unified_kernel.h"
#include <stdint.h>
#include <stdio.h>
static uint32_t xor_bytes(const uint8_t *buf, uint32_t len) {
  uint32_t acc = 0u;
  uint32_t i;
  for (i = 0; i < len; ++i) acc ^= (uint32_t)buf[i];
  return acc;
}
static int expect_ok(int rc, const char *op) {
  if (rc != RMR_UK_OK) {
    printf("FAIL %s rc=%d\n", op, rc);
    return 0;
  }
  return 1;
}
static int expect_offset(const RmR_UnifiedKernel *kernel, uint32_t handle, uint32_t expected, const char *ctx) {
  uint32_t slot;
  if (handle == 0u) {
    printf("FAIL %s handle=0\n", ctx);
    return 0;
  }
  slot = handle - 1u;
  if (slot >= RMR_UK_MAX_SLOTS || !kernel->slots[slot].in_use) {
    printf("FAIL %s invalid handle=%u slot=%u\n", ctx, handle, slot);
    return 0;
  }
  if (handle != slot + 1u) {
    printf("FAIL %s handle-slot contract handle=%u slot=%u\n", ctx, handle, slot);
    return 0;
  }
  if (kernel->slots[slot].offset != expected) {
    printf("FAIL %s offset=%u expected=%u\n", ctx, kernel->slots[slot].offset, expected);
    return 0;
  }
  return 1;
}
int main(void) {
  RmR_UnifiedKernel kernel;
  RmR_UnifiedConfig cfg;
  uint32_t h0, h1, h2, h3, h4, h5, h6, h7, h8;
  uint8_t src_a[200];
  uint8_t src_b[128];
  uint32_t checksum = 0u;
  uint32_t i;
  cfg.seed = 0x1234ABCDu;
  cfg.arena_bytes = 640u;
  if (!expect_ok(RmR_UnifiedKernel_Init(&kernel, &cfg), "init")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 64u, &h0), "alloc h0")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 128u, &h1), "alloc h1")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 256u, &h2), "alloc h2")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 96u, &h3), "alloc h3")) return 1;
  if (!expect_offset(&kernel, h0, 0u, "layout h0") ||
      !expect_offset(&kernel, h1, 64u, "layout h1") ||
      !expect_offset(&kernel, h2, 192u, "layout h2") ||
      !expect_offset(&kernel, h3, 448u, "layout h3")) {
    return 1;
  }
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h1), "free h1") ||
      !expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h2), "free h2")) {
    return 1;
  }
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 128u, &h4), "alloc h4 exact gap") ||
      !expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 200u, &h5), "alloc h5 partial gap") ||
      !expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 56u, &h6), "alloc h6 tail gap")) {
    return 1;
  }
  if (!expect_offset(&kernel, h4, 64u, "reuse exact") ||
      !expect_offset(&kernel, h5, 192u, "reuse partial") ||
      !expect_offset(&kernel, h6, 392u, "reuse remainder")) {
    return 1;
  }
  for (i = 0; i < 200u; ++i) src_a[i] = (uint8_t)((i * 5u + 7u) & 0xFFu);
  for (i = 0; i < 128u; ++i) src_b[i] = (uint8_t)((i * 9u + 3u) & 0xFFu);
  if (!expect_ok(RmR_UnifiedKernel_ArenaWrite(&kernel, h5, 0u, src_a, 200u), "write h5") ||
      !expect_ok(RmR_UnifiedKernel_ArenaWrite(&kernel, h4, 0u, src_b, 128u), "write h4") ||
      !expect_ok(RmR_UnifiedKernel_ArenaCopy(&kernel, h5, 16u, h4, 32u, 64u), "copy h5->h4") ||
      !expect_ok(RmR_UnifiedKernel_ArenaXorChecksum(&kernel, h4, 32u, 64u, &checksum), "checksum h4")) {
    return 1;
  }
  if (checksum != xor_bytes(src_a + 16u, 64u)) {
    printf("FAIL checksum mismatch got=%u exp=%u\n", checksum, xor_bytes(src_a + 16u, 64u));
    return 1;
  }
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 80u, &h7), "alloc h7 near-full")) return 1;
  if (!expect_offset(&kernel, h7, 544u, "h7 append after packed reuse")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h5), "free h5 200-gap")) return 1;
  if (RmR_UnifiedKernel_ArenaAlloc(&kernel, 220u, &h8) == RMR_UK_OK) {
    printf("FAIL alloc 220 should fail (no sufficient contiguous gap)\n");
    return 1;
  }
  if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, 180u, &h8), "alloc h8 fits gap")) return 1;
  if (!expect_offset(&kernel, h8, 192u, "h8 gap-fit")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h0), "prep free h0")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h3), "prep free h3")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h4), "prep free h4")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h6), "prep free h6")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h7), "prep free h7")) return 1;
  if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, h8), "prep free h8")) return 1;
  for (i = 0; i < 1500u; ++i) {
    uint32_t a, b, c;
    uint32_t sz_a = 24u + ((i * 37u) % 73u);
    uint32_t sz_b = 40u + ((i * 11u) % 81u);
    uint32_t sz_c = sz_b / 2u;
    uint8_t tmp[128];
    uint32_t j;
    if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, sz_a, &a), "loop alloc a")) return 1;
    if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, sz_b, &b), "loop alloc b")) return 1;
    for (j = 0; j < sz_b; ++j) tmp[j] = (uint8_t)((i + j * 13u) & 0xFFu);
    if (!expect_ok(RmR_UnifiedKernel_ArenaWrite(&kernel, b, 0u, tmp, sz_b), "loop write b")) return 1;
    if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, b), "loop free b")) return 1;
    if (!expect_ok(RmR_UnifiedKernel_ArenaAlloc(&kernel, sz_c, &c), "loop realloc c")) return 1;
    if (kernel.slots[c - 1u].offset != kernel.slots[b - 1u].offset) {
      printf("FAIL loop reuse offset iter=%u old=%u new=%u\n", i,
             kernel.slots[b - 1u].offset, kernel.slots[c - 1u].offset);
      return 1;
    }
    if (!expect_ok(RmR_UnifiedKernel_ArenaWrite(&kernel, c, 0u, tmp, sz_c), "loop write c")) return 1;
    if (!expect_ok(RmR_UnifiedKernel_ArenaXorChecksum(&kernel, c, 0u, sz_c, &checksum), "loop checksum c")) return 1;
    if (checksum != xor_bytes(tmp, sz_c)) {
      printf("FAIL loop checksum iter=%u\n", i);
      return 1;
    }
    if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, a), "loop free a")) return 1;
    if (!expect_ok(RmR_UnifiedKernel_ArenaFree(&kernel, c), "loop free c")) return 1;
  }
  {
    RmR_UnifiedProcessState proc_a;
    RmR_UnifiedProcessState proc_b;
    RmR_UnifiedRouteState route_a;
    RmR_UnifiedRouteState route_b;
    uint32_t strat;
    for (strat = 0u; strat < 64u; ++strat) {
      uint64_t cpu_cycles = 180000u + ((uint64_t)strat * 917u);
      uint64_t storage_r = 64000u + ((uint64_t)strat * 331u);
      uint64_t storage_w = 12000u + ((uint64_t)strat * 173u);
      uint64_t io_in = 9000u + ((uint64_t)strat * 53u);
      uint64_t io_out = 12000u + ((uint64_t)strat * 29u);
      if (!expect_ok(RmR_UnifiedKernel_Process(&kernel,
                                               cpu_cycles,
                                               storage_r,
                                               storage_w,
                                               io_in,
                                               io_out,
                                               (int64_t)(11 + strat),
                                               (int64_t)(3 + (strat & 7u)),
                                               (int64_t)(5 + (strat & 3u)),
                                               (int64_t)(13 + (strat & 15u)),
                                               &proc_a), "layered process A")) {
        return 1;
      }
      if (!expect_ok(RmR_UnifiedKernel_Route(&kernel, &proc_a, &route_a), "layered route A")) return 1;
      if (!expect_ok(RmR_UnifiedKernel_Process(&kernel,
                                               cpu_cycles,
                                               storage_r,
                                               storage_w,
                                               io_in,
                                               io_out,
                                               (int64_t)(11 + strat),
                                               (int64_t)(3 + (strat & 7u)),
                                               (int64_t)(5 + (strat & 3u)),
                                               (int64_t)(13 + (strat & 15u)),
                                               &proc_b), "layered process B")) {
        return 1;
      }
      if (!expect_ok(RmR_UnifiedKernel_Route(&kernel, &proc_b, &route_b), "layered route B")) return 1;
      if (proc_a.cpu_pressure != proc_b.cpu_pressure ||
          proc_a.storage_pressure != proc_b.storage_pressure ||
          proc_a.io_pressure != proc_b.io_pressure ||
          proc_a.matrix_determinant != proc_b.matrix_determinant ||
          route_a.route_id != route_b.route_id ||
          route_a.route_tag != route_b.route_tag) {
        printf("FAIL layered stability strat=%u\n", strat);
        return 1;
      }
    }
  }
  if (!expect_ok(RmR_UnifiedKernel_Shutdown(&kernel), "shutdown")) return 1;
  printf("OK unified arena selftest\n");
  return 0;
}
