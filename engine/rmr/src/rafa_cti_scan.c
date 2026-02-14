#include <errno.h>
#include <inttypes.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "rmr_hw_detect.h"
#include "rmr_ll_tuning.h"

#define ZERO_BLOCK_BYTES 4096u
#define HIST_SIZE 256u

typedef enum { ROUTE_SEQ, ROUTE_SPIRAL, ROUTE_TOROID, ROUTE_RANDOM_PERM, ROUTE_DELTA_MISS } RouteType;

typedef struct {
  uint64_t w, h, z, seed;
  RouteType route;
} ScanConfig;

typedef struct {
  uint64_t idx, off, ts_ns;
  uint32_t size, crc, ones, miss_score, bad_event;
  double entropy;
} TileRecord;

static uint32_t g_crc32c_tbl[256];
static int g_crc32c_ready = 0;

static uint64_t gcd_u64(uint64_t a, uint64_t b) { while (b) { uint64_t t = a % b; a = b; b = t; } return a; }

static uint64_t mix64(uint64_t x) {
  x ^= x >> 33; x *= 0xff51afd7ed558ccdULL;
  x ^= x >> 33; x *= 0xc4ceb9fe1a85ec53ULL;
  x ^= x >> 33; return x;
}

static void crc32c_init(void) {
  if (g_crc32c_ready) return;
  for (uint32_t i = 0; i < 256u; ++i) {
    uint32_t c = i;
    for (uint32_t j = 0; j < 8u; ++j) c = (c >> 1) ^ (0x82f63b78u & (uint32_t)-(int32_t)(c & 1u));
    g_crc32c_tbl[i] = c;
  }
  g_crc32c_ready = 1;
}

static uint32_t crc32c_sw(const uint8_t *buf, size_t len) {
  uint32_t crc = 0xffffffffu;
  for (size_t i = 0; i < len; ++i) crc = g_crc32c_tbl[(crc ^ buf[i]) & 0xffu] ^ (crc >> 8);
  return ~crc;
}

static uint32_t crc32c_accel(const uint8_t *buf, size_t len) {
#if defined(__aarch64__) && defined(__ARM_FEATURE_CRC32)
  uint32_t crc = 0xffffffffu;
  size_t i = 0;
  for (; i + 8 <= len; i += 8) { uint64_t v; memcpy(&v, buf + i, sizeof(v)); crc = __builtin_aarch64_crc32cx(crc, v); }
  for (; i < len; ++i) crc = __builtin_aarch64_crc32cb(crc, buf[i]);
  return ~crc;
#else
  return crc32c_sw(buf, len);
#endif
}

static uint32_t popcount_ones(const uint8_t *buf, size_t len) {
  uint32_t ones = 0;
  size_t i = 0;
  for (; i + 8 <= len; i += 8) { uint64_t v; memcpy(&v, buf + i, sizeof(v)); ones += RmR_LL_PopCount64(v); }
  for (; i < len; ++i) ones += RmR_LL_PopCount32((uint32_t)buf[i]);
  return ones;
}

static double entropy_estimate(const uint8_t *buf, size_t len) {
  uint32_t hist[HIST_SIZE] = {0};
  if (len == 0) return 0.0;
  for (size_t i = 0; i < len; ++i) hist[buf[i]]++;
  const double inv_len = 1.0 / (double)len;
  const double inv_log2 = 1.4426950408889634;
  double h = 0.0;
  for (size_t i = 0; i < HIST_SIZE; ++i) if (hist[i]) { double p = (double)hist[i] * inv_len; h -= p * log(p) * inv_log2; }
  return h;
}

static int write_u32_at(FILE *f, uint64_t idx, uint32_t value) {
  if (fseek(f, (long)(idx * sizeof(uint32_t)), SEEK_SET) != 0) return -1;
  return fwrite(&value, sizeof(value), 1, f) == 1 ? 0 : -1;
}

static int init_zero_file(FILE *f, uint64_t bytes) {
  uint8_t zero[ZERO_BLOCK_BYTES] = {0};
  while (bytes) {
    size_t n = (bytes > ZERO_BLOCK_BYTES) ? ZERO_BLOCK_BYTES : (size_t)bytes;
    if (fwrite(zero, 1, n, f) != n) return -1;
    bytes -= n;
  }
  return fflush(f);
}

static int visited_test_set(FILE *f, uint64_t idx) {
  const uint64_t byte_idx = idx >> 3;
  const uint8_t mask = (uint8_t)(1u << (idx & 7u));
  uint8_t b;
  if (fseek(f, (long)byte_idx, SEEK_SET) != 0) return -1;
  if (fread(&b, 1, 1, f) != 1) return -1;
  if (b & mask) return 1;
  b = (uint8_t)(b | mask);
  if (fseek(f, (long)byte_idx, SEEK_SET) != 0) return -1;
  if (fwrite(&b, 1, 1, f) != 1) return -1;
  return 0;
}

