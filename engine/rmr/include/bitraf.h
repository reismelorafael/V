#ifndef BITRAF_H
#define BITRAF_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Stable public API for Bitraf engine.
 * Internal engine structs are intentionally hidden.
 */

/* Initializes Bitraf runtime seed state. Returns 0 on success. */
int bitraf_init(uint64_t seed);

/* Deterministic content hash for integrity and signatures. */
uint64_t bitraf_hash(const uint8_t *data, size_t len, uint64_t seed);

/*
 * Encodes input into Bitraf frame format.
 * Returns written bytes, or 0 on error/capacity overflow.
 */
size_t bitraf_compress(const uint8_t *in, size_t in_len,
                       uint8_t *out, size_t out_cap,
                       uint64_t seed);

/*
 * Decodes Bitraf frame back to original content.
 * Returns written bytes, or 0 on error/invalid frame.
 */
size_t bitraf_reconstruct(const uint8_t *in, size_t in_len,
                          uint8_t *out, size_t out_cap,
                          uint64_t seed);

/*
 * Verifies content hash against expected hash.
 * Returns 1 when valid, 0 otherwise.
 */
int bitraf_verify(const uint8_t *data, size_t len,
                  uint64_t expected_hash, uint64_t seed);

#ifdef __cplusplus
}
#endif

#endif
