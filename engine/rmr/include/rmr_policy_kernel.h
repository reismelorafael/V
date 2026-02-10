#ifndef RMR_POLICY_KERNEL_H
#define RMR_POLICY_KERNEL_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_STAGE_PLAN = 1,
  RMR_STAGE_DIFF = 2,
  RMR_STAGE_APPLY = 3,
  RMR_STAGE_VERIFY = 4,
  RMR_STAGE_AUDIT = 5
} RmR_Stage;

typedef enum {
  RMR_ROUTE_CPU = 1,
  RMR_ROUTE_RAM = 2,
  RMR_ROUTE_DISK = 3,
  RMR_ROUTE_FALLBACK = 255
} RmR_RouteId;

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
  uint32_t entropy_milli;
  uint8_t route_id;
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
} RmR_AuditSummary;

uint32_t RmR_CRC32C(const uint8_t *buf, size_t len);
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
