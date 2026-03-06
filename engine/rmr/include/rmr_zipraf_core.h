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

int RmR_Zipraf_Execute(const RmR_ZiprafInput *in, RmR_ZiprafOutput *out);

#ifdef __cplusplus
}
#endif

#endif
