CC ?= cc
AR ?= ar
CFLAGS ?= -O3 -std=c11 -Wall -Wextra -pedantic -Iengine/rmr/include
LDFLAGS ?=

UNAME_S := $(shell uname -s 2>/dev/null || echo Unknown)
SHARED_EXT := so
ifeq ($(OS),Windows_NT)
  SHARED_EXT := dll
else ifeq ($(UNAME_S),Darwin)
  SHARED_EXT := dylib
endif

ENGINE_SRCS := engine/rmr/src/rmr_cycles.c engine/rmr/src/rmr_hw_detect.c engine/rmr/src/rmr_bench.c engine/rmr/src/rmr_bench_suite.c engine/rmr/src/rmr_isorf.c engine/rmr/src/rmr_apk_module.c engine/rmr/src/rmr_policy_kernel.c engine/rmr/src/rmr_qemu_bridge.c
ENGINE_OBJS := $(patsubst %.c,build/%.o,$(ENGINE_SRCS))
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

all: $(LIB_STATIC) $(LIB_BITRAF_STATIC) $(LIB_BITRAF_SHARED) $(DEMO_BIN) $(BENCH_BIN) $(BITRAF_BIN) $(SELFTEST_BIN) $(APK_MODULE_BIN) $(CTI_SCAN_BIN) $(POLICY_DEMO_BIN) $(POLICY_SELFTEST_BIN) $(QEMU_BRIDGE_DEMO_BIN) $(QEMU_BRIDGE_SELFTEST_BIN)

build/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c $< -o $@

$(LIB_STATIC): $(ENGINE_OBJS)
	@mkdir -p $(dir $@)
	$(AR) rcs $@ $^

$(LIB_BITRAF_STATIC): $(BITRAF_API_OBJ)
	@mkdir -p $(dir $@)
	$(AR) rcs $@ $^

$(LIB_BITRAF_SHARED): $(BITRAF_API_SRC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -fPIC -shared $< $(LDFLAGS) -o $@

$(DEMO_BIN): demo_cli/src/main.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(BENCH_BIN): bench/src/rmr_benchmark_main.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(SELFTEST_BIN): demo_cli/src/bitraf_selftest.c $(LIB_BITRAF_STATIC) $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_BITRAF_STATIC) $(LIB_STATIC) $(LDFLAGS) -o $@

$(BITRAF_BIN): engine/rmr/src/rafaelia_bitraf_core.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -DRAF_HOSTED_TEST=1 $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(APK_MODULE_BIN): demo_cli/src/apk_module_demo.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(CTI_SCAN_BIN): engine/rmr/src/rafa_cti_scan.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LDFLAGS) -lm -o $@


$(POLICY_DEMO_BIN): demo_cli/src/policy_kernel_demo.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(POLICY_SELFTEST_BIN): demo_cli/src/policy_kernel_selftest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(QEMU_BRIDGE_DEMO_BIN): demo_cli/src/rmr_qemu_bridge_demo.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@

$(QEMU_BRIDGE_SELFTEST_BIN): demo_cli/src/rmr_qemu_bridge_selftest.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< $(LIB_STATIC) $(LDFLAGS) -o $@
run-demo: $(DEMO_BIN)
	./$(DEMO_BIN)

run-selftest: $(SELFTEST_BIN) $(POLICY_SELFTEST_BIN)
	./$(SELFTEST_BIN)
	./$(POLICY_SELFTEST_BIN)
	./$(QEMU_BRIDGE_SELFTEST_BIN)

run-bench: $(BENCH_BIN)
	./$(BENCH_BIN) bench/results/latest.csv bench/results/latest.json

run-baremetal-gate:
	tools/baremetal/hw_caps_detect.sh reports/baremetal/hw_caps.env
	tools/baremetal/dir_integrity_matrix.sh reports/baremetal/dir_integrity_matrix.json

run-release-gate: run-selftest run-bench run-baremetal-gate
	bench/scripts/run_bench.sh 7 bench/results

clean:
	rm -rf build

.PHONY: all clean run-demo run-selftest run-bench run-baremetal-gate run-release-gate
