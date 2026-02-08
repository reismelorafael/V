/* rmr_apk_module.h - módulo determinístico para compilação de APK multi-arquitetura */
#ifndef RMR_APK_MODULE_H
#define RMR_APK_MODULE_H

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

typedef struct {
  u32 abi_mask;
  u32 min_sdk;
  u32 target_sdk;
  u32 version_code;
  u32 release_signing;
} RmR_ApkProfile;

/* ABI bits: 0=armeabi-v7a, 1=arm64-v8a, 2=x86, 3=x86_64 */
#define RMR_APK_ABI_ARMEABI_V7A (1u << 0)
#define RMR_APK_ABI_ARM64_V8A   (1u << 1)
#define RMR_APK_ABI_X86         (1u << 2)
#define RMR_APK_ABI_X86_64      (1u << 3)
#define RMR_APK_ABI_UNIVERSAL   (RMR_APK_ABI_ARMEABI_V7A | RMR_APK_ABI_ARM64_V8A | RMR_APK_ABI_X86 | RMR_APK_ABI_X86_64)

void RmR_ApkModule_InitProfile(RmR_ApkProfile *out);
u32 RmR_ApkModule_DetectHostAbiMask(void);
u64 RmR_ApkModule_DeterministicFingerprint(const u8 *data, u32 len, u64 seed);
int RmR_ApkModule_ValidateSigningInputs(const char *keystore,
                                        const char *store_password,
                                        const char *key_alias,
                                        const char *key_password);

/*
 * Gera um comando determinístico de build release + assinatura legítima.
 * Retorna tamanho escrito (sem '\0') ou 0 em falha.
 */
u32 RmR_ApkModule_BuildPlan(const RmR_ApkProfile *profile,
                            const char *keystore,
                            const char *store_password,
                            const char *key_alias,
                            const char *key_password,
                            char *out,
                            u32 out_cap);

#endif
