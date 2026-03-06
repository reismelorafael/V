#ifndef RMR_UNIFIED_KERNEL_H
#define RMR_UNIFIED_KERNEL_H

#include <stddef.h>
#include <stdint.h>

#include "rmr_unified_jni_base.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_STATUS_OK = 0,
  RMR_STATUS_ERR_ARG = -1,
  RMR_STATUS_ERR_STATE = -2,
  RMR_STATUS_ERR_NOMEM = -3,
  RMR_STATUS_ERR_VERIFY = -4,
  RMR_STATUS_ERR_ALREADY_INITIALIZED = -5,
  RMR_STATUS_ERR_ALREADY_SHUTDOWN = -6
} rmr_status_t;

typedef struct rmr_legacy_kernel rmr_legacy_kernel_t;

typedef struct {
  uint32_t seed;
} rmr_legacy_kernel_init_desc_t;

typedef struct {
  const uint8_t *data;
  size_t data_len;
} rmr_legacy_kernel_ingest_desc_t;

typedef struct {
  uint64_t cpu_cycles;
  uint64_t storage_read_bytes;
  uint64_t storage_write_bytes;
  uint64_t input_bytes;
  uint64_t output_bytes;
  int64_t matrix_m00;
  int64_t matrix_m01;
  int64_t matrix_m10;
  int64_t matrix_m11;
} rmr_legacy_kernel_process_desc_t;

typedef struct {
  uint32_t cpu_pressure;
  uint32_t storage_pressure;
  uint32_t io_pressure;
  int64_t matrix_determinant;
} rmr_legacy_kernel_process_result_t;

typedef struct {
  const uint8_t *data;
  size_t data_len;
  uint32_t expected_crc32c;
  uint64_t expected_bitraf_hash;
} rmr_legacy_kernel_verify_desc_t;

typedef struct {
  uint32_t route_id;
  uint64_t route_signature;
} rmr_legacy_kernel_route_result_t;

typedef struct {
  uint32_t crc32c;
  uint64_t bitraf_hash;
  uint32_t entropy_milli;
  uint32_t stage_counter;
} rmr_legacy_kernel_ingest_result_t;

typedef struct {
  uint32_t computed_crc32c;
  uint64_t computed_bitraf_hash;
  uint8_t crc_ok;
  uint8_t hash_ok;
} rmr_legacy_kernel_verify_result_t;

typedef struct {
  uint64_t audit_signature;
  uint32_t audit_code;
} rmr_legacy_kernel_audit_result_t;

typedef struct {
  uint32_t arch;
  uint32_t arch_hex;
  uint32_t word_bits;
  uint32_t ptr_bits;
  uint32_t is_little_endian;
  uint32_t has_cycle_counter;
  uint32_t has_asm_probe;
  uint32_t reg_signature_0;
  uint32_t reg_signature_1;
  uint32_t reg_signature_2;
  uint32_t feature_bits_0;
  uint32_t feature_bits_1;
  uint32_t cacheline_bytes;
  uint32_t cache_hint_l1;
  uint32_t cache_hint_l2;
  uint32_t cache_hint_l3;
  uint32_t cache_hint_l4;
  uint32_t page_bytes;
  uint32_t mem_bus_bits;
  uint32_t gpio_word_bits;
  uint32_t gpio_pin_stride;
  uint32_t align_bytes;
} rmr_legacy_capabilities_t;

rmr_status_t rmr_legacy_kernel_init(rmr_legacy_kernel_t **out_kernel, const rmr_legacy_kernel_init_desc_t *desc);
rmr_status_t rmr_legacy_kernel_shutdown(rmr_legacy_kernel_t **kernel);
rmr_status_t rmr_legacy_kernel_ingest(rmr_legacy_kernel_t *kernel,
                                      const rmr_legacy_kernel_ingest_desc_t *desc,
                                      rmr_legacy_kernel_ingest_result_t *out_result);
rmr_status_t rmr_legacy_kernel_process(rmr_legacy_kernel_t *kernel,
                                       const rmr_legacy_kernel_process_desc_t *desc,
                                       rmr_legacy_kernel_process_result_t *out_result);
rmr_status_t rmr_legacy_kernel_route(rmr_legacy_kernel_t *kernel,
                                     const rmr_legacy_kernel_process_result_t *process,
                                     rmr_legacy_kernel_route_result_t *out_result);
rmr_status_t rmr_legacy_kernel_verify(rmr_legacy_kernel_t *kernel,
                                      const rmr_legacy_kernel_verify_desc_t *desc,
                                      rmr_legacy_kernel_verify_result_t *out_result);
