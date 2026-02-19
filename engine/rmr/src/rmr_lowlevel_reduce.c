#include "rmr_lowlevel.h"

uint32_t rmr_lowlevel_reduce_xor(const uint8_t* data, size_t len) {
    if (!data || len == 0u) return 0u;
    uint32_t acc = 0u;
    for (size_t i = 0; i < len; ++i) {
        const uint32_t lane = (uint32_t)data[i] << ((uint32_t)(i & 3u) * 8u);
        acc ^= lane;
        acc = rmr_lowlevel_rotl32(acc, 3u);
    }
    return acc;
}

uint32_t rmr_lowlevel_checksum32(const uint8_t* data, size_t len, uint32_t seed) {
    if (!data || len == 0u) return seed;
    uint32_t state = seed ^ 0xA5A5A5A5u;
    for (size_t i = 0; i < len; ++i) {
        state ^= (uint32_t)data[i] + 0x9Eu + (uint32_t)i;
        state = rmr_lowlevel_rotl32(state, 7u);
        state *= 0x45D9F3Bu;
    }
    return state ^ (uint32_t)len;
}
