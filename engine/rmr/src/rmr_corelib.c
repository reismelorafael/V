#include "rmr_corelib.h"

static int rmr_is_ws(uint8_t c) {
  return (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\v') ? 1 : 0;
}

size_t rmr_len_u8(const uint8_t *s) {
  if (!s) return 0u;

  const uint8_t *p = s;
  while (((uintptr_t)p & (sizeof(size_t) - 1u)) != 0u) {
    if (*p == 0u) return (size_t)(p - s);
    ++p;
  }

  const size_t *w = (const size_t *)p;
  const size_t ones = ~(size_t)0 / 0xFFu;
  const size_t highs = ones << 7;
  for (;;) {
    size_t v = *w;
    if (((v - ones) & (~v) & highs) != 0u) {
      p = (const uint8_t *)w;
      while (*p != 0u) ++p;
      return (size_t)(p - s);
    }
    ++w;
  }
}

size_t rmr_trim_ws(const uint8_t *buf, size_t len, size_t *out_start) {
  size_t start = 0u;
  size_t end = len;
  if (!buf) {
    if (out_start) *out_start = 0u;
    return 0u;
  }

  while (start < len && rmr_is_ws(buf[start])) ++start;
  while (end > start && rmr_is_ws(buf[end - 1u])) --end;

  if (out_start) *out_start = start;
  return end - start;
}

int rmr_mem_eq(const void *a, const void *b, size_t len) {
  const uint8_t *pa = (const uint8_t *)a;
  const uint8_t *pb = (const uint8_t *)b;
  size_t i = 0u;

  if (len == 0u) return 1;
  if (!pa || !pb) return 0;

  while (i < len && (((uintptr_t)(pa + i) | (uintptr_t)(pb + i)) & (sizeof(size_t) - 1u)) != 0u) {
    if (pa[i] != pb[i]) return 0;
    ++i;
  }

  while (i + sizeof(size_t) <= len) {
    if (*(const size_t *)(const void *)(pa + i) != *(const size_t *)(const void *)(pb + i)) return 0;
    i += sizeof(size_t);
  }

  while (i < len) {
    if (pa[i] != pb[i]) return 0;
    ++i;
  }

  return 1;
}

void *rmr_mem_copy(void *dst, const void *src, size_t len) {
  uint8_t *d = (uint8_t *)dst;
  const uint8_t *s = (const uint8_t *)src;
  size_t i = 0u;

  if (!d || !s || len == 0u) return dst;

  if (d < s || d >= (s + len)) {
    while (i < len && (((uintptr_t)(d + i) | (uintptr_t)(s + i)) & (sizeof(size_t) - 1u)) != 0u) {
      d[i] = s[i];
      ++i;
    }
    while (i + sizeof(size_t) <= len) {
      *(size_t *)(void *)(d + i) = *(const size_t *)(const void *)(s + i);
      i += sizeof(size_t);
    }
    while (i < len) {
      d[i] = s[i];
      ++i;
    }
    return dst;
  }

  i = len;
  while (i > 0u && (((uintptr_t)(d + i) | (uintptr_t)(s + i)) & (sizeof(size_t) - 1u)) != 0u) {
    --i;
    d[i] = s[i];
  }
  while (i >= sizeof(size_t)) {
    i -= sizeof(size_t);
    *(size_t *)(void *)(d + i) = *(const size_t *)(const void *)(s + i);
  }
  while (i > 0u) {
    --i;
    d[i] = s[i];
  }

  return dst;
}

void *rmr_mem_set(void *dst, uint8_t value, size_t len) {
  uint8_t *d = (uint8_t *)dst;
  size_t i = 0u;
  size_t fill = value;

  if (!d || len == 0u) return dst;

  while (i < len && ((uintptr_t)(d + i) & (sizeof(size_t) - 1u)) != 0u) {
    d[i++] = value;
  }

  for (size_t shift = 8u; shift < rmr_gpio_word_bits; shift += 8u) {
    fill |= ((size_t)value << shift);
  }

  while (i + sizeof(size_t) <= len) {
    *(size_t *)(void *)(d + i) = fill;
    i += sizeof(size_t);
  }

  while (i < len) d[i++] = value;
  return dst;
}

const uint8_t *rmr_find_byte(const void *buf, size_t len, uint8_t byte) {
  const uint8_t *p = (const uint8_t *)buf;
  size_t i = 0u;

  if (!p) return NULL;

  while (i < len && (((uintptr_t)(p + i)) & (sizeof(size_t) - 1u)) != 0u) {
    if (p[i] == byte) return p + i;
    ++i;
  }

  if (i + sizeof(size_t) <= len) {
    size_t bmask = byte;
    for (size_t shift = 8u; shift < rmr_gpio_word_bits; shift += 8u) {
      bmask |= ((size_t)byte << shift);
    }
    const size_t ones = ~(size_t)0 / 0xFFu;
    const size_t highs = ones << 7;

    while (i + sizeof(size_t) <= len) {
      size_t v = *(const size_t *)(const void *)(p + i);
      size_t x = v ^ bmask;
      if (((x - ones) & (~x) & highs) != 0u) {
        for (size_t j = 0; j < sizeof(size_t); ++j) {
          if (p[i + j] == byte) return p + i + j;
        }
      }
      i += sizeof(size_t);
    }
  }

  while (i < len) {
    if (p[i] == byte) return p + i;
    ++i;
  }
  return NULL;
}
