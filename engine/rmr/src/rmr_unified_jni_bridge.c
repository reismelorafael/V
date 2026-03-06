#include "rmr_unified_kernel.h"
#include "zero_compat.h"

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
  out->cache_hint_l4 = in->cache_hint_l4;
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
  out->bitomega_state = (uint32_t)state->bitomega_node.state;
  out->bitomega_dir = (uint32_t)state->bitomega_node.dir;
  out->bitomega_operational_state = state->bitomega_operational_state;
  out->bitomega_invariant_ok = state->bitomega_invariant_ok;
  out->bitomega_fallback_safe = state->bitomega_fallback_safe;
  out->bitomega_coherence = state->bitomega_node.coherence;
  out->bitomega_entropy = state->bitomega_node.entropy;
  out->bitomega_ctx_coherence_in = state->bitomega_ctx.coherence_in;
  out->bitomega_ctx_entropy_in = state->bitomega_ctx.entropy_in;
  out->bitomega_ctx_noise_in = state->bitomega_ctx.noise_in;
  out->bitomega_ctx_load = state->bitomega_ctx.load;
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
