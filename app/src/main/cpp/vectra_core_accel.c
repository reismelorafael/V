#include <jni.h>
#include <stdint.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdatomic.h>
#include "rmr_unified_kernel.h"

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#define VECTRA_NATIVE_OK 0x56414343

#define VECTRA_ARCH_ARM64 0x0100
#define VECTRA_ARCH_ARM32 0x0200
#define VECTRA_ARCH_X64   0x0300
#define VECTRA_ARCH_X86   0x0400
#define VECTRA_ARCH_RV64  0x0500
#define VECTRA_ARCH_RV32  0x0600
#define VECTRA_ARCH_UNKNOWN 0x0000

#define VECTRA_OS_ANDROID 0x0010
#define VECTRA_OS_LINUX   0x0020
#define VECTRA_OS_UNKNOWN 0x0000

#define VECTRA_FEATURE_NEON   (1u << 0)
#define VECTRA_FEATURE_AES    (1u << 1)
#define VECTRA_FEATURE_CRC32  (1u << 2)
#define VECTRA_FEATURE_POPCNT (1u << 3)
#define VECTRA_FEATURE_SSE42  (1u << 4)
#define VECTRA_FEATURE_AVX2   (1u << 5)
#define VECTRA_FEATURE_SIMD   (1u << 6)

typedef struct {
    uint32_t signature;
    uint32_t pointer_bits;
    uint32_t cache_line_bytes;
    uint32_t page_bytes;
    uint32_t feature_mask;
} vectra_hw_contract_t;

static vectra_hw_contract_t g_hw_contract;
static pthread_once_t g_hw_contract_once = PTHREAD_ONCE_INIT;

static rmr_kernel_state_t g_unified_state;
static pthread_mutex_t g_unified_lock = PTHREAD_MUTEX_INITIALIZER;

#define VECTRA_ARENA_CAPACITY_BYTES (64u * 1024u * 1024u)
#define VECTRA_ARENA_MAX_SLOTS 4096u
#define VECTRA_ARENA_HANDLE_INDEX_BITS 13u
#define VECTRA_ARENA_HANDLE_INDEX_MASK ((1u << VECTRA_ARENA_HANDLE_INDEX_BITS) - 1u)
#define VECTRA_ARENA_HANDLE_GEN_SHIFT VECTRA_ARENA_HANDLE_INDEX_BITS
#define VECTRA_ARENA_HANDLE_GEN_MASK ((1u << (31u - VECTRA_ARENA_HANDLE_GEN_SHIFT)) - 1u)

#define VECTRA_ARENA_OK 0
#define VECTRA_ARENA_ERR_INVALID_ARG -1
#define VECTRA_ARENA_ERR_NO_MEMORY -2
#define VECTRA_ARENA_ERR_OUT_OF_RANGE -3
#define VECTRA_ARENA_ERR_BAD_HANDLE -4
#define VECTRA_ARENA_ERR_INTERNAL -5

typedef struct {
    uint32_t offset;
    uint32_t size;
    uint32_t generation;
    uint8_t in_use;
} vectra_arena_slot_t;

static uint8_t* g_arena_base = NULL;
static uint32_t g_arena_capacity = 0;
static vectra_arena_slot_t g_arena_slots[VECTRA_ARENA_MAX_SLOTS];
static pthread_mutex_t g_arena_lock = PTHREAD_MUTEX_INITIALIZER;
static atomic_uint g_arena_active_slots = 0;

static inline void vectra_kernel_copy_forward(uint8_t* dst, const uint8_t* src, uint32_t len) {
#if defined(__x86_64__) || defined(__i386__)
    if (len != 0u) {
        __asm__ volatile("rep movsb"
                         : "+D"(dst), "+S"(src), "+c"(len)
                         :
                         : "memory");
    }
#else
    uint32_t i = 0u;
    uint32_t end = len & ~31u;
    while (i < end) {
        dst[i] = src[i];
        dst[i + 1u] = src[i + 1u];
        dst[i + 2u] = src[i + 2u];
        dst[i + 3u] = src[i + 3u];
        dst[i + 4u] = src[i + 4u];
        dst[i + 5u] = src[i + 5u];
        dst[i + 6u] = src[i + 6u];
        dst[i + 7u] = src[i + 7u];
        dst[i + 8u] = src[i + 8u];
        dst[i + 9u] = src[i + 9u];
        dst[i + 10u] = src[i + 10u];
        dst[i + 11u] = src[i + 11u];
        dst[i + 12u] = src[i + 12u];
        dst[i + 13u] = src[i + 13u];
        dst[i + 14u] = src[i + 14u];
        dst[i + 15u] = src[i + 15u];
        dst[i + 16u] = src[i + 16u];
        dst[i + 17u] = src[i + 17u];
        dst[i + 18u] = src[i + 18u];
        dst[i + 19u] = src[i + 19u];
        dst[i + 20u] = src[i + 20u];
        dst[i + 21u] = src[i + 21u];
        dst[i + 22u] = src[i + 22u];
        dst[i + 23u] = src[i + 23u];
        dst[i + 24u] = src[i + 24u];
        dst[i + 25u] = src[i + 25u];
        dst[i + 26u] = src[i + 26u];
        dst[i + 27u] = src[i + 27u];
        dst[i + 28u] = src[i + 28u];
        dst[i + 29u] = src[i + 29u];
        dst[i + 30u] = src[i + 30u];
        dst[i + 31u] = src[i + 31u];
        i += 32u;
    }
    while (i < len) {
        dst[i] = src[i];
        i++;
    }
#endif
}

static inline void vectra_kernel_copy_backward(uint8_t* dst, const uint8_t* src, uint32_t len) {
    while (len > 0u) {
        len--;
        dst[len] = src[len];
    }
}

static inline void vectra_kernel_move(uint8_t* dst, const uint8_t* src, uint32_t len) {
    if (len == 0u || dst == src) {
        return;
    }
    if (dst > src && dst < src + len) {
        vectra_kernel_copy_backward(dst, src, len);
        return;
    }
    vectra_kernel_copy_forward(dst, src, len);
}

