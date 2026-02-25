#include "rmr_policy_kernel.h"
#if defined(RMR_JNI_BUILD) && RMR_JNI_BUILD
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#else
#include "rmr_baremetal_compat.h" /* baremetal stdlib substitute */
#endif
#include "rmr_corelib.h"
#include "rmr_hw_detect.h"
#include "rmr_ll_ops.h"
#include "rmr_math_fabric.h"
#include "rmr_ll_tuning.h"

/* BUG FIX baremetal: removido #include <stdio.h> */
/* BUG FIX baremetal: removido #include <stdlib.h> */
/* BUG FIX baremetal: removido #include <string.h> */
/* BUG FIX baremetal: removido #include <sys/stat.h> */

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


static uint32_t clamp_u32_local(uint32_t v, uint32_t lo, uint32_t hi) {
  if (v < lo) return lo;
  if (v > hi) return hi;
  return v;
}
static const char *route_target_from_id(uint8_t id) {
  switch (id) {
    case RMR_ROUTE_CPU: return "CPU";
    case RMR_ROUTE_RAM: return "RAM";
    case RMR_ROUTE_DISK: return "DISK";
    default: return "FALLBACK";
  }
}

static uint64_t mix_u64(uint64_t acc, uint64_t x) {
  acc ^= x + 0x9E3779B97F4A7C15ull + (acc << 6) + (acc >> 2);
  return acc;
}

static uint64_t stage_signature(RmR_Stage stage,
                                uint32_t matrix_seed,
                                const RmR_MathFabricPlan *plan,
                                const RmR_ChunkMeta *m) {
  uint64_t sig = 1469598103934665603ull;
  sig = mix_u64(sig, (uint64_t)(unsigned)stage);
  sig = mix_u64(sig, (uint64_t)matrix_seed);
  sig = mix_u64(sig, (uint64_t)plan->matrix_seed);
  sig = mix_u64(sig, (uint64_t)plan->lane_count);
  sig = mix_u64(sig, (uint64_t)m->route_id);
  sig = mix_u64(sig, (uint64_t)m->crc32c);
  sig = mix_u64(sig, m->hash64);
  return sig;
}


