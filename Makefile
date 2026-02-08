CC ?= cc
AR ?= ar
CFLAGS ?= -O3 -std=c11 -Wall -Wextra -pedantic -Iengine/rmr/include
LDFLAGS ?=

ENGINE_SRCS := engine/rmr/src/rmr_cycles.c engine/rmr/src/rmr_hw_detect.c engine/rmr/src/rmr_bench.c engine/rmr/src/rmr_bench_suite.c engine/rmr/src/rmr_isorf.c
ENGINE_OBJS := $(patsubst %.c,build/%.o,$(ENGINE_SRCS))
BITRAF_API_SRC := engine/rmr/src/bitraf.c
BITRAF_API_OBJ := $(patsubst %.c,build/%.o,$(BITRAF_API_SRC))
BITRAF_BIN := build/demo/bitraf_core

LIB_STATIC := build/engine/librmr.a
LIB_BITRAF_STATIC := build/engine/libbitraf.a
LIB_BITRAF_SHARED := build/engine/libbitraf.so
DEMO_BIN := build/demo/rafaelia_demo
BENCH_BIN := build/bench/rmr_bench

all: $(LIB_STATIC) $(LIB_BITRAF_STATIC) $(LIB_BITRAF_SHARED) $(DEMO_BIN) $(BENCH_BIN) $(BITRAF_BIN)

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

$(BITRAF_BIN): engine/rmr/src/rafaelia_bitraf_core.c $(LIB_STATIC)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -DRAF_HOSTED_TEST=1 $< $(LIB_STATIC) $(LDFLAGS) -o $@

run-demo: $(DEMO_BIN)
	./$(DEMO_BIN)

run-bench: $(BENCH_BIN)
	./$(BENCH_BIN) bench/results/latest.csv bench/results/latest.json

clean:
	rm -rf build

.PHONY: all clean run-demo run-bench
