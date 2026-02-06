#include <jni.h>
#include <stdint.h>
#include <string.h>

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#define VECTRA_NATIVE_OK 0x56414343

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
    if (!src || !dst || length <= 0) return -1;

    jbyte* s = (*env)->GetPrimitiveArrayCritical(env, src, 0);
    jbyte* d = (*env)->GetPrimitiveArrayCritical(env, dst, 0);
    if (!s || !d) {
        if (s) (*env)->ReleasePrimitiveArrayCritical(env, src, s, JNI_ABORT);
        if (d) (*env)->ReleasePrimitiveArrayCritical(env, dst, d, 0);
        return -2;
    }

    memcpy(d + dstOffset, s + srcOffset, (size_t)length);

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
    if (!data || length <= 0) return 0;

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
    for (; i < length; i++) x ^= p[i];
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
