CC ?= cc
AR ?= ar
RMR_JNI_BUILD ?= 1
RMR_BUILD_HOST_TOOLING ?= $(RMR_JNI_BUILD)
RMR_ENABLE_POLICY_MODULE ?= 1
CPPFLAGS ?= -Iengine/rmr/include -DRMR_JNI_BUILD=$(RMR_JNI_BUILD) -DRMR_BUILD_HOST_TOOLING=$(RMR_BUILD_HOST_TOOLING) -DRMR_ENABLE_POLICY_MODULE=$(RMR_ENABLE_POLICY_MODULE)
CFLAGS ?= -O3 -std=c11 -Wall -Wextra -pedantic
LDFLAGS ?=

include engine/rmr/sources_rmr_core.mk

UNAME_S := $(shell uname -s 2>/dev/null || echo Unknown)
SHARED_EXT := so
ifeq ($(OS),Windows_NT)
  SHARED_EXT := dll
else ifeq ($(UNAME_S),Darwin)
  SHARED_EXT := dylib
endif

ENGINE_CORE_SRCS := $(RMR_SOURCE_GROUP_CORE) $(RMR_SOURCE_GROUP_HOST_ONLY)
ENGINE_POLICY_SRCS := $(RMR_SOURCE_GROUP_OPTIONAL_POLICY)
ENGINE_ASM_X86_64_SRCS := $(RMR_SOURCE_GROUP_ASM_X86_64)
ENGINE_ASM_ARM64_SRCS := $(RMR_SOURCE_GROUP_ASM_ARM64)
ENGINE_ASM_RISCV64_SRCS := $(RMR_SOURCE_GROUP_ASM_RISCV64)

ENGINE_SRCS := $(ENGINE_CORE_SRCS)
ifeq ($(RMR_ENABLE_POLICY_MODULE),1)
ENGINE_SRCS += $(ENGINE_POLICY_SRCS)
endif
ENGINE_OBJS := $(patsubst %.c,build/%.o,$(ENGINE_SRCS))

CASM_ASM_SRCS :=
ifeq ($(UNAME_S),Linux)
ifeq ($(shell uname -m 2>/dev/null),x86_64)
  CASM_ASM_SRCS += $(ENGINE_ASM_X86_64_SRCS)
else ifeq ($(shell uname -m 2>/dev/null),riscv64)
  CASM_ASM_SRCS += $(ENGINE_ASM_RISCV64_SRCS)
endif
endif
CASM_ASM_OBJS := $(patsubst %.S,build/%.o,$(CASM_ASM_SRCS))
ENGINE_OBJS += $(CASM_ASM_OBJS)
BITRAF_API_SRC := engine/rmr/src/bitraf.c
BITRAF_API_OBJ := $(patsubst %.c,build/%.o,$(BITRAF_API_SRC))
BITRAF_BIN := build/demo/bitraf_core

LIB_STATIC := build/engine/librmr.a
LIB_BITRAF_STATIC := build/engine/libbitraf.a
LIB_BITRAF_SHARED := build/engine/libbitraf.$(SHARED_EXT)
DEMO_BIN := build/demo/rafaelia_demo
BENCH_BIN := build/bench/rmr_bench
SELFTEST_BIN := build/demo/bitraf_selftest
APK_MODULE_BIN := build/demo/apk_module_demo
CTI_SCAN_BIN := build/demo/rafa_cti_scan
POLICY_DEMO_BIN := build/demo/policy_kernel_demo
POLICY_SELFTEST_BIN := build/demo/policy_kernel_selftest
QEMU_BRIDGE_DEMO_BIN := build/demo/rmr_qemu_bridge_demo
QEMU_BRIDGE_SELFTEST_BIN := build/demo/rmr_qemu_bridge_selftest
MATH_FABRIC_SELFTEST_BIN := build/demo/math_fabric_selftest
DETERMINISM_SIGNATURE_SELFTEST_BIN := build/demo/determinism_signature_selftest
CASM_BRIDGE_SELFTEST_BIN := build/demo/rmr_casm_bridge_selftest
BITOMEGA_SMOKETEST_BIN := build/demo/bitomega_smoketest
UNIFIED_ARENA_SELFTEST_BIN := build/demo/rmr_unified_arena_selftest
LEGACY_KERNEL_SELFTEST_BIN := build/demo/rmr_legacy_kernel_selftest
HW_DETECT_SELFTEST_BIN := build/demo/rmr_hw_detect_selftest
NEON_SIMD_SELFTEST_BIN := build/demo/rmr_neon_simd_selftest
ASM_EQUIVALENCE_SELFTEST_BIN := build/demo/rmr_asm_equivalence_selftest
ZIPRAF_CORE_SELFTEST_BIN := build/demo/zipraf_core_selftest
RMR_REQUIRED_SYMBOLS := RmR_MathFabric_AutodetectPlan RmR_MathFabric_VectorMix
RMR_LINK_LIBS := $(LIB_STATIC) $(LIB_BITRAF_STATIC)

