#ifndef BITOMEGA_H
#define BITOMEGA_H

#include <stddef.h>
#include <stdint.h>

#include "zero.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 * BITΩ (BitOmega) — Unified Directed State System
 * Minimal, stable C API intended to be used by the RMR unified kernel
 * and by higher-level modules (policy, bridge, telemetry).
 *
 * Design goals:
 *  - 10 canonical states (finite, enumerable)
 *  - explicit direction/channel information
 *  - deterministic transition operator Δ(state, context) -> state
 *  - invariant checks to keep the runtime coherent
 */

typedef enum {
  BITOMEGA_NEG = RMR_ZERO_BITOMEGA_STATE_NEG_U8,   /* contraction */
  BITOMEGA_ZERO = RMR_ZERO_BITOMEGA_STATE_ZERO_U8,  /* neutral */
  BITOMEGA_POS = RMR_ZERO_BITOMEGA_STATE_POS_U8,   /* expansion */
  BITOMEGA_MIX = RMR_ZERO_BITOMEGA_STATE_MIX_U8,   /* transitional (+/-) */
  BITOMEGA_VOID = RMR_ZERO_BITOMEGA_STATE_VOID_U8,  /* out-of-domain / undefined */
  BITOMEGA_EDGE = RMR_ZERO_BITOMEGA_STATE_EDGE_U8,  /* threshold / boundary */
  BITOMEGA_FLOW = RMR_ZERO_BITOMEGA_STATE_FLOW_U8,  /* dynamic flow */
  BITOMEGA_LOCK = RMR_ZERO_BITOMEGA_STATE_LOCK_U8,  /* stable lock */
  BITOMEGA_NOISE = RMR_ZERO_BITOMEGA_STATE_NOISE_U8, /* measured noise */
  BITOMEGA_META = RMR_ZERO_BITOMEGA_STATE_META_U8   /* meta-observer / controller */
} bitomega_state_t;

typedef enum {
  BITOMEGA_DIR_NONE = RMR_ZERO_BITOMEGA_DIR_NONE_U8,   /* no direction / unknown */
  BITOMEGA_DIR_UP = RMR_ZERO_BITOMEGA_DIR_UP_U8,     /* expansion */
  BITOMEGA_DIR_DOWN = RMR_ZERO_BITOMEGA_DIR_DOWN_U8,   /* contraction */
  BITOMEGA_DIR_FORWARD = RMR_ZERO_BITOMEGA_DIR_FORWARD_U8,/* propagation */
  BITOMEGA_DIR_RECURSE = RMR_ZERO_BITOMEGA_DIR_RECURSE_U8,/* recursion / feedback */
  BITOMEGA_DIR_NULL = RMR_ZERO_BITOMEGA_DIR_NULL_U8    /* forced null / dropout */
} bitomega_dir_t;

typedef struct {
  /* primary state */
  bitomega_state_t state;
  /* direction channel */
  bitomega_dir_t dir;

  /* scalar fields used by Δ — all normalized to [0,1] by convention */
  float coherence; /* higher = more stable */
  float entropy;   /* higher = more noisy */
} bitomega_node_t;

typedef struct {
  /* normalized context signals in [0,1] */
  float coherence_in;
  float entropy_in;
  float noise_in;

  /* optional: system load proxy in [0,1] */
  float load;

  /* deterministic seed (may be 0) */
  uint64_t seed;
} bitomega_ctx_t;

typedef enum {
  BITOMEGA_OK = 0,
  BITOMEGA_ERR_ARG = -1,
  BITOMEGA_ERR_RANGE = -2
} bitomega_status_t;

/* Helpers */
const char *bitomega_state_name(bitomega_state_t s);
const char *bitomega_dir_name(bitomega_dir_t d);

/* Normalize a float to [0,1] safely (NaN -> 0). */
float bitomega_norm01(float x);

/* Canonical context constructor (all zeros, seed kept). */
bitomega_ctx_t bitomega_ctx_default(uint64_t seed);

/*
 * Δ transition operator.
 * - deterministic given (node, ctx)
 * - modifies node fields (coherence/entropy) conservatively
 */
bitomega_status_t bitomega_transition(bitomega_node_t *node, const bitomega_ctx_t *ctx);

/*
 * Invariant check.
 * Returns 1 if invariants hold, 0 otherwise.
 *
 * Invariants (minimal):
 *  - coherence/entropy in [0,1]
 *  - VOID implies DIR_NONE or DIR_NULL
 *  - META implies coherence >= entropy (observer cannot be "less coherent" than noise)
 */
int bitomega_invariant_ok(const bitomega_node_t *node);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* BITOMEGA_H */