static inline void vectra_kernel_fill(uint8_t* dst, uint8_t value, uint32_t len) {
#if defined(__x86_64__) || defined(__i386__)
    if (len != 0u) {
        __asm__ volatile("rep stosb"
                         : "+D"(dst), "+c"(len)
                         : "a"(value)
                         : "memory");
    }
#else
    uint32_t i = 0u;
    uint32_t end = len & ~31u;
    while (i < end) {
        dst[i] = value;
        dst[i + 1u] = value;
        dst[i + 2u] = value;
        dst[i + 3u] = value;
        dst[i + 4u] = value;
        dst[i + 5u] = value;
        dst[i + 6u] = value;
        dst[i + 7u] = value;
        dst[i + 8u] = value;
        dst[i + 9u] = value;
        dst[i + 10u] = value;
        dst[i + 11u] = value;
        dst[i + 12u] = value;
        dst[i + 13u] = value;
        dst[i + 14u] = value;
        dst[i + 15u] = value;
        dst[i + 16u] = value;
        dst[i + 17u] = value;
        dst[i + 18u] = value;
        dst[i + 19u] = value;
        dst[i + 20u] = value;
        dst[i + 21u] = value;
        dst[i + 22u] = value;
        dst[i + 23u] = value;
        dst[i + 24u] = value;
        dst[i + 25u] = value;
        dst[i + 26u] = value;
        dst[i + 27u] = value;
        dst[i + 28u] = value;
        dst[i + 29u] = value;
        dst[i + 30u] = value;
        dst[i + 31u] = value;
        i += 32u;
    }
    while (i < len) {
        dst[i] = value;
        i++;
    }
#endif
}

