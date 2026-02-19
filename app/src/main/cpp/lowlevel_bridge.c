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
