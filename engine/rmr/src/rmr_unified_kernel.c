#include "rmr_unified_kernel.h"

#include "bitraf.h"
#include "rmr_corelib.h"
#include "rmr_hw_detect.h"
#if defined(RMR_ENABLE_POLICY_MODULE) && RMR_ENABLE_POLICY_MODULE
#include "rmr_policy_kernel.h"
#endif

#include "zero_compat.h"

typedef enum {
  RMR_LEGACY_STATE_NEW = 0,
  RMR_LEGACY_STATE_READY = 1,
  RMR_LEGACY_STATE_SHUTDOWN = 2
} rmr_legacy_state_t;

struct rmr_legacy_kernel {
  rmr_legacy_state_t lifecycle;
  uint32_t seed;
  uint32_t rolling_crc32c;
  uint64_t rolling_bitraf_hash;
  uint32_t stage_counter;
  uint64_t last_route_signature;
  rmr_legacy_capabilities_t capabilities;
};

static int rmr_legacy_is_ready(const rmr_legacy_kernel_t *kernel) {
  return kernel && kernel->lifecycle == RMR_LEGACY_STATE_READY;
}

static void rmr_legacy_capabilities_from_hw(const RmR_HW_Info *hw,
                                             rmr_legacy_capabilities_t *caps) {
  caps->arch = hw->arch;
  caps->arch_hex = hw->arch_hex;
  caps->word_bits = hw->word_bits;
  caps->ptr_bits = hw->ptr_bits;
  caps->is_little_endian = hw->is_little_endian;
  caps->has_cycle_counter = hw->has_cycle_counter;
  caps->has_asm_probe = hw->has_asm_probe;
  caps->reg_signature_0 = hw->reg_signature_0;
  caps->reg_signature_1 = hw->reg_signature_1;
  caps->reg_signature_2 = hw->reg_signature_2;
  caps->feature_bits_0 = hw->feature_bits_0;
  caps->feature_bits_1 = hw->feature_bits_1;
  caps->cacheline_bytes = hw->cacheline_bytes;
  caps->cache_hint_l1 = hw->cache_hint_l1;
  caps->cache_hint_l2 = hw->cache_hint_l2;
  caps->cache_hint_l3 = hw->cache_hint_l3;
  caps->page_bytes = hw->page_bytes;
  caps->mem_bus_bits = hw->mem_bus_bits;
  caps->gpio_word_bits = hw->gpio_word_bits;
  caps->gpio_pin_stride = hw->gpio_pin_stride;
  caps->align_bytes = hw->align_bytes;
}

