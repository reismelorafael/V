#include "rmr_zipraf_core.h"

#include <stdint.h>
#include <stdio.h>

static int output_equal(const RmR_ZiprafOutput *a, const RmR_ZiprafOutput *b) {
  return a->route_tag == b->route_tag &&
         a->bitraf_hash == b->bitraf_hash &&
         a->crc32c == b->crc32c &&
         a->det_signature == b->det_signature &&
         a->status_flags == b->status_flags;
}

int main(void) {
  static const uint8_t payload[] = {
      0x52u, 0x4Du, 0x52u, 0x2Du, 0x5Au, 0x69u, 0x70u, 0x72u,
      0x61u, 0x66u, 0x2Du, 0x43u, 0x6Fu, 0x72u, 0x65u, 0x21u};
  RmR_ZiprafInput in;
  RmR_ZiprafOutput a;
  RmR_ZiprafOutput b;
  int64_t tri_state[3] = {7, 2, -5};
  int64_t tri_flow[6];
  int64_t tri_closed[3];
  uint32_t tri_coherence = 0u;

  in.seed = 0x1234ABCDu;
  in.trajectory_id = 0x0F0E0D0Cu;
  in.invariant_mask = 0x00FF00FFu;
  in.payload_ptr = payload;
  in.payload_len = sizeof(payload);

  if (RmR_Zipraf_Execute(&in, &a) != 0) {
    printf("FAIL zipraf execute #1\n");
    return 1;
  }
  if (RmR_Zipraf_Execute(&in, &b) != 0) {
    printf("FAIL zipraf execute #2\n");
    return 1;
  }

  if (!output_equal(&a, &b)) {
    printf("FAIL non-deterministic output\n");
    printf("A route=%llu hash=%llu crc=%u det=%lld flags=%u\n",
           (unsigned long long)a.route_tag,
           (unsigned long long)a.bitraf_hash,
           (unsigned)a.crc32c,
           (long long)a.det_signature,
           (unsigned)a.status_flags);
    printf("B route=%llu hash=%llu crc=%u det=%lld flags=%u\n",
           (unsigned long long)b.route_tag,
           (unsigned long long)b.bitraf_hash,
           (unsigned)b.crc32c,
           (long long)b.det_signature,
           (unsigned)b.status_flags);
    return 1;
  }

  if (RmR_Zipraf_TriFlow3x6(tri_state, tri_flow) != 0) {
    printf("FAIL tri flow\n");
    return 1;
  }
  if (tri_flow[0] != 5 || tri_flow[1] != -5 ||
      tri_flow[2] != 7 || tri_flow[3] != -7 ||
      tri_flow[4] != -12 || tri_flow[5] != 12) {
    printf("FAIL tri flow values\n");
    return 1;
  }

  if (RmR_Zipraf_TriCloseBase10(tri_flow, tri_closed, &tri_coherence) != 0) {
    printf("FAIL tri close\n");
    return 1;
  }
  if (tri_closed[0] != 34 || tri_closed[1] != 4 || tri_closed[2] != -38) {
    printf("FAIL tri close values %lld %lld %lld\n",
           (long long)tri_closed[0], (long long)tri_closed[1], (long long)tri_closed[2]);
    return 1;
  }
  if (tri_coherence != 1023u) {
    printf("FAIL tri coherence=%u\n", (unsigned)tri_coherence);
    return 1;
  }

  printf("OK zipraf selftest route=%llu hash=%llu crc=%u det=%lld flags=%u triC=%u\n",
         (unsigned long long)a.route_tag,
         (unsigned long long)a.bitraf_hash,
         (unsigned)a.crc32c,
         (long long)a.det_signature,
         (unsigned)a.status_flags,
         (unsigned)tri_coherence);
  return 0;
}
