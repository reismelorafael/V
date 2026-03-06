#ifndef RMR_POLICY_KERNEL_H
#define RMR_POLICY_KERNEL_H

#include <stddef.h>
#include <stdint.h>

#include "zero.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_STAGE_PLAN = RMR_ZERO_STAGE_PSI_U8,
  RMR_STAGE_DIFF = RMR_ZERO_STAGE_CHI_U8,
  RMR_STAGE_APPLY = RMR_ZERO_STAGE_RHO_U8,
  RMR_STAGE_VERIFY = RMR_ZERO_STAGE_DELTA_U8,
  RMR_STAGE_AUDIT = RMR_ZERO_STAGE_SIGMA_U8
} RmR_Stage;

typedef enum {
  RMR_ROUTE_CPU = RMR_ZERO_ROUTE_CPU_U8,
  RMR_ROUTE_RAM = RMR_ZERO_ROUTE_RAM_U8,
  RMR_ROUTE_DISK = RMR_ZERO_ROUTE_DISK_U8,
  RMR_ROUTE_FALLBACK = RMR_ZERO_ROUTE_FALLBACK_U8
} RmR_RouteId;

typedef enum {
  RMR_DECISION_MODE_BRANCHLESS = 1,
  RMR_DECISION_MODE_FALLBACK = 2
} RmR_DecisionMode;

#if defined(__cplusplus)
#define RMR_POLICY_STATIC_ASSERT(COND, MSG) static_assert((COND), MSG)
#elif defined(__STDC_VERSION__) && (__STDC_VERSION__ >= 201112L)
#define RMR_POLICY_STATIC_ASSERT(COND, MSG) _Static_assert((COND), MSG)
#else
#define RMR_POLICY_STATIC_ASSERT(COND, MSG) typedef char rmr_policy_static_assertion_##__LINE__[(COND) ? 1 : -1]
#endif

RMR_POLICY_STATIC_ASSERT(RMR_STAGE_PLAN == RMR_ZERO_STAGE_PSI_U8,
                         "RmR_Stage mismatch: PLAN");
RMR_POLICY_STATIC_ASSERT(RMR_STAGE_DIFF == RMR_ZERO_STAGE_CHI_U8,
                         "RmR_Stage mismatch: DIFF");
RMR_POLICY_STATIC_ASSERT(RMR_STAGE_APPLY == RMR_ZERO_STAGE_RHO_U8,
                         "RmR_Stage mismatch: APPLY");
RMR_POLICY_STATIC_ASSERT(RMR_STAGE_VERIFY == RMR_ZERO_STAGE_DELTA_U8,
                         "RmR_Stage mismatch: VERIFY");
RMR_POLICY_STATIC_ASSERT(RMR_STAGE_AUDIT == RMR_ZERO_STAGE_SIGMA_U8,
                         "RmR_Stage mismatch: AUDIT");

RMR_POLICY_STATIC_ASSERT(RMR_ROUTE_CPU == RMR_ZERO_ROUTE_CPU_U8,
                         "RmR_RouteId mismatch: CPU");
RMR_POLICY_STATIC_ASSERT(RMR_ROUTE_RAM == RMR_ZERO_ROUTE_RAM_U8,
                         "RmR_RouteId mismatch: RAM");
RMR_POLICY_STATIC_ASSERT(RMR_ROUTE_DISK == RMR_ZERO_ROUTE_DISK_U8,
                         "RmR_RouteId mismatch: DISK");
RMR_POLICY_STATIC_ASSERT(RMR_ROUTE_FALLBACK == RMR_ZERO_ROUTE_FALLBACK_U8,
                         "RmR_RouteId mismatch: FALLBACK");

#undef RMR_POLICY_STATIC_ASSERT

typedef struct {
  uint8_t cpu_ok;
  uint8_t ram_ok;
  uint8_t disk_ok;
} RmR_TriadStatus;

typedef struct {
  uint8_t bad_event;
  uint8_t miss;
  uint8_t temp_hint;
} RmR_ChunkFlags;

typedef struct {
  uint64_t offset;
  uint32_t size;
  uint32_t crc32c;
  uint64_t hash64;
  uint64_t stage_signature;
  uint32_t entropy_milli;
  uint32_t math_signature;
  uint8_t route_id;
  uint8_t domain_hint;
  uint8_t decision_mode;
  const char *route_target;
  RmR_ChunkFlags flags;
} RmR_ChunkMeta;

typedef struct {
  size_t chunk_size;
  uint8_t mutation_xor;
  uint32_t mutation_stride;
  RmR_TriadStatus triad;
} RmR_PipelineConfig;

typedef struct {
  uint32_t chunks_planned;
  uint32_t chunks_diff;
  uint32_t chunks_applied;
  uint32_t chunks_verified;
  uint32_t verify_failures;
  uint64_t exec_signature;
} RmR_AuditSummary;

uint32_t RmR_CRC32C(const uint8_t *buf, size_t len);
uint32_t RmR_CRC32C_RawUpdate(uint32_t initial, const uint8_t *buf, size_t len);
uint64_t RmR_Hash64_FNV1a(const uint8_t *buf, size_t len);
uint32_t RmR_EntropyEstimateMilli(const uint8_t *buf, size_t len);

int RmR_RunPolicyPipeline(const char *input_path,
                          const char *output_path,
                          const char *audit_log_path,
                          const RmR_PipelineConfig *config,
                          RmR_AuditSummary *summary);

#ifdef __cplusplus
}
#endif

#endif
