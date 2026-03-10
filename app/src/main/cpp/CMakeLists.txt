cmake_minimum_required(VERSION 3.22.1)
project(vectra_core_accel C ASM)

option(RMR_ENABLE_POLICY_MODULE "Enable RMR policy module" ON)
option(RMR_ENABLE_BITRAF_MODULE "Enable BITRAF module" ON)
option(RMR_ASM_CORE_EXPERIMENTAL "Enable experimental ASM core hot paths" ON)
option(RMR_ASM_CORE_X86_64_VALIDATED "Enable ASM hot paths on x86_64 (validated ABI)" ON)
option(RMR_ASM_CORE_ARM64_VALIDATED "Enable ASM hot paths on arm64-v8a (validated ABI)" ON)
option(RMR_ASM_CORE_RISCV64_VALIDATED "Enable RV64I-compatible CASM path on riscv64 (validated ABI)" ON)

set(VECTRA_REPO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../)
set(VECTRA_RMR_INCLUDE_DIR ${VECTRA_REPO_ROOT}/engine/rmr/include)

set(RMR_SOURCE_BASE ../../../../)
include(${VECTRA_REPO_ROOT}/engine/rmr/sources_rmr_core.cmake)
rmr_manifest_apply_base(VECTRA_RMR_CORE_SOURCES ${RMR_SOURCE_GROUP_CORE})
rmr_manifest_apply_base(VECTRA_RMR_ANDROID_ONLY_SOURCES ${RMR_SOURCE_GROUP_ANDROID_ONLY})
rmr_manifest_apply_base(VECTRA_RMR_OPTIONAL_POLICY_SOURCES ${RMR_SOURCE_GROUP_OPTIONAL_POLICY})
rmr_manifest_apply_base(VECTRA_RMR_ASM_X86_64_SOURCES ${RMR_SOURCE_GROUP_ASM_X86_64})
rmr_manifest_apply_base(VECTRA_RMR_ASM_ARM64_SOURCES ${RMR_SOURCE_GROUP_ASM_ARM64})
rmr_manifest_apply_base(VECTRA_RMR_ASM_RISCV64_SOURCES ${RMR_SOURCE_GROUP_ASM_RISCV64})

set(VECTRA_LOWLEVEL_SOURCES)
set(VECTRA_CASM_SOURCES)

