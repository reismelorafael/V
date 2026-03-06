/* rmr_hw_detect.c - autodetecção avançada low-level */
#include "rmr_hw_detect.h"
#include "rmr_cycles.h"
#include "rmr_ll_ops.h"

static u32 RmR_IsLittleEndian(void){
  unsigned short v = 0x0102u;
  return (*((unsigned char*)&v) == 0x02u) ? 1u : 0u;
}

static u32 RmR_ArchDetect(void){
#if defined(__x86_64__) || defined(_M_X64)
  return 2u;
#elif defined(__i386__) || defined(_M_IX86)
  return 1u;
#elif defined(__aarch64__) || defined(_M_ARM64)
  return 4u;
#elif defined(__arm__) || defined(_M_ARM)
  return 3u;
#elif defined(__riscv)
  return 5u;
#elif defined(__mips__)
  return 6u;
#elif defined(__powerpc64__) || defined(__ppc64__)
  return 7u;
#elif defined(__powerpc__) || defined(__ppc__)
  return 8u;
#elif defined(__s390x__)
  return 9u;
#else
  return 0u;
#endif
}

static u32 RmR_ArchHex(u32 arch){
  if(arch == 1u) return 0x86u;
  if(arch == 2u) return 0x8664u;
  if(arch == 3u) return 0xA32u;
  if(arch == 4u) return 0xA64u;
  if(arch == 5u) return 0x52u;
  if(arch == 6u) return 0x6D31u;
  if(arch == 7u) return 0x7064u;
  if(arch == 8u) return 0x7032u;
  if(arch == 9u) return 0x390u;
  return 0u;
}

static u32 RmR_CachelineHint(u32 arch){
  if(arch == 7u || arch == 9u) return 128u;
  if(arch == 2u || arch == 1u || arch == 4u || arch == 3u || arch == 5u || arch == 8u) return 64u;
  return 64u;
}

static u32 RmR_CacheHintL4(u32 arch){
  if(arch == 7u || arch == 9u) return 64u * 1024u * 1024u;
  if(arch == 2u || arch == 4u) return 32u * 1024u * 1024u;
  if(arch == 5u) return 16u * 1024u * 1024u;
  if(arch == 1u || arch == 3u || arch == 8u) return 8u * 1024u * 1024u;
  return 0u;
}

static u32 RmR_PageHint(u32 arch){
  if(arch == 7u) return 65536u;
  return 4096u;
}

static u32 RmR_MemBusHint(u32 arch){
  if(arch == 2u || arch == 4u || arch == 7u || arch == 9u) return 128u;
  if(arch == 1u || arch == 3u || arch == 5u || arch == 6u || arch == 8u) return 64u;
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
  if(arch == 4u || arch == 3u) return 4u;
  return 1u;
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
