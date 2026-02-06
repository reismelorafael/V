cmake_minimum_required(VERSION 3.22.1)
project(vectra_core_accel C)

add_library(vectra_core_accel SHARED
    vectra_core_accel.c)

find_library(log-lib log)

target_compile_options(vectra_core_accel PRIVATE -O3 -ffast-math -fvisibility=hidden)

target_link_libraries(vectra_core_accel ${log-lib})
