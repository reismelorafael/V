#include "rmr_attractor.h"

RmR_AttractorClass RmR_Attractor_Classify(RmR_AttractorState *st, double entropy, u32 miss_score) {
  u8 i;
  u32 e_sum = 0u;
  u32 m_sum = 0u;
  u32 e_avg;
  u32 m_avg;
  u32 m_var = 0u;
  RmR_AttractorClass cls;
  if (!st) return RMR_ATTR_TOROID;

  if (entropy < 0.0) entropy = 0.0;
  if (entropy > 1.0) entropy = 1.0;

  i = (u8)(st->hist_idx & 7u);
  st->entropy_hist[i] = (u32)(entropy * 256.0);
  st->miss_hist[i] = miss_score;
  st->hist_idx = (u8)((i + 1u) & 7u);

  for (u8 j = 0u; j < 8u; ++j) {
    e_sum += st->entropy_hist[j];
    m_sum += st->miss_hist[j];
  }
  e_avg = e_sum >> 3u;
  m_avg = m_sum >> 3u;

  for (u8 j = 0u; j < 8u; ++j) {
    u32 d = (st->miss_hist[j] > m_avg) ? (st->miss_hist[j] - m_avg) : (m_avg - st->miss_hist[j]);
    m_var += d * d;
  }
  m_var >>= 3u;

  if (e_avg > 230u && m_avg > 800u) cls = RMR_ATTR_SOURCE;
  else if (e_avg > 179u && m_avg > 500u) cls = RMR_ATTR_SPIRAL;
  else if (e_avg > 128u && m_var < 5000u) cls = RMR_ATTR_LEMNISC;
  else if (e_avg > 77u) cls = RMR_ATTR_STRANGE;
  else if (e_avg > 26u && m_var < 1000u) cls = RMR_ATTR_LIMIT;
  else cls = RMR_ATTR_TOROID;

  st->current = cls;
  return cls;
}

bitomega_state_t RmR_Attractor_ToBitOmega(RmR_AttractorClass cls) {
  switch (cls) {
    case RMR_ATTR_SOURCE: return BITOMEGA_POS;
    case RMR_ATTR_SPIRAL: return BITOMEGA_MIX;
    case RMR_ATTR_LEMNISC: return BITOMEGA_NOISE;
    case RMR_ATTR_STRANGE: return BITOMEGA_EDGE;
    case RMR_ATTR_LIMIT: return BITOMEGA_FLOW;
    case RMR_ATTR_TOROID: return BITOMEGA_LOCK;
    default: return BITOMEGA_ZERO;
  }
}
