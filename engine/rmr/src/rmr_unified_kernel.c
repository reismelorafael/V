#include "rmr_unified_kernel.h"

#include <stddef.h>
#include <unistd.h>

static uint32_t rmr_arch_tag(void) {
#if defined(__aarch64__)
    return RMR_KERNEL_ARCH_ARM64;
#elif defined(__arm__)
    return RMR_KERNEL_ARCH_ARM32;
#elif defined(__x86_64__)
    return RMR_KERNEL_ARCH_X64;
#elif defined(__i386__)
    return RMR_KERNEL_ARCH_X86;
#elif defined(__riscv) && (__riscv_xlen == 64)
    return RMR_KERNEL_ARCH_RV64;
#elif defined(__riscv) && (__riscv_xlen == 32)
    return RMR_KERNEL_ARCH_RV32;
#else
    return RMR_KERNEL_ARCH_UNKNOWN;
#endif
}

static uint32_t rmr_os_tag(void) {
#if defined(__ANDROID__)
    return RMR_KERNEL_OS_ANDROID;
#elif defined(__linux__)
    return RMR_KERNEL_OS_LINUX;
#else
    return RMR_KERNEL_OS_UNKNOWN;
#endif
}

static uint32_t rmr_xor_digest(const uint8_t* data, uint32_t len, uint32_t seed) {
    uint32_t x = seed;
    for (uint32_t i = 0; i < len; i++) {
        x ^= ((uint32_t)data[i]) << ((i & 3u) * 8u);
        x = (x << 5u) | (x >> 27u);
    }
    return x;
}

int rmr_kernel_autodetect(rmr_kernel_capabilities_t* out_caps) {
    if (!out_caps) {
        return RMR_KERNEL_ERR_ARG;
    }

    uint32_t features = 0;
#if defined(__ARM_NEON)
    features |= RMR_KERNEL_FEATURE_NEON;
#endif
#if defined(__ARM_FEATURE_AES)
    features |= RMR_KERNEL_FEATURE_AES;
#endif
#if defined(__ARM_FEATURE_CRC32)
    features |= RMR_KERNEL_FEATURE_CRC32;
    features |= RMR_KERNEL_FEATURE_POPCNT;
#endif
#if defined(__POPCNT__)
    features |= RMR_KERNEL_FEATURE_POPCNT;
#endif
#if defined(__SSE4_2__)
    features |= RMR_KERNEL_FEATURE_SSE42;
#endif
#if defined(__AVX2__)
    features |= RMR_KERNEL_FEATURE_AVX2;
#endif
    if ((features & (RMR_KERNEL_FEATURE_NEON | RMR_KERNEL_FEATURE_SSE42 | RMR_KERNEL_FEATURE_AVX2)) != 0u) {
        features |= RMR_KERNEL_FEATURE_SIMD;
    }

    out_caps->signature = rmr_arch_tag() | rmr_os_tag();
    out_caps->pointer_bits = (uint32_t)(sizeof(void*) * 8u);
    out_caps->cache_line_bytes = 64u;
    out_caps->page_bytes = 4096u;
    out_caps->feature_mask = features;
    out_caps->register_width_bits = out_caps->pointer_bits;
    out_caps->pin_count_hint = 128u;
    out_caps->feature_bits_hi = 0u;

#if defined(_SC_PAGESIZE)
    long page = sysconf(_SC_PAGESIZE);
    if (page >= 1024 && page <= 65536) {
        out_caps->page_bytes = (uint32_t)page;
    }
#endif

    return RMR_KERNEL_OK;
}

int rmr_kernel_init(rmr_kernel_state_t* state, uint32_t seed) {
    if (!state) {
        return RMR_KERNEL_ERR_ARG;
    }
    state->initialized = 1u;
    state->seed = seed;
    state->crc = seed ^ 0x9E3779B9u;
    state->entropy = seed ^ 0x85EBCA6Bu;
    state->ingest_count = 0u;
    state->process_count = 0u;
    state->route_count = 0u;
    state->verify_count = 0u;
    state->audit_count = 0u;
    return RMR_KERNEL_OK;
}

int rmr_kernel_shutdown(rmr_kernel_state_t* state) {
    if (!state) {
        return RMR_KERNEL_ERR_ARG;
    }
    state->initialized = 0u;
    return RMR_KERNEL_OK;
}

int rmr_kernel_ingest(rmr_kernel_state_t* state, const uint8_t* data, uint32_t len, uint32_t* out_crc) {
    if (!state || !state->initialized || (!data && len != 0u)) {
        return RMR_KERNEL_ERR_STATE;
    }
    state->crc = rmr_xor_digest(data, len, state->crc);
    state->entropy = (state->entropy ^ state->crc) + (len * 17u);
    state->ingest_count++;
    if (out_crc) {
        *out_crc = state->crc;
    }
    return RMR_KERNEL_OK;
}

int rmr_kernel_process(rmr_kernel_state_t* state, int32_t a, int32_t b, uint32_t mode, int32_t* out_value) {
    if (!state || !state->initialized || !out_value) {
        return RMR_KERNEL_ERR_STATE;
    }
    uint32_t op = mode & 3u;
    int32_t v;
    if (op == 0u) {
        v = (a & ~((int32_t)mode)) | (b & (int32_t)mode);
    } else if (op == 1u) {
        v = a ^ ((b << 1) | ((uint32_t)b >> 31u));
    } else if (op == 2u) {
        v = a + b;
    } else {
        v = a - b;
    }
    state->process_count++;
    state->entropy ^= (uint32_t)v;
    *out_value = v;
    return RMR_KERNEL_OK;
}

int rmr_kernel_route(rmr_kernel_state_t* state, const rmr_kernel_route_input_t* in, rmr_kernel_route_output_t* out) {
    if (!state || !state->initialized || !in || !out) {
        return RMR_KERNEL_ERR_STATE;
    }
    int64_t io = (int64_t)(in->input_bytes + in->output_bytes);
    int64_t storage = (int64_t)(in->storage_read_bytes + in->storage_write_bytes);
    int64_t cpu = (int64_t)in->cpu_cycles;
    int64_t matrix_bias = in->m00 + in->m11 - in->m01 - in->m10;
    int64_t score = cpu + (storage >> 1) + io + matrix_bias;
    uint32_t route = (uint32_t)((score ^ (int64_t)state->entropy) & 0x3);

    out->route = route;
    out->score = (int32_t)score;
    state->route_count++;
    return RMR_KERNEL_OK;
}

int rmr_kernel_verify(rmr_kernel_state_t* state, const uint8_t* data, uint32_t len, uint32_t expected, uint32_t* out_value) {
    if (!state || !state->initialized || (!data && len != 0u)) {
        return RMR_KERNEL_ERR_STATE;
    }
    uint32_t value = rmr_xor_digest(data, len, 0u);
    state->verify_count++;
    if (out_value) {
        *out_value = value;
    }
    return (value == expected) ? RMR_KERNEL_OK : 1;
}

int rmr_kernel_audit(rmr_kernel_state_t* state, uint64_t* out_counters, uint32_t counters_len) {
    if (!state || !state->initialized || !out_counters || counters_len < 7u) {
        return RMR_KERNEL_ERR_STATE;
    }
    out_counters[0] = state->crc;
    out_counters[1] = state->entropy;
    out_counters[2] = state->ingest_count;
    out_counters[3] = state->process_count;
    out_counters[4] = state->route_count;
    out_counters[5] = state->verify_count;
    out_counters[6] = ++state->audit_count;
    return RMR_KERNEL_OK;
}
