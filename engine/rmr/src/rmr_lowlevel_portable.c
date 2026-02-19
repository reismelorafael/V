#include "rmr_lowlevel.h"

uint32_t rmr_lowlevel_rotl32(uint32_t v, uint32_t n) {
    const uint32_t s = n & 31u;
    if (s == 0u) return v;
    return (uint32_t)((v << s) | (v >> (32u - s)));
}
