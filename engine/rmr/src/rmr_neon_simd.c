/* ═══════════════════════════════════════════════════════════════
   rmr_neon_simd.c — RAFAELIA NEON/SIMD Baremetal Acceleration
   ψ→χ→ρ→Δ→Σ→Ω  |  R(t+1) = R(t) × Φ_ethica × (√3/2)^(πφ)
   ───────────────────────────────────────────────────────────────
   Estratégia:
   - ARM64 NEON: 128-bit vectorization via intrinsics
   - x86_64: SSE4.2 / AVX2 via __builtin / intrinsics
   - RISCV64: scalar fallback (V extension future)
   - Supera KVM em operações de: CRC32, XOR fold, memcpy alinhado,
     hash φ-step, parity 2D, popcount bulk
   ─────────────────────────────────────────────────────────────── */
#include "rmr_hw_detect.h"

#if defined(RMR_JNI_BUILD) && RMR_JNI_BUILD
#  include <stdlib.h>
#  include <string.h>
#  define rmr_malloc(sz) malloc(sz)
#  define rmr_free(p)    free(p)
#else
#  include "rmr_baremetal_compat.h"
#endif

/* ── Type definitions ── */
typedef unsigned char      u8;
typedef unsigned int       u32;
typedef unsigned long long u64;

/* ─────────────────────────────────────────────────────────────
   SECTION 1: ARM64 NEON bulk XOR fold (16 bytes/cycle)
   Benchmark target: > 20 GB/s on Cortex-A78
   ───────────────────────────────────────────────────────────── */
#if defined(__aarch64__)
#  if defined(__has_include)
#    if __has_include(<arm_neon.h>)
#      include <arm_neon.h>
#    else
#      error "__aarch64__ build requires <arm_neon.h>"
#    endif
#  else
#    include <arm_neon.h>
#  endif

u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    if (!data || len == 0) return 0u;
    uint32x4_t acc = vdupq_n_u32(0u);
    u32 i = 0;
    /* 16-byte aligned bulk */
    for (; i + 16u <= len; i += 16u) {
        uint8x16_t v = vld1q_u8(data + i);
        acc = veorq_u32(acc, vreinterpretq_u32_u8(v));
    }
    /* horizontal XOR of 4 lanes */
    u32 result = vgetq_lane_u32(acc, 0) ^ vgetq_lane_u32(acc, 1)
               ^ vgetq_lane_u32(acc, 2) ^ vgetq_lane_u32(acc, 3);
    /* tail bytes */
    for (; i < len; ++i) result ^= (u32)data[i];
    return result;
}

/* NEON bulk memcpy — 4× unrolled 16-byte loads */
void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    u32 i = 0;
    for (; i + 64u <= len; i += 64u) {
        uint8x16x4_t v = vld1q_u8_x4(src + i);
        vst1q_u8_x4(dst + i, v);
    }
    for (; i + 16u <= len; i += 16u) {
        vst1q_u8(dst + i, vld1q_u8(src + i));
    }
    for (; i < len; ++i) dst[i] = src[i];
}

/* NEON CRC32C hardware path (ARMv8.0-CRC) */
#if defined(__ARM_FEATURE_CRC32)
#  if defined(__has_include)
#    if __has_include(<arm_acle.h>)
#      include <arm_acle.h>
#    else
#      error "__ARM_FEATURE_CRC32 build requires <arm_acle.h>"
#    endif
#  else
#    include <arm_acle.h>
#  endif
u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed, i = 0;
    for (; i + 8u <= len; i += 8u) {
        u64 word;
        __builtin_memcpy(&word, data + i, 8);
        crc = __crc32cd(crc, word);
    }
    for (; i + 4u <= len; i += 4u) {
        u32 word;
        __builtin_memcpy(&word, data + i, 4);
        crc = __crc32cw(crc, word);
    }
    for (; i < len; ++i) crc = __crc32cb(crc, data[i]);
    return crc;
}
#else
/* SW fallback */
u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed;
    for (u32 i = 0; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0; b < 8u; ++b) {
            u32 mask = (u32)(-(int)(crc & 1u));
            crc = (crc >> 1u) ^ (0x82F63B78u & mask);
        }
    }
    return crc;
}
#endif

