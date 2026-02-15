#ifndef RMR_LL_OPS_H
#define RMR_LL_OPS_H

#include <stdint.h>

typedef struct RmR_LL_AsmProbe {
  uint32_t has_probe;
  uint32_t reg_signature_0;
  uint32_t reg_signature_1;
  uint32_t reg_signature_2;
  uint32_t feature_bits_0;
  uint32_t feature_bits_1;
} RmR_LL_AsmProbe;

static inline uint32_t rmr_mask_u32(uint32_t predicate) {
  return 0u - (uint32_t)(predicate != 0u);
}

static inline uint32_t select_u32(uint32_t mask, uint32_t a, uint32_t b) {
  return (a & mask) | (b & ~mask);
}

static inline uint8_t select_u8(uint32_t mask, uint8_t a, uint8_t b) {
  return (uint8_t)select_u32(mask, (uint32_t)a, (uint32_t)b);
}

uint64_t RmR_LL_ReadCycles(void);
void RmR_LL_FenceFull(void);
void RmR_LL_FenceLoad(void);
void RmR_LL_FenceStore(void);
void RmR_LL_Pause(void);
void RmR_LL_PrefetchRead(const void *ptr);
void RmR_LL_PrefetchWrite(const void *ptr);
uint32_t RmR_LL_BitScanForward32(uint32_t v);
uint32_t RmR_LL_BitScanReverse32(uint32_t v);
uint32_t RmR_LL_Rotl32(uint32_t v, uint32_t shift);
uint32_t RmR_LL_Rotr32(uint32_t v, uint32_t shift);
uint64_t RmR_LL_Rotl64(uint64_t v, uint32_t shift);
uint64_t RmR_LL_Rotr64(uint64_t v, uint32_t shift);
uint32_t RmR_LL_PopCount32(uint32_t v);
uint32_t RmR_LL_PopCount64(uint64_t v);
uint32_t RmR_LL_Clz32(uint32_t v);
uint32_t RmR_LL_Ctz32(uint32_t v);
uint32_t RmR_LL_Clz64(uint64_t v);
uint32_t RmR_LL_Ctz64(uint64_t v);
int RmR_LL_ReadAsmProbe(RmR_LL_AsmProbe *out);

#endif