rmr_status_t rmr_legacy_kernel_autodetect(rmr_legacy_capabilities_t *out_capabilities) {
  if (!out_capabilities) return RMR_STATUS_ERR_ARG;
  RmR_HW_Info hw;
  rmr_mem_set(&hw, 0u, sizeof(hw));
  RmR_HW_Detect(&hw);
  rmr_legacy_capabilities_from_hw(&hw, out_capabilities);
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_init(rmr_legacy_kernel_t **out_kernel,
                                    const rmr_legacy_kernel_init_desc_t *desc) {
  rmr_legacy_kernel_t *kernel;
  if (!out_kernel || !desc) return RMR_STATUS_ERR_ARG;
  if (*out_kernel) {
    if ((*out_kernel)->lifecycle == RMR_LEGACY_STATE_READY) return RMR_STATUS_ERR_ALREADY_INITIALIZED;
    if ((*out_kernel)->lifecycle == RMR_LEGACY_STATE_SHUTDOWN) return RMR_STATUS_ERR_ALREADY_SHUTDOWN;
    return RMR_STATUS_ERR_STATE;
  }

  kernel = (rmr_legacy_kernel_t *)rmr_malloc(sizeof(*kernel));
  if (!kernel) return RMR_STATUS_ERR_NOMEM;

  rmr_mem_set(kernel, 0u, sizeof(*kernel));
  kernel->lifecycle = RMR_LEGACY_STATE_NEW;
  kernel->seed = desc->seed;
  kernel->rolling_crc32c = desc->seed;
  kernel->rolling_bitraf_hash = (uint64_t)desc->seed;

  if (rmr_legacy_kernel_autodetect(&kernel->capabilities) != RMR_STATUS_OK) {
    rmr_free(kernel);
    return RMR_STATUS_ERR_STATE;
  }

  kernel->lifecycle = RMR_LEGACY_STATE_READY;
  *out_kernel = kernel;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_shutdown(rmr_legacy_kernel_t **kernel) {
  rmr_legacy_kernel_t *ctx;
  if (!kernel) return RMR_STATUS_ERR_ARG;
  if (!*kernel) return RMR_STATUS_ERR_ALREADY_SHUTDOWN;

  ctx = *kernel;
  if (ctx->lifecycle == RMR_LEGACY_STATE_SHUTDOWN) {
    rmr_free(ctx);
    *kernel = NULL;
    return RMR_STATUS_ERR_ALREADY_SHUTDOWN;
  }

  ctx->lifecycle = RMR_LEGACY_STATE_SHUTDOWN;
  rmr_mem_set(ctx, 0u, sizeof(*ctx));
  rmr_free(ctx);
  *kernel = NULL;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_ingest(rmr_legacy_kernel_t *kernel,
                                      const rmr_legacy_kernel_ingest_desc_t *desc,
                                      rmr_legacy_kernel_ingest_result_t *out_result) {
  if (!rmr_legacy_is_ready(kernel) || !desc || !out_result || (!desc->data && desc->data_len != 0u)) {
    return RMR_STATUS_ERR_ARG;
  }

  kernel->rolling_crc32c = RmR_CRC32C(desc->data, desc->data_len) ^ kernel->rolling_crc32c;
  kernel->rolling_bitraf_hash = bitraf_hash(desc->data, desc->data_len, kernel->seed) ^ kernel->rolling_bitraf_hash;
  kernel->stage_counter += 1u;

  out_result->crc32c = kernel->rolling_crc32c;
  out_result->bitraf_hash = kernel->rolling_bitraf_hash;
  out_result->entropy_milli = RmR_EntropyEstimateMilli(desc->data, desc->data_len);
  out_result->stage_counter = kernel->stage_counter;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_process(rmr_legacy_kernel_t *kernel,
                                       const rmr_legacy_kernel_process_desc_t *desc,
                                       rmr_legacy_kernel_process_result_t *out_result) {
  if (!rmr_legacy_is_ready(kernel) || !desc || !out_result) return RMR_STATUS_ERR_ARG;

  out_result->cpu_pressure = (uint32_t)((desc->cpu_cycles >> 10u) & 0xFFFFu);
  out_result->storage_pressure =
      (uint32_t)(((desc->storage_read_bytes + desc->storage_write_bytes) >> 10u) & 0xFFFFu);
  out_result->io_pressure = (uint32_t)(((desc->input_bytes + desc->output_bytes) >> 10u) & 0xFFFFu);
  out_result->matrix_determinant =
      (desc->matrix_m00 * desc->matrix_m11) - (desc->matrix_m01 * desc->matrix_m10);

  kernel->stage_counter += 1u;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_route(rmr_legacy_kernel_t *kernel,
                                     const rmr_legacy_kernel_process_result_t *process,
                                     rmr_legacy_kernel_route_result_t *out_result) {
  uint32_t route;
  if (!rmr_legacy_is_ready(kernel) || !process || !out_result) return RMR_STATUS_ERR_ARG;

  route = RMR_ROUTE_DISK;
  if (process->cpu_pressure >= process->storage_pressure && process->cpu_pressure >= process->io_pressure) {
    route = RMR_ROUTE_CPU;
  } else if (process->storage_pressure >= process->io_pressure) {
    route = RMR_ROUTE_RAM;
  }

  out_result->route_id = route;
  out_result->route_signature = ((uint64_t)kernel->rolling_crc32c << 32) ^ kernel->rolling_bitraf_hash ^
                                (uint64_t)process->matrix_determinant ^ (uint64_t)route;
  kernel->last_route_signature = out_result->route_signature;
  kernel->stage_counter += 1u;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_verify(rmr_legacy_kernel_t *kernel,
                                      const rmr_legacy_kernel_verify_desc_t *desc,
                                      rmr_legacy_kernel_verify_result_t *out_result) {
  if (!rmr_legacy_is_ready(kernel) || !desc || !out_result || (!desc->data && desc->data_len != 0u)) {
    return RMR_STATUS_ERR_ARG;
  }

  out_result->computed_crc32c = RmR_CRC32C(desc->data, desc->data_len);
  out_result->computed_bitraf_hash = bitraf_hash(desc->data, desc->data_len, kernel->seed);
  out_result->crc_ok = (out_result->computed_crc32c == desc->expected_crc32c) ? 1u : 0u;
  out_result->hash_ok = bitraf_verify(desc->data, desc->data_len, desc->expected_bitraf_hash, kernel->seed) ? 1u : 0u;
  kernel->stage_counter += 1u;

  if (!out_result->crc_ok || !out_result->hash_ok) return RMR_STATUS_ERR_VERIFY;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_audit(rmr_legacy_kernel_t *kernel,
                                     const rmr_legacy_kernel_ingest_result_t *ingest,
                                     const rmr_legacy_kernel_process_result_t *process,
                                     const rmr_legacy_kernel_route_result_t *route,
                                     const rmr_legacy_kernel_verify_result_t *verify,
                                     rmr_legacy_kernel_audit_result_t *out_result) {
  uint64_t signature;
  if (!rmr_legacy_is_ready(kernel) || !ingest || !process || !route || !verify || !out_result) {
    return RMR_STATUS_ERR_ARG;
  }

  signature = ((uint64_t)ingest->crc32c << 32) ^ ingest->bitraf_hash;
  signature ^= ((uint64_t)process->cpu_pressure << 48) ^ ((uint64_t)process->storage_pressure << 24) ^
               (uint64_t)process->io_pressure;
  signature ^= (uint64_t)process->matrix_determinant;
  signature ^= route->route_signature;
  signature ^= ((uint64_t)verify->computed_crc32c << 1) ^ verify->computed_bitraf_hash;
  signature ^= ((uint64_t)verify->crc_ok << 2) ^ ((uint64_t)verify->hash_ok << 3);

  out_result->audit_signature = signature;
  out_result->audit_code = (verify->crc_ok && verify->hash_ok) ? 0u : 1u;
  kernel->stage_counter += 1u;
  return RMR_STATUS_OK;
}

rmr_status_t rmr_legacy_kernel_get_capabilities(const rmr_legacy_kernel_t *kernel,
                                                rmr_legacy_capabilities_t *out_capabilities) {
  if (!rmr_legacy_is_ready(kernel) || !out_capabilities) return RMR_STATUS_ERR_ARG;
  rmr_mem_copy(out_capabilities, &kernel->capabilities, sizeof(*out_capabilities));
  return RMR_STATUS_OK;
}

static void rmr_unified_caps_from_hw(const RmR_HW_Info *hw, RmR_UnifiedCapabilities *out) {
  uint32_t arch_signature = RMR_SIG_ARCH_UNKNOWN;

  switch (hw->arch) {
    case 4u: /* ARM64 */
      arch_signature = RMR_SIG_ARCH_ARM64;
      break;
    case 3u: /* ARM32 */
      arch_signature = RMR_SIG_ARCH_ARM32;
      break;
    case 2u: /* X64 */
      arch_signature = RMR_SIG_ARCH_X64;
      break;
    case 1u: /* X86 */
      arch_signature = RMR_SIG_ARCH_X86;
      break;
    case 5u: /* RISCV64 */
      arch_signature = RMR_SIG_ARCH_RISCV64;
      break;
    default:
      switch (hw->arch_hex) {
        case 0xA64u: /* ARM64 */
          arch_signature = RMR_SIG_ARCH_ARM64;
          break;
        case 0xA32u: /* ARM32 */
          arch_signature = RMR_SIG_ARCH_ARM32;
          break;
        case 0x8664u: /* X64 */
          arch_signature = RMR_SIG_ARCH_X64;
          break;
        case 0x86u: /* X86 */
          arch_signature = RMR_SIG_ARCH_X86;
          break;
        case 0x52u: /* RISCV64 */
          arch_signature = RMR_SIG_ARCH_RISCV64;
          break;
        default:
          arch_signature = RMR_SIG_ARCH_UNKNOWN;
          break;
      }
      break;
  }

  out->signature = arch_signature;
  out->pointer_bits = hw->ptr_bits;
  out->cache_line_bytes = hw->cacheline_bytes;
  out->page_bytes = hw->page_bytes;
  out->feature_mask = hw->feature_bits_0;
  out->reg_signature_0 = hw->reg_signature_0;
  out->reg_signature_1 = hw->reg_signature_1;
  out->reg_signature_2 = hw->reg_signature_2;
  out->gpio_word_bits = hw->gpio_word_bits;
  out->gpio_pin_stride = hw->gpio_pin_stride;
}

int RmR_UnifiedKernel_Detect(RmR_UnifiedCapabilities *out) {
  RmR_HW_Info hw;
  if (!out) return RMR_KERNEL_ERR_ARG;
  rmr_mem_set(&hw, 0u, sizeof(hw));
  RmR_HW_Detect(&hw);
  rmr_unified_caps_from_hw(&hw, out);
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Init(RmR_UnifiedKernel *kernel, const RmR_UnifiedConfig *config) {
  uint32_t arena_bytes;
  if (!kernel || !config) return RMR_KERNEL_ERR_ARG;
  if (kernel->initialized) return RMR_KERNEL_ERR_STATE;

  rmr_mem_set(kernel, 0u, sizeof(*kernel));
  kernel->seed = config->seed;
  kernel->crc32c = config->seed;
  kernel->entropy = config->seed;
  kernel->initialized = 1u;

  if (RmR_UnifiedKernel_Detect(&kernel->caps) != RMR_UK_OK) return RMR_KERNEL_ERR_STATE;

  arena_bytes = config->arena_bytes ? config->arena_bytes : (64u * 1024u * 1024u);
  kernel->arena_base = (uint8_t *)rmr_malloc((size_t)arena_bytes);
  if (!kernel->arena_base) {
    rmr_mem_set(kernel, 0u, sizeof(*kernel));
    return RMR_KERNEL_ERR_STATE;
  }
  kernel->arena_capacity = arena_bytes;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Shutdown(RmR_UnifiedKernel *kernel) {
  if (!kernel) return RMR_KERNEL_ERR_ARG;
  if (!kernel->initialized) return RMR_KERNEL_ERR_STATE;
  if (kernel->arena_base) rmr_free(kernel->arena_base);
  rmr_mem_set(kernel, 0u, sizeof(*kernel));
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_QueryCapabilities(const RmR_UnifiedKernel *kernel, RmR_UnifiedCapabilities *out) {
  if (!kernel || !out || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  *out = kernel->caps;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Ingest(RmR_UnifiedKernel *kernel, const uint8_t *data, size_t len, RmR_UnifiedIngestState *out) {
  if (!kernel || !out || !kernel->initialized || (!data && len != 0u)) return RMR_KERNEL_ERR_ARG;
  kernel->crc32c = RmR_CRC32C(data, len);
  kernel->entropy = RmR_EntropyEstimateMilli(data, len);
  kernel->stage_counter += 1u;
  out->crc32c = kernel->crc32c;
  out->entropy = kernel->entropy;
  out->stage_counter = kernel->stage_counter;
  return RMR_UK_OK;
}

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
                              RmR_UnifiedProcessState *out) {
  if (!kernel || !out || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  out->cpu_pressure = (uint32_t)((cpu_cycles >> 10u) & 0xFFFFu);
  out->storage_pressure = (uint32_t)(((storage_read_bytes + storage_write_bytes) >> 10u) & 0xFFFFu);
  out->io_pressure = (uint32_t)(((input_bytes + output_bytes) >> 10u) & 0xFFFFu);
  out->matrix_determinant = (m00 * m11) - (m01 * m10);
  kernel->stage_counter += 1u;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Route(RmR_UnifiedKernel *kernel,
                            const RmR_UnifiedProcessState *process,
                            RmR_UnifiedRouteState *out) {
  uint32_t route = RMR_ROUTE_DISK;
  if (!kernel || !process || !out || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  if (process->cpu_pressure >= process->storage_pressure && process->cpu_pressure >= process->io_pressure) {
    route = RMR_ROUTE_CPU;
  } else if (process->storage_pressure >= process->io_pressure) {
    route = RMR_ROUTE_RAM;
  }
  out->route_id = route;
  out->route_tag = ((uint64_t)kernel->crc32c << 32) ^ (uint64_t)process->matrix_determinant ^ (uint64_t)route;
  kernel->last_route_tag = out->route_tag;
  kernel->stage_counter += 1u;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Verify(RmR_UnifiedKernel *kernel,
                             const uint8_t *data,
                             size_t len,
                             uint32_t expected_crc32c,
                             RmR_UnifiedVerifyState *out) {
  if (!kernel || !out || !kernel->initialized || (!data && len != 0u)) return RMR_KERNEL_ERR_ARG;
  out->computed_crc32c = RmR_CRC32C(data, len);
  out->verify_ok = (out->computed_crc32c == expected_crc32c) ? 1u : 0u;
  kernel->stage_counter += 1u;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Audit(RmR_UnifiedKernel *kernel,
                            const RmR_UnifiedIngestState *ingest,
                            const RmR_UnifiedProcessState *process,
                            const RmR_UnifiedRouteState *route,
                            const RmR_UnifiedVerifyState *verify,
                            RmR_UnifiedAuditState *out) {
  if (!kernel || !ingest || !process || !route || !verify || !out || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  out->audit_signature = ((uint64_t)ingest->crc32c << 32) ^ (uint64_t)ingest->entropy ^
                         (uint64_t)process->matrix_determinant ^ route->route_tag ^
                         ((uint64_t)verify->computed_crc32c << 1) ^ (uint64_t)verify->verify_ok;
  kernel->stage_counter += 1u;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_Copy(RmR_UnifiedKernel *kernel, uint8_t *dst, const uint8_t *src, size_t len) {
  if (!kernel || !kernel->initialized || !dst || !src) return RMR_KERNEL_ERR_ARG;
  rmr_mem_copy(dst, src, len);
  return RMR_UK_OK;
}

uint32_t RmR_UnifiedKernel_XorChecksum(RmR_UnifiedKernel *kernel, const uint8_t *data, size_t len) {
  size_t i;
  uint32_t x = 0u;
  if (!kernel || !kernel->initialized || (!data && len != 0u)) return 0u;
  for (i = 0; i < len; ++i) x ^= (uint32_t)data[i];
  return x;
}

uint32_t RmR_UnifiedKernel_Popcount32(uint32_t x) {
  x = x - ((x >> 1u) & 0x55555555u);
  x = (x & 0x33333333u) + ((x >> 2u) & 0x33333333u);
  x = (x + (x >> 4u)) & 0x0F0F0F0Fu;
  return (x * 0x01010101u) >> 24u;
}

uint32_t RmR_UnifiedKernel_ByteSwap32(uint32_t x) {
  return ((x & 0x000000FFu) << 24u) | ((x & 0x0000FF00u) << 8u) | ((x & 0x00FF0000u) >> 8u) |
         ((x & 0xFF000000u) >> 24u);
}

uint32_t RmR_UnifiedKernel_Rotl32(uint32_t x, uint32_t n) {
  n &= 31u;
  return (x << n) | (x >> ((32u - n) & 31u));
}

uint32_t RmR_UnifiedKernel_Rotr32(uint32_t x, uint32_t n) {
  n &= 31u;
  return (x >> n) | (x << ((32u - n) & 31u));
}

static int rmr_unified_slot_lookup(const RmR_UnifiedKernel *kernel, uint32_t handle, uint32_t *slot_index) {
  uint32_t idx = handle - 1u;
  if (!kernel || !slot_index || handle == 0u || idx >= RMR_UK_MAX_SLOTS) return RMR_KERNEL_ERR_ARG;
  if (!kernel->slots[idx].in_use) return RMR_KERNEL_ERR_ARG;
  *slot_index = idx;
  return RMR_UK_OK;
}

static int rmr_unified_collect_active_sorted(const RmR_UnifiedKernel *kernel,
                                             uint32_t *sorted_slots,
                                             uint32_t *active_count,
                                             uint32_t *free_slot) {
  uint32_t i;
  uint32_t count = 0u;
  uint32_t first_free = RMR_UK_MAX_SLOTS;
  if (!kernel || !sorted_slots || !active_count || !free_slot) return RMR_KERNEL_ERR_ARG;

  for (i = 0; i < RMR_UK_MAX_SLOTS; ++i) {
    if (kernel->slots[i].in_use) {
      uint32_t j;
      if (count >= RMR_UK_MAX_SLOTS) return RMR_KERNEL_ERR_STATE;
      sorted_slots[count] = i;
      j = count;
      while (j > 0u && kernel->slots[sorted_slots[j - 1u]].offset > kernel->slots[sorted_slots[j]].offset) {
        uint32_t swap = sorted_slots[j - 1u];
        sorted_slots[j - 1u] = sorted_slots[j];
        sorted_slots[j] = swap;
        --j;
      }
      count += 1u;
    } else if (first_free == RMR_UK_MAX_SLOTS) {
      first_free = i;
    }
  }

  *active_count = count;
  *free_slot = first_free;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_ArenaAlloc(RmR_UnifiedKernel *kernel, uint32_t bytes, uint32_t *out_handle) {
  uint32_t i;
  uint32_t sorted_slots[RMR_UK_MAX_SLOTS];
  uint32_t active_count;
  uint32_t slot;
  uint32_t alloc_offset = 0u;
  uint32_t prev_end = 0u;
  int rc;
  if (!kernel || !out_handle || !kernel->initialized || bytes == 0u) return RMR_KERNEL_ERR_ARG;
  rc = rmr_unified_collect_active_sorted(kernel, sorted_slots, &active_count, &slot);
  if (rc != RMR_UK_OK) return rc;

  if (slot == RMR_UK_MAX_SLOTS) return RMR_KERNEL_ERR_STATE;

  for (i = 0; i < active_count; ++i) {
    const RmR_UnifiedArenaSlot *active = &kernel->slots[sorted_slots[i]];
    uint32_t active_end;

    if (active->size > UINT32_MAX - active->offset) return RMR_KERNEL_ERR_STATE;
    active_end = active->offset + active->size;

    if (active_end > kernel->arena_capacity || active->offset < prev_end) return RMR_KERNEL_ERR_STATE;

    if (bytes <= active->offset - prev_end) {
      alloc_offset = prev_end;
      break;
    }

    prev_end = active_end;
  }

  if (i == active_count) {
    if (prev_end > kernel->arena_capacity || bytes > kernel->arena_capacity - prev_end) return RMR_KERNEL_ERR_STATE;
    alloc_offset = prev_end;
  }

  kernel->slots[slot].offset = alloc_offset;
  kernel->slots[slot].size = bytes;
  kernel->slots[slot].generation += 1u;
  kernel->slots[slot].in_use = 1u;
  *out_handle = slot + 1u;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_ArenaFree(RmR_UnifiedKernel *kernel, uint32_t handle) {
  uint32_t slot;
  int rc = rmr_unified_slot_lookup(kernel, handle, &slot);
  if (rc != RMR_UK_OK) return rc;
  kernel->slots[slot].in_use = 0u;
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_ArenaCopy(RmR_UnifiedKernel *kernel,
                                uint32_t src_handle,
                                uint32_t src_offset,
                                uint32_t dst_handle,
                                uint32_t dst_offset,
                                uint32_t len) {
  uint32_t src_slot;
  uint32_t dst_slot;
  int rc;
  if (!kernel || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  rc = rmr_unified_slot_lookup(kernel, src_handle, &src_slot);
  if (rc != RMR_UK_OK) return rc;
  rc = rmr_unified_slot_lookup(kernel, dst_handle, &dst_slot);
  if (rc != RMR_UK_OK) return rc;
  if (len > kernel->slots[src_slot].size || src_offset > kernel->slots[src_slot].size - len ||
      len > kernel->slots[dst_slot].size || dst_offset > kernel->slots[dst_slot].size - len) {
    return RMR_KERNEL_ERR_ARG;
  }
  if (dst_offset > UINT32_MAX - kernel->slots[dst_slot].offset ||
      src_offset > UINT32_MAX - kernel->slots[src_slot].offset) {
    return RMR_KERNEL_ERR_STATE;
  }
  rmr_mem_copy(kernel->arena_base + (kernel->slots[dst_slot].offset + dst_offset),
               kernel->arena_base + (kernel->slots[src_slot].offset + src_offset),
               len);
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_ArenaXorChecksum(RmR_UnifiedKernel *kernel,
                                       uint32_t handle,
                                       uint32_t offset,
                                       uint32_t len,
                                       uint32_t *out_checksum) {
  uint32_t slot;
  uint32_t i;
  int rc;
  if (!kernel || !out_checksum || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  rc = rmr_unified_slot_lookup(kernel, handle, &slot);
  if (rc != RMR_UK_OK) return rc;
  if (len > kernel->slots[slot].size || offset > kernel->slots[slot].size - len) return RMR_KERNEL_ERR_ARG;
  if (offset > UINT32_MAX - kernel->slots[slot].offset) return RMR_KERNEL_ERR_STATE;
  *out_checksum = 0u;
  for (i = 0; i < len; ++i) {
    *out_checksum ^= (uint32_t)kernel->arena_base[kernel->slots[slot].offset + (offset + i)];
  }
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_ArenaFill(RmR_UnifiedKernel *kernel,
                                uint32_t handle,
                                uint32_t offset,
                                uint32_t len,
                                uint8_t value) {
  uint32_t slot;
  int rc;
  if (!kernel || !kernel->initialized) return RMR_KERNEL_ERR_ARG;
  rc = rmr_unified_slot_lookup(kernel, handle, &slot);
  if (rc != RMR_UK_OK) return rc;
  if (len > kernel->slots[slot].size || offset > kernel->slots[slot].size - len) return RMR_KERNEL_ERR_ARG;
  if (offset > UINT32_MAX - kernel->slots[slot].offset) return RMR_KERNEL_ERR_STATE;
  rmr_mem_set(kernel->arena_base + (kernel->slots[slot].offset + offset), value, len);
  return RMR_UK_OK;
}

int RmR_UnifiedKernel_ArenaWrite(RmR_UnifiedKernel *kernel,
                                 uint32_t handle,
                                 uint32_t offset,
                                 const uint8_t *src,
                                 uint32_t len) {
  uint32_t slot;
  int rc;
  if (!kernel || !kernel->initialized || (!src && len != 0u)) return RMR_KERNEL_ERR_ARG;
  rc = rmr_unified_slot_lookup(kernel, handle, &slot);
  if (rc != RMR_UK_OK) return rc;
  if (len > kernel->slots[slot].size || offset > kernel->slots[slot].size - len) return RMR_KERNEL_ERR_ARG;
  if (offset > UINT32_MAX - kernel->slots[slot].offset) return RMR_KERNEL_ERR_STATE;
  rmr_mem_copy(kernel->arena_base + (kernel->slots[slot].offset + offset), src, len);
  return RMR_UK_OK;
}

static void rmr_caps_from_unified(const RmR_UnifiedCapabilities *in, rmr_jni_capabilities_t *out) {
  out->signature = in->signature;
  out->pointer_bits = in->pointer_bits;
  out->cache_line_bytes = in->cache_line_bytes;
  out->page_bytes = in->page_bytes;
  out->feature_mask = in->feature_mask;
  out->reg_signature_0 = in->reg_signature_0;
  out->reg_signature_1 = in->reg_signature_1;
  out->reg_signature_2 = in->reg_signature_2;
  out->gpio_word_bits = in->gpio_word_bits;
  out->gpio_pin_stride = in->gpio_pin_stride;
  out->register_width_bits = in->pointer_bits;
  out->pin_count_hint = in->gpio_word_bits;
  out->feature_bits_hi = in->reg_signature_2;
}

int rmr_jni_kernel_init(rmr_jni_kernel_state_t *state, uint32_t seed) {
  RmR_UnifiedConfig config;
  if (!state) return RMR_KERNEL_ERR_ARG;
  config.seed = seed;
  config.arena_bytes = 64u * 1024u * 1024u;
  return RmR_UnifiedKernel_Init(state, &config);
}

int rmr_jni_kernel_shutdown(rmr_jni_kernel_state_t *state) {
  if (!state || !state->initialized) return RMR_KERNEL_ERR_STATE;
  return RmR_UnifiedKernel_Shutdown(state);
}

int rmr_jni_kernel_get_capabilities(const rmr_jni_kernel_state_t *state, rmr_jni_capabilities_t *out_caps) {
  RmR_UnifiedCapabilities caps;
  int rc;
  if (!state || !out_caps) return RMR_KERNEL_ERR_ARG;
  rc = RmR_UnifiedKernel_QueryCapabilities(state, &caps);
  if (rc != RMR_UK_OK) return rc;
  rmr_caps_from_unified(&caps, out_caps);
  return RMR_KERNEL_OK;
}

int rmr_jni_kernel_autodetect(rmr_jni_capabilities_t *out_caps) {
  RmR_UnifiedCapabilities caps;
  int rc;
  if (!out_caps) return RMR_KERNEL_ERR_ARG;
  rc = RmR_UnifiedKernel_Detect(&caps);
  if (rc != RMR_UK_OK) return rc;
  rmr_caps_from_unified(&caps, out_caps);
  return RMR_KERNEL_OK;
}

int rmr_jni_kernel_ingest(rmr_jni_kernel_state_t *state, const uint8_t *data, uint32_t len, uint32_t *out_crc32c) {
  RmR_UnifiedIngestState ingest;
  int rc;
  if (!state || !out_crc32c || (!data && len != 0u)) return RMR_KERNEL_ERR_ARG;
  rc = RmR_UnifiedKernel_Ingest(state, data, (size_t)len, &ingest);
  if (rc != RMR_UK_OK) return rc;
  *out_crc32c = ingest.crc32c;
  return RMR_KERNEL_OK;
}

int rmr_jni_kernel_process(rmr_jni_kernel_state_t *state, int32_t a, int32_t b, uint32_t mode, int32_t *out_value) {
  uint32_t ua = (uint32_t)a;
  uint32_t ub = (uint32_t)b;
  if (!state || !out_value || !state->initialized) return RMR_KERNEL_ERR_ARG;
  switch (mode & 3u) {
    case 0u:
      *out_value = (int32_t)(ua ^ ub);
      break;
    case 1u:
      *out_value = (int32_t)(ua + ub);
      break;
    case 2u:
      *out_value = (int32_t)(ua - ub);
      break;
    default:
      *out_value = (int32_t)((ua << (ub & 31u)) | (ua >> ((32u - (ub & 31u)) & 31u)));
      break;
  }
  state->stage_counter += 1u;
  return RMR_KERNEL_OK;
}

int rmr_jni_kernel_route(rmr_jni_kernel_state_t *state, const rmr_jni_route_input_t *in, rmr_jni_route_output_t *out) {
  RmR_UnifiedProcessState process;
  RmR_UnifiedRouteState route;
  int rc;
  if (!state || !in || !out) return RMR_KERNEL_ERR_ARG;
  rc = RmR_UnifiedKernel_Process(state,
                                 in->cpu_cycles,
                                 in->storage_read_bytes,
                                 in->storage_write_bytes,
                                 in->input_bytes,
                                 in->output_bytes,
                                 in->m00,
                                 in->m01,
                                 in->m10,
                                 in->m11,
                                 &process);
  if (rc != RMR_UK_OK) return rc;
  rc = RmR_UnifiedKernel_Route(state, &process, &route);
  if (rc != RMR_UK_OK) return rc;
  out->route = route.route_id;
  out->matrix_determinant = process.matrix_determinant;
  out->cpu_pressure = process.cpu_pressure;
  out->storage_pressure = process.storage_pressure;
  out->io_pressure = process.io_pressure;
  out->route_tag = route.route_tag;
  return RMR_KERNEL_OK;
}

int rmr_jni_kernel_verify(rmr_jni_kernel_state_t *state,
                          const uint8_t *data,
                          uint32_t len,
                          uint32_t expected_crc32c,
                          uint32_t *out_verify_ok) {
  RmR_UnifiedVerifyState verify;
  int rc;
  if (!state || !out_verify_ok || (!data && len != 0u)) return RMR_KERNEL_ERR_ARG;
  rc = RmR_UnifiedKernel_Verify(state, data, (size_t)len, expected_crc32c, &verify);
  if (rc != RMR_UK_OK) return rc;
  *out_verify_ok = verify.verify_ok;
  return verify.verify_ok ? RMR_KERNEL_OK : 1;
}

int rmr_jni_kernel_audit(rmr_jni_kernel_state_t *state, uint64_t *counters, uint32_t counter_count) {
  if (!state || !counters || counter_count < 7u) return RMR_KERNEL_ERR_ARG;
  counters[0] = state->caps.signature;
  counters[1] = state->caps.pointer_bits;
  counters[2] = state->crc32c;
  counters[3] = state->entropy;
  counters[4] = state->stage_counter;
  counters[5] = (uint64_t)(uint32_t)(state->last_route_tag & 0xFFFFFFFFu);
  counters[6] = (uint64_t)(uint32_t)((state->last_route_tag >> 32) & 0xFFFFFFFFu);
  return RMR_KERNEL_OK;
}
