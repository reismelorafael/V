#ifndef RMR_ZERO_H
#define RMR_ZERO_H

#include <stdint.h>

/* Canonical RAFAELIA ZERO constants (hex-sealed literals). */
#define RMR_ZERO_MAGIC_U32            0x524D5230u      /* "RMR0" */
#define RMR_ZERO_ABI_VERSION_U16      0x0100u
#define RMR_ZERO_STAGE_MASK_U8        0x1Fu
#define RMR_ZERO_ROUTE_FALLBACK_U8    0xFFu

/* Canonical numeric constants to avoid local duplicated literals. */
#define RMR_ZERO_U8_MAX_U8            0xFFu
#define RMR_ZERO_U16_MAX_U16          0xFFFFu
#define RMR_ZERO_U32_MAX_U32          0xFFFFFFFFu

/* Hash/mixer canonical constants. */
#define RMR_ZERO_PHI32_U32            0x9E3779B9u
#define RMR_ZERO_PHI64_U64            0x9E3779B97F4A7C15ULL
#define RMR_ZERO_FNV1A64_PRIME_U64    0x100000001B3ULL

/* CRC canonical constants. */
#define RMR_ZERO_CRC32_INIT_U32       0xFFFFFFFFu
#define RMR_ZERO_CRC32_XOROUT_U32     0xFFFFFFFFu
#define RMR_ZERO_CRC32_IEEE_REV_U32   0xEDB88320u
#define RMR_ZERO_CRC32C_POLY_U32      0x82F63B78u

/* ψ → χ → ρ → Δ → Σ → Ω deterministic cycle identifiers. */
#define RMR_ZERO_STAGE_PSI_U8         0x01u
#define RMR_ZERO_STAGE_CHI_U8         0x02u
#define RMR_ZERO_STAGE_RHO_U8         0x03u
#define RMR_ZERO_STAGE_DELTA_U8       0x04u
#define RMR_ZERO_STAGE_SIGMA_U8       0x05u
#define RMR_ZERO_STAGE_OMEGA_U8       0x06u

/* Core route identifiers in canonical hex form. */
#define RMR_ZERO_ROUTE_CPU_U8         0x01u
#define RMR_ZERO_ROUTE_RAM_U8         0x02u
#define RMR_ZERO_ROUTE_DISK_U8        0x03u

/* Build/runtime environment tags. */
#define RMR_ZERO_ENV_BAREMETAL_U8     0x10u
#define RMR_ZERO_ENV_JNI_U8           0x11u

/* POLICY_KERNEL domain: deterministic hash + CRC constants. */
#define RMR_ZERO_POLICY_KERNEL_SIG_MIX64_U64   0x9E3779B97F4A7C15ull /* golden-ratio 64b mixer */
#define RMR_ZERO_POLICY_KERNEL_CRC32C_INIT_U32 0xFFFFFFFFu            /* CRC32C initial all-ones */
#define RMR_ZERO_POLICY_KERNEL_CRC32C_POLY_U32 0x82F63B78u            /* CRC32C Castagnoli polynomial (reflected) */
#define RMR_ZERO_POLICY_KERNEL_FNV1A_BASIS_U64 0x14650FB0739D0383ull  /* FNV-1a 64-bit offset basis */
#define RMR_ZERO_POLICY_KERNEL_FNV1A_PRIME_U64 0x00000100000001B3ull  /* FNV-1a 64-bit prime */

/* BITRAF domain: frame/hash/stream constants. */
#define RMR_ZERO_BITRAF_MAGIC_0_U8            0x42u                   /* 'B' */
#define RMR_ZERO_BITRAF_MAGIC_1_U8            0x54u                   /* 'T' */
#define RMR_ZERO_BITRAF_MAGIC_2_U8            0x52u                   /* 'R' */
#define RMR_ZERO_BITRAF_MAGIC_3_U8            0x46u                   /* 'F' */
#define RMR_ZERO_BITRAF_FLAG_V2_CHUNK_TABLE_U32 0x00000001u
#define RMR_ZERO_BITRAF_PHI64_U64             0x9E3779B97F4A7C15ull   /* φ64 stream stride */
#define RMR_ZERO_BITRAF_FNV1A_BASIS_U64       0xCBF29CE484222325ull   /* FNV-1a 64-bit offset basis */
#define RMR_ZERO_BITRAF_FNV1A_PRIME_U64       0x00000100000001B3ull   /* FNV-1a 64-bit prime */
#define RMR_ZERO_BITRAF_MIX_A_U64             0xBF58476D1CE4E5B9ull
#define RMR_ZERO_BITRAF_MIX_B_U64             0x94D049BB133111EBull
#define RMR_ZERO_BITRAF_MIX_C_U64             0xA0761D6478BD642Full
#define RMR_ZERO_BITRAF_CRC32_INIT_U32        0xFFFFFFFFu            /* CRC32 initial all-ones */
#define RMR_ZERO_BITRAF_CRC32_POLY_U32        0xEDB88320u             /* CRC32 IEEE polynomial (reflected) */
#define RMR_ZERO_BITRAF_IO_MASK_U8            0xFFu

