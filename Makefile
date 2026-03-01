CC ?= cc
AR ?= ar
RMR_JNI_BUILD ?= 1
RMR_ENABLE_POLICY_MODULE ?= 1
CPPFLAGS ?= -Iengine/rmr/include -DRMR_JNI_BUILD=$(RMR_JNI_BUILD) -DRMR_ENABLE_POLICY_MODULE=$(RMR_ENABLE_POLICY_MODULE)
CFLAGS ?= -O3 -std=c11 -Wall -Wextra -pedantic
LDFLAGS ?=

UNAME_S := $(shell uname -s 2>/dev/null || echo Unknown)
SHARED_EXT := so
ifeq ($(OS),Windows_NT)
  SHARED_EXT := dll
else ifeq ($(UNAME_S),Darwin)
  SHARED_EXT := dylib
endif

ENGINE_SRCS := engine/rmr/src/bitomega.c engine/rmr/src/rmr_cycles.c engine/rmr/src/rmr_lowlevel_portable.c engine/rmr/src/rmr_lowlevel_mix.c engine/rmr/src/rmr_lowlevel_reduce.c engine/rmr/src/rmr_neon_simd.c engine/rmr/src/rmr_hw_detect.c engine/rmr/src/rmr_bench.c engine/rmr/src/rmr_bench_suite.c engine/rmr/src/rmr_isorf.c engine/rmr/src/rmr_apk_module.c engine/rmr/src/rmr_math_fabric.c engine/rmr/src/rmr_policy_kernel.c engine/rmr/src/rmr_qemu_bridge.c engine/rmr/src/rmr_corelib.c engine/rmr/src/rmr_ll_ops.c engine/rmr/src/rmr_ll_tuning.c engine/rmr/src/rmr_casm_bridge.c engine/rmr/src/rmr_unified_kernel.c
ENGINE_OBJS := $(patsubst %.c,build/%.o,$(ENGINE_SRCS))

CASM_ASM_SRCS :=
ifeq ($(UNAME_S),Linux)
ifeq ($(shell uname -m 2>/dev/null),x86_64)
  CASM_ASM_SRCS += engine/rmr/interop/rmr_casm_x86_64.S
endif
endif
CASM_ASM_OBJS := $(patsubst %.S,build/%.o,$(CASM_ASM_SRCS))
ENGINE_OBJS += $(CASM_ASM_OBJS)
CASM_BRIDGE_C_SRC := engine/rmr/src/rmr_casm_bridge.c
CASM_BRIDGE_C_OBJ := $(patsubst %.c,build/%.o,$(CASM_BRIDGE_C_SRC))
CASM_BRIDGE_OBJS := $(CASM_BRIDGE_C_OBJ) $(CASM_ASM_OBJS)
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
RMR_REQUIRED_SYMBOLS := RmR_MathFabric_AutodetectPlan RmR_MathFabric_VectorMix

all: $(LIB_STATIC) verify-librmr-symbols $(LIB_BITRAF_STATIC) $(LIB_BITRAF_SHARED) $(DEMO_BIN) $(BENCH_BIN) $(BITRAF_BIN) $(SELFTEST_BIN) $(MATH_FABRIC_SELFTEST_BIN) $(DETERMINISM_SIGNATURE_SELFTEST_BIN) $(CASM_BRIDGE_SELFTEST_BIN) $(BITOMEGA_SMOKETEST_BIN) $(APK_MODULE_BIN) $(CTI_SCAN_BIN) $(POLICY_DEMO_BIN) $(POLICY_SELFTEST_BIN) $(QEMU_BRIDGE_DEMO_BIN) $(QEMU_BRIDGE_SELFTEST_BIN)

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

$(DEMO_BIN): demo_cli/src/main.c $(LIB_STATIC) verify-librmr-symbols
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(BENCH_BIN): bench/src/rmr_benchmark_main.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(SELFTEST_BIN): demo_cli/src/bitraf_selftest.c $(LIB_BITRAF_STATIC) $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_BITRAF_STATIC) $(LIB_STATIC) $(LDFLAGS) -o $@

$(BITRAF_BIN): engine/rmr/src/rafaelia_bitraf_core.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) -DRAF_HOSTED_TEST=1 $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(APK_MODULE_BIN): demo_cli/src/apk_module_demo.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@


