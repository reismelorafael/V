#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <stdio.h>
#include <stdatomic.h>
#include "engine/rmr/include/rmr_unified_kernel.h"

#define VECTRA_KERNEL_CONTRACT_SIZE 8
#define VECTRA_HW_CONTRACT_SIZE 10

static rmr_kernel_state_t g_unified_state;
static pthread_mutex_t g_unified_lock = PTHREAD_MUTEX_INITIALIZER;
static int vectra_kernel_ensure(void) {
    rmr_kernel_capabilities_t caps;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_get_capabilities(&g_unified_state, &caps);
    if (rc != RMR_KERNEL_OK) {
        rc = rmr_kernel_init(&g_unified_state, 0x56414343u);
        if (rc == RMR_KERNEL_OK) {
            rc = rmr_kernel_get_capabilities(&g_unified_state, &caps);
        }
    }
    pthread_mutex_unlock(&g_unified_lock);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeInit(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    return (vectra_kernel_ensure() == RMR_UK_OK) ? (jint)RMR_UK_NATIVE_OK_MAGIC : (jint)-1;
}

JNIEXPORT jintArray JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeReadHardwareContract(JNIEnv* env, jclass clazz) {
    (void)clazz;
    if (vectra_kernel_ensure() != RMR_KERNEL_OK) return NULL;
    rmr_kernel_capabilities_t caps;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_get_capabilities(&g_unified_state, &caps);
    pthread_mutex_unlock(&g_unified_lock);
    if (rc != RMR_KERNEL_OK) return NULL;
    jint payload[VECTRA_HW_CONTRACT_SIZE] = {
        (jint)caps.signature, (jint)caps.pointer_bits, (jint)caps.cache_line_bytes, (jint)caps.page_bytes,
        (jint)caps.feature_mask, (jint)caps.register_width_bits, (jint)caps.pin_count_hint, (jint)caps.feature_bits_hi,
        0, 0
    };
    jintArray arr = (*env)->NewIntArray(env, VECTRA_HW_CONTRACT_SIZE);
    if (!arr) return NULL;
    (*env)->SetIntArrayRegion(env, arr, 0, VECTRA_HW_CONTRACT_SIZE, payload);
    return arr;
}

JNIEXPORT jintArray JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeReadKernelUnitContract(JNIEnv* env, jclass clazz) {
    (void)clazz;
    if (vectra_kernel_ensure() != RMR_KERNEL_OK) return NULL;
    rmr_kernel_capabilities_t caps;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_get_capabilities(&g_unified_state, &caps);
    pthread_mutex_unlock(&g_unified_lock);
    if (rc != RMR_KERNEL_OK) return NULL;
    jint payload[VECTRA_KERNEL_CONTRACT_SIZE] = {
        (jint)caps.signature, (jint)caps.pointer_bits, (jint)caps.cache_line_bytes, (jint)caps.page_bytes,
        (jint)caps.feature_mask, (jint)caps.register_width_bits, (jint)caps.pin_count_hint, (jint)caps.feature_bits_hi
    };
    jintArray arr = (*env)->NewIntArray(env, VECTRA_KERNEL_CONTRACT_SIZE);
    if (!arr) return NULL;
    (*env)->SetIntArrayRegion(env, arr, 0, VECTRA_KERNEL_CONTRACT_SIZE, payload);
    return arr;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCopyBytes(JNIEnv* env, jclass clazz, jbyteArray src, jint srcOffset, jbyteArray dst, jint dstOffset, jint length) {
    (void)clazz;
    if (vectra_kernel_ensure() != RMR_UK_OK || !src || !dst || srcOffset < 0 || dstOffset < 0 || length < 0) return -1;
    jbyte* s = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    jbyte* d = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (!s || !d) {
        if (s) (*env)->ReleasePrimitiveArrayCritical(env, src, s, JNI_ABORT);
        if (d) (*env)->ReleasePrimitiveArrayCritical(env, dst, d, 0);
        return -1;
    }
    int rc = RmR_UnifiedKernel_Copy(&g_unified_state, (uint8_t*)d + dstOffset, (const uint8_t*)s + srcOffset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, src, s, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, d, 0);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeXorChecksum(JNIEnv* env, jclass clazz, jbyteArray data, jint offset, jint length) {
    (void)clazz;
    if (vectra_kernel_ensure() != RMR_UK_OK || !data || offset < 0 || length < 0) return (jint)0x80000000u;
    jbyte* p = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (!p) return (jint)0x80000000u;
    uint32_t x = RmR_UnifiedKernel_XorChecksum(&g_unified_state, (const uint8_t*)p + offset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, data, p, JNI_ABORT);
    return (jint)x;
}

JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativePopcount32(JNIEnv* env, jclass clazz, jint value) {(void)env;(void)clazz; return (jint)RmR_UnifiedKernel_Popcount32((uint32_t)value);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeByteSwap32(JNIEnv* env, jclass clazz, jint value) {(void)env;(void)clazz; return (jint)RmR_UnifiedKernel_ByteSwap32((uint32_t)value);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeRotateLeft32(JNIEnv* env, jclass clazz, jint value, jint distance) {(void)env;(void)clazz; return (jint)RmR_UnifiedKernel_Rotl32((uint32_t)value, (uint32_t)distance);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeRotateRight32(JNIEnv* env, jclass clazz, jint value, jint distance) {(void)env;(void)clazz; return (jint)RmR_UnifiedKernel_Rotr32((uint32_t)value, (uint32_t)distance);} 

JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVec2Pack(JNIEnv* env, jclass clazz, jint x, jint y){(void)env;(void)clazz;return ((y & 0xFFFF) << 16) | (x & 0xFFFF);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVec2X(JNIEnv* env, jclass clazz, jint vec){(void)env;(void)clazz;return (jshort)(vec & 0xFFFF);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVec2Y(JNIEnv* env, jclass clazz, jint vec){(void)env;(void)clazz;return (jshort)(vec >> 16);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVec2AddSat(JNIEnv* env, jclass clazz, jint a, jint b){(void)env;(void)clazz;int ax=(jshort)(a&0xFFFF), ay=(jshort)(a>>16), bx=(jshort)(b&0xFFFF), by=(jshort)(b>>16);int x=ax+bx,y=ay+by; if(x>32767)x=32767; if(x<-32768)x=-32768; if(y>32767)y=32767; if(y<-32768)y=-32768; return ((y & 0xFFFF)<<16)|(x & 0xFFFF);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVec2Dot(JNIEnv* env, jclass clazz, jint a, jint b){(void)env;(void)clazz;int ax=(jshort)(a&0xFFFF), ay=(jshort)(a>>16), bx=(jshort)(b&0xFFFF), by=(jshort)(b>>16); return ax*bx + ay*by;} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVec2Mag2(JNIEnv* env, jclass clazz, jint v){(void)env;(void)clazz;int x=(jshort)(v&0xFFFF), y=(jshort)(v>>16); return x*x + y*y;} 

JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativePlatformSignature(JNIEnv* env, jclass clazz){(void)env;(void)clazz;RmR_UnifiedCapabilities c; if(vectra_kernel_ensure()!=RMR_UK_OK||RmR_UnifiedKernel_QueryCapabilities(&g_unified_state,&c)!=RMR_UK_OK)return 0; return (jint)c.signature;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativePointerBits(JNIEnv* env, jclass clazz){(void)env;(void)clazz;RmR_UnifiedCapabilities c; if(vectra_kernel_ensure()!=RMR_UK_OK||RmR_UnifiedKernel_QueryCapabilities(&g_unified_state,&c)!=RMR_UK_OK)return 32; return (jint)c.pointer_bits;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeCacheLineBytes(JNIEnv* env, jclass clazz){(void)env;(void)clazz;RmR_UnifiedCapabilities c; if(vectra_kernel_ensure()!=RMR_UK_OK||RmR_UnifiedKernel_QueryCapabilities(&g_unified_state,&c)!=RMR_UK_OK)return 64; return (jint)c.cache_line_bytes;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativePageBytes(JNIEnv* env, jclass clazz){(void)env;(void)clazz;RmR_UnifiedCapabilities c; if(vectra_kernel_ensure()!=RMR_UK_OK||RmR_UnifiedKernel_QueryCapabilities(&g_unified_state,&c)!=RMR_UK_OK)return 4096; return (jint)c.page_bytes;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeFeatureMask(JNIEnv* env, jclass clazz){(void)env;(void)clazz;RmR_UnifiedCapabilities c; if(vectra_kernel_ensure()!=RMR_UK_OK||RmR_UnifiedKernel_QueryCapabilities(&g_unified_state,&c)!=RMR_UK_OK)return 0; return (jint)c.feature_mask;}

JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeAllocArena(JNIEnv* env, jclass clazz, jint bytes){(void)env;(void)clazz;uint32_t h=0; if(vectra_kernel_ensure()!=RMR_UK_OK)return -1; int rc=RmR_UnifiedKernel_ArenaAlloc(&g_unified_state,(uint32_t)bytes,&h); return (rc==RMR_UK_OK)?(jint)h:(jint)rc;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeFreeArena(JNIEnv* env, jclass clazz, jint handle){(void)env;(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK)return -1; return (jint)RmR_UnifiedKernel_ArenaFree(&g_unified_state,(uint32_t)handle);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeArenaCopy(JNIEnv* env, jclass clazz, jint sH, jint sO, jint dH, jint dO, jint l){(void)env;(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK)return -1; return (jint)RmR_UnifiedKernel_ArenaCopy(&g_unified_state,(uint32_t)sH,(uint32_t)sO,(uint32_t)dH,(uint32_t)dO,(uint32_t)l);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeArenaXorChecksum(JNIEnv* env, jclass clazz, jint h, jint o, jint l){(void)env;(void)clazz; uint32_t out=0; if(vectra_kernel_ensure()!=RMR_UK_OK)return -1; int rc=RmR_UnifiedKernel_ArenaXorChecksum(&g_unified_state,(uint32_t)h,(uint32_t)o,(uint32_t)l,&out); return (rc==RMR_UK_OK)?(jint)out:(jint)rc;} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeArenaFill(JNIEnv* env, jclass clazz, jint h, jint o, jint l, jint v){(void)env;(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK)return -1; return (jint)RmR_UnifiedKernel_ArenaFill(&g_unified_state,(uint32_t)h,(uint32_t)o,(uint32_t)l,(uint8_t)v);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeArenaWrite(JNIEnv* env, jclass clazz, jint h, jint o, jbyteArray src, jint srcOffset, jint l){(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK||!src)return -1; jbyte* p=(*env)->GetPrimitiveArrayCritical(env,src,NULL); if(!p)return -1; int rc=RmR_UnifiedKernel_ArenaWrite(&g_unified_state,(uint32_t)h,(uint32_t)o,(const uint8_t*)p+srcOffset,(uint32_t)l); (*env)->ReleasePrimitiveArrayCritical(env,src,p,JNI_ABORT); return (jint)rc;}

JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeIngest(JNIEnv* env, jclass clazz, jbyteArray payload){(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK||!payload)return -1; jsize n=(*env)->GetArrayLength(env,payload); jbyte* p=(*env)->GetPrimitiveArrayCritical(env,payload,NULL); if(!p)return -1; RmR_UnifiedIngestState st; int rc=RmR_UnifiedKernel_Ingest(&g_unified_state,(const uint8_t*)p,(size_t)n,&st); (*env)->ReleasePrimitiveArrayCritical(env,payload,p,JNI_ABORT); return rc==RMR_UK_OK?(jint)st.crc32c:-1;}
JNIEXPORT jlongArray JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeProcessRoute(JNIEnv* env, jclass clazz, jlong cpu, jlong sR, jlong sW, jlong inB, jlong outB, jlong m00, jlong m01, jlong m10, jlong m11){(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK)return NULL; RmR_UnifiedProcessState p; RmR_UnifiedRouteState r; if(RmR_UnifiedKernel_Process(&g_unified_state,(uint64_t)cpu,(uint64_t)sR,(uint64_t)sW,(uint64_t)inB,(uint64_t)outB,(int64_t)m00,(int64_t)m01,(int64_t)m10,(int64_t)m11,&p)!=RMR_UK_OK) return NULL; if(RmR_UnifiedKernel_Route(&g_unified_state,&p,&r)!=RMR_UK_OK)return NULL; jlong out[5]={(jlong)p.cpu_pressure,(jlong)p.storage_pressure,(jlong)p.io_pressure,(jlong)p.matrix_determinant,(jlong)r.route_tag}; jlongArray arr=(*env)->NewLongArray(env,5); if(!arr)return NULL; (*env)->SetLongArrayRegion(env,arr,0,5,out); return arr;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeVerify(JNIEnv* env, jclass clazz, jbyteArray payload, jint expected){(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK||!payload)return 0; jsize n=(*env)->GetArrayLength(env,payload); jbyte* p=(*env)->GetPrimitiveArrayCritical(env,payload,NULL); if(!p)return 0; RmR_UnifiedVerifyState v; int rc=RmR_UnifiedKernel_Verify(&g_unified_state,(const uint8_t*)p,(size_t)n,(uint32_t)expected,&v); (*env)->ReleasePrimitiveArrayCritical(env,payload,p,JNI_ABORT); return (rc==RMR_UK_OK)?(jint)v.verify_ok:0;}
JNIEXPORT jlong JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeAudit(JNIEnv* env, jclass clazz, jint crc, jlong matrixDet, jlong routeTag, jint verifyOk){(void)env;(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK)return 0; RmR_UnifiedIngestState i={.crc32c=(uint32_t)crc,.entropy=(uint32_t)crc,.stage_counter=0}; RmR_UnifiedProcessState p={.cpu_pressure=0,.storage_pressure=0,.io_pressure=0,.matrix_determinant=(int64_t)matrixDet}; RmR_UnifiedRouteState r={.route_id=0,.route_tag=(uint64_t)routeTag}; RmR_UnifiedVerifyState v={.computed_crc32c=(uint32_t)crc,.verify_ok=(uint32_t)verifyOk}; RmR_UnifiedAuditState a; if(RmR_UnifiedKernel_Audit(&g_unified_state,&i,&p,&r,&v,&a)!=RMR_UK_OK)return 0; return (jlong)a.audit_signature;}

JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeDeterministicCrc32c(JNIEnv* env, jclass clazz, jint initial, jbyteArray data, jint offset, jint length){(void)clazz; if(vectra_kernel_ensure()!=RMR_UK_OK||!data||offset<0||length<0)return (jint)0x80000000u; jsize n=(*env)->GetArrayLength(env,data); if(offset+length>n)return (jint)0x80000000u; jbyte* p=(*env)->GetPrimitiveArrayCritical(env,data,NULL); if(!p)return (jint)0x80000000u; uint32_t crc=(uint32_t)initial; const uint8_t* src=(const uint8_t*)p+offset; for(jsize i=0;i<length;i++){ crc^=(uint32_t)src[i]; for(uint32_t b=0;b<8;b++){ uint32_t mask=(uint32_t)-(int32_t)(crc&1u); crc=(crc>>1u)^(0x82F63B78u&mask);} } (*env)->ReleasePrimitiveArrayCritical(env,data,p,JNI_ABORT); return (jint)crc;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeDeterministicParity2D8(JNIEnv* env, jclass clazz, jint data16){(void)env;(void)clazz; uint32_t parity=0u; uint32_t d=(uint32_t)data16; for(uint32_t row=0;row<4;row++){ uint32_t rowParity=0u; for(uint32_t col=0;col<4;col++){ uint32_t idx=(row<<2u)|col; rowParity^=(d>>idx)&1u; } parity|=(rowParity<<(row+4u)); } for(uint32_t col=0;col<4;col++){ uint32_t colParity=0u; for(uint32_t row=0;row<4;row++){ uint32_t idx=(row<<2u)|col; colParity^=(d>>idx)&1u; } parity|=(colParity<<col); } return (jint)(parity&0xFFu);} 
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeDeterministicVerify4x4Block(JNIEnv* env, jclass clazz, jint packedBlock){(void)env;(void)clazz; uint32_t data=((uint32_t)packedBlock>>8u)&0xFFFFu; uint32_t stored=(uint32_t)packedBlock&0xFFu; uint32_t parity=0u; for(uint32_t row=0;row<4;row++){ uint32_t rowParity=0u; for(uint32_t col=0;col<4;col++){ uint32_t idx=(row<<2u)|col; rowParity^=(data>>idx)&1u; } parity|=(rowParity<<(row+4u)); } for(uint32_t col=0;col<4;col++){ uint32_t colParity=0u; for(uint32_t row=0;row<4;row++){ uint32_t idx=(row<<2u)|col; colParity^=(data>>idx)&1u; } parity|=(colParity<<col); } return (stored==(parity&0xFFu))?1:0;}
JNIEXPORT jintArray JNICALL Java_com_vectras_vm_core_NativeFastPath_nativeDeterministicPolicyTransition(JNIEnv* env, jclass clazz, jint hitStreak, jint missStreak, jint hasEvent){(void)clazz; jint hits=hitStreak; jint misses=missStreak; if(hasEvent!=0){ hits+=1; misses=0; } else { misses+=1; hits=0; } jint policy=(misses>=2)?1:0; jint out[3]={hits,misses,policy}; jintArray arr=(*env)->NewIntArray(env,3); if(!arr)return NULL; (*env)->SetIntArrayRegion(env,arr,0,3,out); return arr;}



JNIEXPORT jint JNICALL
Java_com_vectras_vm_core_NativeFastPath_nativeCoreInit(JNIEnv* env, jclass clazz, jint seed) {
    (void)env;
    (void)clazz;
    pthread_mutex_lock(&g_unified_lock);
    (void)rmr_kernel_shutdown(&g_unified_state);
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
    if (vectra_kernel_ensure() != RMR_KERNEL_OK || !payload) return RMR_KERNEL_ERR_ARG;
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
    if (vectra_kernel_ensure() != RMR_KERNEL_OK) return RMR_KERNEL_ERR_STATE;
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
    if (vectra_kernel_ensure() != RMR_KERNEL_OK) return RMR_KERNEL_ERR_STATE;
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
    if (vectra_kernel_ensure() != RMR_KERNEL_OK || !payload) return RMR_KERNEL_ERR_ARG;
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
    if (vectra_kernel_ensure() != RMR_KERNEL_OK) return NULL;
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
    if (vectra_kernel_ensure() != RMR_KERNEL_OK) {
        return NULL;
    }
    rmr_kernel_capabilities_t caps;
    pthread_mutex_lock(&g_unified_lock);
    int rc = rmr_kernel_get_capabilities(&g_unified_state, &caps);
    pthread_mutex_unlock(&g_unified_lock);
    if (rc != RMR_KERNEL_OK) {
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
typedef struct { char text[LOGCAT_ENTRY_MAX_BYTES]; uint16_t len; } logcat_entry_t;
static logcat_entry_t g_ring[LOGCAT_RING_MAX_ENTRIES]; static uint32_t g_ring_entries = 0, g_entry_bytes = 0, g_head = 0, g_tail = 0; static pthread_mutex_t g_ring_lock = PTHREAD_MUTEX_INITIALIZER; static atomic_int g_capture_running = 0; static pthread_t g_capture_thread; static FILE* g_logcat_pipe = NULL;
static uint32_t logcat_safe_copy(char* dst, uint32_t dst_cap, const char* src){uint32_t i=0; if(!dst||!src||dst_cap==0)return 0; while(i+1<dst_cap && src[i] && src[i]!='\n' && src[i]!='\r'){dst[i]=src[i];i++;} dst[i]='\0'; return i;}
static void logcat_push_line(const char* line){if(!line||!g_ring_entries||!g_entry_bytes)return; pthread_mutex_lock(&g_ring_lock); logcat_entry_t* s=&g_ring[g_head]; s->len=(uint16_t)logcat_safe_copy(s->text,g_entry_bytes,line); uint32_t n=g_head+1; if(n>=g_ring_entries)n=0; if(n==g_tail){g_tail++; if(g_tail>=g_ring_entries)g_tail=0;} g_head=n; pthread_mutex_unlock(&g_ring_lock);} 
static void* logcat_capture_loop(void* arg){(void)arg; g_logcat_pipe=popen("logcat -v brief","r"); if(!g_logcat_pipe){atomic_store(&g_capture_running,0);return NULL;} char line[LOGCAT_ENTRY_MAX_BYTES]; while(atomic_load(&g_capture_running)){ if(!fgets(line,(int)sizeof(line),g_logcat_pipe))break; logcat_push_line(line);} if(g_logcat_pipe){pclose(g_logcat_pipe); g_logcat_pipe=NULL;} atomic_store(&g_capture_running,0); return NULL;}
JNIEXPORT jint JNICALL Java_com_vectras_vm_core_NativeLogcatBridge_nativeInitCapture(JNIEnv* env, jclass clazz, jint ringEntries, jint entryBytes){(void)env;(void)clazz; if(ringEntries<=0)ringEntries=256; if(ringEntries>LOGCAT_RING_MAX_ENTRIES)ringEntries=LOGCAT_RING_MAX_ENTRIES; if(entryBytes<=64)entryBytes=256; if(entryBytes>LOGCAT_ENTRY_MAX_BYTES)entryBytes=LOGCAT_ENTRY_MAX_BYTES; pthread_mutex_lock(&g_ring_lock); g_ring_entries=(uint32_t)ringEntries; g_entry_bytes=(uint32_t)entryBytes; g_head=0; g_tail=0; for(uint32_t i=0;i<g_ring_entries;i++){g_ring[i].len=0;g_ring[i].text[0]='\0';} pthread_mutex_unlock(&g_ring_lock); if(atomic_exchange(&g_capture_running,1)==1)return 0; if(pthread_create(&g_capture_thread,NULL,logcat_capture_loop,NULL)!=0){atomic_store(&g_capture_running,0);return -1;} return 0;}
JNIEXPORT jstring JNICALL Java_com_vectras_vm_core_NativeLogcatBridge_nativeReadBatch(JNIEnv* env, jclass clazz, jint maxEvents){(void)clazz; if(maxEvents<=0)maxEvents=1; if(maxEvents>256)maxEvents=256; char payload[LOGCAT_BATCH_PAYLOAD_BYTES]; uint32_t out=0; int count=0; pthread_mutex_lock(&g_ring_lock); while(g_tail!=g_head && count<maxEvents && out+2<sizeof(payload)){ logcat_entry_t* s=&g_ring[g_tail]; if(s->len>0){uint32_t copy=s->len; if(out+copy+1>=sizeof(payload)) copy=(uint32_t)(sizeof(payload)-out-2); memcpy(payload+out,s->text,copy); out+=copy; payload[out++]='\n'; count++;} s->len=0; s->text[0]='\0'; g_tail++; if(g_tail>=g_ring_entries) g_tail=0;} pthread_mutex_unlock(&g_ring_lock); payload[out]='\0'; return (*env)->NewStringUTF(env,payload);} 
JNIEXPORT void JNICALL Java_com_vectras_vm_core_NativeLogcatBridge_nativeShutdownCapture(JNIEnv* env, jclass clazz){(void)env;(void)clazz; if(atomic_exchange(&g_capture_running,0)==1) pthread_join(g_capture_thread,NULL);}
