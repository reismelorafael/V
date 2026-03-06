#include "bitraf.h"
#include "bitraf_version.h"
#include "zero.h"

#define BITRAF_MAGIC_0 RMR_ZERO_BITRAF_MAGIC_0_U8
#define BITRAF_MAGIC_1 RMR_ZERO_BITRAF_MAGIC_1_U8
#define BITRAF_MAGIC_2 RMR_ZERO_BITRAF_MAGIC_2_U8
#define BITRAF_MAGIC_3 RMR_ZERO_BITRAF_MAGIC_3_U8
#define BITRAF_HEADER_SIZE 24u
#define BITRAF_CHUNK_BYTES 64u
#define BITRAF_FLAG_V2_CHUNK_TABLE 0x00000001u
#define BITRAF_IV64 0xCBF29CE484222325ULL
#define BITRAF_MIX_A 0xBF58476D1CE4E5B9ULL
#define BITRAF_MIX_B 0x94D049BB133111EBULL
#define BITRAF_MIX_C 0xA0761D6478BD642FULL

static const uint64_t g_bitraf_seed = RMR_ZERO_PHI64_U64;

static uint64_t bitraf_mix64(uint64_t x) {
  x ^= (x >> 30);
  x *= BITRAF_MIX_A;
  x ^= (x >> 27);
  x *= BITRAF_MIX_B;
  x ^= (x >> 31);
  return x;
}

static uint64_t bitraf_seed_effective(uint64_t seed) {
  uint64_t s = seed ^ g_bitraf_seed ^ (uint64_t)(BITRAF_VERSION_MAJOR << 16)
      ^ (uint64_t)(BITRAF_VERSION_MINOR << 8) ^ (uint64_t)BITRAF_VERSION_PATCH;
  return bitraf_mix64(s ^ BITRAF_MIX_C);
}

static uint64_t bitraf_stream_word(uint64_t base, uint64_t index) {
  return bitraf_mix64(base + RMR_ZERO_PHI64_U64 * (index + 1ULL));
}

static void bitraf_store_u32le(uint8_t *p, uint32_t v) {
  p[0] = (uint8_t)(v & RMR_ZERO_BITRAF_IO_MASK_U8);
  p[1] = (uint8_t)((v >> 8) & RMR_ZERO_BITRAF_IO_MASK_U8);
  p[2] = (uint8_t)((v >> 16) & RMR_ZERO_BITRAF_IO_MASK_U8);
  p[3] = (uint8_t)((v >> 24) & RMR_ZERO_BITRAF_IO_MASK_U8);
}

static uint32_t bitraf_load_u32le(const uint8_t *p) {
  return (uint32_t)p[0]
      | ((uint32_t)p[1] << 8)
      | ((uint32_t)p[2] << 16)
      | ((uint32_t)p[3] << 24);
}

static void bitraf_store_u64le(uint8_t *p, uint64_t v) {
  for (unsigned i = 0; i < 8u; ++i) {
    p[i] = (uint8_t)((v >> (8u * i)) & RMR_ZERO_BITRAF_IO_MASK_U8);
  }
}

static uint64_t bitraf_load_u64le(const uint8_t *p) {
  uint64_t v = 0ULL;
  for (unsigned i = 0; i < 8u; ++i) {
    v |= ((uint64_t)p[i] << (8u * i));
  }
  return v;
}



static uint32_t bitraf_crc32(const uint8_t *data, size_t len) {
  uint32_t crc = RMR_ZERO_CRC32_INIT_U32;
  for (size_t i = 0; i < len; ++i) {
    crc ^= (uint32_t)data[i];
    for (unsigned b = 0; b < 8u; ++b) {
      uint32_t m = (uint32_t)(-(int32_t)(crc & 1u));
      crc = (crc >> 1) ^ (RMR_ZERO_CRC32_IEEE_REV_U32 & m);
    }
  }
  return ~crc;
}

int bitraf_init(uint64_t seed) {
  (void)seed;
  return 0;
}

