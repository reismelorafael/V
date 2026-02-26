cmake_minimum_required(VERSION 3.16)
project(rafaelia_rmr C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

function(rmr_apply_common_flags target_name)
  target_compile_options(${target_name} PRIVATE
    $<$<C_COMPILER_ID:MSVC>:/O2 /W4>
    $<$<NOT:$<C_COMPILER_ID:MSVC>>:-O3 -Wall -Wextra -pedantic>
  )
endfunction()

add_library(rmr STATIC
  engine/rmr/src/bitomega.c
  engine/rmr/src/rmr_cycles.c
  engine/rmr/src/rmr_hw_detect.c
  engine/rmr/src/rmr_bench.c
  engine/rmr/src/rmr_bench_suite.c
  engine/rmr/src/rmr_isorf.c
  engine/rmr/src/rmr_apk_module.c
  engine/rmr/src/rmr_policy_kernel.c
  engine/rmr/src/rmr_qemu_bridge.c
  engine/rmr/src/rmr_math_fabric.c
  engine/rmr/src/rmr_corelib.c
  engine/rmr/src/rmr_ll_ops.c
  engine/rmr/src/rmr_ll_tuning.c
  engine/rmr/src/rmr_casm_bridge.c
  engine/rmr/src/rmr_unified_kernel.c
  engine/rmr/src/rmr_lowlevel_portable.c
  engine/rmr/src/rmr_lowlevel_mix.c
  engine/rmr/src/rmr_lowlevel_reduce.c
  engine/rmr/src/rmr_neon_simd.c
)

target_include_directories(rmr PUBLIC engine/rmr/include)
rmr_apply_common_flags(rmr)

add_executable(rafaelia_demo demo_cli/src/main.c)
target_link_libraries(rafaelia_demo PRIVATE rmr)
rmr_apply_common_flags(rafaelia_demo)

add_executable(rmr_bench bench/src/rmr_benchmark_main.c)
target_link_libraries(rmr_bench PRIVATE rmr)
rmr_apply_common_flags(rmr_bench)

add_executable(bitomega_smoketest demo_cli/src/bitomega_smoketest.c)
target_link_libraries(bitomega_smoketest PRIVATE rmr)
rmr_apply_common_flags(bitomega_smoketest)

add_custom_target(run_bitomega_smoketest
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/bench/results
  COMMAND bitomega_smoketest
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  DEPENDS bitomega_smoketest
)

find_package(Python3 COMPONENTS Interpreter REQUIRED)
find_program(GRAPHVIZ_DOT_EXECUTABLE dot REQUIRED)

add_custom_target(bitomega_transition_graph
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/reports
  COMMAND ${Python3_EXECUTABLE} ${CMAKE_SOURCE_DIR}/tools/bitomega_csv_to_dot.py
          ${CMAKE_SOURCE_DIR}/bench/results/bitomega_transitions.csv
          ${CMAKE_SOURCE_DIR}/reports/bitomega_transition_graph.dot
          ${CMAKE_SOURCE_DIR}/reports/bitomega_summary.md
  COMMAND ${GRAPHVIZ_DOT_EXECUTABLE} -Tpng
          ${CMAKE_SOURCE_DIR}/reports/bitomega_transition_graph.dot
          -o ${CMAKE_SOURCE_DIR}/reports/bitomega_transition_graph.png
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  DEPENDS run_bitomega_smoketest
)

add_executable(bitraf_core engine/rmr/src/rafaelia_bitraf_core.c)
target_compile_definitions(bitraf_core PRIVATE RAF_HOSTED_TEST=1)
target_link_libraries(bitraf_core PRIVATE rmr)
rmr_apply_common_flags(bitraf_core)

add_library(bitraf_static STATIC engine/rmr/src/bitraf.c)
set_target_properties(bitraf_static PROPERTIES OUTPUT_NAME bitraf)
target_include_directories(bitraf_static PUBLIC engine/rmr/include)
rmr_apply_common_flags(bitraf_static)

add_library(bitraf_shared SHARED engine/rmr/src/bitraf.c)
set_target_properties(bitraf_shared PROPERTIES OUTPUT_NAME bitraf)
target_include_directories(bitraf_shared PUBLIC engine/rmr/include)
rmr_apply_common_flags(bitraf_shared)

add_executable(bitraf_selftest demo_cli/src/bitraf_selftest.c)
target_link_libraries(bitraf_selftest PRIVATE bitraf_static rmr)
rmr_apply_common_flags(bitraf_selftest)


add_executable(policy_kernel_demo demo_cli/src/policy_kernel_demo.c)
target_link_libraries(policy_kernel_demo PRIVATE rmr)
rmr_apply_common_flags(policy_kernel_demo)

add_executable(policy_kernel_selftest demo_cli/src/policy_kernel_selftest.c)
target_link_libraries(policy_kernel_selftest PRIVATE rmr)
rmr_apply_common_flags(policy_kernel_selftest)

add_executable(math_fabric_selftest demo_cli/src/math_fabric_selftest.c)
target_link_libraries(math_fabric_selftest PRIVATE rmr)
rmr_apply_common_flags(math_fabric_selftest)

add_executable(rafa_cti_scan engine/rmr/src/rafa_cti_scan.c)
target_link_libraries(rafa_cti_scan PRIVATE rmr m)
target_include_directories(rafa_cti_scan PRIVATE engine/rmr/include)
rmr_apply_common_flags(rafa_cti_scan)
