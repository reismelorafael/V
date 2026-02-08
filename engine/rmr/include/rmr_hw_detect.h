/* rmr_hw_detect.h - autodetecção avançada low-level */
#ifndef RMR_HW_DETECT_H
#define RMR_HW_DETECT_H

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

typedef struct {
  u32 arch;
  u32 word_bits;
  u32 ptr_bits;
  u32 is_little_endian;
  u32 has_cycle_counter;
  u32 cacheline_bytes;
  u32 cache_hint_l1;
  u32 cache_hint_l2;
  u32 cache_hint_l3;
  u32 page_bytes;
  u32 mem_bus_bits;
  u32 align_bytes;
} RmR_HW_Info;

void RmR_HW_Detect(RmR_HW_Info *out);

#endif
