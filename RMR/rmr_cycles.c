/* rmr_cycles.c - leitura de ciclos low-level (baremetal) */
#include "rmr_cycles.h"

u64 RmR_ReadCycles(void){
#if defined(__x86_64__) || defined(__i386__)
  unsigned int lo = 0;
  unsigned int hi = 0;
  __asm__ __volatile__("rdtsc" : "=a"(lo), "=d"(hi));
  return ((u64)hi << 32) | (u64)lo;
#elif defined(__aarch64__)
  u64 v = 0;
  __asm__ __volatile__("mrs %0, cntvct_el0" : "=r"(v));
  return v;
#elif defined(__riscv)
  u64 v = 0;
  __asm__ __volatile__("rdcycle %0" : "=r"(v));
  return v;
#else
  return 0u;
#endif
}