rmr_status_t rmr_legacy_kernel_audit(rmr_legacy_kernel_t *kernel,
                                     const rmr_legacy_kernel_ingest_result_t *ingest,
                                     const rmr_legacy_kernel_process_result_t *process,
                                     const rmr_legacy_kernel_route_result_t *route,
                                     const rmr_legacy_kernel_verify_result_t *verify,
                                     rmr_legacy_kernel_audit_result_t *out_result);
rmr_status_t rmr_legacy_kernel_autodetect(rmr_legacy_capabilities_t *out_capabilities);
rmr_status_t rmr_legacy_kernel_get_capabilities(const rmr_legacy_kernel_t *kernel,
                                                rmr_legacy_capabilities_t *out_capabilities);

/* JNI facade contract, explicit and isolated from legacy ABI. */
typedef RmR_UnifiedKernel rmr_jni_kernel_state_t;

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
  /* Legacy aliases retained for existing JNI kernel unit contract callers. */
  uint32_t register_width_bits;
  uint32_t pin_count_hint;
  uint32_t feature_bits_hi;
  uint32_t cache_hint_l4;
} rmr_jni_capabilities_t;

typedef struct {
  uint64_t cpu_cycles;
  uint64_t storage_read_bytes;
  uint64_t storage_write_bytes;
  uint64_t input_bytes;
  uint64_t output_bytes;
  int64_t m00;
  int64_t m01;
  int64_t m10;
  int64_t m11;
} rmr_jni_route_input_t;

typedef struct {
  uint32_t route;
  int64_t matrix_determinant;
  uint32_t cpu_pressure;
  uint32_t storage_pressure;
  uint32_t io_pressure;
  uint64_t route_tag;
  uint32_t bitomega_state;
  uint32_t bitomega_dir;
  uint32_t bitomega_invariant_ok;
  uint32_t bitomega_fallback_safe;
} rmr_jni_route_output_t;

int rmr_jni_kernel_init(rmr_jni_kernel_state_t *state, uint32_t seed);
int rmr_jni_kernel_shutdown(rmr_jni_kernel_state_t *state);
int rmr_jni_kernel_get_capabilities(const rmr_jni_kernel_state_t *state, rmr_jni_capabilities_t *out_caps);
int rmr_jni_kernel_autodetect(rmr_jni_capabilities_t *out_caps);
int rmr_jni_kernel_ingest(rmr_jni_kernel_state_t *state, const uint8_t *data, uint32_t len, uint32_t *out_crc32c);
int rmr_jni_kernel_process(rmr_jni_kernel_state_t *state, int32_t a, int32_t b, uint32_t mode, int32_t *out_value);
int rmr_jni_kernel_route(rmr_jni_kernel_state_t *state, const rmr_jni_route_input_t *in, rmr_jni_route_output_t *out);
int rmr_jni_kernel_verify(rmr_jni_kernel_state_t *state,
                          const uint8_t *data,
                          uint32_t len,
                          uint32_t expected_crc32c,
                          uint32_t *out_verify_ok);
int rmr_jni_kernel_audit(rmr_jni_kernel_state_t *state, uint64_t *counters, uint32_t counter_count);

/* ── Arena API (JNI bridge) ── */
int RmR_UnifiedKernel_ArenaAlloc(rmr_jni_kernel_state_t *kernel, uint32_t bytes, uint32_t *out_handle);
int RmR_UnifiedKernel_ArenaFree(rmr_jni_kernel_state_t *kernel, uint32_t handle);
int RmR_UnifiedKernel_ArenaCopy(rmr_jni_kernel_state_t *kernel,
                                uint32_t src_handle, uint32_t src_offset,
                                uint32_t dst_handle, uint32_t dst_offset,
                                uint32_t len);
int RmR_UnifiedKernel_ArenaXorChecksum(rmr_jni_kernel_state_t *kernel,
                                       uint32_t handle, uint32_t offset,
                                       uint32_t len, uint32_t *out);
int RmR_UnifiedKernel_ArenaFill(rmr_jni_kernel_state_t *kernel,
                                uint32_t handle, uint32_t offset,
                                uint32_t len, uint8_t value);
int RmR_UnifiedKernel_ArenaWrite(rmr_jni_kernel_state_t *kernel,
                                 uint32_t handle, uint32_t offset,
                                 const uint8_t *src, uint32_t len);

#ifdef __cplusplus
}
#endif

#endif
