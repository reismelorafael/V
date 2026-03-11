#include "vectra_lowlevel_backend.h"

#if defined(__aarch64__)
#include <arm_acle.h>
#include <arm_neon.h>
#endif

#include "rmr_lowlevel.h"

static uint32_t vectra_checksum32_arm64(const uint8_t* data, size_t len, uint32_t seed) {
    return rmr_lowlevel_checksum32(data, len, seed);
}

#if defined(__aarch64__)
static uint32_t vectra_reduce_xor_arm64(const uint8_t* data, size_t len) {
    uint8x16_t accv = vdupq_n_u8(0);
    size_t i = 0;
    for (; i + 16u <= len; i += 16u) {
        accv = veorq_u8(accv, vld1q_u8(data + i));
    }

    uint8_t lane_acc = vgetq_lane_u8(accv, 0) ^ vgetq_lane_u8(accv, 1) ^ vgetq_lane_u8(accv, 2) ^ vgetq_lane_u8(accv, 3)
                     ^ vgetq_lane_u8(accv, 4) ^ vgetq_lane_u8(accv, 5) ^ vgetq_lane_u8(accv, 6) ^ vgetq_lane_u8(accv, 7)
                     ^ vgetq_lane_u8(accv, 8) ^ vgetq_lane_u8(accv, 9) ^ vgetq_lane_u8(accv, 10) ^ vgetq_lane_u8(accv, 11)
                     ^ vgetq_lane_u8(accv, 12) ^ vgetq_lane_u8(accv, 13) ^ vgetq_lane_u8(accv, 14) ^ vgetq_lane_u8(accv, 15);

    uint32_t out = (uint32_t)lane_acc;
    for (; i < len; ++i) out ^= data[i];
    return out;
}

static uint32_t vectra_crc32c_arm64(uint32_t initial, const uint8_t* data, size_t len) {
    uint32_t crc = initial;
    size_t i = 0;
    for (; i + 8u <= len; i += 8u) {
        uint64_t v;
        __builtin_memcpy(&v, data + i, sizeof(v));
        crc = __crc32cd(crc, v);
    }
    for (; i < len; ++i) crc = __crc32cb(crc, data[i]);
    return crc;
}
#else
static uint32_t vectra_reduce_xor_arm64(const uint8_t* data, size_t len) { return rmr_lowlevel_reduce_xor(data, len); }
static uint32_t vectra_crc32c_arm64(uint32_t initial, const uint8_t* data, size_t len) {
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

int vectra_backend_arm64_available(uint32_t simd_mask) {
    return (simd_mask & VECTRA_SIMD_NEON) != 0u;
}

void vectra_backend_bind_arm64(vectra_lowlevel_backend_vtable_t* out) {
    out->name = "arm64-neon-crc";
    out->reduce_xor = vectra_reduce_xor_arm64;
    out->checksum32 = vectra_checksum32_arm64;
    out->crc32c = vectra_crc32c_arm64;
}
