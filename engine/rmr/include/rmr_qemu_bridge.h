#ifndef RMR_QEMU_BRIDGE_H
#define RMR_QEMU_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

#include "rmr_hw_detect.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_QEMU_PRESET_BALANCED = 0,
  RMR_QEMU_PRESET_PERFORMANCE = 1,
  RMR_QEMU_PRESET_COMPATIBILITY = 2
} RmR_QemuPreset;

typedef enum {
  RMR_GUEST_ARCH_X86_64 = 0,
  RMR_GUEST_ARCH_I386 = 1,
  RMR_GUEST_ARCH_ARM64 = 2,
  RMR_GUEST_ARCH_PPC = 3
} RmR_GuestArch;

typedef struct {
  RmR_QemuPreset preset;
  RmR_GuestArch arch;
  uint32_t host_cores;
  uint32_t vm_cpus;
  uint32_t vm_mem_mib;
  uint8_t use_kvm;
  uint8_t use_virtio;
  uint8_t use_iothread;
  uint8_t use_direct_io;
  uint8_t use_multifd;
  uint8_t low_latency;
} RmR_QemuPlan;

typedef struct {
  uint8_t running;
  uint32_t vcpu_count;
  uint32_t halted_count;
  uint32_t running_count;
} RmR_QmpTelemetry;

void RmR_QemuPlan_Default(RmR_QemuPlan *plan);
void RmR_QemuPlan_Autotune(const RmR_HW_Info *hw,
                           RmR_GuestArch arch,
                           uint32_t mem_mib,
                           uint8_t low_latency,
                           RmR_QemuPlan *plan);
int RmR_QemuPlan_BuildArgs(const RmR_QemuPlan *plan, char *out, size_t out_len);
int RmR_QmpTelemetry_Parse(const char *qmp_json_line, RmR_QmpTelemetry *out);

#ifdef __cplusplus
}
#endif

#endif
