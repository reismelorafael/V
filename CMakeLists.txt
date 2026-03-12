# Baseline único de CMake alinhado ao pipeline Android JNI/Gradle.
cmake_minimum_required(VERSION 3.22.1)
project(rafaelia_rmr C ASM)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

include(${CMAKE_SOURCE_DIR}/engine/rmr/sources_rmr_core.cmake)

option(RMR_JNI_BUILD "Use hosted/JNI libc path for root builds" ON)
set(RMR_BUILD_HOST_TOOLING "AUTO" CACHE STRING "Host-tooling compatibility path (AUTO mirrors RMR_JNI_BUILD)" )
set_property(CACHE RMR_BUILD_HOST_TOOLING PROPERTY STRINGS AUTO ON OFF)
option(RMR_ENABLE_POLICY_MODULE "Enable RMR policy module for root builds" ON)
option(RMR_ASM_CORE_EXPERIMENTAL "Enable experimental ASM core hot paths" ON)
option(RMR_ASM_CORE_X86_64_VALIDATED "Enable ASM hot paths on x86_64 (validated ABI)" ON)
option(RMR_ASM_CORE_ARM64_VALIDATED "Enable ASM hot paths on arm64-v8a (validated ABI)" ON)
option(RMR_ASM_CORE_RISCV64_VALIDATED "Enable RV64I-compatible CASM path on riscv64 (validated ABI)" ON)

if(RMR_JNI_BUILD)
  set(RMR_JNI_BUILD_VALUE 1)
else()
  set(RMR_JNI_BUILD_VALUE 0)
endif()

if(RMR_BUILD_HOST_TOOLING STREQUAL "AUTO")
  set(RMR_BUILD_HOST_TOOLING_VALUE ${RMR_JNI_BUILD_VALUE})
elseif(RMR_BUILD_HOST_TOOLING)
  set(RMR_BUILD_HOST_TOOLING_VALUE 1)
else()
  set(RMR_BUILD_HOST_TOOLING_VALUE 0)
endif()

if(RMR_ENABLE_POLICY_MODULE)
  set(RMR_ENABLE_POLICY_MODULE_VALUE 1)
else()
  set(RMR_ENABLE_POLICY_MODULE_VALUE 0)
endif()

message(STATUS "RMR_JNI_BUILD resolved to ${RMR_JNI_BUILD_VALUE}")
message(STATUS "RMR_BUILD_HOST_TOOLING input=${RMR_BUILD_HOST_TOOLING} resolved=${RMR_BUILD_HOST_TOOLING_VALUE}")
message(STATUS "RMR_ENABLE_POLICY_MODULE resolved to ${RMR_ENABLE_POLICY_MODULE_VALUE}")

file(WRITE "${CMAKE_BINARY_DIR}/rmr_build_config.env"
  "RMR_JNI_BUILD=${RMR_JNI_BUILD_VALUE}\n"
  "RMR_BUILD_HOST_TOOLING=${RMR_BUILD_HOST_TOOLING_VALUE}\n"
  "RMR_ENABLE_POLICY_MODULE=${RMR_ENABLE_POLICY_MODULE_VALUE}\n"
)

if(RMR_ASM_CORE_EXPERIMENTAL)
  set(RMR_ASM_CORE_EXPERIMENTAL_VALUE 1)
else()
  set(RMR_ASM_CORE_EXPERIMENTAL_VALUE 0)
endif()

if(RMR_ASM_CORE_X86_64_VALIDATED)
  set(RMR_ASM_CORE_X86_64_VALIDATED_VALUE 1)
else()
  set(RMR_ASM_CORE_X86_64_VALIDATED_VALUE 0)
endif()

if(RMR_ASM_CORE_ARM64_VALIDATED)
  set(RMR_ASM_CORE_ARM64_VALIDATED_VALUE 1)
else()
  set(RMR_ASM_CORE_ARM64_VALIDATED_VALUE 0)
endif()

