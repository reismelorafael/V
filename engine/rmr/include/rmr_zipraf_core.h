#ifndef RMR_ZIPRAF_CORE_H
#define RMR_ZIPRAF_CORE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  uint32_t seed;
  uint32_t trajectory_id;
  uint32_t invariant_mask;
  const uint8_t *payload_ptr;
  size_t payload_len;
} RmR_ZiprafInput;

typedef struct {
  uint64_t route_tag;
  uint64_t bitraf_hash;
  uint32_t crc32c;
  int64_t det_signature;
  uint32_t status_flags;
} RmR_ZiprafOutput;

#define RMR_ZIPRAF_STATUS_OK 0u
#define RMR_ZIPRAF_STATUS_ERR_ARG (1u << 0)
#define RMR_ZIPRAF_STATUS_EMPTY_PAYLOAD (1u << 1)
#define RMR_ZIPRAF_STATUS_INVARIANT_MATCH (1u << 2)
#define RMR_ZIPRAF_STATUS_TRI_COHERENT (1u << 3)

/*
 * Kernel triangular 3->6:
 * [a,b,c] -> [a-b,b-a,b-c,c-b,c-a,a-c]
 */
int RmR_Zipraf_TriFlow3x6(const int64_t state3[3], int64_t flow6[6]);

/*
 * Fechamento determinístico em base 10 com métrica de coerência.
 * out_coherence usa escala [0..1023], maior = mais coerente.
 */
int RmR_Zipraf_TriCloseBase10(const int64_t flow6[6], int64_t closed3[3], uint32_t *out_coherence);

int RmR_Zipraf_Execute(const RmR_ZiprafInput *in, RmR_ZiprafOutput *out);

#ifdef __cplusplus
}
#endif

#endif
