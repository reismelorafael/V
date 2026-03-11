#include "vectra_lowlevel_backend.h"

#include "rmr_lowlevel.h"

static uint32_t vectra_reduce_xor_riscv64(const uint8_t* data, size_t len) {
    return rmr_lowlevel_reduce_xor(data, len);
}

static uint32_t vectra_checksum32_riscv64(const uint8_t* data, size_t len, uint32_t seed) {
    return rmr_lowlevel_checksum32(data, len, seed);
}

static uint32_t vectra_crc32c_riscv64(uint32_t initial, const uint8_t* data, size_t len) {
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

int vectra_backend_riscv64_available(uint32_t simd_mask) {
    return (simd_mask & VECTRA_SIMD_RVV) != 0u;
}

void vectra_backend_bind_riscv64(vectra_lowlevel_backend_vtable_t* out) {
    out->name = "riscv64-rvv-or-c";
    out->reduce_xor = vectra_reduce_xor_riscv64;
    out->checksum32 = vectra_checksum32_riscv64;
    out->crc32c = vectra_crc32c_riscv64;
}