CASM_SELFTEST_TARGETS :=
ifneq ($(strip $(CASM_ASM_SRCS)),)
CASM_SELFTEST_TARGETS += $(CASM_BRIDGE_SELFTEST_BIN)
endif

NEON_SELFTEST_TARGETS :=
ifeq ($(UNAME_S),Linux)
ifeq ($(shell uname -m 2>/dev/null),aarch64)
NEON_SELFTEST_TARGETS += $(NEON_SIMD_SELFTEST_BIN)
endif
endif

all: verify-rmr-source-alignment $(LIB_STATIC) verify-librmr-symbols $(LIB_BITRAF_STATIC) $(LIB_BITRAF_SHARED) $(DEMO_BIN) $(BENCH_BIN) $(BITRAF_BIN) $(SELFTEST_BIN) $(MATH_FABRIC_SELFTEST_BIN) $(DETERMINISM_SIGNATURE_SELFTEST_BIN) $(CASM_SELFTEST_TARGETS) $(BITOMEGA_SMOKETEST_BIN) $(UNIFIED_ARENA_SELFTEST_BIN) $(LEGACY_KERNEL_SELFTEST_BIN) $(HW_DETECT_SELFTEST_BIN) $(ASM_EQUIVALENCE_SELFTEST_BIN) $(ZIPRAF_CORE_SELFTEST_BIN) $(NEON_SELFTEST_TARGETS) $(APK_MODULE_BIN) $(CTI_SCAN_BIN) $(POLICY_DEMO_BIN) $(POLICY_SELFTEST_BIN) $(QEMU_BRIDGE_DEMO_BIN) $(QEMU_BRIDGE_SELFTEST_BIN)

verify-rmr-source-alignment:
	tools/verify_rmr_source_alignment.sh

build/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

build/%.o: %.S
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

$(LIB_STATIC): $(ENGINE_OBJS)
	@mkdir -p $(dir $@)
	$(AR) rcs $@ $^

$(LIB_BITRAF_STATIC): $(BITRAF_API_OBJ)
	@mkdir -p $(dir $@)
	$(AR) rcs $@ $^

$(LIB_BITRAF_SHARED): $(BITRAF_API_SRC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) -fPIC -shared $< $(LDFLAGS) -o $@

$(DEMO_BIN): demo_cli/src/main.c $(LIB_STATIC) $(LIB_BITRAF_STATIC) verify-librmr-symbols
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(BENCH_BIN): bench/src/rmr_benchmark_main.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(SELFTEST_BIN): demo_cli/src/bitraf_selftest.c $(LIB_BITRAF_STATIC) $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(BITRAF_BIN): engine/rmr/src/rafaelia_bitraf_core.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) -DRAF_HOSTED_TEST=1 $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(APK_MODULE_BIN): demo_cli/src/apk_module_demo.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@


$(MATH_FABRIC_SELFTEST_BIN): demo_cli/src/math_fabric_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC) verify-librmr-symbols
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

verify-librmr-symbols: $(LIB_STATIC)
	@for sym in $(RMR_REQUIRED_SYMBOLS); do \
		if ! nm -g --defined-only $(LIB_STATIC) | awk '{if ($$3 == "'"$$sym"'") found=1} END {exit(found ? 0 : 1)}'; then \
			echo "[link-contract] missing symbol in $(LIB_STATIC): $$sym" >&2; \
			exit 1; \
		fi; \
	done
	@echo "[link-contract] verified required symbols in $(LIB_STATIC)"

$(CTI_SCAN_BIN): engine/rmr/src/rafa_cti_scan.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -lm -o $@


$(POLICY_DEMO_BIN): demo_cli/src/policy_kernel_demo.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(POLICY_SELFTEST_BIN): demo_cli/src/policy_kernel_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(QEMU_BRIDGE_DEMO_BIN): demo_cli/src/rmr_qemu_bridge_demo.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(QEMU_BRIDGE_SELFTEST_BIN): demo_cli/src/rmr_qemu_bridge_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(DETERMINISM_SIGNATURE_SELFTEST_BIN): demo_cli/src/determinism_signature_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(CASM_BRIDGE_SELFTEST_BIN): demo_cli/src/rmr_casm_bridge_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(BITOMEGA_SMOKETEST_BIN): demo_cli/src/bitomega_smoketest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(UNIFIED_ARENA_SELFTEST_BIN): demo_cli/src/rmr_unified_arena_selftest.c $(LIB_BITRAF_STATIC) $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(LEGACY_KERNEL_SELFTEST_BIN): demo_cli/src/rmr_legacy_kernel_selftest.c $(LIB_BITRAF_STATIC) $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(HW_DETECT_SELFTEST_BIN): demo_cli/src/rmr_hw_detect_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(ASM_EQUIVALENCE_SELFTEST_BIN): demo_cli/src/rmr_asm_equivalence_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(ZIPRAF_CORE_SELFTEST_BIN): demo_cli/src/zipraf_core_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

