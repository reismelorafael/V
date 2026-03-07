#ifndef RMR_ATTRACTOR_H
#define RMR_ATTRACTOR_H

#include "bitomega.h"
#include "rmr_types.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_ATTR_SOURCE = 1u,
  RMR_ATTR_SPIRAL = 2u,
  RMR_ATTR_LEMNISC = 3u,
  RMR_ATTR_STRANGE = 4u,
  RMR_ATTR_LIMIT = 5u,
  RMR_ATTR_TOROID = 6u
} RmR_AttractorClass;

typedef struct {
  u32 entropy_hist[8];
  u32 miss_hist[8];
  u8 hist_idx;
  RmR_AttractorClass current;
} RmR_AttractorState;

RmR_AttractorClass RmR_Attractor_Classify(RmR_AttractorState *st, double entropy, u32 miss_score);
bitomega_state_t RmR_Attractor_ToBitOmega(RmR_AttractorClass cls);

#ifdef __cplusplus
}
#endif

#endif
