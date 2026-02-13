#include "rmr_qemu_bridge.h"

#include <stdio.h>

int main(void) {
  RmR_HW_Info hw;
  RmR_QemuPlan plan;
  RmR_QmpTelemetry tele;
  char args[1024];

  RmR_HW_Detect(&hw);
  RmR_QemuPlan_Autotune(&hw, RMR_GUEST_ARCH_ARM64, 4096u, 0u, &plan);
  if (RmR_QemuPlan_BuildArgs(&plan, args, sizeof(args)) != 0) {
    fprintf(stderr, "failed to build qemu args\n");
    return 1;
  }

  printf("preset=%u vm_cpus=%u vm_mem=%u\n", (unsigned int)plan.preset,
         (unsigned int)plan.vm_cpus, (unsigned int)plan.vm_mem_mib);
  printf("args=%s\n", args);

  const char *status = "{\"return\":{\"status\":\"running\"}}";
  const char *cpus = "{\"return\":[{\"cpu-index\":0,\"halted\":false},{\"cpu-index\":1,\"halted\":true}]}";

  RmR_QmpTelemetry_Parse(status, &tele);
  printf("running=%u\n", (unsigned int)tele.running);

  RmR_QmpTelemetry_Parse(cpus, &tele);
  printf("vcpu=%u running_vcpu=%u halted=%u\n", (unsigned int)tele.vcpu_count,
         (unsigned int)tele.running_count, (unsigned int)tele.halted_count);
  return 0;
}
