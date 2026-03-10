# Canonical RMR source manifest.
#
# Group contract used by all build systems:
#   - core
#   - optional-policy
#   - android-only
#   - host-only
#   - asm-per-arch

set(RMR_SOURCE_GROUP_CORE
  engine/rmr/src/bitomega.c
  engine/rmr/src/rmr_baremetal_compat.c
  engine/rmr/src/rmr_cycles.c
  engine/rmr/src/rmr_hw_detect.c
  engine/rmr/src/rmr_isorf.c
  engine/rmr/src/rmr_apk_module.c
  engine/rmr/src/rmr_qemu_bridge.c
  engine/rmr/src/rmr_math_fabric.c
  engine/rmr/src/rafaelia_formulas_core.c
  engine/rmr/src/rmr_corelib.c
  engine/rmr/src/rmr_ll_ops.c
  engine/rmr/src/rmr_ll_tuning.c
  engine/rmr/src/rmr_casm_bridge.c
  engine/rmr/src/rmr_unified_kernel.c
  engine/rmr/src/rmr_unified_jni_bridge.c
  engine/rmr/src/rmr_host_compat.c
  engine/rmr/src/rmr_zipraf_core.c
  engine/rmr/src/rmr_lowlevel_portable.c
  engine/rmr/src/rmr_lowlevel_mix.c
  engine/rmr/src/rmr_lowlevel_reduce.c
  engine/rmr/src/rmr_neon_simd.c
)

set(RMR_SOURCE_GROUP_OPTIONAL_POLICY
  engine/rmr/src/rmr_policy_kernel.c
)

# Android JNI/library-only units. Intentionally excluded from hosted targets.
set(RMR_SOURCE_GROUP_ANDROID_ONLY
  engine/rmr/src/rmr_tcg_cache.c
  engine/rmr/src/rmr_virtio_blk.c
  engine/rmr/src/rmr_attractor.c
  engine/rmr/src/rmr_vhw_model.c
  engine/rmr/src/rmr_ethica_loss.c
)

# Hosted/root-only units. Intentionally excluded from Android shared library.
set(RMR_SOURCE_GROUP_HOST_ONLY
  engine/rmr/src/rmr_bench.c
  engine/rmr/src/rmr_bench_suite.c
)

set(RMR_SOURCE_GROUP_ASM_X86_64
  engine/rmr/interop/rmr_lowlevel_x86_64.S
  engine/rmr/interop/rmr_casm_x86_64.S
)

set(RMR_SOURCE_GROUP_ASM_ARM64
  engine/rmr/interop/rmr_casm_arm64.S
)

set(RMR_SOURCE_GROUP_ASM_RISCV64
  engine/rmr/interop/rmr_casm_riscv64.S
)

function(rmr_manifest_apply_base OUT_VAR)
  set(_rmr_manifest_out)
  foreach(_rmr_manifest_src IN LISTS ARGN)
    if(DEFINED RMR_SOURCE_BASE AND NOT RMR_SOURCE_BASE STREQUAL "")
      list(APPEND _rmr_manifest_out "${RMR_SOURCE_BASE}${_rmr_manifest_src}")
    else()
      list(APPEND _rmr_manifest_out "${_rmr_manifest_src}")
    endif()
  endforeach()
  set(${OUT_VAR} "${_rmr_manifest_out}" PARENT_SCOPE)
endfunction()
