/* rmr_hw_detect.h - autodetecção avançada low-level */
#ifndef RMR_HW_DETECT_H
#define RMR_HW_DETECT_H

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

typedef struct {
  u32 arch;
  u32 arch_hex;
  u32 word_bits;
  u32 ptr_bits;
  u32 is_little_endian;
  u32 has_cycle_counter;
  u32 has_asm_probe;
  u32 reg_signature_0;
  u32 reg_signature_1;
  u32 reg_signature_2;
  u32 feature_bits_0;
  u32 feature_bits_1;
  u32 cacheline_bytes;
  u32 cache_hint_l1;
  u32 cache_hint_l2;
  u32 cache_hint_l3;
  u32 cache_hint_l4;
  u32 page_bytes;
  u32 mem_bus_bits;
  u32 gpio_word_bits;
  u32 gpio_pin_stride;
  u32 align_bytes;
} RmR_HW_Info;

void RmR_HW_Detect(RmR_HW_Info *out);

#endif