static int vectra_arena_decode_handle(jint handle, uint32_t* slot_index, uint32_t* generation) {
    if (handle <= 0 || !slot_index || !generation) {
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    uint32_t raw = (uint32_t)handle;
    uint32_t index = raw & VECTRA_ARENA_HANDLE_INDEX_MASK;
    if (index == 0u || index > VECTRA_ARENA_MAX_SLOTS) {
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    uint32_t gen = (raw >> VECTRA_ARENA_HANDLE_GEN_SHIFT) & VECTRA_ARENA_HANDLE_GEN_MASK;
    if (gen == 0u) {
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    *slot_index = index - 1u;
    *generation = gen;
    return VECTRA_ARENA_OK;
}

static jint vectra_arena_make_handle(uint32_t slot_index, uint32_t generation) {
    uint32_t safe_gen = generation & VECTRA_ARENA_HANDLE_GEN_MASK;
    if (safe_gen == 0u) {
        safe_gen = 1u;
    }
    uint32_t raw = (safe_gen << VECTRA_ARENA_HANDLE_GEN_SHIFT) | (slot_index + 1u);
    return (jint)raw;
}

static int vectra_arena_ensure_initialized(void) {
    if (g_arena_base) {
        return VECTRA_ARENA_OK;
    }

    uint8_t* base = (uint8_t*)malloc((size_t)VECTRA_ARENA_CAPACITY_BYTES);
    if (!base) {
        return VECTRA_ARENA_ERR_NO_MEMORY;
    }

    g_arena_base = base;
    g_arena_capacity = VECTRA_ARENA_CAPACITY_BYTES;
    return VECTRA_ARENA_OK;
}

static int vectra_arena_find_region(uint32_t bytes, uint32_t* out_offset) {
    if (!out_offset || bytes == 0u || bytes > g_arena_capacity) {
        return VECTRA_ARENA_ERR_INVALID_ARG;
    }

    uint32_t cursor = 0u;
    while (cursor <= g_arena_capacity - bytes) {
        int next = -1;
        uint32_t next_offset = g_arena_capacity;
        for (uint32_t i = 0; i < VECTRA_ARENA_MAX_SLOTS; i++) {
            if (!g_arena_slots[i].in_use) {
                continue;
            }
            uint32_t off = g_arena_slots[i].offset;
            if (off >= cursor && off < next_offset) {
                next = (int)i;
                next_offset = off;
            }
        }

        if (next < 0) {
            *out_offset = cursor;
            return VECTRA_ARENA_OK;
        }

        if (next_offset - cursor >= bytes) {
            *out_offset = cursor;
            return VECTRA_ARENA_OK;
        }

        uint32_t end = g_arena_slots[next].offset + g_arena_slots[next].size;
        if (end < g_arena_slots[next].offset || end > g_arena_capacity || end <= cursor) {
            return VECTRA_ARENA_ERR_INTERNAL;
        }
        cursor = end;
    }

    return VECTRA_ARENA_ERR_NO_MEMORY;
}

static int vectra_arena_get_slot_for_range(jint handle, jint offset, jint length,
                                           vectra_arena_slot_t** out_slot,
                                           uint8_t** out_ptr) {
    if (!out_slot || !out_ptr || offset < 0 || length < 0) {
        return VECTRA_ARENA_ERR_INVALID_ARG;
    }

    if (!g_arena_base || g_arena_capacity == 0u) {
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    uint32_t slot_index = 0u;
    uint32_t generation = 0u;
    int rc = vectra_arena_decode_handle(handle, &slot_index, &generation);
    if (rc != VECTRA_ARENA_OK) {
        return rc;
    }

    vectra_arena_slot_t* slot = &g_arena_slots[slot_index];
    if (!slot->in_use || slot->generation != generation) {
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    uint32_t uoffset = (uint32_t)offset;
    uint32_t ulength = (uint32_t)length;
    if (uoffset > slot->size) {
        return VECTRA_ARENA_ERR_OUT_OF_RANGE;
    }
    if (ulength > (slot->size - uoffset)) {
        return VECTRA_ARENA_ERR_OUT_OF_RANGE;
    }

    uint32_t absolute = slot->offset + uoffset;
    if (absolute > g_arena_capacity || ulength > (g_arena_capacity - absolute)) {
        return VECTRA_ARENA_ERR_OUT_OF_RANGE;
    }

    *out_slot = slot;
    *out_ptr = g_arena_base + absolute;
    return VECTRA_ARENA_OK;
}

static uint32_t vectra_arch_tag(void) {
#if defined(__aarch64__)
    return VECTRA_ARCH_ARM64;
#elif defined(__arm__)
    return VECTRA_ARCH_ARM32;
#elif defined(__x86_64__)
    return VECTRA_ARCH_X64;
#elif defined(__i386__)
    return VECTRA_ARCH_X86;
#elif defined(__riscv) && (__riscv_xlen == 64)
    return VECTRA_ARCH_RV64;
#elif defined(__riscv) && (__riscv_xlen == 32)
    return VECTRA_ARCH_RV32;
#else
    return VECTRA_ARCH_UNKNOWN;
#endif
}

static uint32_t vectra_os_tag(void) {
#if defined(__ANDROID__)
    return VECTRA_OS_ANDROID;
#elif defined(__linux__)
    return VECTRA_OS_LINUX;
#else
    return VECTRA_OS_UNKNOWN;
#endif
}

static uint32_t vectra_cacheline_hint(uint32_t arch_tag) {
    if (arch_tag == VECTRA_ARCH_UNKNOWN) {
        return 64u;
    }
    return 64u;
}

static uint32_t vectra_page_hint(uint32_t arch_tag) {
    (void)arch_tag;
    return 4096u;
}

#if defined(__i386__) || defined(__x86_64__)
static void vectra_cpuid(uint32_t leaf, uint32_t subleaf, uint32_t* eax, uint32_t* ebx, uint32_t* ecx, uint32_t* edx) {
#if defined(__i386__) && defined(__PIC__)
    __asm__ volatile (
        "mov %%ebx, %%edi\n"
        "cpuid\n"
        "xchg %%edi, %%ebx\n"
        : "=a"(*eax), "=D"(*ebx), "=c"(*ecx), "=d"(*edx)
        : "a"(leaf), "c"(subleaf)
        : "cc"
    );
#else
    __asm__ volatile (
        "cpuid"
        : "=a"(*eax), "=b"(*ebx), "=c"(*ecx), "=d"(*edx)
        : "a"(leaf), "c"(subleaf)
        : "cc"
    );
#endif
}
#endif

static uint32_t vectra_page_bytes(uint32_t arch_tag) {
#if defined(_SC_PAGESIZE)
    long page = sysconf(_SC_PAGESIZE);
    if (page >= 1024 && page <= 65536) {
        return (uint32_t)page;
    }
#endif
    return vectra_page_hint(arch_tag);
}

static uint32_t vectra_cache_line_bytes(uint32_t arch_tag) {
#if defined(_SC_LEVEL1_DCACHE_LINESIZE)
    long line = sysconf(_SC_LEVEL1_DCACHE_LINESIZE);
    if (line >= 16 && line <= 512) {
        return (uint32_t)line;
    }
#endif
#if defined(__i386__) || defined(__x86_64__)
    uint32_t eax;
    uint32_t ebx;
    uint32_t ecx;
    uint32_t edx;
    vectra_cpuid(0x80000006u, 0u, &eax, &ebx, &ecx, &edx);
    uint32_t cpuid_line = ecx & 0xFFu;
    if (cpuid_line >= 16u && cpuid_line <= 512u) {
        return cpuid_line;
    }
#endif
    return vectra_cacheline_hint(arch_tag);
}

static uint32_t vectra_feature_mask(void) {
    uint32_t mask = 0;

#if defined(__ARM_NEON)
    mask |= VECTRA_FEATURE_NEON;
#endif
#if defined(__ARM_FEATURE_AES)
    mask |= VECTRA_FEATURE_AES;
#endif
#if defined(__ARM_FEATURE_CRC32)
    mask |= VECTRA_FEATURE_CRC32;
    mask |= VECTRA_FEATURE_POPCNT;
#endif
#if defined(__POPCNT__)
    mask |= VECTRA_FEATURE_POPCNT;
#endif

#if defined(__i386__) || defined(__x86_64__)
    uint32_t eax;
    uint32_t ebx;
    uint32_t ecx;
    uint32_t edx;
    vectra_cpuid(1u, 0u, &eax, &ebx, &ecx, &edx);

    if ((ecx & (1u << 20)) != 0u) {
        mask |= VECTRA_FEATURE_SSE42;
    }
    if ((ecx & (1u << 23)) != 0u) {
        mask |= VECTRA_FEATURE_POPCNT;
    }
    if ((ecx & (1u << 25)) != 0u) {
        mask |= VECTRA_FEATURE_AES;
    }

    vectra_cpuid(7u, 0u, &eax, &ebx, &ecx, &edx);
    if ((ebx & (1u << 5)) != 0u) {
        mask |= VECTRA_FEATURE_AVX2;
    }
#endif

    if ((mask & (VECTRA_FEATURE_NEON | VECTRA_FEATURE_SSE42 | VECTRA_FEATURE_AVX2)) != 0u) {
        mask |= VECTRA_FEATURE_SIMD;
    }

    return mask;
}

static void vectra_hw_contract_init(void) {
    rmr_kernel_capabilities_t caps;
    if (rmr_kernel_autodetect(&caps) != RMR_KERNEL_OK) {
        uint32_t arch = vectra_arch_tag();
        g_hw_contract.signature = arch | vectra_os_tag();
        g_hw_contract.pointer_bits = (uint32_t)(sizeof(void*) * 8u);
        g_hw_contract.cache_line_bytes = vectra_cache_line_bytes(arch);
        g_hw_contract.page_bytes = vectra_page_bytes(arch);
        g_hw_contract.feature_mask = vectra_feature_mask();
        return;
    }
    g_hw_contract.signature = caps.signature;
    g_hw_contract.pointer_bits = caps.pointer_bits;
    g_hw_contract.cache_line_bytes = caps.cache_line_bytes;
    g_hw_contract.page_bytes = caps.page_bytes;
    g_hw_contract.feature_mask = caps.feature_mask;
}

static const vectra_hw_contract_t* vectra_hw_contract_get(void) {
    pthread_once(&g_hw_contract_once, vectra_hw_contract_init);
    return &g_hw_contract;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeInit(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    pthread_mutex_lock(&g_unified_lock);
    (void)rmr_kernel_init(&g_unified_state, 0x56454354u);
    pthread_mutex_unlock(&g_unified_lock);
    return VECTRA_NATIVE_OK;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCopyBytes(JNIEnv* env, jclass clazz,
                                                        jbyteArray src, jint srcOffset,
                                                        jbyteArray dst, jint dstOffset,
                                                        jint length) {
    (void)clazz;
    if (!src || !dst || length <= 0 || srcOffset < 0 || dstOffset < 0) return -1;

    jsize srcLen = (*env)->GetArrayLength(env, src);
    jsize dstLen = (*env)->GetArrayLength(env, dst);
    jlong srcEnd = (jlong)srcOffset + (jlong)length;
    jlong dstEnd = (jlong)dstOffset + (jlong)length;
    if (srcEnd > (jlong)srcLen || dstEnd > (jlong)dstLen) return -3;

    jboolean sameArray = (*env)->IsSameObject(env, src, dst);

    jbyte* s = (*env)->GetPrimitiveArrayCritical(env, src, 0);
    jbyte* d = (*env)->GetPrimitiveArrayCritical(env, dst, 0);
    if (!s || !d) {
        if (s) (*env)->ReleasePrimitiveArrayCritical(env, src, s, JNI_ABORT);
        if (d) (*env)->ReleasePrimitiveArrayCritical(env, dst, d, 0);
        return -2;
    }

    const uint8_t* in = (const uint8_t*)s + srcOffset;
    uint8_t* out = (uint8_t*)d + dstOffset;

    if (sameArray) {
        vectra_kernel_move(out, in, (uint32_t)length);
    } else {
        vectra_kernel_copy_forward(out, in, (uint32_t)length);
    }

    (*env)->ReleasePrimitiveArrayCritical(env, src, s, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, d, 0);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeXorChecksum(JNIEnv* env, jclass clazz,
                                                          jbyteArray data,
                                                          jint offset,
                                                          jint length) {
    (void)clazz;
    if (!data || length <= 0 || offset < 0) return 0;

    jsize dataLen = (*env)->GetArrayLength(env, data);
    jlong dataEnd = (jlong)offset + (jlong)length;
    if (dataEnd > (jlong)dataLen) return INT32_MIN;

    jbyte* base = (*env)->GetPrimitiveArrayCritical(env, data, 0);
    if (!base) return INT32_MIN;

    const uint8_t* p = (const uint8_t*)base + offset;
    int x = 0;

#if defined(__ARM_NEON)
    int i = 0;
    uint8x16_t acc = vdupq_n_u8(0);
    int end = length & ~15;
    for (; i < end; i += 16) {
        uint8x16_t v = vld1q_u8(p + i);
        acc = veorq_u8(acc, v);
    }
    uint8_t lane[16];
    vst1q_u8(lane, acc);
    for (int j = 0; j < 16; j++) x ^= lane[j];
    for (; i < length; i++) x ^= p[i];
#else
    int i = 0;
    int end = length & ~7;
    while (i < end) {
        x ^= p[i];
        x ^= p[i + 1];
        x ^= p[i + 2];
        x ^= p[i + 3];
        x ^= p[i + 4];
        x ^= p[i + 5];
        x ^= p[i + 6];
        x ^= p[i + 7];
        i += 8;
    }
    while (i < length) {
        x ^= p[i];
        i++;
    }
#endif

    (*env)->ReleasePrimitiveArrayCritical(env, data, base, JNI_ABORT);
    return x;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePopcount32(JNIEnv* env, jclass clazz, jint value) {
    (void)env;
    (void)clazz;
#if defined(__GNUC__) || defined(__clang__)
    return __builtin_popcount((unsigned int)value);
#else
    uint32_t v = (uint32_t)value;
    v = v - ((v >> 1) & 0x55555555u);
    v = (v & 0x33333333u) + ((v >> 2) & 0x33333333u);
    v = (v + (v >> 4)) & 0x0F0F0F0Fu;
    v = v + (v >> 8);
    v = v + (v >> 16);
    return (jint)(v & 0x3Fu);
#endif
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeByteSwap32(JNIEnv* env, jclass clazz, jint value) {
    (void)env;
    (void)clazz;
#if defined(__GNUC__) || defined(__clang__)
    return (jint)__builtin_bswap32((uint32_t)value);
#else
    uint32_t v = (uint32_t)value;
    return (jint)((v >> 24) | ((v >> 8) & 0x0000FF00u) | ((v << 8) & 0x00FF0000u) | (v << 24));
#endif
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeRotateLeft32(JNIEnv* env, jclass clazz, jint value, jint distance) {
    (void)env;
    (void)clazz;
    uint32_t v = (uint32_t)value;
    uint32_t d = (uint32_t)distance & 31u;
    if (d == 0u) {
        return (jint)v;
    }
#if defined(__aarch64__)
    uint32_t out;
    __asm__ volatile("ror %w0, %w1, %w2" : "=r"(out) : "r"(v), "r"((uint32_t)(32u - d)));
    return (jint)out;
#elif defined(__arm__)
    uint32_t out;
    __asm__ volatile("ror %0, %1, %2" : "=r"(out) : "r"(v), "r"((uint32_t)(32u - d)));
    return (jint)out;
#else
    return (jint)((v << d) | (v >> ((32u - d) & 31u)));
#endif
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeRotateRight32(JNIEnv* env, jclass clazz, jint value, jint distance) {
    (void)env;
    (void)clazz;
    uint32_t v = (uint32_t)value;
    uint32_t d = (uint32_t)distance & 31u;
    if (d == 0u) {
        return (jint)v;
    }
#if defined(__aarch64__)
    uint32_t out;
    __asm__ volatile("ror %w0, %w1, %w2" : "=r"(out) : "r"(v), "r"(d));
    return (jint)out;
#elif defined(__arm__)
    uint32_t out;
    __asm__ volatile("ror %0, %1, %2" : "=r"(out) : "r"(v), "r"(d));
    return (jint)out;
#else
    return (jint)((v >> d) | (v << ((32u - d) & 31u)));
#endif
}

static inline int32_t vectra_clamp16(int32_t value) {
    if (value < -32768) {
        return -32768;
    }
    if (value > 32767) {
        return 32767;
    }
    return value;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeVec2Pack(JNIEnv* env, jclass clazz, jint x, jint y) {
    (void)env;
    (void)clazz;
    return (jint)((((uint32_t)y & 0xFFFFu) << 16) | ((uint32_t)x & 0xFFFFu));
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeVec2X(JNIEnv* env, jclass clazz, jint vec) {
    (void)env;
    (void)clazz;
    return (jint)(int16_t)((uint32_t)vec & 0xFFFFu);
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeVec2Y(JNIEnv* env, jclass clazz, jint vec) {
    (void)env;
    (void)clazz;
    return (jint)(int16_t)(((uint32_t)vec >> 16) & 0xFFFFu);
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeVec2AddSat(JNIEnv* env, jclass clazz, jint a, jint b) {
    (void)env;
    (void)clazz;

    int32_t ax = (int16_t)((uint32_t)a & 0xFFFFu);
    int32_t ay = (int16_t)(((uint32_t)a >> 16) & 0xFFFFu);
    int32_t bx = (int16_t)((uint32_t)b & 0xFFFFu);
    int32_t by = (int16_t)(((uint32_t)b >> 16) & 0xFFFFu);

    int32_t sx = vectra_clamp16(ax + bx);
    int32_t sy = vectra_clamp16(ay + by);

    return (jint)((((uint32_t)sy & 0xFFFFu) << 16) | ((uint32_t)sx & 0xFFFFu));
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeVec2Dot(JNIEnv* env, jclass clazz, jint a, jint b) {
    (void)env;
    (void)clazz;

    int32_t ax = (int16_t)((uint32_t)a & 0xFFFFu);
    int32_t ay = (int16_t)(((uint32_t)a >> 16) & 0xFFFFu);
    int32_t bx = (int16_t)((uint32_t)b & 0xFFFFu);
    int32_t by = (int16_t)(((uint32_t)b >> 16) & 0xFFFFu);

#if defined(__aarch64__)
    int32_t result;
    __asm__ volatile(
            "smull x8, %w1, %w3\n\t"
            "smaddl x8, %w2, %w4, x8\n\t"
            "mov %w0, w8\n\t"
            : "=r"(result)
            : "r"(ax), "r"(ay), "r"(bx), "r"(by)
            : "x8");
    return (jint)result;
#else
    return (jint)(ax * bx + ay * by);
#endif
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeVec2Mag2(JNIEnv* env, jclass clazz, jint vec) {
    (void)env;
    (void)clazz;

    int32_t x = (int16_t)((uint32_t)vec & 0xFFFFu);
    int32_t y = (int16_t)(((uint32_t)vec >> 16) & 0xFFFFu);
#if defined(__aarch64__)
    int32_t result;
    __asm__ volatile(
            "smull x8, %w1, %w1\n\t"
            "smaddl x8, %w2, %w2, x8\n\t"
            "mov %w0, w8\n\t"
            : "=r"(result)
            : "r"(x), "r"(y)
            : "x8");
    return (jint)result;
#else
    return (jint)(x * x + y * y);
#endif
}

JNIEXPORT jintArray JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeReadKernelUnitContract(JNIEnv* env, jclass clazz) {
    (void)clazz;
    const vectra_hw_contract_t* hw = vectra_hw_contract_get();

    long cpu = sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu <= 0) {
        cpu = 1;
    }

    jint values[8];
    values[0] = (jint)hw->signature;
    values[1] = (jint)hw->pointer_bits;
    values[2] = (jint)hw->cache_line_bytes;
    values[3] = (jint)hw->page_bytes;
    values[4] = (jint)hw->feature_mask;
    values[5] = (jint)cpu;
    values[6] = (jint)VECTRA_ARENA_CAPACITY_BYTES;
    values[7] = (jint)(hw->cache_line_bytes * 64u);

    jintArray out = (*env)->NewIntArray(env, 8);
    if (!out) {
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, out, 0, 8, values);
    return out;
}

JNIEXPORT jintArray JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeReadHardwareContract(JNIEnv* env, jclass clazz) {
    (void)clazz;
    const vectra_hw_contract_t* hw = vectra_hw_contract_get();
    jint values[5];
    values[0] = (jint)hw->signature;
    values[1] = (jint)hw->pointer_bits;
    values[2] = (jint)hw->cache_line_bytes;
    values[3] = (jint)hw->page_bytes;
    values[4] = (jint)hw->feature_mask;

    jintArray out = (*env)->NewIntArray(env, 5);
    if (!out) {
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, out, 0, 5, values);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePlatformSignature(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)vectra_hw_contract_get()->signature;
}


JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePointerBits(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)vectra_hw_contract_get()->pointer_bits;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCacheLineBytes(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)vectra_hw_contract_get()->cache_line_bytes;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePageBytes(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)vectra_hw_contract_get()->page_bytes;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeFeatureMask(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)vectra_hw_contract_get()->feature_mask;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeAllocArena(JNIEnv* env, jclass clazz, jint bytes) {
    (void)env;
    (void)clazz;
    if (bytes <= 0) {
        return VECTRA_ARENA_ERR_INVALID_ARG;
    }

    pthread_mutex_lock(&g_arena_lock);

    int init_rc = vectra_arena_ensure_initialized();
    if (init_rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return init_rc;
    }

    uint32_t request = (uint32_t)bytes;
    uint32_t slot_index = VECTRA_ARENA_MAX_SLOTS;
    for (uint32_t i = 0; i < VECTRA_ARENA_MAX_SLOTS; i++) {
        if (!g_arena_slots[i].in_use) {
            slot_index = i;
            break;
        }
    }
    if (slot_index == VECTRA_ARENA_MAX_SLOTS) {
        pthread_mutex_unlock(&g_arena_lock);
        return VECTRA_ARENA_ERR_NO_MEMORY;
    }

    uint32_t offset = 0u;
    int rc = vectra_arena_find_region(request, &offset);
    if (rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return rc;
    }

    vectra_arena_slot_t* slot = &g_arena_slots[slot_index];
    uint32_t next_gen = slot->generation + 1u;
    if ((next_gen & VECTRA_ARENA_HANDLE_GEN_MASK) == 0u) {
        next_gen = 1u;
    }

    slot->offset = offset;
    slot->size = request;
    slot->generation = next_gen;
    slot->in_use = 1u;
    vectra_kernel_fill(g_arena_base + offset, 0u, request);
    atomic_fetch_add(&g_arena_active_slots, 1u);

    jint handle = vectra_arena_make_handle(slot_index, slot->generation);
    pthread_mutex_unlock(&g_arena_lock);
    return handle;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeFreeArena(JNIEnv* env, jclass clazz, jint handle) {
    (void)env;
    (void)clazz;

    pthread_mutex_lock(&g_arena_lock);

    if (!g_arena_base || g_arena_capacity == 0u) {
        pthread_mutex_unlock(&g_arena_lock);
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    uint32_t slot_index = 0u;
    uint32_t generation = 0u;
    int rc = vectra_arena_decode_handle(handle, &slot_index, &generation);
    if (rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return rc;
    }

    vectra_arena_slot_t* slot = &g_arena_slots[slot_index];
    if (!slot->in_use || slot->generation != generation) {
        pthread_mutex_unlock(&g_arena_lock);
        return VECTRA_ARENA_ERR_BAD_HANDLE;
    }

    slot->in_use = 0u;
    slot->offset = 0u;
    slot->size = 0u;
    atomic_fetch_sub(&g_arena_active_slots, 1u);

    if (atomic_load(&g_arena_active_slots) == 0u) {
        free(g_arena_base);
        g_arena_base = NULL;
        g_arena_capacity = 0u;
    }

    pthread_mutex_unlock(&g_arena_lock);
    return VECTRA_ARENA_OK;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeArenaCopy(JNIEnv* env, jclass clazz,
                                                         jint srcHandle, jint srcOffset,
                                                         jint dstHandle, jint dstOffset,
                                                         jint length) {
    (void)env;
    (void)clazz;
    if (length < 0 || srcOffset < 0 || dstOffset < 0) {
        return VECTRA_ARENA_ERR_INVALID_ARG;
    }
    if (length == 0) {
        return VECTRA_ARENA_OK;
    }

    pthread_mutex_lock(&g_arena_lock);

    vectra_arena_slot_t* src_slot = NULL;
    vectra_arena_slot_t* dst_slot = NULL;
    uint8_t* src_ptr = NULL;
    uint8_t* dst_ptr = NULL;

    int rc = vectra_arena_get_slot_for_range(srcHandle, srcOffset, length, &src_slot, &src_ptr);
    if (rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return rc;
    }
    rc = vectra_arena_get_slot_for_range(dstHandle, dstOffset, length, &dst_slot, &dst_ptr);
    if (rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return rc;
    }

    vectra_kernel_move(dst_ptr, src_ptr, (uint32_t)length);

    pthread_mutex_unlock(&g_arena_lock);
    return VECTRA_ARENA_OK;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeArenaXorChecksum(JNIEnv* env, jclass clazz,
                                                                jint handle, jint offset,
                                                                jint length) {
    (void)env;
    (void)clazz;
    if (offset < 0 || length < 0) {
        return VECTRA_ARENA_ERR_INVALID_ARG;
    }
    if (length == 0) {
        return 0;
    }

    pthread_mutex_lock(&g_arena_lock);

    vectra_arena_slot_t* slot = NULL;
    uint8_t* ptr = NULL;
    int rc = vectra_arena_get_slot_for_range(handle, offset, length, &slot, &ptr);
    (void)slot;
    if (rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return rc;
    }

    int x = 0;
#if defined(__ARM_NEON)
    int i = 0;
    uint8x16_t acc = vdupq_n_u8(0);
    int end = length & ~15;
    for (; i < end; i += 16) {
        uint8x16_t v = vld1q_u8(ptr + i);
        acc = veorq_u8(acc, v);
    }
    uint8_t lane[16];
    vst1q_u8(lane, acc);
    for (int j = 0; j < 16; j++) {
        x ^= lane[j];
    }
    for (; i < length; i++) {
        x ^= ptr[i];
    }
#else
    int i = 0;
    int end = length & ~7;
    while (i < end) {
        x ^= ptr[i];
        x ^= ptr[i + 1];
        x ^= ptr[i + 2];
        x ^= ptr[i + 3];
        x ^= ptr[i + 4];
        x ^= ptr[i + 5];
        x ^= ptr[i + 6];
        x ^= ptr[i + 7];
        i += 8;
    }
    while (i < length) {
        x ^= ptr[i];
        i++;
    }
#endif

    pthread_mutex_unlock(&g_arena_lock);
    return x;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeArenaFill(JNIEnv* env, jclass clazz,
                                                         jint handle, jint offset,
                                                         jint length, jint value) {
    (void)env;
    (void)clazz;
    if (offset < 0 || length < 0 || (value & ~0xFF) != 0) {
        return VECTRA_ARENA_ERR_INVALID_ARG;
    }
    if (length == 0) {
        return VECTRA_ARENA_OK;
    }

    pthread_mutex_lock(&g_arena_lock);

    vectra_arena_slot_t* slot = NULL;
    uint8_t* ptr = NULL;
    int rc = vectra_arena_get_slot_for_range(handle, offset, length, &slot, &ptr);
    (void)slot;
    if (rc != VECTRA_ARENA_OK) {
        pthread_mutex_unlock(&g_arena_lock);
        return rc;
    }

    uint8_t fill = (uint8_t)(value & 0xFF);
    vectra_kernel_fill(ptr, fill, (uint32_t)length);


    pthread_mutex_unlock(&g_arena_lock);
    return VECTRA_ARENA_OK;
}



JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreInit(JNIEnv* env, jclass clazz, jint seed) {
    (void)env;
    (void)clazz;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_init(&g_unified_state, (uint32_t)seed);
    pthread_mutex_unlock(&g_unified_lock);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreShutdown(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_shutdown(&g_unified_state);
    pthread_mutex_unlock(&g_unified_lock);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreIngest(JNIEnv* env, jclass clazz, jbyteArray payload) {
    (void)clazz;
    if (!payload) return RMR_KERNEL_ERR_ARG;
    jsize len = (*env)->GetArrayLength(env, payload);
    jbyte* ptr = (*env)->GetPrimitiveArrayCritical(env, payload, 0);
    if (!ptr) return RMR_KERNEL_ERR_STATE;
    uint32_t out = 0u;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_ingest(&g_unified_state, (const uint8_t*)ptr, (uint32_t)len, &out);
    pthread_mutex_unlock(&g_unified_lock);
    (*env)->ReleasePrimitiveArrayCritical(env, payload, ptr, JNI_ABORT);
    return (jint)((rc == RMR_KERNEL_OK) ? (int32_t)out : rc);
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreProcess(JNIEnv* env, jclass clazz, jint a, jint b, jint mode) {
    (void)env;
    (void)clazz;
    int32_t out = 0;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_process(&g_unified_state, (int32_t)a, (int32_t)b, (uint32_t)mode, &out);
    pthread_mutex_unlock(&g_unified_lock);
    return (jint)((rc == RMR_KERNEL_OK) ? out : rc);
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreRoute(JNIEnv* env, jclass clazz,
                                                         jlong cpuCycles, jlong storageReadBytes,
                                                         jlong storageWriteBytes, jlong inputBytes,
                                                         jlong outputBytes, jlong m00, jlong m01,
                                                         jlong m10, jlong m11) {
    (void)env;
    (void)clazz;
    rmr_kernel_route_input_t in;
    in.cpu_cycles = (uint64_t)cpuCycles;
    in.storage_read_bytes = (uint64_t)storageReadBytes;
    in.storage_write_bytes = (uint64_t)storageWriteBytes;
    in.input_bytes = (uint64_t)inputBytes;
    in.output_bytes = (uint64_t)outputBytes;
    in.m00 = (int64_t)m00;
    in.m01 = (int64_t)m01;
    in.m10 = (int64_t)m10;
    in.m11 = (int64_t)m11;
    rmr_kernel_route_output_t out;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_route(&g_unified_state, &in, &out);
    pthread_mutex_unlock(&g_unified_lock);
    return (jint)((rc == RMR_KERNEL_OK) ? (int32_t)out.route : rc);
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreVerify(JNIEnv* env, jclass clazz, jbyteArray payload, jint expected) {
    (void)clazz;
    if (!payload) return RMR_KERNEL_ERR_ARG;
    jsize len = (*env)->GetArrayLength(env, payload);
    jbyte* ptr = (*env)->GetPrimitiveArrayCritical(env, payload, 0);
    if (!ptr) return RMR_KERNEL_ERR_STATE;
    uint32_t out = 0u;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_verify(&g_unified_state, (const uint8_t*)ptr, (uint32_t)len, (uint32_t)expected, &out);
    pthread_mutex_unlock(&g_unified_lock);
    (*env)->ReleasePrimitiveArrayCritical(env, payload, ptr, JNI_ABORT);
    if (rc == RMR_KERNEL_OK) {
        return 1;
    }
    if (rc == 1) {
        return 0;
    }
    return (jint)rc;
}

JNIEXPORT jlongArray JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreAudit(JNIEnv* env, jclass clazz) {
    (void)clazz;
    uint64_t counters[7] = {0};
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_audit(&g_unified_state, counters, 7u);
    pthread_mutex_unlock(&g_unified_lock);
    if (rc != RMR_KERNEL_OK) {
        return NULL;
    }
    jlongArray out = (*env)->NewLongArray(env, 7);
    if (!out) {
        return NULL;
    }
    jlong values[7];
    for (int i = 0; i < 7; i++) values[i] = (jlong)counters[i];
    (*env)->SetLongArrayRegion(env, out, 0, 7, values);
    return out;
}

JNIEXPORT jintArray JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeReadUnifiedCapabilities(JNIEnv* env, jclass clazz) {
    (void)clazz;
    rmr_kernel_capabilities_t caps;
    if (rmr_kernel_autodetect(&caps) != RMR_KERNEL_OK) {
        return NULL;
    }
    jint values[8];
    values[0] = (jint)caps.signature;
    values[1] = (jint)caps.pointer_bits;
    values[2] = (jint)caps.cache_line_bytes;
    values[3] = (jint)caps.page_bytes;
    values[4] = (jint)caps.feature_mask;
    values[5] = (jint)caps.register_width_bits;
    values[6] = (jint)caps.pin_count_hint;
    values[7] = (jint)caps.feature_bits_hi;
    jintArray out = (*env)->NewIntArray(env, 8);
    if (!out) {
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, out, 0, 8, values);
    return out;
}

#define LOGCAT_RING_MAX_ENTRIES 1024
#define LOGCAT_ENTRY_MAX_BYTES 1024
#define LOGCAT_BATCH_PAYLOAD_BYTES (LOGCAT_ENTRY_MAX_BYTES * 64)

typedef struct {
    char text[LOGCAT_ENTRY_MAX_BYTES];
    uint16_t len;
} logcat_entry_t;

static logcat_entry_t g_ring[LOGCAT_RING_MAX_ENTRIES];
static uint32_t g_ring_entries = 0;
static uint32_t g_entry_bytes = 0;
static uint32_t g_head = 0;
static uint32_t g_tail = 0;
static pthread_mutex_t g_ring_lock = PTHREAD_MUTEX_INITIALIZER;
static atomic_int g_capture_running = 0;
static pthread_t g_capture_thread;
static FILE* g_logcat_pipe = NULL;

static uint32_t logcat_safe_copy(char* dst, uint32_t dst_cap, const char* src) {
    if (!dst || dst_cap == 0 || !src) return 0;
    uint32_t i = 0;
    while (i + 1 < dst_cap && src[i] != '\0' && src[i] != '\n' && src[i] != '\r') {
        dst[i] = src[i];
        i++;
    }
    dst[i] = '\0';
    return i;
}

static void logcat_push_line(const char* line) {
    if (!line || g_ring_entries == 0 || g_entry_bytes == 0) return;

    pthread_mutex_lock(&g_ring_lock);
    logcat_entry_t* slot = &g_ring[g_head];
    slot->len = (uint16_t)logcat_safe_copy(slot->text, g_entry_bytes, line);

    uint32_t next = g_head + 1;
    if (next >= g_ring_entries) next = 0;

    if (next == g_tail) {
        g_tail++;
        if (g_tail >= g_ring_entries) g_tail = 0;
    }

    g_head = next;
    pthread_mutex_unlock(&g_ring_lock);
}

static void append_formatted_line(const char* prefix, const char* source) {
    char formatted[LOGCAT_ENTRY_MAX_BYTES];
    uint32_t pos = 0;

    while (prefix[pos] != '\0' && pos + 1 < sizeof(formatted)) {
        formatted[pos] = prefix[pos];
        pos++;
    }

    uint32_t copied = logcat_safe_copy(formatted + pos, (uint32_t)(sizeof(formatted) - pos), source);
    pos += copied;

    const char* suffix = "</font>";
    for (uint32_t i = 0; suffix[i] != '\0' && pos + 1 < sizeof(formatted); i++) {
        formatted[pos++] = suffix[i];
    }

    formatted[pos] = '\0';
    logcat_push_line(formatted);
}

static void* logcat_capture_loop(void* arg) {
    (void)arg;

    g_logcat_pipe = popen("logcat -v brief", "r");
    if (!g_logcat_pipe) {
        atomic_store(&g_capture_running, 0);
        return NULL;
    }

    char line[LOGCAT_ENTRY_MAX_BYTES];
    while (atomic_load(&g_capture_running)) {
        if (!fgets(line, (int)sizeof(line), g_logcat_pipe)) {
            break;
        }

        if (line[0] == 'E' && line[1] == '/') {
            append_formatted_line("<font color='red'>[E] ", line);
        } else if (line[0] == 'W' && line[1] == '/') {
            append_formatted_line("<font color='#FFC107'>[W] ", line);
        }
    }

    if (g_logcat_pipe) {
        pclose(g_logcat_pipe);
        g_logcat_pipe = NULL;
    }
    atomic_store(&g_capture_running, 0);
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeLogcatBridge_nativeInitCapture(JNIEnv* env, jclass clazz,
                                                               jint ringEntries, jint entryBytes) {
    (void)env;
    (void)clazz;

    if (ringEntries <= 0) ringEntries = 256;
    if (ringEntries > LOGCAT_RING_MAX_ENTRIES) ringEntries = LOGCAT_RING_MAX_ENTRIES;
    if (entryBytes <= 64) entryBytes = 256;
    if (entryBytes > LOGCAT_ENTRY_MAX_BYTES) entryBytes = LOGCAT_ENTRY_MAX_BYTES;

    pthread_mutex_lock(&g_ring_lock);
    g_ring_entries = (uint32_t)ringEntries;
    g_entry_bytes = (uint32_t)entryBytes;
    g_head = 0;
    g_tail = 0;
    for (uint32_t i = 0; i < g_ring_entries; i++) {
        g_ring[i].len = 0;
        g_ring[i].text[0] = '\0';
    }
    pthread_mutex_unlock(&g_ring_lock);

    if (atomic_exchange(&g_capture_running, 1) == 1) {
        return 0;
    }

    if (pthread_create(&g_capture_thread, NULL, logcat_capture_loop, NULL) != 0) {
        atomic_store(&g_capture_running, 0);
        return -1;
    }

    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_vectras_vm_core_NativeLogcatBridge_nativeReadBatch(JNIEnv* env, jclass clazz, jint maxEvents) {
    (void)clazz;
    if (maxEvents <= 0) maxEvents = 1;
    if (maxEvents > 256) maxEvents = 256;

    char payload[LOGCAT_BATCH_PAYLOAD_BYTES];
    uint32_t out = 0;
    int readCount = 0;

    pthread_mutex_lock(&g_ring_lock);
    while (g_tail != g_head && readCount < maxEvents && out + 2 < sizeof(payload)) {
        logcat_entry_t* slot = &g_ring[g_tail];
        if (slot->len > 0) {
            uint32_t copyLen = slot->len;
            if (out + copyLen + 1 >= sizeof(payload)) {
                copyLen = (uint32_t)(sizeof(payload) - out - 2);
            }
            memcpy(payload + out, slot->text, copyLen);
            out += copyLen;
            payload[out++] = '\n';
            readCount++;
        }
        slot->len = 0;
        slot->text[0] = '\0';
        g_tail++;
        if (g_tail >= g_ring_entries) g_tail = 0;
    }
    pthread_mutex_unlock(&g_ring_lock);

    payload[out] = '\0';
    return (*env)->NewStringUTF(env, payload);
}

JNIEXPORT void JNICALL
Java_com_vectras_vm_core_NativeLogcatBridge_nativeShutdownCapture(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;

    if (atomic_exchange(&g_capture_running, 0) == 1) {
        pthread_join(g_capture_thread, NULL);
    }

    pthread_mutex_lock(&g_ring_lock);
    g_head = 0;
    g_tail = 0;
    for (uint32_t i = 0; i < g_ring_entries; i++) {
        g_ring[i].len = 0;
        g_ring[i].text[0] = '\0';
    }
    pthread_mutex_unlock(&g_ring_lock);
}
