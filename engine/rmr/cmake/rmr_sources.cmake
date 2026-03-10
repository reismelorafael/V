get_filename_component(RMR_SOURCE_MANIFEST_DIR "${CMAKE_CURRENT_LIST_DIR}" ABSOLUTE)
get_filename_component(RMR_REPO_ROOT "${RMR_SOURCE_MANIFEST_DIR}/../../.." ABSOLUTE)

set(RMR_CORE_COMMON_SOURCES
  ${RMR_REPO_ROOT}/engine/rmr/src/bitomega.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_baremetal_compat.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_cycles.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_hw_detect.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_bench.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_bench_suite.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_isorf.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_apk_module.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_qemu_bridge.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_math_fabric.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rafaelia_formulas_core.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_corelib.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_ll_ops.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_ll_tuning.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_casm_bridge.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_unified_kernel.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_unified_jni_bridge.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_host_compat.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_zipraf_core.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_lowlevel_portable.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_lowlevel_mix.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_lowlevel_reduce.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_neon_simd.c
)

set(RMR_EXTENDED_MODULE_SOURCES
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_tcg_cache.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_virtio_blk.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_attractor.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_vhw_model.c
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_ethica_loss.c
)

set(RMR_POLICY_MODULE_SOURCES
  ${RMR_REPO_ROOT}/engine/rmr/src/rmr_policy_kernel.c
)

set(RMR_CASM_X86_64_SOURCES
  ${RMR_REPO_ROOT}/engine/rmr/interop/rmr_lowlevel_x86_64.S
  ${RMR_REPO_ROOT}/engine/rmr/interop/rmr_casm_x86_64.S
)

set(RMR_CASM_ARM64_SOURCES
  ${RMR_REPO_ROOT}/engine/rmr/interop/rmr_casm_arm64.S
)

set(RMR_CASM_RISCV64_SOURCES
  ${RMR_REPO_ROOT}/engine/rmr/interop/rmr_casm_riscv64.S
)
