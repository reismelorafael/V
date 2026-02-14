#ifndef RMR_CORELIB_H
#define RMR_CORELIB_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Metadata for deterministic tuning by word width/alignment. */
enum {
  rmr_gpio_word_bits = (unsigned int)(sizeof(size_t) * 8u),
  rmr_ptr_bits = (unsigned int)(sizeof(uintptr_t) * 8u)
};

size_t rmr_len_u8(const uint8_t *s);
size_t rmr_trim_ws(const uint8_t *buf, size_t len, size_t *out_start);
int rmr_mem_eq(const void *a, const void *b, size_t len);
void *rmr_mem_copy(void *dst, const void *src, size_t len);
void *rmr_mem_set(void *dst, uint8_t value, size_t len);
const uint8_t *rmr_find_byte(const void *buf, size_t len, uint8_t byte);

#ifdef __cplusplus
}
#endif

#endif