static uint64_t spiral_xy_to_index(uint64_t step, uint64_t w, uint64_t h) {
  if (w == 0 || h == 0) return 0;
  int64_t left = 0, top = 0, right = (int64_t)w - 1, bottom = (int64_t)h - 1;
  uint64_t k = step % (w * h);
  while (left <= right && top <= bottom) {
    uint64_t seg = (uint64_t)(right - left + 1);
    if (k < seg) return (uint64_t)top * w + (uint64_t)(left + (int64_t)k);
    k -= seg;
    seg = (uint64_t)(bottom - top);
    if (k < seg) return (uint64_t)(top + 1 + (int64_t)k) * w + (uint64_t)right;
    k -= seg;
    if (top != bottom) { seg = (uint64_t)(right - left); if (k < seg) return (uint64_t)bottom * w + (uint64_t)(right - 1 - (int64_t)k); k -= seg; }
    if (left != right) { seg = (uint64_t)(bottom - top - 1); if (k < seg) return (uint64_t)(bottom - 1 - (int64_t)k) * w + (uint64_t)left; k -= seg; }
    ++left; --right; ++top; --bottom;
  }
  return step % (w * h);
}

static uint64_t route_candidate(uint64_t step, uint64_t ntiles, const ScanConfig *cfg,
                                uint64_t prev_idx, uint32_t prev_crc, uint32_t prev_miss,
                                uint64_t a_perm, uint64_t b_perm) {
  if (ntiles == 0) return 0;
  switch (cfg->route) {
    case ROUTE_SEQ: return step;
    case ROUTE_TOROID: {
      uint64_t stride = ((cfg->seed << 1) | 1u) % ntiles;
      if (!stride) stride = 1;
      while (gcd_u64(stride, ntiles) != 1) stride += 2;
      return (step * stride) % ntiles;
    }
    case ROUTE_RANDOM_PERM: return (a_perm * step + b_perm) % ntiles;
    case ROUTE_SPIRAL: {
      const uint64_t layer_size = cfg->w * cfg->h;
      if (!layer_size) return step % ntiles;
      const uint64_t z = (step / layer_size) % (cfg->z ? cfg->z : 1u);
      const uint64_t in = spiral_xy_to_index(step % layer_size, cfg->w, cfg->h);
      return (z * layer_size + in) % ntiles;
    }
    case ROUTE_DELTA_MISS: {
      uint64_t m = mix64(((uint64_t)prev_crc << 32) ^ prev_miss ^ cfg->seed ^ (step * 0x9e3779b97f4a7c15ULL));
      return (prev_idx + 1 + (m % ntiles)) % ntiles;
    }
  }
  return step;
}

