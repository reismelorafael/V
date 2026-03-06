#ifndef RMR_UNIFIED_JNI_BASE_H
#define RMR_UNIFIED_JNI_BASE_H

#include <stddef.h>
#include <stdint.h>

#include "bitomega.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_UK_OK 0
#define RMR_KERNEL_OK 0
#define RMR_KERNEL_ERR_ARG -1
#define RMR_KERNEL_ERR_STATE -2
#define RMR_UK_NATIVE_OK_MAGIC 0x56414343u  /* "VACC" — MUST match NativeFastPath.NATIVE_OK_MAGIC */

#if defined(__cplusplus)
static_assert(RMR_UK_OK == RMR_KERNEL_OK, "status contract mismatch: RMR_UK_OK != RMR_KERNEL_OK");
#else
_Static_assert(RMR_UK_OK == RMR_KERNEL_OK, "status contract mismatch: RMR_UK_OK != RMR_KERNEL_OK");
#endif

#if defined(RMR_UK_ERR_ARG) && defined(RMR_KERNEL_ERR_ARG)
#if defined(__cplusplus)
static_assert(RMR_UK_ERR_ARG == RMR_KERNEL_ERR_ARG,
              "status contract mismatch: RMR_UK_ERR_ARG != RMR_KERNEL_ERR_ARG");
#else
_Static_assert(RMR_UK_ERR_ARG == RMR_KERNEL_ERR_ARG,
               "status contract mismatch: RMR_UK_ERR_ARG != RMR_KERNEL_ERR_ARG");
#endif
#endif

#if defined(RMR_UK_ERR_STATE) && defined(RMR_KERNEL_ERR_STATE)
#if defined(__cplusplus)
static_assert(RMR_UK_ERR_STATE == RMR_KERNEL_ERR_STATE,
              "status contract mismatch: RMR_UK_ERR_STATE != RMR_KERNEL_ERR_STATE");
#else
_Static_assert(RMR_UK_ERR_STATE == RMR_KERNEL_ERR_STATE,
               "status contract mismatch: RMR_UK_ERR_STATE != RMR_KERNEL_ERR_STATE");
#endif
#endif

#define RMR_UK_MAX_SLOTS 1024u

typedef struct {
  /* signature = stable architecture code compatible with NativeFastPath.ARCH_*. */
  uint32_t signature;
  uint32_t pointer_bits;
  uint32_t cache_line_bytes;
  uint32_t page_bytes;
  /* feature_mask = feature bits only; excludes architecture-identification bits. */
  uint32_t feature_mask;
  uint32_t reg_signature_0;
  uint32_t reg_signature_1;
  uint32_t reg_signature_2;
  uint32_t gpio_word_bits;
  uint32_t gpio_pin_stride;
  uint32_t cache_hint_l4;
} RmR_UnifiedCapabilities;

#define RMR_SIG_ARCH_UNKNOWN 0x0000u
#define RMR_SIG_ARCH_ARM64 0x0100u
#define RMR_SIG_ARCH_ARM32 0x0200u
#define RMR_SIG_ARCH_X64 0x0300u
#define RMR_SIG_ARCH_X86 0x0400u
#define RMR_SIG_ARCH_RISCV64 0x0500u

typedef struct {
  uint32_t seed;
  uint32_t arena_bytes;
} RmR_UnifiedConfig;

typedef struct {
  uint32_t cpu_pressure;
  uint32_t storage_pressure;
  uint32_t io_pressure;
  int64_t matrix_determinant;
} RmR_UnifiedProcessState;

typedef struct {
  uint32_t route_id;
  uint64_t route_tag;
} RmR_UnifiedRouteState;

typedef struct {
  uint32_t computed_crc32c;
  uint32_t verify_ok;
} RmR_UnifiedVerifyState;

typedef struct {
  uint32_t crc32c;
  uint32_t entropy;
  uint32_t stage_counter;
} RmR_UnifiedIngestState;

typedef struct {
  uint64_t audit_signature;
} RmR_UnifiedAuditState;

typedef struct {
  uint32_t offset;
  uint32_t size;
  uint32_t generation;
  uint8_t in_use;
} RmR_UnifiedArenaSlot;