if(RMR_ASM_CORE_EXPERIMENTAL AND ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64" AND RMR_ASM_CORE_X86_64_VALIDATED)
    list(APPEND VECTRA_LOWLEVEL_SOURCES ${VECTRA_RMR_ASM_X86_64_SOURCES})
endif()

if(RMR_ASM_CORE_EXPERIMENTAL AND NOT ANDROID AND (CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64") AND RMR_ASM_CORE_X86_64_VALIDATED)
    list(APPEND VECTRA_LOWLEVEL_SOURCES ${VECTRA_RMR_ASM_X86_64_SOURCES})
endif()

if(RMR_ASM_CORE_EXPERIMENTAL AND ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a" AND RMR_ASM_CORE_ARM64_VALIDATED)
    list(APPEND VECTRA_CASM_SOURCES ${VECTRA_RMR_ASM_ARM64_SOURCES})
endif()

# Roadmap explícito: ABI riscv64 permanece INATIVA por política Android
# (APP_ABI_POLICY/SUPPORTED_ABIS). A rotina CASM riscv64 já é RV64I-compatible
# (sem extensão M) e depende apenas de habilitação dessa política ABI.
# if(RMR_ASM_CORE_EXPERIMENTAL AND ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "riscv64" AND RMR_ASM_CORE_RISCV64_VALIDATED)
#     list(APPEND VECTRA_CASM_SOURCES ${VECTRA_RMR_ASM_RISCV64_SOURCES})
# endif()

add_library(vectra_core_accel SHARED
    vectra_core_accel.c
    lowlevel_bridge.c
    hardware_profile_bridge.c
    ${VECTRA_RMR_CORE_SOURCES}
    # Android-only engine units: intentionally excluded from host/root build targets.
    ${VECTRA_RMR_ANDROID_ONLY_SOURCES}
    ${VECTRA_LOWLEVEL_SOURCES}
    ${VECTRA_CASM_SOURCES})

if(VECTRA_CASM_SOURCES)
    target_compile_definitions(vectra_core_accel PRIVATE VECTRA_HAS_CASM_MARKER=1)
else()
    target_compile_definitions(vectra_core_accel PRIVATE VECTRA_HAS_CASM_MARKER=0)
endif()

if(RMR_ENABLE_POLICY_MODULE)
    add_library(rmr_policy_static STATIC
        ../../../../engine/rmr/src/rmr_baremetal_compat.c
        ${VECTRA_RMR_OPTIONAL_POLICY_SOURCES}
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

# BUG#3 FIX: -ffreestanding/-fno-builtin removed — Android JNI requires bionic libc
set(VECTRA_COMMON_COMPILE_OPTIONS -O2 -fno-fast-math -fvisibility=hidden -DRMR_JNI_BUILD=1 -DRMR_BUILD_HOST_TOOLING=1)
set(VECTRA_ABI_COMPILE_OPTIONS)

# Flag arm64 vinda do Gradle: baseline (-march=armv8-a) e tuning opcional (-mcpu=<valor>).
set(VECTRA_ARM64_CPP_FLAGS "${VECTRA_ARM64_CPP_FLAGS}" CACHE STRING "Extra arm64 C/C++ flags from Gradle")
set(VECTRA_ARM64_CPP_FLAGS_LIST)
if(VECTRA_ARM64_CPP_FLAGS)
    separate_arguments(VECTRA_ARM64_CPP_FLAGS_LIST NATIVE_COMMAND "${VECTRA_ARM64_CPP_FLAGS}")
endif()

# CMake já define -march=armv8-a+crc+simd para arm64-v8a; evita sobrescrever esse targeting.
set(VECTRA_ARM64_TUNING_FLAGS)
foreach(_flag IN LISTS VECTRA_ARM64_CPP_FLAGS_LIST)
    if(NOT _flag MATCHES "^-march=")
        list(APPEND VECTRA_ARM64_TUNING_FLAGS ${_flag})
    endif()
endforeach()

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    # +crc enables CRC32 intrinsics | +simd enables NEON | +sha2 enables SHA hw
    set(VECTRA_ABI_COMPILE_OPTIONS
        -march=armv8-a+crc+simd
        -DRMR_ARCH_ARM64=1
        -DRMR_FEAT_CRC32_HW=1)
    list(APPEND VECTRA_ABI_COMPILE_OPTIONS ${VECTRA_ARM64_TUNING_FLAGS})
endif()
if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(VECTRA_ABI_COMPILE_OPTIONS -msse4.2 -mpopcnt -DRMR_ARCH_X86_64=1)
endif()

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(VECTRA_ABI_COMPILE_OPTIONS -mssse3 -msse4.1 -DRMR_ARCH_X86=1)
endif()
if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    set(VECTRA_ABI_COMPILE_OPTIONS -march=armv7-a -mfpu=neon -DRMR_ARCH_ARM32=1)
endif()

if(TARGET rmr_policy_static)
    target_compile_options(rmr_policy_static PRIVATE
        ${VECTRA_COMMON_COMPILE_OPTIONS}
        ${VECTRA_ABI_COMPILE_OPTIONS})
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
        ${VECTRA_ABI_COMPILE_OPTIONS})
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
    ${VECTRA_ABI_COMPILE_OPTIONS})

target_compile_definitions(vectra_core_accel PRIVATE
    RMR_ASM_CORE_EXPERIMENTAL=$<BOOL:${RMR_ASM_CORE_EXPERIMENTAL}>
    RMR_ASM_CORE_X86_64_VALIDATED=$<BOOL:${RMR_ASM_CORE_X86_64_VALIDATED}>
    RMR_ASM_CORE_ARM64_VALIDATED=$<BOOL:${RMR_ASM_CORE_ARM64_VALIDATED}>
    RMR_ASM_CORE_RISCV64_VALIDATED=$<BOOL:${RMR_ASM_CORE_RISCV64_VALIDATED}>)

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
