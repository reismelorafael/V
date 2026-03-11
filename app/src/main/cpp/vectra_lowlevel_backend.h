#ifndef VECTRA_LOWLEVEL_BACKEND_H
#define VECTRA_LOWLEVEL_BACKEND_H

#include <stddef.h>
#include <stdint.h>

typedef uint32_t (*vectra_reduce_xor_fn)(const uint8_t* data, size_t len);
typedef uint32_t (*vectra_checksum32_fn)(const uint8_t* data, size_t len, uint32_t seed);
typedef uint32_t (*vectra_crc32c_fn)(uint32_t initial, const uint8_t* data, size_t len);

typedef struct vectra_lowlevel_backend_vtable {
    const char* name;
    vectra_reduce_xor_fn reduce_xor;
    vectra_checksum32_fn checksum32;
    vectra_crc32c_fn crc32c;
} vectra_lowlevel_backend_vtable_t;

enum {
    VECTRA_SIMD_NEON = 1u << 0,
    VECTRA_SIMD_SSE2 = 1u << 1,
    VECTRA_SIMD_SSE42 = 1u << 2,
    VECTRA_SIMD_AVX = 1u << 3,
    VECTRA_SIMD_RVV = 1u << 4
};

void vectra_backend_bind_fallback(vectra_lowlevel_backend_vtable_t* out);
int vectra_backend_arm64_available(uint32_t simd_mask);
void vectra_backend_bind_arm64(vectra_lowlevel_backend_vtable_t* out);
int vectra_backend_armv7_available(uint32_t simd_mask);
void vectra_backend_bind_armv7(vectra_lowlevel_backend_vtable_t* out);
int vectra_backend_x86_64_available(uint32_t simd_mask);
void vectra_backend_bind_x86_64(vectra_lowlevel_backend_vtable_t* out);
int vectra_backend_x86_available(uint32_t simd_mask);
void vectra_backend_bind_x86(vectra_lowlevel_backend_vtable_t* out);
int vectra_backend_riscv64_available(uint32_t simd_mask);
void vectra_backend_bind_riscv64(vectra_lowlevel_backend_vtable_t* out);

#endif
