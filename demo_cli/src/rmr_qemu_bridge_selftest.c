#include "rmr_qemu_bridge.h"

#include <stdio.h>
#include <string.h>

static int expect(int cond, const char *msg) {
  if (!cond) {
    fprintf(stderr, "FAIL: %s\n", msg);
    return 1;
  }
  return 0;
}

int main(void) {
  int failed = 0;
  RmR_QemuPlan plan;
  RmR_HW_Info hw;
  memset(&hw, 0, sizeof(hw));
  hw.arch = 4u;
  hw.ptr_bits = 64u;
  hw.word_bits = 64u;
  hw.cache_hint_l2 = 1024u * 1024u;
  hw.cache_hint_l4 = 32u * 1024u * 1024u;

  RmR_QemuPlan_Autotune(&hw, RMR_GUEST_ARCH_X86_64, 8192u, 0u, &plan);
  failed += expect(plan.preset == RMR_QEMU_PRESET_PERFORMANCE, "preset performance");
  failed += expect(plan.vm_cpus >= 2u, "vm cpus >=2");

  char out[512];
  failed += expect(RmR_QemuPlan_BuildArgs(&plan, out, sizeof(out)) == 0, "build args");
  failed += expect(strstr(out, "-smp") != NULL, "args has smp");
  failed += expect(strstr(out, "-accel kvm") != NULL, "args has kvm");

  {
    RmR_QemuPlan with_l4;
    RmR_QemuPlan without_l4;
    RmR_HW_Info hw_no_l4 = hw;
    hw_no_l4.cache_hint_l4 = 0u;
    RmR_QemuPlan_Autotune(&hw, RMR_GUEST_ARCH_X86_64, 4096u, 0u, &with_l4);
    RmR_QemuPlan_Autotune(&hw_no_l4, RMR_GUEST_ARCH_X86_64, 4096u, 0u, &without_l4);
    failed += expect(with_l4.preset == RMR_QEMU_PRESET_PERFORMANCE, "l4 keeps performance preset");
    failed += expect(with_l4.host_cores >= without_l4.host_cores, "l4 increases or preserves host cores");
  }

  RmR_QemuPlan_Autotune(&hw, RMR_GUEST_ARCH_PPC, 2048u, 0u, &plan);
  failed += expect(plan.preset == RMR_QEMU_PRESET_COMPATIBILITY, "ppc preset compatibility");
  failed += expect(plan.use_virtio == 0u, "ppc disables virtio");
  failed += expect(plan.use_iothread == 0u, "ppc disables iothread");
  failed += expect(RmR_QemuPlan_BuildArgs(&plan, out, sizeof(out)) == 0, "build args ppc");
  failed += expect(strstr(out, "-drive if=ide") != NULL, "ppc uses ide fallback");
  failed += expect(strstr(out, "-device rtl8139,netdev=n0") != NULL, "ppc uses rtl8139 fallback");
  failed += expect(strstr(out, "virtio-scsi-pci") == NULL, "ppc avoids virtio-scsi device");

  RmR_QmpTelemetry tele;
  failed += expect(RmR_QmpTelemetry_Parse("{\"return\":{\"status\":\"running\"}}", &tele) == 0, "parse status");
  failed += expect(tele.running == 1u, "running state");

  failed += expect(RmR_QmpTelemetry_Parse("{\"return\":[{\"cpu-index\":0,\"halted\":false},{\"cpu-index\":1,\"halted\":true}]}", &tele) == 0, "parse cpus");
  failed += expect(tele.vcpu_count == 2u, "vcpu count");
  failed += expect(tele.running_count == 1u, "running count");

  failed += expect(RmR_QmpTelemetry_Parse("{\"return\":[{\"cpu-index\":0,\"halted\" : false},{\"cpu-index\":1,\"halted\" : true}]}", &tele) == 0, "parse cpus spaced halted");
  failed += expect(tele.vcpu_count == 2u, "vcpu count spaced halted");
  failed += expect(tele.halted_count == 1u, "halted count spaced halted");
  failed += expect(tele.running_count == 1u, "running count spaced halted");

  failed += expect(RmR_QmpTelemetry_Parse("{\"return\":{\"cpus\":999999999999}}", &tele) == 0, "parse cpus overflow clamp");
  failed += expect(tele.vcpu_count == 0xFFFFFFFFu, "cpus overflow clamps to u32 max");

  if (failed != 0) {
    fprintf(stderr, "rmr_qemu_bridge_selftest FAILED (%d)\n", failed);
    return 1;
  }
  printf("rmr_qemu_bridge_selftest OK\n");
  return 0;
}
