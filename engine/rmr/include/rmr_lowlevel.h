#ifndef RMR_LOWLEVEL_H
#define RMR_LOWLEVEL_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

uint32_t rmr_lowlevel_rotl32(uint32_t v, uint32_t n);
uint32_t rmr_lowlevel_fold32(uint32_t a, uint32_t b, uint32_t c, uint32_t d);
uint32_t rmr_lowlevel_reduce_xor(const uint8_t* data, size_t len);
uint32_t rmr_lowlevel_checksum32(const uint8_t* data, size_t len, uint32_t seed);

#ifdef __cplusplus
}
#endif

#endif
