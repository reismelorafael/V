#include "vectra_lowlevel_backend.h"

#if defined(__i386__)
#include <emmintrin.h>
#include <nmmintrin.h>
#endif

#include "rmr_lowlevel.h"

static uint32_t vectra_checksum32_x86(const uint8_t* data, size_t len, uint32_t seed) {
    return rmr_lowlevel_checksum32(data, len, seed);
}

#if defined(__i386__)
static uint32_t vectra_reduce_xor_x86(const uint8_t* data, size_t len) {
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

static uint32_t vectra_crc32c_x86(uint32_t initial, const uint8_t* data, size_t len) {
    uint32_t crc = initial;
    size_t i = 0;
    for (; i + 4u <= len; i += 4u) {
        uint32_t v;
        __builtin_memcpy(&v, data + i, sizeof(v));
        crc = _mm_crc32_u32(crc, v);
    }
    for (; i < len; ++i) crc = _mm_crc32_u8(crc, data[i]);
    return crc;
}
#else
static uint32_t vectra_reduce_xor_x86(const uint8_t* data, size_t len) { return rmr_lowlevel_reduce_xor(data, len); }
static uint32_t vectra_crc32c_x86(uint32_t initial, const uint8_t* data, size_t len) {
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

int vectra_backend_x86_available(uint32_t simd_mask) {
    return (simd_mask & VECTRA_SIMD_SSE2) != 0u;
}

void vectra_backend_bind_x86(vectra_lowlevel_backend_vtable_t* out) {
    out->name = "x86-sse42";
    out->reduce_xor = vectra_reduce_xor_x86;
    out->checksum32 = vectra_checksum32_x86;
    out->crc32c = vectra_crc32c_x86;
}
