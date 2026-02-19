#include <jni.h>
#include <stdint.h>
#include "rmr_lowlevel.h"

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_LowLevelBridge_nativeFold32(JNIEnv* env, jclass clazz,
                                                      jint a, jint b, jint c, jint d) {
    (void)env;
    (void)clazz;
    return (jint)rmr_lowlevel_fold32((uint32_t)a, (uint32_t)b, (uint32_t)c, (uint32_t)d);
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_LowLevelBridge_nativeReduceXor(JNIEnv* env, jclass clazz,
                                                         jbyteArray data, jint offset, jint length) {
    (void)clazz;
    if (!data || offset < 0 || length < 0) return 0;
    const jsize n = (*env)->GetArrayLength(env, data);
    if (offset > n || length > (n - offset)) return 0;
    jbyte* p = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (!p) return 0;
    const uint32_t out = rmr_lowlevel_reduce_xor((const uint8_t*)p + (size_t)offset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, data, p, JNI_ABORT);
    return (jint)out;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_LowLevelBridge_nativeChecksum32(JNIEnv* env, jclass clazz,
                                                          jbyteArray data, jint offset, jint length, jint seed) {
    (void)clazz;
    if (!data || offset < 0 || length < 0) return seed;
    const jsize n = (*env)->GetArrayLength(env, data);
    if (offset > n || length > (n - offset)) return seed;
    jbyte* p = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (!p) return seed;
    const uint32_t out = rmr_lowlevel_checksum32((const uint8_t*)p + (size_t)offset, (size_t)length, (uint32_t)seed);
    (*env)->ReleasePrimitiveArrayCritical(env, data, p, JNI_ABORT);
    return (jint)out;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_LowLevelBridge_nativeXorChecksumCompat(JNIEnv* env, jclass clazz,
                                                                 jbyteArray data, jint offset, jint length) {
    (void)clazz;
    if (!data || offset < 0 || length < 0) return 0;
    const jsize n = (*env)->GetArrayLength(env, data);
    if (offset > n || length > (n - offset)) return 0;
    if (length == 0) return 0;

    jbyte* p = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (!p) return 0;

    const uint8_t* src = (const uint8_t*)p + (size_t)offset;
    uint32_t x = 0u;
    jint i = 0;
    const jint end = length & ~7;
    while (i < end) {
        x ^= (uint32_t)(src[i] & 0xFFu);
        x ^= (uint32_t)(src[i + 1] & 0xFFu);
        x ^= (uint32_t)(src[i + 2] & 0xFFu);
        x ^= (uint32_t)(src[i + 3] & 0xFFu);
        x ^= (uint32_t)(src[i + 4] & 0xFFu);
        x ^= (uint32_t)(src[i + 5] & 0xFFu);
        x ^= (uint32_t)(src[i + 6] & 0xFFu);
        x ^= (uint32_t)(src[i + 7] & 0xFFu);
        i += 8;
    }
    while (i < length) {
        x ^= (uint32_t)(src[i] & 0xFFu);
        i += 1;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, data, p, JNI_ABORT);
    return (jint)x;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_LowLevelBridge_nativeCrc32cCompat(JNIEnv* env, jclass clazz,
                                                            jint initial, jbyteArray data, jint offset, jint length) {
    (void)clazz;
    if (!data || offset < 0 || length < 0) return initial;
    const jsize n = (*env)->GetArrayLength(env, data);
    if (offset > n || length > (n - offset)) return initial;
    if (length == 0) return initial;

    jbyte* p = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (!p) return initial;

    const uint8_t* src = (const uint8_t*)p + (size_t)offset;
    uint32_t crc = (uint32_t)initial;
    for (jint i = 0; i < length; ++i) {
        crc ^= src[i];
        for (uint32_t b = 0; b < 8u; ++b) {
            const uint32_t mask = (uint32_t)-(int32_t)(crc & 1u);
            crc = (crc >> 1u) ^ (0x82F63B78u & mask);
        }
    }

    (*env)->ReleasePrimitiveArrayCritical(env, data, p, JNI_ABORT);
    return (jint)crc;
}