/* NEON φ-step: R(t+1) = R(t) × PHI64 vectorized */
void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    /* PHI32 = 0x9E3779B9 */
    uint32x4_t phi = vdupq_n_u32(0x9E3779B9u);
    u32 i = 0;
    for (; i + 4u <= count; i += 4u) {
        uint32x4_t s = vld1q_u32(states + i);
        s = vmulq_u32(s, phi);
        /* ensure non-zero: vceqq_u32 → select 1 if zero */
        uint32x4_t zero_mask = vceqq_u32(s, vdupq_n_u32(0u));
        s = vorrq_u32(s, vandq_u32(zero_mask, vdupq_n_u32(1u)));
        vst1q_u32(states + i, s);
    }
    for (; i < count; ++i) {
        states[i] = states[i] * 0x9E3779B9u;
        if (!states[i]) states[i] = 1u;
    }
}

/* NEON popcount bulk (64-bit input via vcntq_u8) */
u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    uint64x2_t acc = vdupq_n_u64(0);
    u32 i = 0;
    for (; i + 4u <= count; i += 4u) {
        uint8x16_t v = vld1q_u8((const u8 *)(data + i));
        uint8x16_t bits = vcntq_u8(v);
        acc = vpadalq_u32(acc, vpaddlq_u16(vpaddlq_u8(bits)));
    }
    u64 total = vgetq_lane_u64(acc, 0) + vgetq_lane_u64(acc, 1);
    for (; i < count; ++i) {
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        total += (u64)((v * 0x01010101u) >> 24u);
    }
    return (u32)total;
}

#elif defined(__x86_64__) || defined(__i386__)
/* ─────────────────────────────────────────────────────────────
   SECTION 2: x86_64 SSE4.2 / POPCNT paths
   ───────────────────────────────────────────────────────────── */
#if defined(__has_include)
#  if __has_include(<immintrin.h>)
#    include <immintrin.h>
#  else
#    error "x86 SIMD build requires <immintrin.h>"
#  endif
#else
#  include <immintrin.h>
#endif

u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    if (!data || len == 0) return 0u;
    u32 result = 0u, i = 0;
#if defined(__SSE2__)
    __m128i acc = _mm_setzero_si128();
    for (; i + 16u <= len; i += 16u) {
        __m128i v = _mm_loadu_si128((const __m128i *)(data + i));
        acc = _mm_xor_si128(acc, v);
    }
    /* horizontal XOR 128→32 */
    u32 t[4];
    _mm_storeu_si128((__m128i *)t, acc);
    result = t[0] ^ t[1] ^ t[2] ^ t[3];
#endif
    for (; i < len; ++i) result ^= (u32)data[i];
    return result;
}

void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    __builtin_memcpy(dst, src, len);
}

u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
#if defined(__SSE4_2__)
    u32 crc = seed, i = 0;
    for (; i + 8u <= len; i += 8u) {
        u64 w; __builtin_memcpy(&w, data + i, 8);
        crc = (u32)_mm_crc32_u64(crc, w);
    }
    for (; i < len; ++i) crc = _mm_crc32_u8(crc, data[i]);
    return crc;
#else
    u32 crc = seed;
    for (u32 i = 0; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0; b < 8u; ++b) {
            u32 m = (u32)(-(int)(crc & 1u));
            crc = (crc >> 1u) ^ (0x82F63B78u & m);
        }
    }
    return crc;
#endif
}

void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    for (u32 i = 0; i < count; ++i) {
        states[i] *= 0x9E3779B9u;
        if (!states[i]) states[i] = 1u;
    }
}

u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    u64 total = 0;
    for (u32 i = 0; i < count; ++i) {
#if defined(__POPCNT__)
        total += (u64)__builtin_popcount(data[i]);
#else
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        total += (u64)((v * 0x01010101u) >> 24u);
#endif
    }
    return (u32)total;
}

#else
/* ── Generic scalar fallback ── */
u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    u32 r = 0;
    for (u32 i = 0; i < len; ++i) r ^= (u32)data[i];
    return r;
}
void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    for (u32 i = 0; i < len; ++i) dst[i] = src[i];
}
u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed;
    for (u32 i = 0; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0; b < 8u; ++b) {
            u32 m = (u32)(-(int)(crc & 1u));
            crc = (crc >> 1u) ^ (0x82F63B78u & m);
        }
    }
    return crc;
}
void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    for (u32 i = 0; i < count; ++i) {
        states[i] *= 0x9E3779B9u;
        if (!states[i]) states[i] = 1u;
    }
}
u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    u64 t = 0;
    for (u32 i = 0; i < count; ++i) {
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        t += (u64)((v * 0x01010101u) >> 24u);
    }
    return (u32)t;
}
#endif
