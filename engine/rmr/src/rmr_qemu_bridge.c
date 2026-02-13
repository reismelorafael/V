#include "rmr_qemu_bridge.h"

#include <stdio.h>
#include <string.h>

static uint32_t clamp_u32(uint32_t v, uint32_t lo, uint32_t hi) {
  if (v < lo) return lo;
  if (v > hi) return hi;
  return v;
}

void RmR_QemuPlan_Default(RmR_QemuPlan *plan) {
  if (!plan) return;
  memset(plan, 0, sizeof(*plan));
  plan->preset = RMR_QEMU_PRESET_BALANCED;
  plan->arch = RMR_GUEST_ARCH_X86_64;
  plan->host_cores = 2;
  plan->vm_cpus = 2;
  plan->vm_mem_mib = 2048;
  plan->use_virtio = 1;
}

void RmR_QemuPlan_Autotune(const RmR_HW_Info *hw,
                           RmR_GuestArch arch,
                           uint32_t mem_mib,
                           uint8_t low_latency,
                           RmR_QemuPlan *plan) {
  if (!plan) return;
  RmR_QemuPlan_Default(plan);
  plan->arch = arch;
  plan->low_latency = low_latency ? 1u : 0u;
  plan->vm_mem_mib = clamp_u32(mem_mib ? mem_mib : 2048u, 512u, 32768u);

  uint32_t host_cores = 2u;
  if (hw) {
    host_cores = clamp_u32(hw->word_bits >= 64u ? 8u : 4u, 2u, 16u);
    if (hw->arch == 4u || hw->arch == 2u) host_cores = clamp_u32(host_cores + 2u, 2u, 16u);
    plan->use_kvm = (hw->arch == 2u || hw->arch == 4u) ? 1u : 0u;
    if (hw->cache_hint_l2 >= (512u * 1024u)) {
      plan->preset = RMR_QEMU_PRESET_PERFORMANCE;
    }
    if (hw->ptr_bits < 64u) {
      plan->preset = RMR_QEMU_PRESET_COMPATIBILITY;
      plan->use_kvm = 0u;
    }
  }

  if (low_latency) {
    plan->preset = RMR_QEMU_PRESET_COMPATIBILITY;
  }

  if (arch == RMR_GUEST_ARCH_PPC) {
    plan->use_virtio = 0u;
    plan->preset = RMR_QEMU_PRESET_COMPATIBILITY;
  }

  plan->host_cores = host_cores;
  switch (plan->preset) {
    case RMR_QEMU_PRESET_PERFORMANCE:
      plan->vm_cpus = clamp_u32(host_cores - 1u, 2u, 12u);
      plan->use_iothread = 1u;
      plan->use_direct_io = 1u;
      plan->use_multifd = 1u;
      break;
    case RMR_QEMU_PRESET_COMPATIBILITY:
      plan->vm_cpus = 2u;
      plan->use_iothread = 0u;
      plan->use_direct_io = 0u;
      plan->use_multifd = 0u;
      break;
    case RMR_QEMU_PRESET_BALANCED:
    default:
      plan->vm_cpus = clamp_u32(host_cores / 2u, 2u, 8u);
      plan->use_iothread = 1u;
      plan->use_direct_io = 0u;
      plan->use_multifd = 0u;
      break;
  }
}

int RmR_QemuPlan_BuildArgs(const RmR_QemuPlan *plan, char *out, size_t out_len) {
  if (!plan || !out || out_len == 0u) return -1;

  const char *machine = "pc";
  if (plan->arch == RMR_GUEST_ARCH_ARM64) machine = "virt";

  const char *cache_mode = plan->use_direct_io ? "none" : "writeback";
  const char *aio_mode = plan->use_direct_io ? "io_uring" : "threads";

  int n = snprintf(out, out_len,
                   "-M %s -smp cpus=%u -m %u "
                   "-drive if=virtio,cache=%s,aio=%s "
                   "-netdev user,id=n0 -device virtio-net-pci,netdev=n0",
                   machine,
                   (unsigned int)plan->vm_cpus,
                   (unsigned int)plan->vm_mem_mib,
                   cache_mode,
                   aio_mode);
  if (n < 0 || (size_t)n >= out_len) return -2;

  size_t used = (size_t)n;
  if (plan->use_iothread) {
    n = snprintf(out + used, out_len - used, " -object iothread,id=ioth0 -device virtio-scsi-pci,id=scsi0,iothread=ioth0");
    if (n < 0 || (size_t)n >= (out_len - used)) return -2;
    used += (size_t)n;
  }
  if (plan->use_kvm) {
    n = snprintf(out + used, out_len - used, " -accel kvm");
    if (n < 0 || (size_t)n >= (out_len - used)) return -2;
    used += (size_t)n;
  }
  if (plan->low_latency) {
    n = snprintf(out + used, out_len - used, " -icount shift=0,align=off,sleep=off");
    if (n < 0 || (size_t)n >= (out_len - used)) return -2;
    used += (size_t)n;
  }
  if (plan->use_multifd) {
    n = snprintf(out + used, out_len - used, " -incoming defer");
    if (n < 0 || (size_t)n >= (out_len - used)) return -2;
  }
  return 0;
}

static int parse_u32_after_key(const char *s, const char *key, uint32_t *out) {
  const char *p = strstr(s, key);
  if (!p) return -1;
  p += strlen(key);
  while (*p == ' ' || *p == ':' || *p == '"') p++;
  uint32_t v = 0u;
  int found = 0;
  while (*p >= '0' && *p <= '9') {
    v = (v * 10u) + (uint32_t)(*p - '0');
    p++;
    found = 1;
  }
  if (!found) return -1;
  *out = v;
  return 0;
}

int RmR_QmpTelemetry_Parse(const char *qmp_json_line, RmR_QmpTelemetry *out) {
  if (!qmp_json_line || !out) return -1;
  memset(out, 0, sizeof(*out));

  if (strstr(qmp_json_line, "\"status\":\"running\"")) {
    out->running = 1u;
  }

  if (strstr(qmp_json_line, "query-cpus-fast") || strstr(qmp_json_line, "cpu-index")) {
    const char *p = qmp_json_line;
    while ((p = strstr(p, "\"cpu-index\"")) != NULL) {
      out->vcpu_count++;
      p += 11;
    }
    p = qmp_json_line;
    while ((p = strstr(p, "\"halted\":true")) != NULL) {
      out->halted_count++;
      p += 13;
    }
    if (out->vcpu_count >= out->halted_count) {
      out->running_count = out->vcpu_count - out->halted_count;
    }
  } else {
    parse_u32_after_key(qmp_json_line, "\"cpus\"", &out->vcpu_count);
  }

  return 0;
}
