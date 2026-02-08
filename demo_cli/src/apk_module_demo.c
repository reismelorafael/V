#include <stdio.h>
#include "rmr_apk_module.h"

static u32 local_strlen(const char *s){
  u32 n = 0u;
  if(!s) return 0u;
  while(s[n] != '\0') n++;
  return n;
}

int main(int argc, char **argv){
  RmR_ApkProfile profile;
  char plan[4096];
  u64 fp;
  u32 plan_len;

  if(argc != 8){
    printf("uso: %s <keystore> <store_pass> <alias> <key_pass> <termux_prefix> <home> <shell>\n", argv[0]);
    return 1;
  }

  RmR_ApkModule_InitProfile(&profile);
  profile.abi_mask = RMR_APK_ABI_UNIVERSAL;
  profile.termux_mode = RmR_ApkModule_DetectTermuxLike(argv[5], argv[6], argv[7]);
  RmR_ApkModule_AutotuneProfile(&profile);

  if(RmR_ApkModule_BuildPlan(&profile, argv[1], argv[2], argv[3], argv[4], plan, (u32)sizeof(plan)) == 0u){
    printf("falha ao gerar plano determinístico de compilação/assinatura.\n");
    return 2;
  }

  plan_len = local_strlen(plan);
  fp = RmR_ApkModule_DeterministicFingerprint((const u8*)plan, plan_len, 0xCAFEBABEULL);

  printf("termux_mode=%u\n", profile.termux_mode);
  printf("host_abi_mask=0x%08X\n", profile.host_abi_mask);
  printf("hw_cacheline=0x%08X\n", profile.hw_cacheline_bytes);
  printf("hw_page=0x%08X\n", profile.hw_page_bytes);
  printf("build_plan=%s\n", plan);
  printf("deterministic_fp=0x%016llX\n", (unsigned long long)fp);

  return 0;
}
