cmake_minimum_required(VERSION 3.22.1)
project(vectra_core_accel C)

set(VECTRA_CORE_ACCEL_SOURCES
    vectra_core_accel.c
    ../../../../engine/rmr/src/rmr_unified_kernel.c
    ../../../../engine/rmr/src/rmr_hw_detect.c
    ../../../../engine/rmr/src/rmr_policy_kernel.c
    ../../../../engine/rmr/src/bitraf.c
    ../../../../engine/rmr/src/rmr_corelib.c
    ../../../../engine/rmr/src/rmr_cycles.c
    ../../../../engine/rmr/src/rmr_ll_ops.c
    ../../../../engine/rmr/src/rmr_math_fabric.c
    ../../../../engine/rmr/src/rmr_ll_tuning.c)

list(REMOVE_DUPLICATES VECTRA_CORE_ACCEL_SOURCES)

add_library(vectra_core_accel SHARED ${VECTRA_CORE_ACCEL_SOURCES})

find_library(log-lib log)

target_compile_options(vectra_core_accel PRIVATE -O3 -ffast-math -fvisibility=hidden)

target_link_libraries(vectra_core_accel ${log-lib})

target_include_directories(vectra_core_accel PRIVATE ../../../../ ../../../../engine/rmr/include)