if(RMR_ASM_CORE_RISCV64_VALIDATED)
  set(RMR_ASM_CORE_RISCV64_VALIDATED_VALUE 1)
else()
  set(RMR_ASM_CORE_RISCV64_VALIDATED_VALUE 0)
endif()

function(rmr_apply_common_flags target_name)
  target_compile_options(${target_name} PRIVATE
    $<$<C_COMPILER_ID:MSVC>:/O2 /W4>
    $<$<NOT:$<C_COMPILER_ID:MSVC>>:-O3 -Wall -Wextra -pedantic>
  )
  target_compile_definitions(${target_name} PRIVATE
    RMR_JNI_BUILD=${RMR_JNI_BUILD_VALUE}
    RMR_BUILD_HOST_TOOLING=${RMR_BUILD_HOST_TOOLING_VALUE}
    RMR_ENABLE_POLICY_MODULE=${RMR_ENABLE_POLICY_MODULE_VALUE}
    RMR_ASM_CORE_EXPERIMENTAL=${RMR_ASM_CORE_EXPERIMENTAL_VALUE}
    RMR_ASM_CORE_X86_64_VALIDATED=${RMR_ASM_CORE_X86_64_VALIDATED_VALUE}
    RMR_ASM_CORE_ARM64_VALIDATED=${RMR_ASM_CORE_ARM64_VALIDATED_VALUE}
    RMR_ASM_CORE_RISCV64_VALIDATED=${RMR_ASM_CORE_RISCV64_VALIDATED_VALUE}
  )
  target_include_directories(${target_name} PRIVATE ${CMAKE_SOURCE_DIR}/engine/rmr/include)
endfunction()



rmr_manifest_apply_base(RMR_SOURCES ${RMR_SOURCE_GROUP_CORE} ${RMR_SOURCE_GROUP_HOST_ONLY})

if(RMR_ENABLE_POLICY_MODULE)
  rmr_manifest_apply_base(RMR_POLICY_SOURCES ${RMR_SOURCE_GROUP_OPTIONAL_POLICY})
  list(APPEND RMR_SOURCES ${RMR_POLICY_SOURCES})
endif()

set(RMR_HAS_CASM FALSE)
set(RMR_HAS_NEON_SELFTEST FALSE)

if(RMR_ASM_CORE_EXPERIMENTAL AND CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64" AND RMR_ASM_CORE_X86_64_VALIDATED)
  list(APPEND RMR_SOURCES
    ${RMR_SOURCE_GROUP_ASM_X86_64}
  )
  set(RMR_HAS_CASM TRUE)
endif()

if(RMR_ASM_CORE_EXPERIMENTAL AND CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "riscv64" AND RMR_ASM_CORE_RISCV64_VALIDATED)
  list(APPEND RMR_SOURCES
    ${RMR_SOURCE_GROUP_ASM_RISCV64}
  )
  set(RMR_HAS_CASM TRUE)
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)$")
  set(RMR_HAS_NEON_SELFTEST TRUE)
endif()

if(RMR_HAS_CASM)
  add_compile_definitions(VECTRA_HAS_CASM_MARKER=1)
else()
  add_compile_definitions(VECTRA_HAS_CASM_MARKER=0)
endif()

add_library(rmr STATIC ${RMR_SOURCES})
rmr_apply_common_flags(rmr)

set(RMR_REQUIRED_SYMBOLS
  RmR_MathFabric_AutodetectPlan
  RmR_MathFabric_VectorMix
)

add_custom_command(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/verify_librmr_symbols.stamp
  COMMAND ${CMAKE_SOURCE_DIR}/tools/verify_librmr_symbols.sh
          $<TARGET_FILE:rmr>
          ${RMR_REQUIRED_SYMBOLS}
  COMMAND ${CMAKE_COMMAND} -E touch ${CMAKE_CURRENT_BINARY_DIR}/verify_librmr_symbols.stamp
  DEPENDS rmr ${CMAKE_SOURCE_DIR}/tools/verify_librmr_symbols.sh
  VERBATIM
)

