# API — Bitraf Engine (C)

API pública estável do Produto 1 (Engine), sem exposição de structs internas.

## Headers
- `engine/rmr/include/bitraf.h`
- `engine/rmr/include/bitraf_version.h`

## SemVer
- `BITRAF_VERSION_MAJOR`
- `BITRAF_VERSION_MINOR`
- `BITRAF_VERSION_PATCH`
- `BITRAF_VERSION_STRING`

## Contrato mínimo
```c
int bitraf_init(uint64_t seed);
uint64_t bitraf_hash(const uint8_t *data, size_t len, uint64_t seed);
size_t bitraf_compress(const uint8_t *in, size_t in_len,
                       uint8_t *out, size_t out_cap,
                       uint64_t seed);
size_t bitraf_reconstruct(const uint8_t *in, size_t in_len,
                          uint8_t *out, size_t out_cap,
                          uint64_t seed);
int bitraf_verify(const uint8_t *data, size_t len,
                  uint64_t expected_hash, uint64_t seed);
```

## Exemplo de uso (C)
```c
#include "bitraf.h"
#include <stdint.h>
#include <stdio.h>

int main(void) {
  static const uint8_t payload[] = "RAFAELIA_ENGINE";
  uint8_t packed[256];
  uint8_t restored[256];

  uint64_t seed = 0x123456789ABCDEF0ULL;
  bitraf_init(seed);

  size_t packed_len = bitraf_compress(payload, sizeof(payload)-1, packed, sizeof(packed), seed);
  size_t plain_len = bitraf_reconstruct(packed, packed_len, restored, sizeof(restored), seed);

  uint64_t h = bitraf_hash(restored, plain_len, seed);
  int ok = bitraf_verify(restored, plain_len, h, seed);

  printf("packed=%zu plain=%zu ok=%d\n", packed_len, plain_len, ok);
  return ok ? 0 : 1;
}
```

## Linkagem
### Make
```bash
make all
cc -O3 -Iengine/rmr/include app.c build/engine/libbitraf.a -o app
```

### CMake
```bash
cmake -S . -B build-cmake
cmake --build build-cmake -j
# alvo: bitraf_static (libbitraf.a) e bitraf_shared (libbitraf.so)
```
