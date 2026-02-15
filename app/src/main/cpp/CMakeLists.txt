cmake_minimum_required(VERSION 3.22.1)
project(vectra_core_accel C)

set(VECTRA_REPO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../)
set(VECTRA_RMR_INCLUDE_DIR ${VECTRA_REPO_ROOT}/engine/rmr/include)

add_library(vectra_core_accel SHARED
    vectra_core_accel.c
    ../../../../engine/rmr/src/rmr_unified_kernel.c
    ../../../../engine/rmr/src/rmr_hw_detect.c
    ../../../../engine/rmr/src/rmr_corelib.c
    ../../../../engine/rmr/src/rmr_ll_ops.c
    ../../../../engine/rmr/src/rmr_cycles.c)

if(RMR_ENABLE_POLICY_MODULE)
    add_library(rmr_policy_static STATIC
        ../../../../engine/rmr/src/rmr_policy_kernel.c
        ../../../../engine/rmr/src/rmr_math_fabric.c
        ../../../../engine/rmr/src/rmr_ll_tuning.c)
endif()

add_library(bitraf_static STATIC
    ../../../../engine/rmr/src/bitraf.c)

find_library(log-lib log)
find_package(JNI REQUIRED)

if(ANDROID)
    find_library(log-lib log)
else()
    set(log-lib "")
endif()

target_compile_options(rmr_core_static PRIVATE -O3 -ffast-math -fvisibility=hidden)
if(TARGET rmr_policy_static)
    target_compile_options(rmr_policy_static PRIVATE -O3 -ffast-math -fvisibility=hidden)
endif()
target_compile_options(bitraf_static PRIVATE -O3 -ffast-math -fvisibility=hidden)
target_compile_options(vectra_core_accel PRIVATE -O3 -ffast-math -fvisibility=hidden)

target_link_libraries(vectra_core_accel PRIVATE rmr_core_static ${log-lib})

if(TARGET rmr_policy_static)
    target_link_libraries(vectra_core_accel PRIVATE rmr_policy_static)
endif()

if(RMR_ENABLE_BITRAF_MODULE)
    target_link_libraries(vectra_core_accel PRIVATE bitraf_static)
endif()

target_include_directories(vectra_core_accel PRIVATE
    ${VECTRA_RMR_INCLUDE_DIR}
    ${JNI_INCLUDE_DIRS})

if(CMAKE_ANDROID_NDK)
    target_include_directories(vectra_core_accel PRIVATE
        ${CMAKE_ANDROID_NDK}/sysroot/usr/include
        ${CMAKE_ANDROID_NDK}/sysroot/usr/include/${CMAKE_ANDROID_ARCH_TRIPLE})
endif()
