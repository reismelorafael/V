#include "bitomega.h"

#include <math.h>

static float clamp01(float x) {
  if (!(x == x)) return 0.0f; /* NaN -> 0 */
  if (x < 0.0f) return 0.0f;
  if (x > 1.0f) return 1.0f;
  return x;
}

const char *bitomega_state_name(bitomega_state_t s) {
  switch (s) {
    case BITOMEGA_NEG: return "NEG";
    case BITOMEGA_ZERO: return "ZERO";
    case BITOMEGA_POS: return "POS";
    case BITOMEGA_MIX: return "MIX";
    case BITOMEGA_VOID: return "VOID";
    case BITOMEGA_EDGE: return "EDGE";
    case BITOMEGA_FLOW: return "FLOW";
    case BITOMEGA_LOCK: return "LOCK";
    case BITOMEGA_NOISE: return "NOISE";
    case BITOMEGA_META: return "META";
    default: return "UNKNOWN";
  }
}

const char *bitomega_dir_name(bitomega_dir_t d) {
  switch (d) {
    case BITOMEGA_DIR_NONE: return "NONE";
    case BITOMEGA_DIR_UP: return "UP";
    case BITOMEGA_DIR_DOWN: return "DOWN";
    case BITOMEGA_DIR_FORWARD: return "FORWARD";
    case BITOMEGA_DIR_RECURSE: return "RECURSE";
    case BITOMEGA_DIR_NULL: return "NULL";
    default: return "UNKNOWN";
  }
}

float bitomega_norm01(float x) { return clamp01(x); }

bitomega_ctx_t bitomega_ctx_default(uint64_t seed) {
  bitomega_ctx_t c;
  c.coherence_in = 0.0f;
  c.entropy_in = 0.0f;
  c.noise_in = 0.0f;
  c.load = 0.0f;
  c.seed = seed;
  return c;
}

int bitomega_invariant_ok(const bitomega_node_t *node) {
  if (!node) return 0;
  if (node->coherence < 0.0f || node->coherence > 1.0f) return 0;
  if (node->entropy < 0.0f || node->entropy > 1.0f) return 0;

  if (node->state == BITOMEGA_VOID) {
    if (!(node->dir == BITOMEGA_DIR_NONE || node->dir == BITOMEGA_DIR_NULL)) return 0;
  }
  if (node->state == BITOMEGA_META) {
    if (node->coherence + 1e-6f < node->entropy) return 0;
  }
  return 1;
}

static void update_fields(bitomega_node_t *n, const bitomega_ctx_t *c) {
  /* conservative smoothing toward inputs */
  const float a = 0.25f; /* smoothing factor */
  n->coherence = clamp01((1.0f - a) * clamp01(n->coherence) + a * clamp01(c->coherence_in));
  n->entropy   = clamp01((1.0f - a) * clamp01(n->entropy)   + a * clamp01(c->entropy_in));
}

bitomega_status_t bitomega_transition(bitomega_node_t *node, const bitomega_ctx_t *ctx) {
  if (!node || !ctx) return BITOMEGA_ERR_ARG;

  /* normalize context and node fields */
  bitomega_ctx_t c = *ctx;
  c.coherence_in = clamp01(c.coherence_in);
  c.entropy_in   = clamp01(c.entropy_in);
  c.noise_in     = clamp01(c.noise_in);
  c.load         = clamp01(c.load);

  node->coherence = clamp01(node->coherence);
  node->entropy   = clamp01(node->entropy);

  update_fields(node, &c);

  /* Derived signals */
  const float coh = node->coherence;
  const float ent = node->entropy;
  const float noi = c.noise_in;
  const float ld  = c.load;

  /* Δ rules (minimal, deterministic) */
  switch (node->state) {
    case BITOMEGA_FLOW:
      if (coh > 0.80f && noi < 0.30f) { node->state = BITOMEGA_LOCK; node->dir = BITOMEGA_DIR_RECURSE; }
      else if (noi > 0.70f || ent > 0.70f) { node->state = BITOMEGA_NOISE; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_LOCK:
      if (noi > 0.55f || ent > 0.65f) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      else if (ld > 0.85f) { node->state = BITOMEGA_EDGE; node->dir = BITOMEGA_DIR_FORWARD; }
      break;

    case BITOMEGA_MIX:
      if (coh > ent + 0.10f) { node->state = BITOMEGA_POS; node->dir = BITOMEGA_DIR_UP; }
      else if (ent > coh + 0.10f) { node->state = BITOMEGA_NEG; node->dir = BITOMEGA_DIR_DOWN; }
      else { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_POS:
      if (ld > 0.90f) { node->state = BITOMEGA_EDGE; node->dir = BITOMEGA_DIR_FORWARD; }
      else if (noi > 0.60f) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      break;

    case BITOMEGA_NEG:
      if (noi < 0.20f && coh > 0.60f) { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      else if (ent > 0.80f) { node->state = BITOMEGA_VOID; node->dir = BITOMEGA_DIR_NULL; }
      break;

    case BITOMEGA_ZERO:
      if (coh > 0.70f && noi < 0.40f) { node->state = BITOMEGA_FLOW; node->dir = BITOMEGA_DIR_FORWARD; }
      else if (noi > 0.80f) { node->state = BITOMEGA_NOISE; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_EDGE:
      if (ld < 0.60f && coh > 0.60f) { node->state = BITOMEGA_FLOW; node->dir = BITOMEGA_DIR_FORWARD; }
      else if (noi > 0.70f) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      break;

    case BITOMEGA_NOISE:
      if (noi < 0.35f && coh > 0.55f) { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      else if (ent > 0.90f) { node->state = BITOMEGA_VOID; node->dir = BITOMEGA_DIR_NULL; }
      break;

    case BITOMEGA_VOID:
      /* VOID stays VOID unless coherence reappears */
      if (coh > 0.80f && noi < 0.20f) { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_META:
      /* META steers toward coherence */
      if (ent > coh) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      else if (coh > 0.85f) { node->state = BITOMEGA_LOCK; node->dir = BITOMEGA_DIR_RECURSE; }
      break;

    default:
      return BITOMEGA_ERR_RANGE;
  }

  /* final invariant clamp */
  if (!bitomega_invariant_ok(node)) {
    /* self-heal: drop to ZERO in safe mode */
    node->state = BITOMEGA_ZERO;
    node->dir = BITOMEGA_DIR_NONE;
    node->coherence = clamp01(node->coherence);
    node->entropy = clamp01(node->entropy);
  }
  return BITOMEGA_OK;
}
