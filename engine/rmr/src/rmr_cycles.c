/* rmr_cycles.c - leitura de ciclos low-level (baremetal) */
#include "rmr_cycles.h"

#if defined(_MSC_VER)
#include <intrin.h>
#endif

u64 RmR_ReadCycles(void){
#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
  return (u64)__rdtsc();
#elif defined(__x86_64__) || defined(__i386__)
  unsigned int lo = 0;
  unsigned int hi = 0;
  __asm__ __volatile__("lfence\nrdtsc" : "=a"(lo), "=d"(hi) :: "memory");
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
