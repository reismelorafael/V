#include "rmr_tcg_cache.h"

#include "zero_compat.h"

static u8 rmr_isorf_set_byte(RmR_ISOraf_Store *st, u64 byte_offset, u8 value) {
  u64 bit = byte_offset << 3u;
  for (u32 i = 0u; i < 8u; ++i) {
    if (!RmR_ISOraf_SetBit(st, bit + i, (u8)((value >> i) & 1u))) return 0u;
  }
  return 1u;
}

static u8 rmr_isorf_get_byte(const RmR_ISOraf_Store *st, u64 byte_offset) {
  u8 out = 0u;
  u64 bit = byte_offset << 3u;
  for (u32 i = 0u; i < 8u; ++i) {
    out |= (u8)(RmR_ISOraf_GetBit(st, bit + i) << i);
  }
  return out;
}

void RmR_TCGCache_Init(RmR_TCGCache *cache) {
  if (!cache) return;
  rmr_mem_set(cache, 0u, sizeof(*cache));
  RmR_ISOraf_Init(&cache->store,
                  cache->pages,
                  RMR_TCG_CACHE_MAX_BLOCKS,
                  cache->data_words,
                  (u32)(sizeof(cache->data_words) / sizeof(cache->data_words[0])),
                  RMR_TCG_HOST_BLOCK_MAX * 8u);
}

const u8 *RmR_TCGCache_Lookup(RmR_TCGCache *cache, u32 guest_crc32c, u32 *host_size_out) {
  u32 i;
  u32 size;
  RmR_TCGBlock *block;
  static u8 host_block[RMR_TCG_HOST_BLOCK_MAX];
  if (!cache) return (const u8 *)0;

  for (i = 0u; i < cache->block_count; ++i) {
    if (cache->index[i].guest_crc32c != guest_crc32c) continue;
    block = &cache->index[i];
    if (!block->validated) return (const u8 *)0;

    size = block->host_size;
    if (size > RMR_TCG_HOST_BLOCK_MAX) return (const u8 *)0;
    for (u32 k = 0u; k < size; ++k) {
      host_block[k] = rmr_isorf_get_byte(&cache->store, block->toroidal_addr + k);
    }

    block->hit_count += 1u;
    cache->total_hits += 1u;
    if (host_size_out) *host_size_out = size;
    return host_block;
  }

  cache->total_misses += 1u;
  return (const u8 *)0;
}

u8 RmR_TCGCache_Insert(RmR_TCGCache *cache, u32 guest_crc32c, const u8 *host_block, u32 host_size, u8 arch_host) {
  RmR_TCGBlock *dst;
  u64 toroidal_addr;
  if (!cache || !host_block || host_size == 0u || host_size > RMR_TCG_HOST_BLOCK_MAX) return 0u;
  if (cache->block_count >= RMR_TCG_CACHE_MAX_BLOCKS) return 0u;

  toroidal_addr = (u64)cache->block_count * (u64)RMR_TCG_HOST_BLOCK_MAX;
  for (u32 i = 0u; i < host_size; ++i) {
    if (!rmr_isorf_set_byte(&cache->store, toroidal_addr + i, host_block[i])) return 0u;
  }

  dst = &cache->index[cache->block_count++];
  dst->guest_crc32c = guest_crc32c;
  dst->host_size = host_size;
  dst->toroidal_addr = toroidal_addr;
  dst->hit_count = 0u;
  dst->arch_host = arch_host;
  dst->validated = 1u;
  return 1u;
}

u32 RmR_TCGCache_HitRatio(const RmR_TCGCache *cache) {
  u64 total;
  if (!cache) return 0u;
  total = cache->total_hits + cache->total_misses;
  if (!total) return 0u;
  return (u32)((cache->total_hits * 100u) / total);
}
