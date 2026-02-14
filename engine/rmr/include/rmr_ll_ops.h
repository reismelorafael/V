#ifndef RMR_LL_OPS_H
#define RMR_LL_OPS_H

#include <stdint.h>

static inline uint32_t rmr_mask_u32(uint32_t predicate) {
  return 0u - (uint32_t)(predicate != 0u);
}

static inline uint32_t select_u32(uint32_t mask, uint32_t a, uint32_t b) {
  return (a & mask) | (b & ~mask);
}

static inline uint8_t select_u8(uint32_t mask, uint8_t a, uint8_t b) {
  return (uint8_t)select_u32(mask, (uint32_t)a, (uint32_t)b);
}

#endif