$(NEON_SIMD_SELFTEST_BIN): demo_cli/src/neon_simd_selftest.c $(LIB_STATIC) $(LIB_BITRAF_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(RMR_LINK_LIBS) $(LDFLAGS) -o $@

run-bitomega-smoketest: $(BITOMEGA_SMOKETEST_BIN)
	@mkdir -p bench/results
	./$(BITOMEGA_SMOKETEST_BIN)
run-demo: $(DEMO_BIN)
	./$(DEMO_BIN)

run-casm-selftest: $(CASM_SELFTEST_TARGETS)
	@if [ -z "$(CASM_SELFTEST_TARGETS)" ]; then \
		echo "[run-casm-selftest] CASM bridge selftest unavailable for this host architecture"; \
		exit 0; \
	fi
	./$(CASM_BRIDGE_SELFTEST_BIN)

run-selftest: $(SELFTEST_BIN) $(MATH_FABRIC_SELFTEST_BIN) $(DETERMINISM_SIGNATURE_SELFTEST_BIN) $(CASM_SELFTEST_TARGETS) $(POLICY_SELFTEST_BIN) $(QEMU_BRIDGE_SELFTEST_BIN) $(BITOMEGA_SMOKETEST_BIN) $(UNIFIED_ARENA_SELFTEST_BIN) $(LEGACY_KERNEL_SELFTEST_BIN) $(HW_DETECT_SELFTEST_BIN) $(ASM_EQUIVALENCE_SELFTEST_BIN) $(ZIPRAF_CORE_SELFTEST_BIN) $(NEON_SELFTEST_TARGETS)
	@set -e; \
	status=0; \
	for test_cmd in \
		"./$(SELFTEST_BIN)" \
		"./$(MATH_FABRIC_SELFTEST_BIN)" \
		"./$(DETERMINISM_SIGNATURE_SELFTEST_BIN)" \
		"./$(POLICY_SELFTEST_BIN)" \
		"./$(QEMU_BRIDGE_SELFTEST_BIN)" \
		"./$(UNIFIED_ARENA_SELFTEST_BIN)" \
		"./$(LEGACY_KERNEL_SELFTEST_BIN)" \
		"./$(HW_DETECT_SELFTEST_BIN)" \
		"./$(ASM_EQUIVALENCE_SELFTEST_BIN)" \
		"./$(ZIPRAF_CORE_SELFTEST_BIN)"; do \
		echo "[run-selftest] $$test_cmd"; \
		if ! sh -c "$$test_cmd"; then \
			status=1; \
		fi; \
	done; \
	if [ -n "$(CASM_SELFTEST_TARGETS)" ]; then \
		echo "[run-selftest] ./$(CASM_BRIDGE_SELFTEST_BIN)"; \
		if ! ./$(CASM_BRIDGE_SELFTEST_BIN); then \
			status=1; \
		fi; \
	fi; \
	if [ -n "$(NEON_SELFTEST_TARGETS)" ]; then \
		echo "[run-selftest] ./$(NEON_SIMD_SELFTEST_BIN)"; \
		if ! ./$(NEON_SIMD_SELFTEST_BIN); then \
			status=1; \
		fi; \
	fi; \
	mkdir -p bench/results; \
	echo "[run-selftest] ./$(BITOMEGA_SMOKETEST_BIN)"; \
	if ! ./$(BITOMEGA_SMOKETEST_BIN); then \
		status=1; \
	fi; \
	exit $$status

run-bench: $(BENCH_BIN)
	./$(BENCH_BIN) bench/results/latest.csv bench/results/latest.json

run-baremetal-gate:
	tools/baremetal/hw_caps_detect.sh reports/baremetal/hw_caps.env
	tools/baremetal/dir_integrity_matrix.sh reports/baremetal/dir_integrity_matrix.json

run-release-gate: run-selftest run-bench run-baremetal-gate
	bench/scripts/run_bench.sh 7 bench/results

check-engine-source-manifest:
	python3 tools/sync_engine_sources.py --check

clean:
	rm -rf build

.PHONY: all clean verify-librmr-symbols run-demo run-casm-selftest run-selftest run-bitomega-smoketest run-bench run-baremetal-gate run-release-gate check-engine-source-manifest

print-build-config:
	@echo "RMR_JNI_BUILD=$(RMR_JNI_BUILD)"
	@echo "RMR_BUILD_HOST_TOOLING=$(RMR_BUILD_HOST_TOOLING)"
	@echo "RMR_ENABLE_POLICY_MODULE=$(RMR_ENABLE_POLICY_MODULE)"
	@echo "CPPFLAGS=$(CPPFLAGS)"
	@echo "CFLAGS=$(CFLAGS)"

print-build-config-env:
	@mkdir -p build
	@printf 'RMR_JNI_BUILD=%s\nRMR_BUILD_HOST_TOOLING=%s\nRMR_ENABLE_POLICY_MODULE=%s\n' \
		"$(RMR_JNI_BUILD)" "$(RMR_BUILD_HOST_TOOLING)" "$(RMR_ENABLE_POLICY_MODULE)" > build/rmr_build_config.env
	@cat build/rmr_build_config.env

.PHONY: print-build-config print-build-config-env
