/* rmr_hw_detect.c - autodetecção avançada low-level */
#include "rmr_hw_detect.h"
#include "rmr_cycles.h"
#include "rmr_ll_ops.h"
#include "zero.h"

static u32 RmR_IsLittleEndian(void){
  unsigned short v = 0x0102u;
  return (*((unsigned char*)&v) == 0x02u) ? 1u : 0u;
}

static u32 RmR_ArchDetect(void){
#if defined(__x86_64__) || defined(_M_X64)
  return RMR_ZERO_HW_ARCH_X86_64_U32;
#elif defined(__i386__) || defined(_M_IX86)
  return RMR_ZERO_HW_ARCH_I386_U32;
#elif defined(__aarch64__) || defined(_M_ARM64)
  return RMR_ZERO_HW_ARCH_ARM64_U32;
#elif defined(__arm__) || defined(_M_ARM)
  return RMR_ZERO_HW_ARCH_ARM_U32;
#elif defined(__riscv)
  return RMR_ZERO_HW_ARCH_RISCV_U32;
#elif defined(__mips__)
  return RMR_ZERO_HW_ARCH_MIPS_U32;
#elif defined(__powerpc64__) || defined(__ppc64__)
  return RMR_ZERO_HW_ARCH_PPC64_U32;
#elif defined(__powerpc__) || defined(__ppc__)
  return RMR_ZERO_HW_ARCH_PPC32_U32;
#elif defined(__s390x__)
  return RMR_ZERO_HW_ARCH_S390X_U32;
#else
  return RMR_ZERO_HW_ARCH_UNKNOWN_U32;
#endif
}

static u32 RmR_ArchHex(u32 arch){
  if(arch == RMR_ZERO_HW_ARCH_I386_U32) return RMR_ZERO_HW_ARCH_TAG_I386_U32;
  if(arch == RMR_ZERO_HW_ARCH_X86_64_U32) return RMR_ZERO_HW_ARCH_TAG_X86_64_U32;
  if(arch == RMR_ZERO_HW_ARCH_ARM_U32) return RMR_ZERO_HW_ARCH_TAG_ARM_U32;
  if(arch == RMR_ZERO_HW_ARCH_ARM64_U32) return RMR_ZERO_HW_ARCH_TAG_ARM64_U32;
  if(arch == RMR_ZERO_HW_ARCH_RISCV_U32) return RMR_ZERO_HW_ARCH_TAG_RISCV_U32;
  if(arch == RMR_ZERO_HW_ARCH_MIPS_U32) return RMR_ZERO_HW_ARCH_TAG_MIPS_U32;
  if(arch == RMR_ZERO_HW_ARCH_PPC64_U32) return RMR_ZERO_HW_ARCH_TAG_PPC64_U32;
  if(arch == RMR_ZERO_HW_ARCH_PPC32_U32) return RMR_ZERO_HW_ARCH_TAG_PPC32_U32;
  if(arch == RMR_ZERO_HW_ARCH_S390X_U32) return RMR_ZERO_HW_ARCH_TAG_S390X_U32;
  return 0u;
}

static u32 RmR_CachelineHint(u32 arch){
  if(arch == RMR_ZERO_HW_ARCH_PPC64_U32 || arch == RMR_ZERO_HW_ARCH_S390X_U32) return 128u;
  if(arch == RMR_ZERO_HW_ARCH_X86_64_U32 || arch == RMR_ZERO_HW_ARCH_I386_U32 || arch == RMR_ZERO_HW_ARCH_ARM64_U32 || arch == RMR_ZERO_HW_ARCH_ARM_U32 || arch == RMR_ZERO_HW_ARCH_RISCV_U32 || arch == RMR_ZERO_HW_ARCH_PPC32_U32) return 64u;
  return 64u;
}

