#ifndef RMR_ZERO_H
#define RMR_ZERO_H

#include <stdint.h>

/* Canonical RAFAELIA ZERO constants (hex-sealed literals). */
#define RMR_ZERO_MAGIC_U32            0x524D5230u      /* "RMR0" */
#define RMR_ZERO_ABI_VERSION_U16      0x0100u
#define RMR_ZERO_STAGE_MASK_U8        0x1Fu
#define RMR_ZERO_ROUTE_FALLBACK_U8    0xFFu

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

/* Optional hard override. */
#ifndef RMR_ZERO_ENV_ACTIVE
  #if defined(RMR_JNI_BUILD) && RMR_JNI_BUILD
    #define RMR_ZERO_ENV_ACTIVE RMR_ZERO_ENV_JNI_U8
  #else
    #define RMR_ZERO_ENV_ACTIVE RMR_ZERO_ENV_BAREMETAL_U8
  #endif
#endif

#endif
