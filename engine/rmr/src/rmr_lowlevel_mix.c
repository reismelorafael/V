#include "rmr_lowlevel.h"

#if defined(__x86_64__)
extern uint32_t rmr_lowlevel_mix_step_x86_64(uint32_t a, uint32_t b);
#endif

uint32_t rmr_lowlevel_fold32(uint32_t a, uint32_t b, uint32_t c, uint32_t d) {
#if defined(__x86_64__)
    uint32_t x = rmr_lowlevel_mix_step_x86_64(a, b);
#else
    uint32_t x = a ^ rmr_lowlevel_rotl32(b, 5u);
    x += 0x9E3779B9u;
#endif
    x ^= rmr_lowlevel_rotl32(c, 13u);
    x += (d ^ 0x85EBCA6Bu);
    x ^= (x >> 16u);
    x *= 0x7FEB352Du;
    x ^= (x >> 15u);
    x *= 0x846CA68Bu;
    x ^= (x >> 16u);
    return x;
}
