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

  RmR_QemuPlan_Autotune(&hw, RMR_GUEST_ARCH_X86_64, 8192u, 0u, &plan);
  failed += expect(plan.preset == RMR_QEMU_PRESET_PERFORMANCE, "preset performance");
  failed += expect(plan.vm_cpus >= 2u, "vm cpus >=2");

  char out[512];
  failed += expect(RmR_QemuPlan_BuildArgs(&plan, out, sizeof(out)) == 0, "build args");
  failed += expect(strstr(out, "-smp") != NULL, "args has smp");
  failed += expect(strstr(out, "-accel kvm") != NULL, "args has kvm");

  RmR_QmpTelemetry tele;
  failed += expect(RmR_QmpTelemetry_Parse("{\"return\":{\"status\":\"running\"}}", &tele) == 0, "parse status");
  failed += expect(tele.running == 1u, "running state");

  failed += expect(RmR_QmpTelemetry_Parse("{\"return\":[{\"cpu-index\":0,\"halted\":false},{\"cpu-index\":1,\"halted\":true}]}", &tele) == 0, "parse cpus");
  failed += expect(tele.vcpu_count == 2u, "vcpu count");
  failed += expect(tele.running_count == 1u, "running count");

  if (failed != 0) {
    fprintf(stderr, "rmr_qemu_bridge_selftest FAILED (%d)\n", failed);
    return 1;
  }
  printf("rmr_qemu_bridge_selftest OK\n");
  return 0;
}
