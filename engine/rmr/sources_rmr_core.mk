# GENERATED FILE: do not edit directly.
# Source of truth: engine/rmr/sources_rmr_core.cmake
# Regenerate with: tools/sync_rmr_manifest_to_mk.py

RMR_SOURCE_GROUP_CORE := \
	engine/rmr/src/bitomega.c \
	engine/rmr/src/rmr_baremetal_compat.c \
	engine/rmr/src/rmr_cycles.c \
	engine/rmr/src/rmr_hw_detect.c \
	engine/rmr/src/rmr_isorf.c \
	engine/rmr/src/rmr_apk_module.c \
	engine/rmr/src/rmr_qemu_bridge.c \
	engine/rmr/src/rmr_math_fabric.c \
	engine/rmr/src/rafaelia_formulas_core.c \
	engine/rmr/src/rmr_corelib.c \
	engine/rmr/src/rmr_ll_ops.c \
	engine/rmr/src/rmr_ll_tuning.c \
	engine/rmr/src/rmr_casm_bridge.c \
	engine/rmr/src/rmr_unified_kernel.c \
	engine/rmr/src/rmr_unified_jni_bridge.c \
	engine/rmr/src/rmr_host_compat.c \
	engine/rmr/src/rmr_zipraf_core.c \
	engine/rmr/src/rmr_lowlevel_portable.c \
	engine/rmr/src/rmr_lowlevel_mix.c \
	engine/rmr/src/rmr_lowlevel_reduce.c \
	engine/rmr/src/rmr_neon_simd.c

RMR_SOURCE_GROUP_OPTIONAL_POLICY := \
	engine/rmr/src/rmr_policy_kernel.c

RMR_SOURCE_GROUP_ANDROID_ONLY := \
	engine/rmr/src/rmr_tcg_cache.c \
	engine/rmr/src/rmr_virtio_blk.c \
	engine/rmr/src/rmr_attractor.c \
	engine/rmr/src/rmr_vhw_model.c \
	engine/rmr/src/rmr_ethica_loss.c

RMR_SOURCE_GROUP_HOST_ONLY := \
	engine/rmr/src/rmr_bench.c \
	engine/rmr/src/rmr_bench_suite.c

RMR_SOURCE_GROUP_ASM_X86_64 := \
	engine/rmr/interop/rmr_lowlevel_x86_64.S \
	engine/rmr/interop/rmr_casm_x86_64.S

RMR_SOURCE_GROUP_ASM_ARM64 := \
	engine/rmr/interop/rmr_casm_arm64.S

RMR_SOURCE_GROUP_ASM_RISCV64 := \
	engine/rmr/interop/rmr_casm_riscv64.S
