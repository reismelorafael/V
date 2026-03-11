#include <jni.h>
#include <stdint.h>

#include "rmr_hw_detect.h"
#include "hardware_profile_bridge_internal.h"

const char* vectra_hw_effective_abi(void) {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#elif defined(__riscv)
    return "riscv64";
#else
    return "unknown";
#endif
}

static uint32_t vectra_simd_mask(void) {
    uint32_t mask = 0u;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    mask |= 1u;
#endif
#if defined(__SSE2__)
    mask |= (1u << 1);
#endif
#if defined(__SSE4_2__)
    mask |= (1u << 2);
#endif
#if defined(__AVX__)
    mask |= (1u << 3);
#endif
#if defined(__riscv_vector)
    mask |= (1u << 4);
#endif
    return mask;
}

JNIEXPORT jintArray JNICALL
Java_com_vectras_vm_core_HardwareProfileBridge_nativeCollectSnapshot(JNIEnv* env, jclass clazz) {
    (void)clazz;
    uint32_t values_u32[9];
    vectra_hw_collect_snapshot(values_u32);
    jint values[9];
    for (int i = 0; i < 9; ++i) values[i] = (jint)values_u32[i];

    jintArray out = (*env)->NewIntArray(env, 9);
    if (!out) return NULL;
    (*env)->SetIntArrayRegion(env, out, 0, 9, values);
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_vectras_vm_core_HardwareProfileBridge_nativeEffectiveAbi(JNIEnv* env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, vectra_hw_effective_abi());
}

void vectra_hw_collect_snapshot(uint32_t out_values[9]) {
    RmR_HW_Info info;
    RmR_HW_Detect(&info);

    out_values[0] = info.arch;
    out_values[1] = info.arch_hex;
    out_values[2] = info.ptr_bits;
    out_values[3] = info.is_little_endian;
    out_values[4] = info.has_cycle_counter;
    out_values[5] = info.has_asm_probe;
    out_values[6] = info.feature_bits_0;
    out_values[7] = info.feature_bits_1;
    out_values[8] = vectra_simd_mask();
}
