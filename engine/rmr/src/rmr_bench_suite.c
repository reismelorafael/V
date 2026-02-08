/* rmr_bench_suite.c - suite industrial (50 testes) */
#include "rmr_bench_suite.h"
#include "rmr_cycles.h"

typedef struct {
  u8 kind;
  u32 p0;
  u32 p1;
} RmR_Bench_Def;

static u32 RmR_Lcg(u32 *s){
  u32 x = *s;
  x = (x * 1664525u) + 1013904223u;
  *s = x;
  return x;
}

static u32 RmR_Score(u64 cycles, u32 ops, u32 checksum){
  if(cycles == 0u) return checksum ^ ops;
  return (u32)((((u64)ops) << 8) / cycles) ^ checksum;
}

static u32 RmR_Bench_Alu(u32 iters){
  u32 s = 0xA5A5A5A5u;
  for(u32 i=0;i<iters;i++){
    s ^= (s << 5);
    s += (s >> 3);
    s *= 3u;
    s ^= (s << 1);
  }
  return s;
}

static u32 RmR_Bench_Branch(u32 iters){
  u32 s = 0x3C3C3C3Cu;
  u32 h = 0u;
  for(u32 i=0;i<iters;i++){
    if(s & 1u){ h++; s ^= 0x9E3779B9u; }
    else { s += 0x7F4A7C15u; }
    s = (s << 1) | (s >> 31);
  }
  return h ^ s;
}

static u32 RmR_Bench_Bitops(u32 iters){
  u32 s = 0xF00DBAAAu;
  for(u32 i=0;i<iters;i++){
    s ^= (s >> 7);
    s ^= (s << 9);
    s = (s << 13) | (s >> 19);
  }
  return s;
}

static u32 RmR_Bench_Mem(u32 len, u32 stride){
  static u32 buf[4096];
  u32 s = 0xC0DEC0DEu;
  u32 n = (len > 4096u) ? 4096u : len;
  for(u32 i=0;i<n;i++) buf[i] = RmR_Lcg(&s);
  u32 acc = 0u;
  for(u32 i=0;i<n;i+= (stride ? stride : 1u)){
    acc ^= buf[i] + i;
  }
  return acc;
}

static u32 RmR_Bench_Matrix(u32 n){
  u32 a[64];
  u32 b[64];
  u32 c[64];
  u32 s = 0x9E3779B9u;
  u32 nn = (n > 8u) ? 8u : n;
  for(u32 i=0;i<nn*nn;i++){ a[i]=RmR_Lcg(&s); b[i]=RmR_Lcg(&s); c[i]=0u; }
  u32 chk = 0u;
  for(u32 i=0;i<nn;i++){
    for(u32 j=0;j<nn;j++){
      u32 sum = 0u;
      for(u32 k=0;k<nn;k++) sum += a[i*nn+k] * b[k*nn+j];
      c[i*nn+j] = sum;
      chk ^= (sum + i + j);
    }
  }
  return chk;
}

static void RmR_RunOne(const RmR_Bench_Def *d, RmR_Bench_Metric *m){
  u64 start = RmR_ReadCycles();
  u32 checksum = 0u;
  u32 ops = d->p0;
  if(d->kind == 0u) checksum = RmR_Bench_Alu(d->p0);
  else if(d->kind == 1u) checksum = RmR_Bench_Branch(d->p0);
  else if(d->kind == 2u) checksum = RmR_Bench_Bitops(d->p0);
  else if(d->kind == 3u) checksum = RmR_Bench_Mem(d->p0, d->p1);
  else if(d->kind == 4u) checksum = RmR_Bench_Matrix(d->p0);
  u64 end = RmR_ReadCycles();
  u64 cycles = (end > start) ? (end - start) : 0u;
  u32 score = RmR_Score(cycles, ops, checksum);
  m->score = score;
  m->variance = (score >> 3) ^ (checksum & 0xFFu);
  m->error_margin = (score >> 5) + (m->variance & 0x3Fu);
}

void RmR_BenchSuite_Run(const RmR_Bench_Config *cfg, RmR_Bench_SuiteResult *out){
  if(!out) return;
  u32 stride = cfg ? cfg->stride_bytes : 1u;
  u32 iters = cfg ? cfg->max_iters : 1024u;
  u32 msize = cfg ? cfg->matrix_n : 4u;
  static const RmR_Bench_Def defs[RMR_BENCH_COUNT] = {
    {0,256,0},{0,512,0},{0,1024,0},{0,2048,0},{0,4096,0},
    {1,256,0},{1,512,0},{1,1024,0},{1,2048,0},{1,4096,0},
    {2,256,0},{2,512,0},{2,1024,0},{2,2048,0},{2,4096,0},
    {3,128,1},{3,256,1},{3,512,1},{3,1024,1},{3,2048,1},
    {3,128,2},{3,256,2},{3,512,2},{3,1024,2},{3,2048,2},
    {3,128,4},{3,256,4},{3,512,4},{3,1024,4},{3,2048,4},
    {4,2,0},{4,3,0},{4,4,0},{4,5,0},{4,6,0},
    {0,512,0},{1,512,0},{2,512,0},{3,512,2},{4,4,0},
    {0,1024,0},{1,1024,0},{2,1024,0},{3,1024,4},{4,5,0},
    {0,2048,0},{1,2048,0},{2,2048,0},{3,2048,8},{4,6,0}
  };
  out->total_score = 0u;
  out->total_error = 0u;
  for(u32 i=0;i<RMR_BENCH_COUNT;i++){
    RmR_Bench_Def d = defs[i];
    if(d.kind == 0u || d.kind == 1u || d.kind == 2u) d.p0 = (d.p0 * iters) >> 10;
    if(d.kind == 3u) d.p1 = (stride ? stride : 1u);
    if(d.kind == 4u) d.p0 = msize;
    RmR_RunOne(&d, &out->metric[i]);
    out->total_score += out->metric[i].score;
    out->total_error += out->metric[i].error_margin;
  }
}