static uint64_t now_ns(void) {
  struct timespec ts;
  timespec_get(&ts, TIME_UTC);
  return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

static int parse_u64(const char *s, uint64_t *out) {
  char *end = NULL;
  errno = 0;
  unsigned long long v = strtoull(s, &end, 10);
  if (errno || !end || *end != '\0') return -1;
  *out = (uint64_t)v;
  return 0;
}

static RouteType parse_route(const char *s) {
  if (!strcmp(s, "SPIRAL")) return ROUTE_SPIRAL;
  if (!strcmp(s, "TOROID")) return ROUTE_TOROID;
  if (!strcmp(s, "RANDOM_PERM")) return ROUTE_RANDOM_PERM;
  if (!strcmp(s, "DELTA_MISS")) return ROUTE_DELTA_MISS;
  return ROUTE_SEQ;
}

int main(int argc, char **argv) {
  if (argc < 4) {
    fprintf(stderr, "usage: %s <input.zip> <out.bitstack.jsonl> <crc_matrix.bin> [--w N --h N --z N --seed N --route SEQ|SPIRAL|TOROID|RANDOM_PERM|DELTA_MISS]\n", argv[0]);
    return 2;
  }

  RmR_HW_Info hw;
  RmR_LL_TunePlan tune;
  memset(&hw, 0, sizeof(hw));
  memset(&tune, 0, sizeof(tune));
  RmR_HW_Detect(&hw);
  RmR_LL_ApplyTuneDefaults(&hw, &tune);

  ScanConfig cfg = {.w = 50, .h = 50, .z = 10, .seed = 1, .route = ROUTE_SEQ};
  for (int i = 4; i + 1 < argc; i += 2) {
    if (!strcmp(argv[i], "--w")) parse_u64(argv[i + 1], &cfg.w);
    else if (!strcmp(argv[i], "--h")) parse_u64(argv[i + 1], &cfg.h);
    else if (!strcmp(argv[i], "--z")) parse_u64(argv[i + 1], &cfg.z);
    else if (!strcmp(argv[i], "--seed")) parse_u64(argv[i + 1], &cfg.seed);
    else if (!strcmp(argv[i], "--route")) cfg.route = parse_route(argv[i + 1]);
  }

  FILE *fin = fopen(argv[1], "rb");
  FILE *fjson = fopen(argv[2], "wb");
  FILE *fcrc = fopen(argv[3], "w+b");
  if (!fin || !fjson || !fcrc) return 1;

  if (fseek(fin, 0, SEEK_END) != 0) return 1;
  long fsz = ftell(fin);
  if (fsz < 0 || fseek(fin, 0, SEEK_SET) != 0) return 1;

  uint32_t chunk_bytes = tune.cti_chunk_size ? tune.cti_chunk_size : 4096u;
  if (chunk_bytes < 512u) chunk_bytes = 512u;
  uint32_t scan_stride = tune.cti_stride ? tune.cti_stride : 1u;
  uint32_t prefetch_bytes = tune.cti_prefetch;

  uint64_t ntiles = ((uint64_t)fsz + (uint64_t)chunk_bytes - 1u) / (uint64_t)chunk_bytes;
  if (init_zero_file(fcrc, ntiles * sizeof(uint32_t)) != 0) return 1;

  FILE *fvis = tmpfile();
  if (!fvis) return 1;
  if (init_zero_file(fvis, (ntiles + 7u) / 8u) != 0) return 1;

  crc32c_init();
  uint8_t *tile = (uint8_t *)malloc(chunk_bytes);
  if (!tile) return 1;

  uint64_t produced = 0, step = 0, prev_idx = 0;
  uint32_t prev_crc = 0, prev_miss = 0;

  uint64_t a_perm = (mix64(cfg.seed) | 1u) % (ntiles ? ntiles : 1u);
  if (!a_perm) a_perm = 1;
  while (ntiles && gcd_u64(a_perm, ntiles) != 1) a_perm += 2;
  uint64_t b_perm = mix64(cfg.seed ^ 0x9e3779b97f4a7c15ULL) % (ntiles ? ntiles : 1u);

  while (produced < ntiles) {
    uint64_t idx = route_candidate(step, ntiles, &cfg, prev_idx, prev_crc, prev_miss, a_perm, b_perm);
    for (uint64_t probe = 0; probe < ntiles; ++probe) {
      int seen = visited_test_set(fvis, idx);
      if (seen == 0) break;
      if (seen < 0) return 1;
      idx = (idx + (uint64_t)scan_stride) % ntiles;
    }

    uint64_t off = idx * (uint64_t)chunk_bytes;
    size_t size = (off + (uint64_t)chunk_bytes <= (uint64_t)fsz) ? (size_t)chunk_bytes : (size_t)((uint64_t)fsz - off);
    if (fseek(fin, (long)off, SEEK_SET) != 0 || fread(tile, 1, size, fin) != size) return 1;

    TileRecord rec;
    rec.idx = idx;
    rec.off = off;
    rec.size = (uint32_t)size;
    rec.ts_ns = now_ns();
    if (prefetch_bytes > 0u && size > (size_t)prefetch_bytes) {
      for (size_t pfx = 0; pfx + (size_t)prefetch_bytes < size; pfx += (size_t)prefetch_bytes) {
        __builtin_prefetch(tile + pfx + (size_t)prefetch_bytes, 0, 1);
      }
    }
    rec.crc = crc32c_accel(tile, size);
    rec.ones = popcount_ones(tile, size);
    rec.entropy = entropy_estimate(tile, size);
    uint32_t delta = prev_crc ^ rec.crc;
    uint32_t entropy_term = (uint32_t)(rec.entropy * 256.0);
    rec.miss_score = (uint32_t)(((uint64_t)__builtin_popcount(delta) * 17u + entropy_term + prev_miss) & 1023u);
    rec.bad_event = (rec.size != chunk_bytes) | (rec.entropy < 1.0) | (rec.miss_score > 950u);

    uint64_t x = cfg.w ? (idx % cfg.w) : 0;
    uint64_t y = cfg.h ? ((idx / (cfg.w ? cfg.w : 1u)) % cfg.h) : 0;
    uint64_t z = cfg.z ? ((idx / ((cfg.w ? cfg.w : 1u) * (cfg.h ? cfg.h : 1u))) % cfg.z) : 0;

    if (write_u32_at(fcrc, idx, rec.crc) != 0) return 1;
    if (fprintf(fjson,
                "{\"idx\":%" PRIu64 ",\"off\":%" PRIu64 ",\"size\":%" PRIu32
                ",\"ts\":%" PRIu64 ",\"crc\":%" PRIu32 ",\"ones\":%" PRIu32
                ",\"H\":%.6f,\"miss_score\":%" PRIu32 ",\"bad_event\":%" PRIu32
                ",\"x\":%" PRIu64 ",\"y\":%" PRIu64 ",\"z\":%" PRIu64 "}\n",
                rec.idx, rec.off, rec.size, rec.ts_ns, rec.crc, rec.ones, rec.entropy,
                rec.miss_score, rec.bad_event, x, y, z) < 0) return 1;

    prev_idx = idx;
    prev_crc = rec.crc;
    prev_miss = rec.miss_score;
    ++produced;
    step += (uint64_t)scan_stride;
  }

  free(tile);
  fclose(fvis);
  fclose(fin);
  fflush(fjson);
  fflush(fcrc);
  fclose(fjson);
  fclose(fcrc);
  return 0;
}
