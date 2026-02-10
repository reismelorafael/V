#include "rmr_policy_kernel.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(__aarch64__) && defined(__ARM_FEATURE_CRC32)
#include <arm_acle.h>
#endif
#if defined(__x86_64__) || defined(__i386__)
#include <nmmintrin.h>
#endif

typedef struct {
  RmR_ChunkMeta *v;
  size_t n;
  size_t cap;
} ChunkVec;

static const char *route_target_from_id(uint8_t id) {
  switch (id) {
    case RMR_ROUTE_CPU: return "CPU";
    case RMR_ROUTE_RAM: return "RAM";
    case RMR_ROUTE_DISK: return "DISK";
    default: return "FALLBACK";
  }
}

static void choose_route(const RmR_TriadStatus *triad, uint32_t chunk_idx, RmR_ChunkMeta *m) {
  uint8_t options[3];
  uint32_t n = 0;
  if (triad->cpu_ok) options[n++] = RMR_ROUTE_CPU;
  if (triad->ram_ok) options[n++] = RMR_ROUTE_RAM;
  if (triad->disk_ok) options[n++] = RMR_ROUTE_DISK;

  if (n >= 2) {
    m->route_id = options[chunk_idx % n];
    m->flags.bad_event = 0;
    m->flags.miss = 0;
  } else {
    m->route_id = RMR_ROUTE_FALLBACK;
    m->flags.bad_event = 1;
    m->flags.miss = 1;
  }
  m->route_target = route_target_from_id(m->route_id);
}

static int vec_push(ChunkVec *vec, const RmR_ChunkMeta *m) {
  if (vec->n == vec->cap) {
    size_t next = vec->cap ? vec->cap * 2u : 64u;
    RmR_ChunkMeta *nv = (RmR_ChunkMeta *)realloc(vec->v, next * sizeof(*nv));
    if (!nv) return -1;
    vec->v = nv;
    vec->cap = next;
  }
  vec->v[vec->n++] = *m;
  return 0;
}

static int append_event(FILE *logf, uint64_t event_idx, RmR_Stage stage, const RmR_ChunkMeta *m) {
  int written = fprintf(logf,
                        "event=%llu stage=%u off=%llu size=%u route=%u target=%s crc32c=%08x hash64=%016llx entropy_milli=%u flags=%u:%u:%u\n",
                        (unsigned long long)event_idx,
                        (unsigned int)stage,
                        (unsigned long long)m->offset,
                        m->size,
                        (unsigned int)m->route_id,
                        m->route_target,
                        m->crc32c,
                        (unsigned long long)m->hash64,
                        m->entropy_milli,
                        (unsigned int)m->flags.bad_event,
                        (unsigned int)m->flags.miss,
                        (unsigned int)m->flags.temp_hint);
  return (written > 0) ? 0 : -1;
}

static uint32_t g_crc32c_table[256];
static int g_crc32c_table_ready;

static void init_crc32c_table(void) {
  if (g_crc32c_table_ready) return;
  for (uint32_t i = 0; i < 256; ++i) {
    uint32_t c = i;
    for (uint32_t b = 0; b < 8; ++b) {
      c = (c & 1u) ? (0x82F63B78u ^ (c >> 1)) : (c >> 1);
    }
    g_crc32c_table[i] = c;
  }
  g_crc32c_table_ready = 1;
}

static uint32_t crc32c_sw(const uint8_t *buf, size_t len) {
  uint32_t crc = 0xFFFFFFFFu;
  for (size_t i = 0; i < len; ++i) {
    crc = g_crc32c_table[(crc ^ buf[i]) & 0xFFu] ^ (crc >> 8);
  }
  return ~crc;
}

uint32_t RmR_CRC32C(const uint8_t *buf, size_t len) {
  init_crc32c_table();
#if defined(__aarch64__) && defined(__ARM_FEATURE_CRC32)
  uint32_t crc = 0xFFFFFFFFu;
  size_t i = 0;
  for (; i + 8 <= len; i += 8) {
    uint64_t x;
    memcpy(&x, buf + i, sizeof(x));
    crc = __crc32cd(crc, x);
  }
  for (; i < len; ++i) crc = __crc32cb(crc, buf[i]);
  return ~crc;
#elif defined(__SSE4_2__) && (defined(__x86_64__) || defined(__i386__))
  uint32_t crc = 0xFFFFFFFFu;
  size_t i = 0;
  for (; i + 8 <= len; i += 8) {
    uint64_t x;
    memcpy(&x, buf + i, sizeof(x));
    crc = (uint32_t)_mm_crc32_u64(crc, x);
  }
  for (; i < len; ++i) crc = _mm_crc32_u8(crc, buf[i]);
  return ~crc;
#else
  return crc32c_sw(buf, len);
#endif
}

uint64_t RmR_Hash64_FNV1a(const uint8_t *buf, size_t len) {
  uint64_t h = 1469598103934665603ull;
  for (size_t i = 0; i < len; ++i) {
    h ^= (uint64_t)buf[i];
    h *= 1099511628211ull;
  }
  return h;
}

uint32_t RmR_EntropyEstimateMilli(const uint8_t *buf, size_t len) {
  if (len == 0) return 0;
  uint8_t seen[256];
  memset(seen, 0, sizeof(seen));
  uint32_t unique = 0;
  uint32_t transitions = 0;
  for (size_t i = 0; i < len; ++i) {
    if (!seen[buf[i]]) { seen[buf[i]] = 1; unique++; }
    if (i > 0 && buf[i] != buf[i - 1]) transitions++;
  }
  uint32_t uniq_component = (unique * 6000u) / 256u;
  uint32_t trans_component = (transitions * 2000u) / (uint32_t)(len > 1 ? (len - 1) : 1);
  return uniq_component + trans_component;
}

