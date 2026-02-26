#ifndef BITOMEGA_H
#define BITOMEGA_H

#include <stddef.h>
#include <stdint.h>

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
  BITOMEGA_NEG = 0,   /* contraction */
  BITOMEGA_ZERO = 1,  /* neutral */
  BITOMEGA_POS = 2,   /* expansion */
  BITOMEGA_MIX = 3,   /* transitional (+/-) */
  BITOMEGA_VOID = 4,  /* out-of-domain / undefined */
  BITOMEGA_EDGE = 5,  /* threshold / boundary */
  BITOMEGA_FLOW = 6,  /* dynamic flow */
  BITOMEGA_LOCK = 7,  /* stable lock */
  BITOMEGA_NOISE = 8, /* measured noise */
  BITOMEGA_META = 9   /* meta-observer / controller */
} bitomega_state_t;

typedef enum {
  BITOMEGA_DIR_NONE = 0,   /* no direction / unknown */
  BITOMEGA_DIR_UP = 1,     /* expansion */
  BITOMEGA_DIR_DOWN = 2,   /* contraction */
  BITOMEGA_DIR_FORWARD = 3,/* propagation */
  BITOMEGA_DIR_RECURSE = 4,/* recursion / feedback */
  BITOMEGA_DIR_NULL = 5    /* forced null / dropout */
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
