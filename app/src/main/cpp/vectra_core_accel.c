#include <jni.h>
#include <stdint.h>
#include <unistd.h>

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


static jint vectra_page_bytes(void) {
#if defined(_SC_PAGESIZE)
    long page = sysconf(_SC_PAGESIZE);
    if (page >= 1024 && page <= 65536) {
        return (jint)page;
    }
#endif
    return 4096;
}

static jint vectra_cache_line_bytes(void) {
#if defined(_SC_LEVEL1_DCACHE_LINESIZE)
    long line = sysconf(_SC_LEVEL1_DCACHE_LINESIZE);
    if (line >= 16 && line <= 512) {
        return (jint)line;
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
        return (jint)cpuid_line;
    }
#endif
    return 64;
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
#endif
#if defined(__POPCNT__) || defined(__ARM_FEATURE_CRC32)
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

    vectra_cpuid(7u, 0u, &eax, &ebx, &ecx, &edx);
    if ((ebx & (1u << 5)) != 0u) {
        mask |= VECTRA_FEATURE_AVX2;
    }
#endif

    return mask;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeInit(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
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

    if (sameArray && dstOffset > srcOffset && dstOffset < srcOffset + length) {
        jint i = length;
        while (i > 0) {
            i--;
            out[i] = in[i];
        }

        (*env)->ReleasePrimitiveArrayCritical(env, src, s, JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, dst, d, 0);
        return 0;
    }

    jint i = 0;
    jint end = length & ~31;
    while (i < end) {
        out[i] = in[i];
        out[i + 1] = in[i + 1];
        out[i + 2] = in[i + 2];
        out[i + 3] = in[i + 3];
        out[i + 4] = in[i + 4];
        out[i + 5] = in[i + 5];
        out[i + 6] = in[i + 6];
        out[i + 7] = in[i + 7];
        out[i + 8] = in[i + 8];
        out[i + 9] = in[i + 9];
        out[i + 10] = in[i + 10];
        out[i + 11] = in[i + 11];
        out[i + 12] = in[i + 12];
        out[i + 13] = in[i + 13];
        out[i + 14] = in[i + 14];
        out[i + 15] = in[i + 15];
        out[i + 16] = in[i + 16];
        out[i + 17] = in[i + 17];
        out[i + 18] = in[i + 18];
        out[i + 19] = in[i + 19];
        out[i + 20] = in[i + 20];
        out[i + 21] = in[i + 21];
        out[i + 22] = in[i + 22];
        out[i + 23] = in[i + 23];
        out[i + 24] = in[i + 24];
        out[i + 25] = in[i + 25];
        out[i + 26] = in[i + 26];
        out[i + 27] = in[i + 27];
        out[i + 28] = in[i + 28];
        out[i + 29] = in[i + 29];
        out[i + 30] = in[i + 30];
        out[i + 31] = in[i + 31];
        i += 32;
    }

    while (i < length) {
        out[i] = in[i];
        i++;
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

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePlatformSignature(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)(vectra_arch_tag() | vectra_os_tag());
}


JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePointerBits(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)((int)(sizeof(void*) * 8));
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCacheLineBytes(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return vectra_cache_line_bytes();
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativePageBytes(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return vectra_page_bytes();
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeFeatureMask(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (jint)vectra_feature_mask();
}