static void apply_mutation(uint8_t *buf, size_t len, uint64_t base_off, uint8_t xor_mask, uint32_t stride) {
  if (stride == 0) return;
  for (size_t i = 0; i < len; ++i) {
    uint64_t abs_off = base_off + (uint64_t)i;
    if ((abs_off % stride) == 0u) {
      buf[i] ^= xor_mask;
    }
  }
}

int RmR_RunPolicyPipeline(const char *input_path,
                          const char *output_path,
                          const char *audit_log_path,
                          const RmR_PipelineConfig *config,
                          RmR_AuditSummary *summary) {
  if (!input_path || !output_path || !audit_log_path || !config || config->chunk_size == 0) return -1;

  RmR_AuditSummary local_summary;
  memset(&local_summary, 0, sizeof(local_summary));

  FILE *in = fopen(input_path, "rb");
  if (!in) return -2;
  FILE *out = fopen(output_path, "wb");
  if (!out) { fclose(in); return -3; }
  FILE *logf = fopen(audit_log_path, "ab");
  if (!logf) { fclose(in); fclose(out); return -4; }

  ChunkVec plan = {0}, applied = {0};
  uint8_t *buf = (uint8_t *)malloc(config->chunk_size);
  if (!buf) {
    fclose(in); fclose(out); fclose(logf);
    return -5;
  }

  uint64_t event_idx = 1;
  uint64_t offset = 0;
  size_t rd;
  while ((rd = fread(buf, 1, config->chunk_size, in)) > 0) {
    RmR_ChunkMeta m;
    memset(&m, 0, sizeof(m));
    m.offset = offset;
    m.size = (uint32_t)rd;
    m.crc32c = RmR_CRC32C(buf, rd);
    m.hash64 = RmR_Hash64_FNV1a(buf, rd);
    m.entropy_milli = RmR_EntropyEstimateMilli(buf, rd);
    m.flags.temp_hint = (m.entropy_milli > 5500u) ? 1u : 0u;
    choose_route(&config->triad, local_summary.chunks_planned, &m);
    if (append_event(logf, event_idx++, RMR_STAGE_PLAN, &m) != 0 || vec_push(&plan, &m) != 0) goto fail;
    local_summary.chunks_planned++;

    apply_mutation(buf, rd, offset, config->mutation_xor, config->mutation_stride);

    RmR_ChunkMeta am = m;
    am.crc32c = RmR_CRC32C(buf, rd);
    am.hash64 = RmR_Hash64_FNV1a(buf, rd);
    am.entropy_milli = RmR_EntropyEstimateMilli(buf, rd);
    am.flags.temp_hint = (am.entropy_milli > 5500u) ? 1u : 0u;

    if (append_event(logf, event_idx++, RMR_STAGE_APPLY, &am) != 0 || vec_push(&applied, &am) != 0) goto fail;
    local_summary.chunks_applied++;

    if (fwrite(buf, 1, rd, out) != rd) goto fail;
    offset += rd;
  }
  fflush(out);

  for (size_t i = 0; i < plan.n && i < applied.n; ++i) {
    RmR_ChunkMeta d = applied.v[i];
    d.flags.miss = (plan.v[i].crc32c != applied.v[i].crc32c) ? 1u : 0u;
    if (append_event(logf, event_idx++, RMR_STAGE_DIFF, &d) != 0) goto fail;
    local_summary.chunks_diff++;
  }

  fclose(out);
  out = NULL;

  FILE *verify = fopen(output_path, "rb");
  if (!verify) goto fail;
  offset = 0;
  size_t idx = 0;
  while ((rd = fread(buf, 1, config->chunk_size, verify)) > 0 && idx < applied.n) {
    RmR_ChunkMeta vm = applied.v[idx];
    vm.offset = offset;
    vm.size = (uint32_t)rd;
    vm.crc32c = RmR_CRC32C(buf, rd);
    vm.hash64 = RmR_Hash64_FNV1a(buf, rd);
    vm.entropy_milli = RmR_EntropyEstimateMilli(buf, rd);
    vm.flags.miss = (vm.crc32c != applied.v[idx].crc32c) ? 1u : 0u;
    if (vm.flags.miss) {
      vm.flags.bad_event = 1u;
      local_summary.verify_failures++;
    }
    if (append_event(logf, event_idx++, RMR_STAGE_VERIFY, &vm) != 0) {
      fclose(verify);
      goto fail;
    }
    local_summary.chunks_verified++;
    offset += rd;
    idx++;
  }
  fclose(verify);

  {
    RmR_ChunkMeta final_meta;
    memset(&final_meta, 0, sizeof(final_meta));
    final_meta.route_id = RMR_ROUTE_FALLBACK;
    final_meta.route_target = route_target_from_id(RMR_ROUTE_FALLBACK);
    final_meta.flags.bad_event = (local_summary.verify_failures > 0) ? 1u : 0u;
    if (append_event(logf, event_idx++, RMR_STAGE_AUDIT, &final_meta) != 0) goto fail;
  }

  free(buf);
  free(plan.v);
  free(applied.v);
  fclose(logf);
  fclose(in);
  if (summary) *summary = local_summary;
  return (local_summary.verify_failures == 0) ? 0 : 1;

fail:
  free(buf);
  free(plan.v);
  free(applied.v);
  if (logf) fclose(logf);
  if (in) fclose(in);
  if (out) fclose(out);
  return -6;
}