static u32 RmR_CacheHintL4(u32 arch){
  if(arch == RMR_ZERO_HW_ARCH_PPC64_U32 || arch == RMR_ZERO_HW_ARCH_S390X_U32) return 64u * 1024u * 1024u;
  if(arch == RMR_ZERO_HW_ARCH_X86_64_U32 || arch == RMR_ZERO_HW_ARCH_ARM64_U32) return 32u * 1024u * 1024u;
  if(arch == RMR_ZERO_HW_ARCH_RISCV_U32) return 16u * 1024u * 1024u;
  if(arch == RMR_ZERO_HW_ARCH_I386_U32 || arch == RMR_ZERO_HW_ARCH_ARM_U32 || arch == RMR_ZERO_HW_ARCH_PPC32_U32) return 8u * 1024u * 1024u;
  return 0u;
}

static u32 RmR_PageHint(u32 arch){
  if(arch == RMR_ZERO_HW_ARCH_PPC64_U32) return 65536u;
  return 4096u;
}

static u32 RmR_MemBusHint(u32 arch){
  if(arch == RMR_ZERO_HW_ARCH_X86_64_U32 || arch == RMR_ZERO_HW_ARCH_ARM64_U32 || arch == RMR_ZERO_HW_ARCH_PPC64_U32 || arch == RMR_ZERO_HW_ARCH_S390X_U32) return 128u;
  if(arch == RMR_ZERO_HW_ARCH_I386_U32 || arch == RMR_ZERO_HW_ARCH_ARM_U32 || arch == RMR_ZERO_HW_ARCH_RISCV_U32 || arch == RMR_ZERO_HW_ARCH_MIPS_U32 || arch == RMR_ZERO_HW_ARCH_PPC32_U32) return 64u;
  return 64u;
}

static void RmR_AsmProbe(u32 arch, RmR_HW_Info *out){
  (void)arch;
  RmR_LL_AsmProbe probe;
  out->has_asm_probe = 0u;
  out->reg_signature_0 = 0u;
  out->reg_signature_1 = 0u;
  out->reg_signature_2 = 0u;
  out->feature_bits_0 = 0u;
  out->feature_bits_1 = 0u;

  if (RmR_LL_ReadAsmProbe(&probe) != 0) return;
  out->has_asm_probe = probe.has_probe;
  out->reg_signature_0 = probe.reg_signature_0;
  out->reg_signature_1 = probe.reg_signature_1;
  out->reg_signature_2 = probe.reg_signature_2;
  out->feature_bits_0 = probe.feature_bits_0;
  out->feature_bits_1 = probe.feature_bits_1;
}

static u32 RmR_GpioWordBits(u32 ptr_bits){
  if(ptr_bits >= 64u) return 64u;
  return 32u;
}

static u32 RmR_GpioPinStride(u32 arch){
  if(arch == RMR_ZERO_HW_ARCH_ARM64_U32 || arch == RMR_ZERO_HW_ARCH_ARM_U32) return 4u;
  return RMR_ZERO_HW_ARCH_I386_U32;
}

void RmR_HW_Detect(RmR_HW_Info *out){
  if(!out) return;
  u32 arch = RmR_ArchDetect();
  out->arch = arch;
  out->arch_hex = RmR_ArchHex(arch);
  out->word_bits = (u32)(sizeof(unsigned long) * 8u);
  out->ptr_bits = (u32)(sizeof(void*) * 8u);
  out->is_little_endian = RmR_IsLittleEndian();
  out->has_cycle_counter = (RmR_ReadCycles() != 0u) ? 1u : 0u;
  out->cacheline_bytes = RmR_CachelineHint(arch);
  out->cache_hint_l1 = 32u * 1024u;
  out->cache_hint_l2 = 256u * 1024u;
  out->cache_hint_l3 = 1024u * 1024u;
  out->cache_hint_l4 = RmR_CacheHintL4(arch);
  out->page_bytes = RmR_PageHint(arch);
  out->mem_bus_bits = RmR_MemBusHint(arch);
  out->gpio_word_bits = RmR_GpioWordBits(out->ptr_bits);
  out->gpio_pin_stride = RmR_GpioPinStride(arch);
  out->align_bytes = out->cacheline_bytes;
  RmR_AsmProbe(arch, out);
}
