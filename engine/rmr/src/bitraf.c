#include "bitraf.h"
#include "bitraf_version.h"

#define BITRAF_MAGIC_0 ((uint8_t)'B')
#define BITRAF_MAGIC_1 ((uint8_t)'T')
#define BITRAF_MAGIC_2 ((uint8_t)'R')
#define BITRAF_MAGIC_3 ((uint8_t)'F')
#define BITRAF_HEADER_SIZE 24u

static uint64_t g_bitraf_seed = 0x9E3779B97F4A7C15ULL;

static uint64_t bitraf_mix64(uint64_t x) {
  x ^= (x >> 30);
  x *= 0xBF58476D1CE4E5B9ULL;
  x ^= (x >> 27);
  x *= 0x94D049BB133111EBULL;
  x ^= (x >> 31);
  return x;
}

static uint64_t bitraf_seed_effective(uint64_t seed) {
  uint64_t s = seed ^ g_bitraf_seed ^ (uint64_t)(BITRAF_VERSION_MAJOR << 16)
      ^ (uint64_t)(BITRAF_VERSION_MINOR << 8) ^ (uint64_t)BITRAF_VERSION_PATCH;
  return bitraf_mix64(s ^ 0xA0761D6478BD642FULL);
}

static uint64_t bitraf_stream_word(uint64_t base, uint64_t index) {
  return bitraf_mix64(base + 0x9E3779B97F4A7C15ULL * (index + 1ULL));
}

static void bitraf_store_u32le(uint8_t *p, uint32_t v) {
  p[0] = (uint8_t)(v & 0xFFu);
  p[1] = (uint8_t)((v >> 8) & 0xFFu);
  p[2] = (uint8_t)((v >> 16) & 0xFFu);
  p[3] = (uint8_t)((v >> 24) & 0xFFu);
}

static uint32_t bitraf_load_u32le(const uint8_t *p) {
  return (uint32_t)p[0]
      | ((uint32_t)p[1] << 8)
      | ((uint32_t)p[2] << 16)
      | ((uint32_t)p[3] << 24);
}

static void bitraf_store_u64le(uint8_t *p, uint64_t v) {
  for (unsigned i = 0; i < 8u; ++i) {
    p[i] = (uint8_t)((v >> (8u * i)) & 0xFFu);
  }
}

static uint64_t bitraf_load_u64le(const uint8_t *p) {
  uint64_t v = 0ULL;
  for (unsigned i = 0; i < 8u; ++i) {
    v |= ((uint64_t)p[i] << (8u * i));
  }
  return v;
}

int bitraf_init(uint64_t seed) {
  g_bitraf_seed = seed ? bitraf_mix64(seed) : 0x9E3779B97F4A7C15ULL;
  return 0;
}

uint64_t bitraf_hash(const uint8_t *data, size_t len, uint64_t seed) {
  uint64_t h = 0xCBF29CE484222325ULL ^ bitraf_seed_effective(seed);
  if (!data && len) {
    return 0ULL;
  }
  for (size_t i = 0; i < len; ++i) {
    h ^= (uint64_t)data[i];
    h *= 0x100000001B3ULL;
    h ^= (h >> 33);
  }
  return bitraf_mix64(h ^ (uint64_t)len);
}

size_t bitraf_compress(const uint8_t *in, size_t in_len,
                       uint8_t *out, size_t out_cap,
                       uint64_t seed) {
  if ((!in && in_len) || !out || out_cap < (size_t)BITRAF_HEADER_SIZE) {
    return 0u;
  }
  if (in_len > 0xFFFFFFFFu) {
    return 0u;
  }
  if (out_cap < (size_t)BITRAF_HEADER_SIZE + in_len) {
    return 0u;
  }

  uint64_t eff_seed = bitraf_seed_effective(seed);
  uint64_t content_hash = bitraf_hash(in, in_len, seed);

  out[0] = BITRAF_MAGIC_0;
  out[1] = BITRAF_MAGIC_1;
  out[2] = BITRAF_MAGIC_2;
  out[3] = BITRAF_MAGIC_3;
  bitraf_store_u32le(out + 4, (uint32_t)in_len);
  bitraf_store_u64le(out + 8, content_hash);
  bitraf_store_u64le(out + 16, eff_seed);

  for (size_t i = 0; i < in_len; ++i) {
    uint64_t w = bitraf_stream_word(eff_seed, (uint64_t)i >> 3);
    uint8_t k = (uint8_t)((w >> (8u * (unsigned)(i & 7u))) & 0xFFu);
    out[BITRAF_HEADER_SIZE + i] = (uint8_t)(in[i] ^ k);
  }
  return (size_t)BITRAF_HEADER_SIZE + in_len;
}

size_t bitraf_reconstruct(const uint8_t *in, size_t in_len,
                          uint8_t *out, size_t out_cap,
                          uint64_t seed) {
  if (!in || !out || in_len < (size_t)BITRAF_HEADER_SIZE) {
    return 0u;
  }
  if (in[0] != BITRAF_MAGIC_0 || in[1] != BITRAF_MAGIC_1
      || in[2] != BITRAF_MAGIC_2 || in[3] != BITRAF_MAGIC_3) {
    return 0u;
  }

  uint32_t plain_len = bitraf_load_u32le(in + 4);
  uint64_t frame_hash = bitraf_load_u64le(in + 8);
  uint64_t frame_seed = bitraf_load_u64le(in + 16);
  uint64_t eff_seed = bitraf_seed_effective(seed);

  if (frame_seed != eff_seed) {
    return 0u;
  }
  if (in_len < (size_t)BITRAF_HEADER_SIZE + (size_t)plain_len) {
    return 0u;
  }
  if (out_cap < (size_t)plain_len) {
    return 0u;
  }

  for (size_t i = 0; i < (size_t)plain_len; ++i) {
    uint64_t w = bitraf_stream_word(eff_seed, (uint64_t)i >> 3);
    uint8_t k = (uint8_t)((w >> (8u * (unsigned)(i & 7u))) & 0xFFu);
    out[i] = (uint8_t)(in[BITRAF_HEADER_SIZE + i] ^ k);
  }

  if (bitraf_hash(out, (size_t)plain_len, seed) != frame_hash) {
    return 0u;
  }
  return (size_t)plain_len;
}

int bitraf_verify(const uint8_t *data, size_t len,
                  uint64_t expected_hash, uint64_t seed) {
  return bitraf_hash(data, len, seed) == expected_hash ? 1 : 0;
}
