#ifndef RMR_CASM_BRIDGE_H
#define RMR_CASM_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

typedef struct RmR_CASM_Report {
  uint32_t used_asm;
  uint32_t processed_bytes;
  uint32_t checksum;
} RmR_CASM_Report;

uint32_t RmR_CASM_XorFold32(const uint8_t *data, size_t size, RmR_CASM_Report *report);
uint32_t RmR_CASM_XorFold32_C(const uint8_t *data, size_t size);
uint32_t RmR_CASM_XorFold32_Interop(const uint8_t *data, size_t size, uint32_t *asm_out, uint32_t *c_out);

#endif