add_custom_target(verify_librmr_symbols
  DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/verify_librmr_symbols.stamp
)

add_custom_target(verify_contracts
  DEPENDS verify_librmr_symbols
)

add_executable(rafaelia_demo demo_cli/src/main.c)
target_link_libraries(rafaelia_demo PRIVATE rmr)
rmr_apply_common_flags(rafaelia_demo)

add_executable(rmr_bench bench/src/rmr_benchmark_main.c)
target_link_libraries(rmr_bench PRIVATE rmr)
target_compile_definitions(rmr_bench PRIVATE RMR_JNI_BUILD=1)
rmr_apply_common_flags(rmr_bench)

add_executable(bitomega_smoketest demo_cli/src/bitomega_smoketest.c)
target_link_libraries(bitomega_smoketest PRIVATE rmr)
rmr_apply_common_flags(bitomega_smoketest)

add_executable(bitraf_core engine/rmr/src/rafaelia_bitraf_core.c)
target_compile_definitions(bitraf_core PRIVATE RAF_HOSTED_TEST=1)
target_link_libraries(bitraf_core PRIVATE rmr)
rmr_apply_common_flags(bitraf_core)

add_library(bitraf_static STATIC engine/rmr/src/bitraf.c)
set_target_properties(bitraf_static PROPERTIES OUTPUT_NAME bitraf)
rmr_apply_common_flags(bitraf_static)

target_link_libraries(rmr PUBLIC bitraf_static)

add_library(bitraf_shared SHARED engine/rmr/src/bitraf.c)
set_target_properties(bitraf_shared PROPERTIES OUTPUT_NAME bitraf_shared)
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

add_executable(determinism_signature_selftest demo_cli/src/determinism_signature_selftest.c)
target_link_libraries(determinism_signature_selftest PRIVATE rmr)
rmr_apply_common_flags(determinism_signature_selftest)

add_executable(apk_module_demo demo_cli/src/apk_module_demo.c)
target_link_libraries(apk_module_demo PRIVATE rmr)
rmr_apply_common_flags(apk_module_demo)

add_executable(rmr_qemu_bridge_demo demo_cli/src/rmr_qemu_bridge_demo.c)
target_link_libraries(rmr_qemu_bridge_demo PRIVATE rmr)
rmr_apply_common_flags(rmr_qemu_bridge_demo)

add_executable(rmr_qemu_bridge_selftest demo_cli/src/rmr_qemu_bridge_selftest.c)
target_link_libraries(rmr_qemu_bridge_selftest PRIVATE rmr)
rmr_apply_common_flags(rmr_qemu_bridge_selftest)

add_executable(rafa_cti_scan engine/rmr/src/rafa_cti_scan.c)
target_link_libraries(rafa_cti_scan PRIVATE rmr m)
rmr_apply_common_flags(rafa_cti_scan)

if(RMR_HAS_CASM)
  add_executable(rmr_casm_bridge_selftest demo_cli/src/rmr_casm_bridge_selftest.c)
  target_link_libraries(rmr_casm_bridge_selftest PRIVATE rmr)
  rmr_apply_common_flags(rmr_casm_bridge_selftest)
endif()

add_executable(rmr_asm_equivalence_selftest demo_cli/src/rmr_asm_equivalence_selftest.c)
target_link_libraries(rmr_asm_equivalence_selftest PRIVATE rmr)
rmr_apply_common_flags(rmr_asm_equivalence_selftest)

add_executable(rmr_unified_arena_selftest demo_cli/src/rmr_unified_arena_selftest.c)

add_executable(rmr_legacy_kernel_selftest demo_cli/src/rmr_legacy_kernel_selftest.c)
target_link_libraries(rmr_legacy_kernel_selftest PRIVATE rmr)
rmr_apply_common_flags(rmr_legacy_kernel_selftest)