uint64_t bitraf_hash(const uint8_t *data, size_t len, uint64_t seed) {
  uint64_t h = BITRAF_IV64 ^ bitraf_seed_effective(seed);
  if (!data && len) {
    return 0ULL;
  }
  for (size_t i = 0; i < len; ++i) {
    h ^= (uint64_t)data[i];
    h *= RMR_ZERO_FNV1A64_PRIME_U64;
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
  if (in_len > (size_t)RMR_ZERO_U32_MAX_U32) {
    return 0u;
  }

  size_t chunk_count = (in_len + (size_t)BITRAF_CHUNK_BYTES - 1u) / (size_t)BITRAF_CHUNK_BYTES;
  if (chunk_count > (size_t)RMR_ZERO_U16_MAX_U16) {
    return 0u;
  }

  size_t ext_size = 8u + chunk_count * 4u;
  size_t frame_size = (size_t)BITRAF_HEADER_SIZE + ext_size + in_len;
  if (out_cap < frame_size) {
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

  uint8_t *ext = out + BITRAF_HEADER_SIZE;
  bitraf_store_u32le(ext + 0, BITRAF_FLAG_V2_CHUNK_TABLE);
  ext[4] = (uint8_t)BITRAF_CHUNK_BYTES;
  ext[5] = 4u;
  ext[6] = (uint8_t)(chunk_count & RMR_ZERO_BITRAF_IO_MASK_U8);
  ext[7] = (uint8_t)((chunk_count >> 8) & RMR_ZERO_BITRAF_IO_MASK_U8);

  for (size_t c = 0; c < chunk_count; ++c) {
    size_t off = c * (size_t)BITRAF_CHUNK_BYTES;
    size_t clen = in_len - off;
    if (clen > (size_t)BITRAF_CHUNK_BYTES) {
      clen = (size_t)BITRAF_CHUNK_BYTES;
    }
    uint32_t crc = bitraf_crc32(in + off, clen);
    bitraf_store_u32le(ext + 8u + c * 4u, crc);
  }

  size_t payload_off = (size_t)BITRAF_HEADER_SIZE + ext_size;
  for (size_t i = 0; i < in_len; ++i) {
    uint64_t w = bitraf_stream_word(eff_seed, (uint64_t)i >> 3);
    uint8_t k = (uint8_t)((w >> (8u * (unsigned)(i & 7u))) & RMR_ZERO_BITRAF_IO_MASK_U8);
    out[payload_off + i] = (uint8_t)(in[i] ^ k);
  }
  return frame_size;
}

size_t bitraf_reconstruct_ex(const uint8_t *in, size_t in_len,
                             uint8_t *out, size_t out_cap,
                             uint64_t seed, int mode,
                             bitraf_diag *diag) {
  if (diag) {
    diag->status = BITRAF_RECON_STATUS_OK;
    diag->error_offset = (size_t)-1;
    diag->chunk_index = (size_t)-1;
    diag->bad_chunk_count = 0u;
    diag->expected_checksum = 0u;
    diag->actual_checksum = 0u;
  }

  if (!in || !out || in_len < (size_t)BITRAF_HEADER_SIZE) {
    if (diag) { diag->status = BITRAF_RECON_STATUS_FRAME; }
    return 0u;
  }
  if (in[0] != BITRAF_MAGIC_0 || in[1] != BITRAF_MAGIC_1
      || in[2] != BITRAF_MAGIC_2 || in[3] != BITRAF_MAGIC_3) {
    if (diag) { diag->status = BITRAF_RECON_STATUS_FRAME; }
    return 0u;
  }

  uint32_t plain_len = bitraf_load_u32le(in + 4);
  uint64_t frame_hash = bitraf_load_u64le(in + 8);
  uint64_t frame_seed = bitraf_load_u64le(in + 16);
  uint64_t eff_seed = bitraf_seed_effective(seed);

  if (frame_seed != eff_seed) {
    if (diag) { diag->status = BITRAF_RECON_STATUS_FRAME; }
    return 0u;
  }
  if (out_cap < (size_t)plain_len) {
    if (diag) { diag->status = BITRAF_RECON_STATUS_FRAME; }
    return 0u;
  }

  size_t payload_off = (size_t)BITRAF_HEADER_SIZE;
  size_t chunk_count = 0u;
  size_t chunk_bytes = 0u;
  const uint8_t *chunk_table = 0;

  if (in_len >= (size_t)BITRAF_HEADER_SIZE + 8u) {
    const uint8_t *ext = in + BITRAF_HEADER_SIZE;
    uint32_t flags = bitraf_load_u32le(ext + 0);
    uint8_t cbytes = ext[4];
    uint8_t csum_size = ext[5];
    uint16_t ccount = (uint16_t)ext[6] | ((uint16_t)ext[7] << 8);
    if ((flags & BITRAF_FLAG_V2_CHUNK_TABLE) != 0u && csum_size == 4u && cbytes != 0u) {
      size_t ext_size = 8u + (size_t)ccount * 4u;
      if (in_len >= (size_t)BITRAF_HEADER_SIZE + ext_size + (size_t)plain_len) {
        chunk_bytes = (size_t)cbytes;
        chunk_count = (size_t)ccount;
        chunk_table = ext + 8u;
        payload_off = (size_t)BITRAF_HEADER_SIZE + ext_size;
      }
    }
  }

  if (in_len < payload_off + (size_t)plain_len) {
    if (diag) { diag->status = BITRAF_RECON_STATUS_FRAME; }
    return 0u;
  }

  for (size_t i = 0; i < (size_t)plain_len; ++i) {
    uint64_t w = bitraf_stream_word(eff_seed, (uint64_t)i >> 3);
    uint8_t k = (uint8_t)((w >> (8u * (unsigned)(i & 7u))) & RMR_ZERO_BITRAF_IO_MASK_U8);
    out[i] = (uint8_t)(in[payload_off + i] ^ k);
  }

  if (chunk_table && chunk_bytes) {
    size_t recomputed_chunks = ((size_t)plain_len + chunk_bytes - 1u) / chunk_bytes;
    size_t lim = chunk_count < recomputed_chunks ? chunk_count : recomputed_chunks;
    for (size_t c = 0; c < lim; ++c) {
      size_t off = c * chunk_bytes;
      size_t clen = (size_t)plain_len - off;
      if (clen > chunk_bytes) {
        clen = chunk_bytes;
      }
      uint32_t expect = bitraf_load_u32le(chunk_table + c * 4u);
      uint32_t got = bitraf_crc32(out + off, clen);
      if (expect != got) {
        if (diag) {
          if (diag->error_offset == (size_t)-1) {
            diag->error_offset = off;
            diag->chunk_index = c;
            diag->expected_checksum = expect;
            diag->actual_checksum = got;
            diag->status = BITRAF_RECON_STATUS_CHUNK;
          }
          diag->bad_chunk_count += 1u;
        }
        if (mode == BITRAF_RECON_MODE_STRICT) {
          return 0u;
        }
      }
    }
  }

  if (bitraf_hash(out, (size_t)plain_len, seed) != frame_hash) {
    if (diag && diag->status == BITRAF_RECON_STATUS_OK) {
      diag->status = BITRAF_RECON_STATUS_HASH;
    }
    if (mode == BITRAF_RECON_MODE_STRICT) {
      return 0u;
    }
  }
  return (size_t)plain_len;
}

size_t bitraf_reconstruct(const uint8_t *in, size_t in_len,
                          uint8_t *out, size_t out_cap,
                          uint64_t seed) {
  return bitraf_reconstruct_ex(in, in_len, out, out_cap, seed,
                               BITRAF_RECON_MODE_STRICT, 0);
}

int bitraf_verify(const uint8_t *data, size_t len,
                  uint64_t expected_hash, uint64_t seed) {
  uint64_t h = bitraf_hash(data, len, seed);
  uint64_t d = h ^ expected_hash;
  d |= d >> 32;
  d |= d >> 16;
  d |= d >> 8;
  d |= d >> 4;
  d |= d >> 2;
  d |= d >> 1;
  return (int)((d ^ 1u) & 1u);
}
