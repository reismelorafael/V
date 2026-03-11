#ifndef VECTRA_HW_PROFILE_BRIDGE_INTERNAL_H
#define VECTRA_HW_PROFILE_BRIDGE_INTERNAL_H

#include <stdint.h>

const char* vectra_hw_effective_abi(void);
void vectra_hw_collect_snapshot(uint32_t out_values[9]);

#endif