/* BITOMEGA domain: canonical state/direction tags. */
#define RMR_ZERO_BITOMEGA_STATE_NEG_U8        0x00u
#define RMR_ZERO_BITOMEGA_STATE_ZERO_U8       0x01u
#define RMR_ZERO_BITOMEGA_STATE_POS_U8        0x02u
#define RMR_ZERO_BITOMEGA_STATE_MIX_U8        0x03u
#define RMR_ZERO_BITOMEGA_STATE_VOID_U8       0x04u
#define RMR_ZERO_BITOMEGA_STATE_EDGE_U8       0x05u
#define RMR_ZERO_BITOMEGA_STATE_FLOW_U8       0x06u
#define RMR_ZERO_BITOMEGA_STATE_LOCK_U8       0x07u
#define RMR_ZERO_BITOMEGA_STATE_NOISE_U8      0x08u
#define RMR_ZERO_BITOMEGA_STATE_META_U8       0x09u
#define RMR_ZERO_BITOMEGA_DIR_NONE_U8         0x00u
#define RMR_ZERO_BITOMEGA_DIR_UP_U8           0x01u
#define RMR_ZERO_BITOMEGA_DIR_DOWN_U8         0x02u
#define RMR_ZERO_BITOMEGA_DIR_FORWARD_U8      0x03u
#define RMR_ZERO_BITOMEGA_DIR_RECURSE_U8      0x04u
#define RMR_ZERO_BITOMEGA_DIR_NULL_U8         0x05u

/* HW_DETECT domain: architecture ids and compact architecture tags. */
#define RMR_ZERO_HW_ARCH_UNKNOWN_U32          0x00000000u
#define RMR_ZERO_HW_ARCH_I386_U32             0x00000001u
#define RMR_ZERO_HW_ARCH_X86_64_U32           0x00000002u
#define RMR_ZERO_HW_ARCH_ARM_U32              0x00000003u
#define RMR_ZERO_HW_ARCH_ARM64_U32            0x00000004u
#define RMR_ZERO_HW_ARCH_RISCV_U32            0x00000005u
#define RMR_ZERO_HW_ARCH_MIPS_U32             0x00000006u
#define RMR_ZERO_HW_ARCH_PPC64_U32            0x00000007u
#define RMR_ZERO_HW_ARCH_PPC32_U32            0x00000008u
#define RMR_ZERO_HW_ARCH_S390X_U32            0x00000009u
#define RMR_ZERO_HW_ARCH_TAG_I386_U32         0x00000086u             /* x86 */
#define RMR_ZERO_HW_ARCH_TAG_X86_64_U32       0x00008664u             /* x86_64 */
#define RMR_ZERO_HW_ARCH_TAG_ARM_U32          0x00000A32u             /* arm32 */
#define RMR_ZERO_HW_ARCH_TAG_ARM64_U32        0x00000A64u             /* arm64 */
#define RMR_ZERO_HW_ARCH_TAG_RISCV_U32        0x00000052u             /* rv */
#define RMR_ZERO_HW_ARCH_TAG_MIPS_U32         0x00006D31u             /* m1 */
#define RMR_ZERO_HW_ARCH_TAG_PPC64_U32        0x00007064u             /* p64 */
#define RMR_ZERO_HW_ARCH_TAG_PPC32_U32        0x00007032u             /* p32 */
#define RMR_ZERO_HW_ARCH_TAG_S390X_U32        0x00000390u             /* s390 */

/* ZIPRAF domain: deterministic status/mask/coherence constants. */
#define RMR_ZERO_ZIPRAF_STATUS_OK_U32            0x00000000u
#define RMR_ZERO_ZIPRAF_STATUS_ERR_ARG_U32       0x00000001u
#define RMR_ZERO_ZIPRAF_STATUS_EMPTY_PAYLOAD_U32 0x00000002u
#define RMR_ZERO_ZIPRAF_STATUS_INVARIANT_MATCH_U32 0x00000004u
#define RMR_ZERO_ZIPRAF_STATUS_TRI_COHERENT_U32  0x00000008u
#define RMR_ZERO_ZIPRAF_U32_MASK_U64             0xFFFFFFFFu
#define RMR_ZERO_ZIPRAF_TRI_COHERENT_MIN_U32     0x000003C0u           /* 960 coherence floor */

/* QEMU_BRIDGE domain: guest arch/preset canonical tags. */
#define RMR_ZERO_QEMU_PRESET_BALANCED_U8       0x00u
#define RMR_ZERO_QEMU_PRESET_PERFORMANCE_U8    0x01u
#define RMR_ZERO_QEMU_PRESET_COMPATIBILITY_U8  0x02u
#define RMR_ZERO_QEMU_GUEST_ARCH_X86_64_U8     0x00u
#define RMR_ZERO_QEMU_GUEST_ARCH_I386_U8       0x01u
#define RMR_ZERO_QEMU_GUEST_ARCH_ARM64_U8      0x02u
#define RMR_ZERO_QEMU_GUEST_ARCH_PPC_U8        0x03u

/* Optional hard override. */
#ifndef RMR_ZERO_ENV_ACTIVE
  #if defined(RMR_JNI_BUILD) && RMR_JNI_BUILD
    #define RMR_ZERO_ENV_ACTIVE RMR_ZERO_ENV_JNI_U8
  #else
    #define RMR_ZERO_ENV_ACTIVE RMR_ZERO_ENV_BAREMETAL_U8
  #endif
#endif

#endif