$(MATH_FABRIC_SELFTEST_BIN): demo_cli/src/math_fabric_selftest.c $(LIB_STATIC) verify-librmr-symbols
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

verify-librmr-symbols: $(LIB_STATIC)
	@for sym in $(RMR_REQUIRED_SYMBOLS); do \
		if ! nm -g --defined-only $(LIB_STATIC) | awk '{if ($$3 == "'"$$sym"'") found=1} END {exit(found ? 0 : 1)}'; then \
			echo "[link-contract] missing symbol in $(LIB_STATIC): $$sym" >&2; \
			exit 1; \
		fi; \
	done
	@echo "[link-contract] verified required symbols in $(LIB_STATIC)"

$(CTI_SCAN_BIN): engine/rmr/src/rafa_cti_scan.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -lm -o $@


$(POLICY_DEMO_BIN): demo_cli/src/policy_kernel_demo.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(POLICY_SELFTEST_BIN): demo_cli/src/policy_kernel_selftest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(QEMU_BRIDGE_DEMO_BIN): demo_cli/src/rmr_qemu_bridge_demo.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(QEMU_BRIDGE_SELFTEST_BIN): demo_cli/src/rmr_qemu_bridge_selftest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(DETERMINISM_SIGNATURE_SELFTEST_BIN): demo_cli/src/determinism_signature_selftest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(CASM_BRIDGE_SELFTEST_BIN): demo_cli/src/rmr_casm_bridge_selftest.c $(CASM_BRIDGE_OBJS)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(CASM_BRIDGE_OBJS) $(LDFLAGS) -o $@

$(BITOMEGA_SMOKETEST_BIN): demo_cli/src/bitomega_smoketest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

run-bitomega-smoketest: $(BITOMEGA_SMOKETEST_BIN)
	@mkdir -p bench/results
	./$(BITOMEGA_SMOKETEST_BIN)
run-demo: $(DEMO_BIN)
	./$(DEMO_BIN)

run-casm-selftest: $(CASM_BRIDGE_SELFTEST_BIN)
	./$(CASM_BRIDGE_SELFTEST_BIN)

run-selftest: $(SELFTEST_BIN) $(MATH_FABRIC_SELFTEST_BIN) $(DETERMINISM_SIGNATURE_SELFTEST_BIN) $(CASM_BRIDGE_SELFTEST_BIN) $(POLICY_SELFTEST_BIN) $(QEMU_BRIDGE_SELFTEST_BIN) $(BITOMEGA_SMOKETEST_BIN)
	./$(SELFTEST_BIN)
	./$(MATH_FABRIC_SELFTEST_BIN)
	./$(DETERMINISM_SIGNATURE_SELFTEST_BIN)
	./$(CASM_BRIDGE_SELFTEST_BIN)
	./$(POLICY_SELFTEST_BIN)
	./$(QEMU_BRIDGE_SELFTEST_BIN)
	@mkdir -p bench/results
	./$(BITOMEGA_SMOKETEST_BIN)

run-bench: $(BENCH_BIN)
	./$(BENCH_BIN) bench/results/latest.csv bench/results/latest.json

run-baremetal-gate:
	tools/baremetal/hw_caps_detect.sh reports/baremetal/hw_caps.env
	tools/baremetal/dir_integrity_matrix.sh reports/baremetal/dir_integrity_matrix.json

run-release-gate: run-selftest run-bench run-baremetal-gate
	bench/scripts/run_bench.sh 7 bench/results

clean:
	rm -rf build

.PHONY: all clean verify-librmr-symbols run-demo run-casm-selftest run-selftest run-bitomega-smoketest run-bench run-baremetal-gate run-release-gate

# ── NEON SIMD selftest (BUG#45 addition) ──
NEON_SIMD_SELFTEST_BIN := build/demo/rmr_neon_simd_selftest
$(NEON_SIMD_SELFTEST_BIN): demo_cli/src/neon_simd_selftest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

print-build-config:
	@echo "RMR_JNI_BUILD=$(RMR_JNI_BUILD)"
	@echo "RMR_ENABLE_POLICY_MODULE=$(RMR_ENABLE_POLICY_MODULE)"
	@echo "CPPFLAGS=$(CPPFLAGS)"
	@echo "CFLAGS=$(CFLAGS)"

.PHONY: print-build-config
