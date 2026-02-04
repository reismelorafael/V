/* rmr_bench.c - microbenchmarks determinísticos low-level */
#include "rmr_bench.h"
#include "rmr_cycles.h"

#ifndef RMR_BENCH_MAX
#define RMR_BENCH_MAX 16u
#endif

static u32 RmR_Lcg(u32 *s){
  u32 x = *s;
  x = (x * 1664525u) + 1013904223u;
  *s = x;
  return x;
}

static u32 RmR_Score(u32 ops, u64 cycles, u8 shift, u32 checksum){
  if(cycles > 0u){
    u64 score = (((u64)ops) << shift) / cycles;
    return (u32)(score ^ (u64)checksum);
  }
  return (checksum ^ (ops << shift));
}

static u32 RmR_Bench_Alu(u8 n, u8 shift){
  u32 seed = 0xA5A5A5A5u ^ (u32)n;
  u64 start = RmR_ReadCycles();
  for(u32 i=0;i< (u32)n * 1024u;i++){
    seed ^= (seed << 5);
    seed += (seed >> 3);
    seed *= 3u;
    seed ^= (seed << 1);
  }
  u64 end = RmR_ReadCycles();
  return RmR_Score((u32)n * 1024u * 4u, (end > start) ? (end - start) : 0u, shift, seed);
}

static u32 RmR_Bench_Branch(u8 n, u8 shift){
  u32 seed = 0x3C3C3C3Cu ^ (u32)n;
  u32 hits = 0;
  u64 start = RmR_ReadCycles();
  for(u32 i=0;i< (u32)n * 2048u;i++){
    if(seed & 1u){
      hits++;
      seed ^= 0x9E3779B9u;
    } else {
      seed += 0x7F4A7C15u;
    }
    seed = (seed << 1) | (seed >> 31);
  }
  u64 end = RmR_ReadCycles();
  return RmR_Score((u32)n * 2048u, (end > start) ? (end - start) : 0u, shift, hits);
}

static u32 RmR_Bench_Mem(u8 n, u8 shift){
  static u32 mem[RMR_BENCH_MAX * RMR_BENCH_MAX * 8u];
  u32 seed = 0xC0DEC0DEu ^ (u32)n;
  u32 len = (u32)n * (u32)n * 8u;
  for(u32 i=0;i<len;i++){
    mem[i] = RmR_Lcg(&seed);
  }
  u32 acc = 0;
  u64 start = RmR_ReadCycles();
  for(u32 i=0;i<len;i+= (u32)n){
    acc ^= mem[i] + (u32)i;
  }
  u64 end = RmR_ReadCycles();
  return RmR_Score(len, (end > start) ? (end - start) : 0u, shift, acc);
}

static u32 RmR_Bench_Matrix(u8 n, u8 shift){
  u32 a[RMR_BENCH_MAX * RMR_BENCH_MAX];
  u32 b[RMR_BENCH_MAX * RMR_BENCH_MAX];
  u32 c[RMR_BENCH_MAX * RMR_BENCH_MAX];
  u32 seed = 0x9E3779B9u ^ (u32)n;
  for(u8 i=0;i<n;i++){
    for(u8 j=0;j<n;j++){
      a[(u32)i * (u32)n + (u32)j] = RmR_Lcg(&seed);
      b[(u32)i * (u32)n + (u32)j] = RmR_Lcg(&seed);
      c[(u32)i * (u32)n + (u32)j] = 0u;
    }
  }
  u32 checksum = 0;
  u64 start = RmR_ReadCycles();
  for(u8 i=0;i<n;i++){
    for(u8 j=0;j<n;j++){
      u32 sum = 0u;
      for(u8 k=0;k<n;k++){
        sum += a[(u32)i * (u32)n + (u32)k] * b[(u32)k * (u32)n + (u32)j];
      }
      c[(u32)i * (u32)n + (u32)j] = sum;
      checksum ^= (sum + (u32)i + (u32)j);
    }
  }
  u64 end = RmR_ReadCycles();
  return RmR_Score((u32)n * (u32)n * (u32)n, (end > start) ? (end - start) : 0u, shift, checksum);
}

void RmR_Bench_Run(u8 size, u8 shift, RmR_Bench_Result *out){
  if(!out) return;
  u8 n = (size < 4u) ? 4u : size;
  if(n > (u8)RMR_BENCH_MAX) n = (u8)RMR_BENCH_MAX;
  out->alu = RmR_Bench_Alu(n, shift);
  out->mem = RmR_Bench_Mem(n, shift);
  out->branch = RmR_Bench_Branch(n, shift);
  out->matrix = RmR_Bench_Matrix(n, shift);
}