static void choose_route_fallback(const RmR_TriadStatus *triad, uint32_t chunk_idx, RmR_ChunkMeta *m) {
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

static void choose_route_branchless(const RmR_TriadStatus *triad, uint32_t chunk_idx, RmR_ChunkMeta *m) {
  uint32_t cpu = rmr_mask_u32((uint32_t)triad->cpu_ok);
  uint32_t ram = rmr_mask_u32((uint32_t)triad->ram_ok);
  uint32_t disk = rmr_mask_u32((uint32_t)triad->disk_ok);

  uint32_t n = ((cpu & 1u) + (ram & 1u) + (disk & 1u));
  uint32_t idx = (n > 0u) ? (chunk_idx % n) : 0u;

  uint32_t has_cpu_slot = cpu;
  uint32_t cpu_slot = 0u;
  uint32_t has_ram_slot = ram & rmr_mask_u32(idx >= ((has_cpu_slot & 1u) ? 1u : 0u));
  uint32_t ram_slot = ((has_cpu_slot & 1u) ? 1u : 0u);
  uint32_t has_disk_slot = disk & rmr_mask_u32(idx >= (ram_slot + ((has_ram_slot & 1u) ? 1u : 0u)));

  uint32_t route = RMR_ROUTE_FALLBACK;
  route = select_u32(has_cpu_slot & rmr_mask_u32(idx == cpu_slot), RMR_ROUTE_CPU, route);
  route = select_u32(has_ram_slot & rmr_mask_u32(idx == ram_slot), RMR_ROUTE_RAM, route);
  route = select_u32(has_disk_slot, RMR_ROUTE_DISK, route);

  uint32_t has_quorum = rmr_mask_u32(n >= 2u);
  m->route_id = select_u8(has_quorum, (uint8_t)route, (uint8_t)RMR_ROUTE_FALLBACK);
  m->flags.bad_event = select_u8(has_quorum, 0u, 1u);
  m->flags.miss = select_u8(has_quorum, 0u, 1u);
  m->route_target = route_target_from_id(m->route_id);
}

static void choose_route(const RmR_TriadStatus *triad,
                         uint32_t chunk_idx,
                         uint8_t decision_mode,
                         RmR_ChunkMeta *m) {
  RmR_ChunkMeta fallback_meta = *m;
  choose_route_fallback(triad, chunk_idx, &fallback_meta);

  if (decision_mode == RMR_DECISION_MODE_FALLBACK) {
    *m = fallback_meta;
    m->decision_mode = RMR_DECISION_MODE_FALLBACK;
    return;
  }

  RmR_ChunkMeta branchless_meta = *m;
  choose_route_branchless(triad, chunk_idx, &branchless_meta);

  uint32_t fallback_sig = (uint32_t)fallback_meta.route_id
                        ^ ((uint32_t)fallback_meta.flags.bad_event << 8)
                        ^ ((uint32_t)fallback_meta.flags.miss << 9);
  uint32_t branchless_sig = (uint32_t)branchless_meta.route_id
                          ^ ((uint32_t)branchless_meta.flags.bad_event << 8)
                          ^ ((uint32_t)branchless_meta.flags.miss << 9);

  if (branchless_sig != fallback_sig) {
    branchless_meta.route_id = fallback_meta.route_id;
    branchless_meta.flags.bad_event = fallback_meta.flags.bad_event;
    branchless_meta.flags.miss = fallback_meta.flags.miss;
    branchless_meta.route_target = fallback_meta.route_target;
  }

  *m = branchless_meta;
  m->decision_mode = RMR_DECISION_MODE_BRANCHLESS;
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
                        "event=%llu stage=%u off=%llu size=%u route=%u target=%s crc32c=%08x hash64=%016llx stage_sig=%016llx entropy_milli=%u math_sig=%08x domain=%u decision=%u flags=%u:%u:%u\n",
                        (unsigned long long)event_idx,
                        (unsigned int)stage,
                        (unsigned long long)m->offset,
                        m->size,
                        (unsigned int)m->route_id,
                        m->route_target,
                        m->crc32c,
                        (unsigned long long)m->hash64,
                        (unsigned long long)m->stage_signature,
                        m->entropy_milli,
                        m->math_signature,
                        (unsigned int)m->domain_hint,
                        (unsigned int)m->decision_mode,
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

static uint32_t crc32c_sw_update(uint32_t crc, const uint8_t *buf, size_t len) {
  for (size_t i = 0; i < len; ++i) {
    crc = g_crc32c_table[(crc ^ buf[i]) & 0xFFu] ^ (crc >> 8);
  }
  return crc;
}

static uint32_t crc32c_aarch64_update(uint32_t crc, const uint8_t *buf, size_t len) {
#if defined(__aarch64__) && defined(__ARM_FEATURE_CRC32)
  size_t i = 0;
  for (; i + 8 <= len; i += 8) {
    uint64_t x;
    rmr_mem_copy(&x, buf + i, sizeof(x));
    crc = __crc32cd(crc, x);
  }
  for (; i < len; ++i) crc = __crc32cb(crc, buf[i]);
  return crc;
#else
  (void)buf;
  (void)len;
  return crc;
#endif
}

uint32_t RmR_CRC32C_RawUpdate(uint32_t initial, const uint8_t *buf, size_t len) {
  if ((!buf && len != 0u) || len == 0u) return initial;
  init_crc32c_table();
#if defined(__aarch64__) && defined(__ARM_FEATURE_CRC32)
  return crc32c_aarch64_update(initial, buf, len);
#elif defined(__SSE4_2__) && (defined(__x86_64__) || defined(__i386__))
  uint32_t crc = initial;
  size_t i = 0;
  for (; i + 8 <= len; i += 8) {
    uint64_t x;
    rmr_mem_copy(&x, buf + i, sizeof(x));
    crc = (uint32_t)_mm_crc32_u64(crc, x);
  }
  for (; i < len; ++i) crc = _mm_crc32_u8(crc, buf[i]);
  return crc;
#else
  return crc32c_sw_update(initial, buf, len);
#endif
}

uint32_t RmR_CRC32C(const uint8_t *buf, size_t len) {
  return ~RmR_CRC32C_RawUpdate(0xFFFFFFFFu, buf, len);
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
  rmr_mem_set(seen, 0, sizeof(seen));
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

static void build_math_signature(const RmR_MathFabricPlan *plan,
                                 const uint8_t *buf,
                                 size_t len,
                                 uint64_t chunk_offset,
                                 RmR_ChunkMeta *m) {
  uint32_t points[RMR_MATH_POINTS];
  uint32_t domains[RMR_MATH_DOMAINS];
  uint32_t rolling = 0xC001D00Du ^ (uint32_t)chunk_offset;

  for (uint32_t p = 0; p < RMR_MATH_POINTS; ++p) {
    uint32_t stride = (uint32_t)len / RMR_MATH_POINTS;
    uint32_t idx = (stride * p) + p;
    if (len == 0) idx = 0;
    else idx %= (uint32_t)len;
    rolling = RmR_LL_Rotl32(rolling, 5u);
    rolling ^= (len > 0) ? (uint32_t)buf[idx] : 0u;
    rolling ^= (m->crc32c >> ((p & 3u) * 8u));
    points[p] = rolling ^ (m->entropy_milli << (p & 7u));
  }

  RmR_MathFabric_VectorMix(plan, points, domains);
  m->math_signature = 0u;
  m->domain_hint = 0u;
  for (uint32_t d = 0; d < RMR_MATH_DOMAINS; ++d) {
    m->math_signature ^= domains[d] + (d * 0x9E37u);
    if ((domains[d] & 0xFFu) > (domains[m->domain_hint] & 0xFFu)) {
      m->domain_hint = (uint8_t)d;
    }
  }
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

static int paths_refer_same_file(const char *a, const char *b) {
  {
    size_t al = rmr_len_u8((const uint8_t *)a);
    size_t bl = rmr_len_u8((const uint8_t *)b);
    if (al == bl && rmr_mem_eq(a, b, al)) return 1;
  }

  struct stat sa;
  struct stat sb;
  if (stat(a, &sa) != 0 || stat(b, &sb) != 0) return 0;
  return (sa.st_dev == sb.st_dev && sa.st_ino == sb.st_ino) ? 1 : 0;
}

int RmR_RunPolicyPipeline(const char *input_path,
                          const char *output_path,
                          const char *audit_log_path,
                          const RmR_PipelineConfig *config,
  RmR_AuditSummary *summary) {
  if (!input_path || !output_path || !audit_log_path || !config || config->chunk_size == 0) return -1;
  if (paths_refer_same_file(input_path, output_path)) return -7;

  RmR_AuditSummary local_summary;
  RmR_HW_Info hw;
  RmR_MathFabricPlan math_plan;
  RmR_LL_TunePlan tune;
  size_t io_batch_size;
  uint32_t commit_quantum;
  uint32_t commit_counter = 0u;
  const uint8_t decision_mode = RMR_DECISION_MODE_BRANCHLESS;
  memset(&local_summary, 0, sizeof(local_summary));
  local_summary.exec_signature = 1469598103934665603ull;
  memset(&hw, 0, sizeof(hw));
  memset(&math_plan, 0, sizeof(math_plan));
  RmR_HW_Detect(&hw);
  RmR_MathFabric_AutodetectPlan(&hw, &math_plan);
  RmR_LL_ApplyTuneDefaults(&hw, &tune);
  math_plan.lane_count = clamp_u32_local(tune.policy_lane_width, 4u, 32u);
  io_batch_size = config->chunk_size;
  if (tune.policy_batch_size > 0u && (size_t)tune.policy_batch_size < io_batch_size) {
    io_batch_size = (size_t)tune.policy_batch_size;
  }
  if (io_batch_size == 0u) io_batch_size = config->chunk_size;
  commit_quantum = tune.policy_commit_quantum ? tune.policy_commit_quantum : 16u;

  FILE *in = fopen(input_path, "rb");
  if (!in) return -2;
  FILE *out = fopen(output_path, "wb");
  if (!out) { fclose(in); return -3; }
  FILE *logf = fopen(audit_log_path, "ab");
  if (!logf) { fclose(in); fclose(out); return -4; }

  ChunkVec plan = {0}, applied = {0};
  uint8_t *buf = (uint8_t *)malloc(io_batch_size);
  if (!buf) {
    fclose(in); fclose(out); fclose(logf);
    return -5;
  }

  uint64_t event_idx = 1;
  uint64_t offset = 0;
  size_t rd;
  while ((rd = fread(buf, 1, io_batch_size, in)) > 0) {
    RmR_ChunkMeta m;
    rmr_mem_set(&m, 0, sizeof(m));
    m.offset = offset;
    m.size = (uint32_t)rd;
    m.crc32c = RmR_CRC32C(buf, rd);
    m.hash64 = RmR_Hash64_FNV1a(buf, rd);
    m.entropy_milli = RmR_EntropyEstimateMilli(buf, rd);
    m.flags.temp_hint = (m.entropy_milli > 5500u) ? 1u : 0u;
    build_math_signature(&math_plan, buf, rd, offset, &m);
    choose_route(&config->triad, local_summary.chunks_planned, decision_mode, &m);
    m.stage_signature = stage_signature(RMR_STAGE_PLAN, math_plan.matrix_seed, &math_plan, &m);
    local_summary.exec_signature = mix_u64(local_summary.exec_signature, m.stage_signature);
    if (append_event(logf, event_idx++, RMR_STAGE_PLAN, &m) != 0 || vec_push(&plan, &m) != 0) goto fail;
    local_summary.chunks_planned++;

    apply_mutation(buf, rd, offset, config->mutation_xor, config->mutation_stride);

    RmR_ChunkMeta am = m;
    am.crc32c = RmR_CRC32C(buf, rd);
    am.hash64 = RmR_Hash64_FNV1a(buf, rd);
    am.entropy_milli = RmR_EntropyEstimateMilli(buf, rd);
    am.flags.temp_hint = (am.entropy_milli > 5500u) ? 1u : 0u;
    build_math_signature(&math_plan, buf, rd, offset, &am);
    am.stage_signature = stage_signature(RMR_STAGE_APPLY, math_plan.matrix_seed, &math_plan, &am);
    local_summary.exec_signature = mix_u64(local_summary.exec_signature, am.stage_signature);

    if (append_event(logf, event_idx++, RMR_STAGE_APPLY, &am) != 0 || vec_push(&applied, &am) != 0) goto fail;
    local_summary.chunks_applied++;

    if (fwrite(buf, 1, rd, out) != rd) goto fail;
    commit_counter++;
    if (commit_counter >= commit_quantum) {
      if (fflush(out) != 0 || fflush(logf) != 0) goto fail;
      commit_counter = 0u;
    }
    offset += rd;
  }
  fflush(out);

  for (size_t i = 0; i < plan.n && i < applied.n; ++i) {
    RmR_ChunkMeta d = applied.v[i];
    d.flags.miss = (plan.v[i].crc32c != applied.v[i].crc32c) ? 1u : 0u;
    d.stage_signature = stage_signature(RMR_STAGE_DIFF, math_plan.matrix_seed, &math_plan, &d);
    local_summary.exec_signature = mix_u64(local_summary.exec_signature, d.stage_signature);
    if (append_event(logf, event_idx++, RMR_STAGE_DIFF, &d) != 0) goto fail;
    local_summary.chunks_diff++;
  }

  fclose(out);
  out = NULL;

  FILE *verify = fopen(output_path, "rb");
  if (!verify) goto fail;
  offset = 0;
  size_t idx = 0;
  while ((rd = fread(buf, 1, io_batch_size, verify)) > 0 && idx < applied.n) {
    RmR_ChunkMeta vm = applied.v[idx];
    vm.offset = offset;
    vm.size = (uint32_t)rd;
    vm.crc32c = RmR_CRC32C(buf, rd);
    vm.hash64 = RmR_Hash64_FNV1a(buf, rd);
    vm.entropy_milli = RmR_EntropyEstimateMilli(buf, rd);
    build_math_signature(&math_plan, buf, rd, offset, &vm);
    vm.decision_mode = applied.v[idx].decision_mode;
    vm.flags.miss = (vm.crc32c != applied.v[idx].crc32c) ? 1u : 0u;
    vm.stage_signature = stage_signature(RMR_STAGE_VERIFY, math_plan.matrix_seed, &math_plan, &vm);
    local_summary.exec_signature = mix_u64(local_summary.exec_signature, vm.stage_signature);
    if (vm.flags.miss) {
      vm.flags.bad_event = 1u;
      local_summary.verify_failures++;
    }
    if (append_event(logf, event_idx++, RMR_STAGE_VERIFY, &vm) != 0) {
      fclose(verify);
      goto fail;
    }
    local_summary.chunks_verified++;
    commit_counter++;
    if (commit_counter >= commit_quantum) {
      if (fflush(logf) != 0) {
        fclose(verify);
        goto fail;
      }
      commit_counter = 0u;
    }
    offset += rd;
    idx++;
  }
  fclose(verify);

  {
    RmR_ChunkMeta final_meta;
    rmr_mem_set(&final_meta, 0, sizeof(final_meta));
    final_meta.route_id = RMR_ROUTE_FALLBACK;
    final_meta.route_target = route_target_from_id(RMR_ROUTE_FALLBACK);
    final_meta.math_signature = math_plan.matrix_seed ^ hw.arch;
    final_meta.domain_hint = (uint8_t)(math_plan.lane_count & 0x7u);
    final_meta.decision_mode = decision_mode;
    final_meta.flags.bad_event = (local_summary.verify_failures > 0) ? 1u : 0u;
    final_meta.crc32c = (uint32_t)local_summary.exec_signature;
    final_meta.hash64 = local_summary.exec_signature ^ ((uint64_t)hw.arch << 32);
    final_meta.stage_signature = stage_signature(RMR_STAGE_AUDIT, math_plan.matrix_seed, &math_plan, &final_meta);
    local_summary.exec_signature = mix_u64(local_summary.exec_signature, final_meta.stage_signature);
    final_meta.hash64 = local_summary.exec_signature;
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
