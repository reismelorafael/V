#include <stdio.h>
#include "rmr_apk_module.h"

int main(int argc, char **argv){
  RmR_ApkProfile profile;
  char plan[2048];
  u64 fp;
  u32 plan_len = 0u;

  if(argc != 5){
    printf("uso: %s <keystore> <store_pass> <alias> <key_pass>\n", argv[0]);
    return 1;
  }

  RmR_ApkModule_InitProfile(&profile);
  profile.abi_mask = RMR_APK_ABI_UNIVERSAL;

  if(RmR_ApkModule_BuildPlan(&profile, argv[1], argv[2], argv[3], argv[4], plan, (u32)sizeof(plan)) == 0u){
    printf("falha ao gerar plano determinístico de compilação/assinatura.\n");
    return 2;
  }

  while(plan[plan_len] != '\0') plan_len++;

  fp = RmR_ApkModule_DeterministicFingerprint((const u8*)plan, plan_len, 0xCAFEBABEULL);

  printf("host_abi_mask=0x%08X\n", RmR_ApkModule_DetectHostAbiMask());
  printf("build_plan=%s\n", plan);
  printf("deterministic_fp=0x%016llX\n", (unsigned long long)fp);

  return 0;
}
