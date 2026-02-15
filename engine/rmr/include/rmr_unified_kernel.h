#ifndef RMR_UNIFIED_KERNEL_H
#define RMR_UNIFIED_KERNEL_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_KERNEL_OK 0
#define RMR_KERNEL_ERR_ARG -1
#define RMR_KERNEL_ERR_STATE -2

#define RMR_KERNEL_ARCH_UNKNOWN 0x0000u
#define RMR_KERNEL_ARCH_ARM64   0x0100u
#define RMR_KERNEL_ARCH_ARM32   0x0200u
#define RMR_KERNEL_ARCH_X64     0x0300u
#define RMR_KERNEL_ARCH_X86     0x0400u
#define RMR_KERNEL_ARCH_RV64    0x0500u
#define RMR_KERNEL_ARCH_RV32    0x0600u

#define RMR_KERNEL_OS_UNKNOWN   0x0000u
#define RMR_KERNEL_OS_ANDROID   0x0010u
#define RMR_KERNEL_OS_LINUX     0x0020u

#define RMR_KERNEL_FEATURE_NEON   (1u << 0)
#define RMR_KERNEL_FEATURE_AES    (1u << 1)
#define RMR_KERNEL_FEATURE_CRC32  (1u << 2)
#define RMR_KERNEL_FEATURE_POPCNT (1u << 3)
#define RMR_KERNEL_FEATURE_SSE42  (1u << 4)
#define RMR_KERNEL_FEATURE_AVX2   (1u << 5)
#define RMR_KERNEL_FEATURE_SIMD   (1u << 6)

typedef struct {
    uint32_t signature;
    uint32_t pointer_bits;
    uint32_t cache_line_bytes;
    uint32_t page_bytes;
    uint32_t feature_mask;
    uint32_t register_width_bits;
    uint32_t pin_count_hint;
    uint32_t feature_bits_hi;
} rmr_kernel_capabilities_t;

typedef struct {
    uint32_t initialized;
    uint32_t seed;
    uint32_t crc;
    uint32_t entropy;
    uint64_t ingest_count;
    uint64_t process_count;
    uint64_t route_count;
    uint64_t verify_count;
    uint64_t audit_count;
} rmr_kernel_state_t;

typedef struct {
    uint64_t cpu_cycles;
    uint64_t storage_read_bytes;
    uint64_t storage_write_bytes;
    uint64_t input_bytes;
    uint64_t output_bytes;
    int64_t m00;
    int64_t m01;
    int64_t m10;
    int64_t m11;
} rmr_kernel_route_input_t;

typedef struct {
    uint32_t route;
    int32_t score;
} rmr_kernel_route_output_t;

int rmr_kernel_init(rmr_kernel_state_t* state, uint32_t seed);
int rmr_kernel_shutdown(rmr_kernel_state_t* state);
int rmr_kernel_ingest(rmr_kernel_state_t* state, const uint8_t* data, uint32_t len, uint32_t* out_crc);
int rmr_kernel_process(rmr_kernel_state_t* state, int32_t a, int32_t b, uint32_t mode, int32_t* out_value);
int rmr_kernel_route(rmr_kernel_state_t* state, const rmr_kernel_route_input_t* in, rmr_kernel_route_output_t* out);
int rmr_kernel_verify(rmr_kernel_state_t* state, const uint8_t* data, uint32_t len, uint32_t expected, uint32_t* out_value);
int rmr_kernel_audit(rmr_kernel_state_t* state, uint64_t* out_counters, uint32_t counters_len);
int rmr_kernel_autodetect(rmr_kernel_capabilities_t* out_caps);

#ifdef __cplusplus
}
#endif

#endif