typedef struct {
  uint32_t initialized;
  uint32_t seed;
  uint32_t crc32c;
  uint32_t entropy;
  uint32_t stage_counter;
  uint64_t last_route_tag;
  bitomega_node_t bitomega_node;
  uint32_t bitomega_invariant_ok;
  uint32_t bitomega_fallback_safe;
  RmR_UnifiedCapabilities caps;
  uint8_t *arena_base;
  uint32_t arena_capacity;
  RmR_UnifiedArenaSlot slots[RMR_UK_MAX_SLOTS];
} RmR_UnifiedKernel;

int RmR_UnifiedKernel_Init(RmR_UnifiedKernel *kernel, const RmR_UnifiedConfig *config);
int RmR_UnifiedKernel_Shutdown(RmR_UnifiedKernel *kernel);
int RmR_UnifiedKernel_QueryCapabilities(const RmR_UnifiedKernel *kernel, RmR_UnifiedCapabilities *out);
int RmR_UnifiedKernel_Detect(RmR_UnifiedCapabilities *out);
int RmR_UnifiedKernel_Ingest(RmR_UnifiedKernel *kernel, const uint8_t *data, size_t len, RmR_UnifiedIngestState *out);
int RmR_UnifiedKernel_Process(RmR_UnifiedKernel *kernel,
                              uint64_t cpu_cycles,
                              uint64_t storage_read_bytes,
                              uint64_t storage_write_bytes,
                              uint64_t input_bytes,
                              uint64_t output_bytes,
                              int64_t m00,
                              int64_t m01,
                              int64_t m10,
                              int64_t m11,
                              RmR_UnifiedProcessState *out);
int RmR_UnifiedKernel_Route(RmR_UnifiedKernel *kernel,
                            const RmR_UnifiedProcessState *process,
                            RmR_UnifiedRouteState *out);
int RmR_UnifiedKernel_Verify(RmR_UnifiedKernel *kernel,
                             const uint8_t *data,
                             size_t len,
                             uint32_t expected_crc32c,
                             RmR_UnifiedVerifyState *out);
int RmR_UnifiedKernel_Audit(RmR_UnifiedKernel *kernel,
                            const RmR_UnifiedIngestState *ingest,
                            const RmR_UnifiedProcessState *process,
                            const RmR_UnifiedRouteState *route,
                            const RmR_UnifiedVerifyState *verify,
                            RmR_UnifiedAuditState *out);

int RmR_UnifiedKernel_Copy(RmR_UnifiedKernel *kernel, uint8_t *dst, const uint8_t *src, size_t len);
uint32_t RmR_UnifiedKernel_XorChecksum(RmR_UnifiedKernel *kernel, const uint8_t *data, size_t len);
uint32_t RmR_UnifiedKernel_Popcount32(uint32_t x);
uint32_t RmR_UnifiedKernel_ByteSwap32(uint32_t x);
uint32_t RmR_UnifiedKernel_Rotl32(uint32_t x, uint32_t n);
uint32_t RmR_UnifiedKernel_Rotr32(uint32_t x, uint32_t n);
int RmR_UnifiedKernel_ArenaAlloc(RmR_UnifiedKernel *kernel, uint32_t bytes, uint32_t *out_handle);
int RmR_UnifiedKernel_ArenaFree(RmR_UnifiedKernel *kernel, uint32_t handle);
int RmR_UnifiedKernel_ArenaCopy(RmR_UnifiedKernel *kernel,
                                uint32_t src_handle,
                                uint32_t src_offset,
                                uint32_t dst_handle,
                                uint32_t dst_offset,
                                uint32_t len);
int RmR_UnifiedKernel_ArenaXorChecksum(RmR_UnifiedKernel *kernel,
                                       uint32_t handle,
                                       uint32_t offset,
                                       uint32_t len,
                                       uint32_t *out_checksum);
int RmR_UnifiedKernel_ArenaFill(RmR_UnifiedKernel *kernel,
                                uint32_t handle,
                                uint32_t offset,
                                uint32_t len,
                                uint8_t value);
int RmR_UnifiedKernel_ArenaWrite(RmR_UnifiedKernel *kernel,
                                 uint32_t handle,
                                 uint32_t offset,
                                 const uint8_t *src,
                                 uint32_t len);

#ifdef __cplusplus
}
#endif

#endif
