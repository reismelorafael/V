cmake_minimum_required(VERSION 3.22.1)
project(vectra_core_accel C)

option(RMR_ENABLE_POLICY_MODULE "Enable RMR policy module" ON)
option(RMR_ENABLE_BITRAF_MODULE "Enable BITRAF module" ON)

set(VECTRA_REPO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../)
set(VECTRA_RMR_INCLUDE_DIR ${VECTRA_REPO_ROOT}/engine/rmr/include)

set(VECTRA_LOWLEVEL_SOURCES)

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    list(APPEND VECTRA_LOWLEVEL_SOURCES ../../../../engine/rmr/interop/rmr_lowlevel_x86_64.S)
endif()

if(NOT ANDROID AND (CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64"))
    list(APPEND VECTRA_LOWLEVEL_SOURCES ../../../../engine/rmr/interop/rmr_lowlevel_x86_64.S)
endif()

add_library(vectra_core_accel SHARED
    vectra_core_accel.c
    lowlevel_bridge.c
    ../../../../engine/rmr/src/rmr_unified_kernel.c
    ../../../../engine/rmr/src/rmr_hw_detect.c
    ../../../../engine/rmr/src/rmr_corelib.c
    ../../../../engine/rmr/src/rmr_ll_ops.c
    ../../../../engine/rmr/src/rmr_cycles.c
    ${VECTRA_LOWLEVEL_SOURCES})

if(RMR_ENABLE_POLICY_MODULE)
    add_library(rmr_policy_static STATIC
        ../../../../engine/rmr/src/rmr_policy_kernel.c
        ../../../../engine/rmr/src/rmr_math_fabric.c
        ../../../../engine/rmr/src/rmr_ll_tuning.c)
endif()

if(RMR_ENABLE_BITRAF_MODULE)
    add_library(bitraf_static STATIC
        ../../../../engine/rmr/src/bitraf.c)
endif()

find_library(log-lib log)

if(ANDROID)
    find_library(log-lib log)
else()
    find_package(JNI REQUIRED COMPONENTS JVM)
    set(log-lib "")
endif()

set(VECTRA_COMMON_COMPILE_OPTIONS -O3 -ffast-math -fvisibility=hidden)
set(VECTRA_ARM64_COMPILE_OPTIONS)

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(VECTRA_ARM64_COMPILE_OPTIONS -march=armv8-a)
endif()

if(TARGET rmr_policy_static)
    target_compile_options(rmr_policy_static PRIVATE
        ${VECTRA_COMMON_COMPILE_OPTIONS}
        ${VECTRA_ARM64_COMPILE_OPTIONS})
    target_include_directories(rmr_policy_static PRIVATE
        ${VECTRA_RMR_INCLUDE_DIR})
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_POLICY_MODULE=1)
else()
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_POLICY_MODULE=0)
endif()

if(TARGET rmr_policy_static)
    target_link_libraries(vectra_core_accel PRIVATE rmr_policy_static)
endif()

if(TARGET bitraf_static)
    target_compile_options(bitraf_static PRIVATE
        ${VECTRA_COMMON_COMPILE_OPTIONS}
        ${VECTRA_ARM64_COMPILE_OPTIONS})
    target_include_directories(bitraf_static PRIVATE
        ${VECTRA_RMR_INCLUDE_DIR})
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_BITRAF_MODULE=1)
else()
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_BITRAF_MODULE=0)
endif()

if(TARGET bitraf_static)
    target_link_libraries(vectra_core_accel PRIVATE bitraf_static)
endif()

target_compile_options(vectra_core_accel PRIVATE
    ${VECTRA_COMMON_COMPILE_OPTIONS}
    ${VECTRA_ARM64_COMPILE_OPTIONS})

target_link_libraries(vectra_core_accel PRIVATE ${log-lib})

target_include_directories(vectra_core_accel PRIVATE
    ${VECTRA_RMR_INCLUDE_DIR})

if(NOT ANDROID)
    target_include_directories(vectra_core_accel PRIVATE ${JNI_INCLUDE_DIRS})
endif()

if(CMAKE_ANDROID_NDK)
    target_include_directories(vectra_core_accel PRIVATE
        ${CMAKE_ANDROID_NDK}/sysroot/usr/include
        ${CMAKE_ANDROID_NDK}/sysroot/usr/include/${CMAKE_ANDROID_ARCH_TRIPLE})
endif()
