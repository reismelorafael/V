#include "rmr_ll_ops.h"

#if defined(_MSC_VER)
#include <intrin.h>
#endif

static uint32_t rmr_popcount32_fallback(uint32_t v) {
  uint32_t c = 0;
  while (v) {
    c += (v & 1u);
    v >>= 1;
  }
  return c;
}

static uint32_t rmr_popcount64_fallback(uint64_t v) {
  uint32_t c = 0;
  while (v) {
    c += (uint32_t)(v & 1u);
    v >>= 1;
  }
  return c;
}

uint64_t RmR_LL_ReadCycles(void) {
#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
  return (uint64_t)__rdtsc();
#elif defined(__x86_64__) || defined(__i386__)
  uint32_t lo = 0;
  uint32_t hi = 0;
  __asm__ __volatile__("lfence\nrdtsc" : "=a"(lo), "=d"(hi) :: "memory");
  return ((uint64_t)hi << 32) | (uint64_t)lo;
#elif defined(__aarch64__)
  uint64_t v = 0;
  __asm__ __volatile__("isb" ::: "memory");
  __asm__ __volatile__("mrs %0, cntvct_el0" : "=r"(v));
  return v;
#elif defined(__riscv)
  uint64_t v = 0;
  __asm__ __volatile__("fence iorw, iorw" ::: "memory");
  __asm__ __volatile__("rdcycle %0" : "=r"(v));
  return v;
#else
  return 0u;
#endif
}

void RmR_LL_FenceFull(void) {
#if defined(__x86_64__) || defined(__i386__)
  __asm__ __volatile__("mfence" ::: "memory");
#elif defined(__aarch64__)
  __asm__ __volatile__("dmb ish" ::: "memory");
#elif defined(__riscv)
  __asm__ __volatile__("fence iorw, iorw" ::: "memory");
#else
  __asm__ __volatile__("" ::: "memory");
#endif
}

void RmR_LL_FenceLoad(void) {
#if defined(__x86_64__) || defined(__i386__)
  __asm__ __volatile__("lfence" ::: "memory");
#elif defined(__aarch64__)
  __asm__ __volatile__("dmb ishld" ::: "memory");
#elif defined(__riscv)
  __asm__ __volatile__("fence ir, ir" ::: "memory");
#else
  __asm__ __volatile__("" ::: "memory");
#endif
}

void RmR_LL_FenceStore(void) {
#if defined(__x86_64__) || defined(__i386__)
  __asm__ __volatile__("sfence" ::: "memory");
#elif defined(__aarch64__)
  __asm__ __volatile__("dmb ishst" ::: "memory");
#elif defined(__riscv)
  __asm__ __volatile__("fence ow, ow" ::: "memory");
#else
  __asm__ __volatile__("" ::: "memory");
#endif
}

void RmR_LL_Pause(void) {
#if defined(__x86_64__) || defined(__i386__)
  __asm__ __volatile__("pause");
#elif defined(__aarch64__)
  __asm__ __volatile__("yield");
#elif defined(__riscv)
  __asm__ __volatile__("fence rw, rw" ::: "memory");
#else
  __asm__ __volatile__("" ::: "memory");
#endif
}

void RmR_LL_PrefetchRead(const void *ptr) {
#if defined(__GNUC__) || defined(__clang__)
  __builtin_prefetch(ptr, 0, 3);
#else
  (void)ptr;
#endif
}

void RmR_LL_PrefetchWrite(const void *ptr) {
#if defined(__GNUC__) || defined(__clang__)
  __builtin_prefetch(ptr, 1, 3);
#else
  (void)ptr;
#endif
}

uint32_t RmR_LL_BitScanForward32(uint32_t v) {
  if (v == 0u) return 32u;
#if defined(__GNUC__) || defined(__clang__)
  return (uint32_t)__builtin_ctz(v);
#else
  uint32_t i = 0;
  while (((v >> i) & 1u) == 0u) ++i;
  return i;
#endif
}

uint32_t RmR_LL_BitScanReverse32(uint32_t v) {
  if (v == 0u) return 32u;
#if defined(__GNUC__) || defined(__clang__)
  return 31u - (uint32_t)__builtin_clz(v);
#else
  uint32_t i = 31u;
  while (((v >> i) & 1u) == 0u) --i;
  return i;
#endif
}

uint32_t RmR_LL_Rotl32(uint32_t v, uint32_t shift) {
  shift &= 31u;
  return (v << shift) | (v >> ((32u - shift) & 31u));
}

