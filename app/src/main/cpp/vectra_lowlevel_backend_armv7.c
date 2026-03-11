#include "vectra_lowlevel_backend.h"

#if defined(__arm__)
#include <arm_neon.h>
#endif

#include "rmr_lowlevel.h"

static uint32_t vectra_checksum32_armv7(const uint8_t* data, size_t len, uint32_t seed) {
    return rmr_lowlevel_checksum32(data, len, seed);
}

#if defined(__arm__)
static uint32_t vectra_reduce_xor_armv7(const uint8_t* data, size_t len) {
    uint8x16_t accv = vdupq_n_u8(0);
    size_t i = 0;
    for (; i + 16u <= len; i += 16u) {
        accv = veorq_u8(accv, vld1q_u8(data + i));
    }
    uint8x8_t low = vget_low_u8(accv);
    uint8x8_t high = vget_high_u8(accv);
    uint8x8_t x = veor_u8(low, high);
    uint8_t lane_acc = vget_lane_u8(x, 0) ^ vget_lane_u8(x, 1) ^ vget_lane_u8(x, 2) ^ vget_lane_u8(x, 3)
                     ^ vget_lane_u8(x, 4) ^ vget_lane_u8(x, 5) ^ vget_lane_u8(x, 6) ^ vget_lane_u8(x, 7);
    uint32_t out = (uint32_t)lane_acc;
    for (; i < len; ++i) out ^= data[i];
    return out;
}
#else
static uint32_t vectra_reduce_xor_armv7(const uint8_t* data, size_t len) { return rmr_lowlevel_reduce_xor(data, len); }
#endif

static uint32_t vectra_crc32c_armv7(uint32_t initial, const uint8_t* data, size_t len) {
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

int vectra_backend_armv7_available(uint32_t simd_mask) {
    return (simd_mask & VECTRA_SIMD_NEON) != 0u;
}

void vectra_backend_bind_armv7(vectra_lowlevel_backend_vtable_t* out) {
    out->name = "armv7-neon";
    out->reduce_xor = vectra_reduce_xor_armv7;
    out->checksum32 = vectra_checksum32_armv7;
    out->crc32c = vectra_crc32c_armv7;
}
