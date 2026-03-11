#include "vectra_lowlevel_backend.h"

#if defined(__x86_64__)
#include <emmintrin.h>
#include <nmmintrin.h>
#endif

#include "rmr_lowlevel.h"

static uint32_t vectra_checksum32_x86_64(const uint8_t* data, size_t len, uint32_t seed) {
    return rmr_lowlevel_checksum32(data, len, seed);
}

#if defined(__x86_64__)
static uint32_t vectra_reduce_xor_x86_64(const uint8_t* data, size_t len) {
    __m128i acc = _mm_setzero_si128();
    size_t i = 0;
    for (; i + 16u <= len; i += 16u) {
        acc = _mm_xor_si128(acc, _mm_loadu_si128((const __m128i*)(data + i)));
    }

    uint8_t lanes[16];
    _mm_storeu_si128((__m128i*)lanes, acc);
    uint32_t out = 0u;
    for (size_t l = 0; l < 16u; ++l) out ^= lanes[l];
    for (; i < len; ++i) out ^= data[i];
    return out;
}

static uint32_t vectra_crc32c_x86_64(uint32_t initial, const uint8_t* data, size_t len) {
    uint64_t crc = initial;
    size_t i = 0;
    for (; i + 8u <= len; i += 8u) {
        uint64_t v;
        __builtin_memcpy(&v, data + i, sizeof(v));
        crc = _mm_crc32_u64(crc, v);
    }
    uint32_t crc32 = (uint32_t)crc;
    for (; i < len; ++i) crc32 = _mm_crc32_u8(crc32, data[i]);
    return crc32;
}
#else
static uint32_t vectra_reduce_xor_x86_64(const uint8_t* data, size_t len) { return rmr_lowlevel_reduce_xor(data, len); }
static uint32_t vectra_crc32c_x86_64(uint32_t initial, const uint8_t* data, size_t len) {
    uint32_t crc = initial;
    for (size_t i = 0; i < len; ++i) {
        crc ^= data[i];
        for (uint32_t b = 0; b < 8u; ++b) {
            const uint32_t mask = (uint32_t)-(int32_t)(crc & 1u);
            crc = (crc >> 1u) ^ (0x82F63B78u & mask);
        }
    }
    return crc;
}
#endif

int vectra_backend_x86_64_available(uint32_t simd_mask) {
    return (simd_mask & VECTRA_SIMD_SSE2) != 0u;
}

void vectra_backend_bind_x86_64(vectra_lowlevel_backend_vtable_t* out) {
    out->name = "x86_64-sse42";
    out->reduce_xor = vectra_reduce_xor_x86_64;
    out->checksum32 = vectra_checksum32_x86_64;
    out->crc32c = vectra_crc32c_x86_64;
}