uint32_t RmR_LL_Rotr32(uint32_t v, uint32_t shift) {
  shift &= 31u;
  return (v >> shift) | (v << ((32u - shift) & 31u));
}

uint64_t RmR_LL_Rotl64(uint64_t v, uint32_t shift) {
  shift &= 63u;
  return (v << shift) | (v >> ((64u - shift) & 63u));
}

uint64_t RmR_LL_Rotr64(uint64_t v, uint32_t shift) {
  shift &= 63u;
  return (v >> shift) | (v << ((64u - shift) & 63u));
}

uint32_t RmR_LL_PopCount32(uint32_t v) {
#if defined(__GNUC__) || defined(__clang__)
  return (uint32_t)__builtin_popcount(v);
#else
  return rmr_popcount32_fallback(v);
#endif
}

uint32_t RmR_LL_PopCount64(uint64_t v) {
#if defined(__GNUC__) || defined(__clang__)
  return (uint32_t)__builtin_popcountll(v);
#else
  return rmr_popcount64_fallback(v);
#endif
}

uint32_t RmR_LL_Clz32(uint32_t v) {
  if (v == 0u) return 32u;
#if defined(__GNUC__) || defined(__clang__)
  return (uint32_t)__builtin_clz(v);
#else
  return 31u - RmR_LL_BitScanReverse32(v);
#endif
}

uint32_t RmR_LL_Ctz32(uint32_t v) {
  return RmR_LL_BitScanForward32(v);
}

uint32_t RmR_LL_Clz64(uint64_t v) {
  if (v == 0u) return 64u;
#if defined(__GNUC__) || defined(__clang__)
  return (uint32_t)__builtin_clzll(v);
#else
  uint32_t c = 0;
  uint64_t m = (uint64_t)1 << 63;
  while ((v & m) == 0u) {
    ++c;
    m >>= 1;
  }
  return c;
#endif
}

uint32_t RmR_LL_Ctz64(uint64_t v) {
  if (v == 0u) return 64u;
#if defined(__GNUC__) || defined(__clang__)
  return (uint32_t)__builtin_ctzll(v);
#else
  uint32_t c = 0;
  while ((v & 1u) == 0u) {
    ++c;
    v >>= 1;
  }
  return c;
#endif
}

int RmR_LL_ReadAsmProbe(RmR_LL_AsmProbe *out) {
  if (!out) return -1;
  out->has_probe = 0u;
  out->reg_signature_0 = 0u;
  out->reg_signature_1 = 0u;
  out->reg_signature_2 = 0u;
  out->feature_bits_0 = 0u;
  out->feature_bits_1 = 0u;

#if defined(__x86_64__) || defined(__i386__)
  {
    uint32_t eax = 0u;
    uint32_t ebx = 0u;
    uint32_t ecx = 0u;
    uint32_t edx = 0u;
    __asm__ volatile ("cpuid" : "=a"(eax), "=b"(ebx), "=c"(ecx), "=d"(edx) : "a"(0u), "c"(0u));
    out->reg_signature_0 = ebx;
    out->reg_signature_1 = edx;
    out->reg_signature_2 = ecx;

    __asm__ volatile ("cpuid" : "=a"(eax), "=b"(ebx), "=c"(ecx), "=d"(edx) : "a"(1u), "c"(0u));
    out->feature_bits_0 = ecx;
    out->feature_bits_1 = edx;
    out->has_probe = 1u;
  }
#elif defined(__aarch64__)
  {
    uint64_t ctr = 0u;
    uint64_t dczid = 0u;
    __asm__ volatile ("mrs %0, ctr_el0" : "=r"(ctr));
    __asm__ volatile ("mrs %0, dczid_el0" : "=r"(dczid));
    out->reg_signature_0 = (uint32_t)(ctr & 0xFFFFFFFFu);
    out->reg_signature_1 = (uint32_t)((ctr >> 32) & 0xFFFFFFFFu);
    out->feature_bits_0 = (uint32_t)(dczid & 0xFFFFFFFFu);
    out->feature_bits_1 = (uint32_t)((dczid >> 32) & 0xFFFFFFFFu);
    out->has_probe = 1u;
  }
#elif defined(__riscv)
  {
    uintptr_t misa = 0u;
    __asm__ volatile ("csrr %0, misa" : "=r"(misa));
    out->reg_signature_0 = (uint32_t)misa;
    out->feature_bits_0 = (uint32_t)misa;
    out->has_probe = 1u;
  }
#endif
  return 0;
}