add_executable(rmr_hw_detect_selftest demo_cli/src/rmr_hw_detect_selftest.c)
target_link_libraries(rmr_hw_detect_selftest PRIVATE rmr)
rmr_apply_common_flags(rmr_hw_detect_selftest)

add_executable(zipraf_core_selftest demo_cli/src/zipraf_core_selftest.c)
target_link_libraries(zipraf_core_selftest PRIVATE rmr)
rmr_apply_common_flags(zipraf_core_selftest)

if(RMR_HAS_NEON_SELFTEST)
  add_executable(rmr_neon_simd_selftest demo_cli/src/neon_simd_selftest.c)
  target_link_libraries(rmr_neon_simd_selftest PRIVATE rmr)
  rmr_apply_common_flags(rmr_neon_simd_selftest)
endif()

target_link_libraries(rmr_unified_arena_selftest PRIVATE bitraf_static rmr)
rmr_apply_common_flags(rmr_unified_arena_selftest)


set(RMR_RUN_SELFTEST_COMMANDS
  bitraf_selftest
  math_fabric_selftest
  determinism_signature_selftest
  policy_kernel_selftest
  rmr_qemu_bridge_selftest
  rmr_unified_arena_selftest
  rmr_legacy_kernel_selftest
  rmr_hw_detect_selftest
  rmr_asm_equivalence_selftest
  zipraf_core_selftest
  bitomega_smoketest
)

set(RMR_RUN_SELFTEST_DEPENDS
  bitraf_selftest
  math_fabric_selftest
  determinism_signature_selftest
  policy_kernel_selftest
  rmr_qemu_bridge_selftest
  rmr_unified_arena_selftest
  rmr_legacy_kernel_selftest
  rmr_hw_detect_selftest
  rmr_asm_equivalence_selftest
  zipraf_core_selftest
  bitomega_smoketest
)

if(RMR_HAS_CASM)
  list(INSERT RMR_RUN_SELFTEST_COMMANDS 3 rmr_casm_bridge_selftest)
  list(INSERT RMR_RUN_SELFTEST_DEPENDS 3 rmr_casm_bridge_selftest)
endif()

if(RMR_HAS_NEON_SELFTEST)
  list(APPEND RMR_RUN_SELFTEST_COMMANDS rmr_neon_simd_selftest)
  list(APPEND RMR_RUN_SELFTEST_DEPENDS rmr_neon_simd_selftest)
endif()

add_custom_target(run_selftest
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/bench/results
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  DEPENDS verify_contracts ${RMR_RUN_SELFTEST_DEPENDS}
)

foreach(RMR_SELFTEST_CMD IN LISTS RMR_RUN_SELFTEST_COMMANDS)
  add_custom_command(TARGET run_selftest POST_BUILD
    COMMAND $<TARGET_FILE:${RMR_SELFTEST_CMD}>
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  )
endforeach()
add_custom_target(run_bitomega_smoketest
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/bench/results
  COMMAND $<TARGET_FILE:bitomega_smoketest>
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  DEPENDS bitomega_smoketest
)

find_package(Python3 COMPONENTS Interpreter QUIET)
find_program(GRAPHVIZ_DOT_EXECUTABLE dot)
if(Python3_Interpreter_FOUND AND GRAPHVIZ_DOT_EXECUTABLE)
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
else()
  set(RMR_REPORT_MISSING_TOOLS "")
  if(NOT Python3_Interpreter_FOUND)
    list(APPEND RMR_REPORT_MISSING_TOOLS "Python3 interpreter")
  endif()
  if(NOT GRAPHVIZ_DOT_EXECUTABLE)
    list(APPEND RMR_REPORT_MISSING_TOOLS "Graphviz dot")
  endif()
  string(JOIN ", " RMR_REPORT_MISSING_TOOLS_TEXT ${RMR_REPORT_MISSING_TOOLS})
  message(STATUS "Skipping bitomega_transition_graph target: missing ${RMR_REPORT_MISSING_TOOLS_TEXT}")
endif()
