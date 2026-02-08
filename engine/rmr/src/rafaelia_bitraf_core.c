/* rafaelia_bitraf_core.c
   Núcleo C: BITRAF (D/I/P/R) + slot10 + base20 + dual parity + atrator 42
   - Sem libc (freestanding-friendly)
   - Append-only: não reescreve payload, só adiciona "pontos"
   - Top-42: mantém 42 melhores pontos (plasticidade adaptativa)
*/

typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef signed int         s32;

#ifndef NULL
#define NULL ((void*)0)
#endif

/* ==== Bench/Cycles externos (opcionais, low-level) ==== */
typedef struct {
  u32 alu;
  u32 mem;
  u32 branch;
  u32 matrix;
} RmR_Bench_Result;

u64 RmR_ReadCycles(void);
void RmR_Bench_Run(u8 size, u8 shift, RmR_Bench_Result *out);
typedef struct {
  u32 score;
  u32 variance;
  u32 error_margin;
} RmR_Bench_Metric;
typedef struct {
  RmR_Bench_Metric metric[50];
  u32 total_score;
  u32 total_error;
} RmR_Bench_SuiteResult;
typedef struct {
  u32 budget_cycles;
  u32 max_iters;
  u32 stride_bytes;
  u32 matrix_n;
} RmR_Bench_Config;
void RmR_BenchSuite_Run(const RmR_Bench_Config *cfg, RmR_Bench_SuiteResult *out);

/* ==== Backend mínimo (pluga em stdout/UART/MMIO) ==== */
struct RMR_API {
  u32 (*write)(void *ctx, const u8 *buf, u32 len);
  void (*panic)(void *ctx, const char *msg);
  u64 (*read_cycles)(void *ctx);
  void *ctx;
};

static struct RMR_API g_api;

static void rmr_bind_api(const struct RMR_API *api){
  if(api) g_api = *api;
}

static void rmr_panic(const char *msg){
  if(g_api.panic) g_api.panic(g_api.ctx, msg);
  for(;;) { /* loop */ }
}

static void rmr_write_bytes(const u8 *buf, u32 len){
  if(g_api.write) (void)g_api.write(g_api.ctx, buf, len);
}

static u64 rmr_read_cycles(void){
  if(g_api.read_cycles) return g_api.read_cycles(g_api.ctx);
  return RmR_ReadCycles();
}

/* ==== util sem libc ==== */
static void rmr_memset(void *dst, u8 v, u32 n){
  u8 *d=(u8*)dst;
  while(n--) *d++=v;
}
static void rmr_memcpy(void *dst, const void *src, u32 n){
  u8 *d=(u8*)dst; const u8*s=(const u8*)src;
  while(n--) *d++=*s++;
}

static u16 rmr_u16_clamp(u16 v, u16 lo, u16 hi){
  if(v < lo) return lo;
  if(v > hi) return hi;
  return v;
}

static u8 rmr_u8_clamp(u8 v, u8 lo, u8 hi){
  if(v < lo) return lo;
  if(v > hi) return hi;
  return v;
}

/* ==== CPU/arquitetura (compile-time) ==== */
typedef enum {
  RMR_ARCH_UNKNOWN = 0,
  RMR_ARCH_X86     = 1,
  RMR_ARCH_X64     = 2,
  RMR_ARCH_ARM32   = 3,
  RMR_ARCH_ARM64   = 4,
  RMR_ARCH_RISCV   = 5,
  RMR_ARCH_MIPS    = 6
} rmr_arch_t;

static rmr_arch_t rmr_detect_arch(void){
#if defined(__x86_64__) || defined(_M_X64)
  return RMR_ARCH_X64;
#elif defined(__i386__) || defined(_M_IX86)
  return RMR_ARCH_X86;
#elif defined(__aarch64__)
  return RMR_ARCH_ARM64;
#elif defined(__arm__) || defined(_M_ARM)
  return RMR_ARCH_ARM32;
#elif defined(__riscv)
  return RMR_ARCH_RISCV;
#elif defined(__mips__)
  return RMR_ARCH_MIPS;
#else
  return RMR_ARCH_UNKNOWN;
#endif
}

static u8 rmr_is_little_endian(void){
  u16 v = 0x0102u;
  return (*((u8*)&v) == 0x02u) ? 1u : 0u;
}

typedef struct {
  rmr_arch_t arch;
  u8 is_little_endian;
  u8 word_bits;
  u8 ptr_bits;
} rmr_hw_caps_t;

static void rmr_hw_caps_detect(rmr_hw_caps_t *caps){
  if(!caps) return;
  caps->arch = rmr_detect_arch();
  caps->is_little_endian = rmr_is_little_endian();
  caps->word_bits = (u8)(sizeof(unsigned long) * 8u);
  caps->ptr_bits = (u8)(sizeof(void*) * 8u);
}

/* ==== Util numérico sem libc ==== */
static u32 rmr_isqrt_u32(u32 x){
  u32 res = 0;
  u32 bit = 1u << 30;
  while(bit > x) bit >>= 2;
  while(bit != 0){
    u32 tmp = res + bit;
    if(x >= tmp){
      x -= tmp;
      res = (tmp + bit);
    }
    res >>= 1;
    bit >>= 2;
  }
  return res;
}

/* ==== Imagem: análise low-level em 23 pontos ==== */
typedef struct {
  u32 width;
  u32 height;
  u32 stride;   /* bytes por linha */
  u8  channels; /* 1..4 */
  u8  bpp;      /* bits por pixel (total) */
  u8  format;   /* 0=grayscale,1=rgb,2=rgba,3=bgr,4=bgra */
} rmr_image_info_t;

typedef struct {
  u32 x;
  u32 y;
  u32 value;      /* intensidade 0..255 (média de canais) */
  u8  sector8;    /* setor polar 0..7 */
  u32 vec_len;    /* comprimento do vetor (fixo) */
} rmr_point23_t;

static u32 rmr_image_bytes_per_pixel(u8 bpp){
  u32 bytes = (u32)((bpp + 7u) >> 3);
  return (bytes == 0u) ? 1u : bytes;
}

static u32 rmr_image_pixel_offset(const rmr_image_info_t *info, u32 x, u32 y){
  u32 bytes = rmr_image_bytes_per_pixel(info->bpp);
  return (y * info->stride) + (x * bytes);
}

static u32 rmr_image_sample_value(
  const rmr_image_info_t *info,
  const u8 *buf,
  u32 buf_len,
  u32 x,
  u32 y
){
  if(!info || !buf) return 0;
  if(x >= info->width || y >= info->height) return 0;
  u32 bytes = rmr_image_bytes_per_pixel(info->bpp);
  u32 offset = rmr_image_pixel_offset(info, x, y);
  if(offset + bytes > buf_len) return 0;

  if(info->channels <= 1u){
    return (u32)buf[offset];
  }

  u32 sum = 0;
  u32 channels = (info->channels > bytes) ? bytes : info->channels;
  for(u32 i=0;i<channels;i++){
    sum += (u32)buf[offset + i];
  }
  return (channels == 0u) ? 0u : (sum / (u32)channels);
}

static u8 rmr_sector8(u32 cx, u32 cy, u32 x, u32 y){
  s32 dx = (s32)x - (s32)cx;
  s32 dy = (s32)y - (s32)cy;
  u8 sx = (dx >= 0) ? 1u : 0u;
  u8 sy = (dy >= 0) ? 1u : 0u;
  u32 adx = (dx >= 0) ? (u32)dx : (u32)(-dx);
  u32 ady = (dy >= 0) ? (u32)dy : (u32)(-dy);
  if(adx >= (ady << 1)) return (u8)(sy ? 0u : 4u); /* leste/oeste */
  if(ady >= (adx << 1)) return (u8)(sx ? 2u : 6u); /* norte/sul */
  return (u8)((sy << 2) | (sx << 1) | 1u);         /* diagonais */
}

static void rmr_image_points23(
  const rmr_image_info_t *info,
  const u8 *buf,
  u32 buf_len,
  rmr_point23_t out_pts[23]
){
  if(!info || !out_pts) return;
  static const u8 nx[23] = {2,5,8,1,3,5,7,9,2,4,6,8,1,3,5,7,9,2,4,6,8,5,5};
  static const u8 ny[23] = {2,2,2,4,4,4,4,4,6,6,6,6,8,8,8,8,8,9,9,9,9,5,1};
  static const u8 den = 10;
  u32 cx = info->width >> 1;
  u32 cy = info->height >> 1;

  for(u32 i=0;i<23;i++){
    u32 x = (info->width  * (u32)nx[i]) / (u32)den;
    u32 y = (info->height * (u32)ny[i]) / (u32)den;
    u32 val = rmr_image_sample_value(info, buf, buf_len, x, y);
    u32 dx = (x >= cx) ? (x - cx) : (cx - x);
    u32 dy = (y >= cy) ? (y - cy) : (cy - y);
    u32 vec_len = rmr_isqrt_u32((dx * dx) + (dy * dy));

    out_pts[i].x = x;
    out_pts[i].y = y;
    out_pts[i].value = val;
    out_pts[i].sector8 = rmr_sector8(cx, cy, x, y);
    out_pts[i].vec_len = vec_len;
  }
}

/* ==== BITRAF: estados ==== */
typedef enum {
  RAF_D = 0, /* Data */
  RAF_I = 1, /* Informação */
  RAF_P = 2, /* Processo */
  RAF_R = 3  /* Retorno/Resultado */
} raf_state_t;

/* ==== “Pontos” (append-only) ==== */
typedef struct {
  u32 idx;        /* índice do ponto */
  u8  state;      /* RAF_D/I/P/R */
  u8  slot10;     /* 0..9 */
  u8  sym20;      /* 0..19 (base20) */
  u8  p0;         /* parity 0 */
  u8  p1;         /* parity 1 */
  u16 noise;      /* ruído absorvido (contagem/assinatura simples) */
  u16 crc16;      /* checksum leve (opcional) */
  u32 aux;        /* campo livre (ex: endereço lógico, flags, etc.) */
} raf_point_t;

/* ==== Top-42 “melhores pontos” ==== */
#ifndef RAF_BEST_MAX
#define RAF_BEST_MAX 42u
#endif

/* ==== Configuração determinística/low-level ==== */
typedef struct {
  u16 score_noise_weight;   /* peso do ruído (0..255) */
  u16 score_parity_bonus;   /* bônus paridade */
  u16 score_slot_sym_bonus; /* bônus simetria slot/sym */
  u16 score_attractor_bonus;/* bônus atrator */
  u8 attractor_gate_dr;     /* digital-root alvo */
  u8 attractor_gate_mod;    /* módulo (slot+sym) */
  u8 attractor_gate_eq;     /* valor do gate */
  u8 noise_mix_shift;       /* shift de mistura do ruído */
  u32 seed_mul;             /* multiplicador determinístico */
  u32 seed_add;             /* soma determinística */
  u8 bench_shift;           /* escala do score de benchmark */
  u8 bench_size;            /* tamanho da matriz (<=16) */
} raf_config_t;

static void raf_config_init(raf_config_t *cfg){
  if(!cfg) return;
  cfg->score_noise_weight   = 255u;
  cfg->score_parity_bonus   = 64u;
  cfg->score_slot_sym_bonus = 32u;
  cfg->score_attractor_bonus= 128u;
  cfg->attractor_gate_dr    = 6u;
  cfg->attractor_gate_mod   = 10u;
  cfg->attractor_gate_eq    = 2u;
  cfg->noise_mix_shift      = 0u;
  cfg->seed_mul             = 7u;
  cfg->seed_add             = 14u;
  cfg->bench_shift          = 8u;
  cfg->bench_size           = 8u;
}

static void raf_config_autoadapt(raf_config_t *cfg, const rmr_hw_caps_t *caps){
  if(!cfg || !caps) return;
  switch(caps->arch){
    case RMR_ARCH_X86:
    case RMR_ARCH_X64:
      cfg->seed_mul = 7u;
      cfg->seed_add = 14u;
      cfg->bench_shift = 8u;
      cfg->bench_size = 8u;
      break;
    case RMR_ARCH_ARM32:
    case RMR_ARCH_ARM64:
      cfg->seed_mul = 9u;
      cfg->seed_add = 18u;
      cfg->bench_shift = 7u;
      cfg->bench_size = 8u;
      break;
    case RMR_ARCH_RISCV:
      cfg->seed_mul = 11u;
      cfg->seed_add = 22u;
      cfg->bench_shift = 6u;
      cfg->bench_size = 6u;
      break;
    default:
      cfg->seed_mul = 7u;
      cfg->seed_add = 14u;
      cfg->bench_shift = 8u;
      cfg->bench_size = 8u;
      break;
  }
}

typedef struct {
  u32 idx;
  u16 score;
} raf_best_t;

/* ==== Anel de pontos ==== */
#ifndef RAF_POINTS_MAX
#define RAF_POINTS_MAX 4096u
#endif

#ifndef RMR_BENCH_MAX
#define RMR_BENCH_MAX 16u
#endif

typedef struct {
  raf_point_t pts[RAF_POINTS_MAX];
  u32 head;      /* próximo a escrever */
  u32 count;     /* total escrito (pode saturar) */
  u32 attract;   /* contador de atrator 42 */
  u32 noise_acc; /* acumulador de ruído global */
  u32 bench_score;
  u32 bench_alu;
  u32 bench_mem;
  u32 bench_branch;
  u32 bench_matrix;
  u32 bench_suite_score;
  u32 bench_suite_error;
  u32 bench_count;
  raf_best_t best[RAF_BEST_MAX];
  u32 best_count;
} raf_store_t;

static void raf_autotune_config(raf_config_t *cfg, const raf_store_t *st){
  if(!cfg || !st) return;
  if(st->count < 64u) return;
  u32 noise_avg = st->noise_acc / st->count;
  if(noise_avg > 1024u){
    if(cfg->score_noise_weight > 64u){
      cfg->score_noise_weight = (u16)(cfg->score_noise_weight - 1u);
    }
  } else if(noise_avg < 256u){
    if(cfg->score_noise_weight < 255u){
      cfg->score_noise_weight = (u16)(cfg->score_noise_weight + 1u);
    }
  }
  if(st->attract < (st->count >> 6)){
    if(cfg->score_attractor_bonus < 192u){
      cfg->score_attractor_bonus = (u16)(cfg->score_attractor_bonus + 1u);
    }
  } else if(st->attract > (st->count >> 4)){
    if(cfg->score_attractor_bonus > 96u){
      cfg->score_attractor_bonus = (u16)(cfg->score_attractor_bonus - 1u);
    }
  }
  cfg->score_noise_weight = rmr_u16_clamp(cfg->score_noise_weight, 32u, 255u);
  cfg->score_attractor_bonus = rmr_u16_clamp(cfg->score_attractor_bonus, 64u, 255u);

  if(st->bench_count > 0u){
    u32 err = st->bench_suite_error;
    u32 err_gate = (err > 0x2000u) ? 1u : 0u;
    if(st->bench_score > 0x4000u){
      cfg->noise_mix_shift = 0u;
    } else {
      cfg->noise_mix_shift = 1u;
    }
    cfg->bench_size = rmr_u8_clamp((u8)((st->bench_score & 0x0Fu) + 4u), 4u, (u8)RMR_BENCH_MAX);
    if(st->bench_mem > st->bench_alu && err_gate == 0u){
      if(cfg->score_noise_weight > 96u){
        cfg->score_noise_weight = (u16)(cfg->score_noise_weight - 1u);
      }
    } else if(st->bench_alu > st->bench_mem && err_gate == 0u){
      if(cfg->score_noise_weight < 255u){
        cfg->score_noise_weight = (u16)(cfg->score_noise_weight + 1u);
      }
    }
  }
}

static u32 rmr_lcg_u32(u32 *state){
  u32 x = *state;
  x = (x * 1664525u) + 1013904223u;
  *state = x;
  return x;
}

static void rmr_matrix_fill(u32 *m, u8 n, u32 *seed){
  for(u8 i=0;i<n;i++){
    for(u8 j=0;j<n;j++){
      m[(u32)i * (u32)n + (u32)j] = rmr_lcg_u32(seed);
    }
  }
}

static u32 rmr_matrix_mul_accum(const u32 *a, const u32 *b, u32 *c, u8 n){
  u32 checksum = 0u;
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
  return checksum;
}

static u32 rmr_bench_matrix_score(u8 n, u8 bench_shift){
  u32 a[RMR_BENCH_MAX * RMR_BENCH_MAX];
  u32 b[RMR_BENCH_MAX * RMR_BENCH_MAX];
  u32 c[RMR_BENCH_MAX * RMR_BENCH_MAX];
  u32 seed = 0x9E3779B9u ^ (u32)n;
  u64 start = rmr_read_cycles();
  rmr_matrix_fill(a, n, &seed);
  rmr_matrix_fill(b, n, &seed);
  u32 checksum = rmr_matrix_mul_accum(a, b, c, n);
  u64 end = rmr_read_cycles();
  u64 cycles = (end > start) ? (end - start) : 0u;
  u32 ops = (u32)n * (u32)n * (u32)n;
  if(cycles > 0u){
    u64 score = (((u64)ops) << bench_shift) / cycles;
    return (u32)(score ^ (u64)checksum);
  }
  return checksum ^ (ops << bench_shift);
}

static void raf_bench_update(raf_store_t *st, raf_config_t *cfg){
  if(!st || !cfg) return;
  u8 size = rmr_u8_clamp(cfg->bench_size, 4u, (u8)RMR_BENCH_MAX);
  RmR_Bench_Result r;
  RmR_Bench_Run(size, cfg->bench_shift, &r);
  st->bench_alu = r.alu;
  st->bench_mem = r.mem;
  st->bench_branch = r.branch;
  st->bench_matrix = r.matrix;
  st->bench_score = (r.alu ^ r.mem ^ r.branch ^ r.matrix);
  {
    RmR_Bench_SuiteResult sr;
    RmR_Bench_Config sc;
    sc.budget_cycles = 0u;
    sc.max_iters = 1024u;
    sc.stride_bytes = 2u;
    sc.matrix_n = 4u;
    RmR_BenchSuite_Run(&sc, &sr);
    st->bench_suite_score = sr.total_score;
    st->bench_suite_error = sr.total_error;
  }
  st->bench_count++;
}

/* ==== CRC16 mínimo (polinômio simples) ==== */
static u16 raf_crc16(const u8 *data, u32 len){
  u16 crc = 0xA5A5u;
  for(u32 i=0;i<len;i++){
    crc ^= (u16)data[i] << 8;
    for(u8 b=0;b<8;b++){
      if(crc & 0x8000u) crc = (u16)((crc<<1) ^ 0x1021u);
      else             crc = (u16)(crc<<1);
    }
  }
  return crc;
}

/* ==== Paridade (dupla) ==== */
/* p0: XOR de bits pares; p1: XOR de bits ímpares (por posição) */
static void raf_dual_parity_u64(u64 x, u8 *p0, u8 *p1){
  u64 even = x & 0x5555555555555555ULL; /* bits 0,2,4.. */
  u64 odd  = x & 0xAAAAAAAAAAAAAAAAULL; /* bits 1,3,5.. */
  /* reduz XOR para 1 bit */
  even ^= even>>32; even ^= even>>16; even ^= even>>8; even ^= even>>4; even ^= even>>2; even ^= even>>1;
  odd  ^= odd >>32; odd  ^= odd >>16; odd  ^= odd >>8; odd  ^= odd >>4; odd  ^= odd >>2; odd  ^= odd >>1;
  *p0 = (u8)(even & 1u);
  *p1 = (u8)(odd  & 1u);
}

/* ==== “9→1” (redução digital / coerência) ==== */
static u8 raf_digital_root_9to1(u32 v){
  /* digital root (0..9), mapeia 9->1 e 0->0 */
  if(v==0) return 0;
  u32 r = v % 9u;
  return (r==0u)? 1u : (u8)r;
}

/* ==== Base20: normaliza 0..19 ==== */
static u8 raf_base20(u32 v){ return (u8)(v % 20u); }

/* ==== slot10: normaliza 0..9 ==== */
static u8 raf_slot10(u32 v){ return (u8)(v % 10u); }

/* ==== Atrator 42 (estado âncora) ==== */
static u8 raf_is_attractor42(const raf_point_t *p){
  /* “42” como fechamento: combinações coerentes e repetíveis.
     Aqui: digital-root(state+slot10+sym20+paridades+noise) == 6 e (slot10+sym20)%? = 2
     Você pode trocar a regra sem quebrar o store (é só função). */
  u32 mix = (u32)p->state + (u32)p->slot10 + (u32)p->sym20 + (u32)p->p0 + (u32)p->p1 + (u32)p->noise;
  u8 dr = raf_digital_root_9to1(mix);
  u8 gate = (u8)((p->slot10 + p->sym20) % 10u);
  return (dr == 6u && gate == 2u) ? 1u : 0u;
}

static u8 raf_is_attractor42_cfg(const raf_point_t *p, const raf_config_t *cfg){
  if(!p || !cfg) return 0u;
  u32 mix = (u32)p->state + (u32)p->slot10 + (u32)p->sym20 + (u32)p->p0 + (u32)p->p1 + (u32)p->noise;
  u8 dr = raf_digital_root_9to1(mix);
  u8 gate = (u8)((p->slot10 + p->sym20) % (u32)cfg->attractor_gate_mod);
  return (dr == cfg->attractor_gate_dr && gate == cfg->attractor_gate_eq) ? 1u : 0u;
}

/* ==== Score para Top-42 (plasticidade) ==== */
static u16 raf_point_score_cfg(const raf_point_t *p, const raf_config_t *cfg){
  /* Heurística determinística: privilegia coerência (paridades),
     atrator 42, baixo ruído e simetria de símbolos. */
  if(!p || !cfg) return 0;
  u16 score = 0;
  u16 noise_pen = (u16)(p->noise & 0x00FFu);
  if(cfg->score_noise_weight >= noise_pen){
    score += (u16)(cfg->score_noise_weight - noise_pen);
  }
  score += (p->p0 == p->p1) ? cfg->score_parity_bonus : 0u;
  score += (p->slot10 == (p->sym20 % 10u)) ? cfg->score_slot_sym_bonus : 0u;
  score += raf_is_attractor42_cfg(p, cfg) ? cfg->score_attractor_bonus : 0u;
  return score;
}

static void raf_update_best42(raf_store_t *st, const raf_point_t *p, const raf_config_t *cfg){
  if(!st || !p) return;
  u16 score = raf_point_score_cfg(p, cfg);
  u32 i = 0;

  if(st->best_count < RAF_BEST_MAX){
    st->best[st->best_count].idx = p->idx;
    st->best[st->best_count].score = score;
    st->best_count++;
  } else {
    u32 min_i = 0;
    u16 min_s = st->best[0].score;
    for(i=1;i<RAF_BEST_MAX;i++){
      if(st->best[i].score < min_s){
        min_s = st->best[i].score;
        min_i = i;
      }
    }
    if(score > min_s){
      st->best[min_i].idx = p->idx;
      st->best[min_i].score = score;
    } else {
      return;
    }
  }

  /* ordena por score decrescente (inserção simples) */
  for(i=1;i<st->best_count;i++){
    u32 j = i;
    raf_best_t tmp = st->best[i];
    while(j>0 && st->best[j-1].score < tmp.score){
      st->best[j] = st->best[j-1];
      j--;
    }
    st->best[j] = tmp;
  }
}

/* ==== Cria “ponto” (append-only) ==== */
static void raf_append_point(
  raf_store_t *st,
  raf_state_t state,
  u32 slot_seed,
  u32 sym_seed,
  u64 payload64,
  u16 noise_hint,
  u32 aux,
  const raf_config_t *cfg
){
  if(!st) rmr_panic("raf_store NULL");
  raf_config_t local_cfg;
  if(!cfg){
    raf_config_init(&local_cfg);
    cfg = &local_cfg;
  }
  raf_point_t *p = &st->pts[st->head];

  p->idx   = st->count;
  p->state = (u8)state;
  p->slot10= raf_slot10(slot_seed);
  p->sym20 = raf_base20(sym_seed);

  raf_dual_parity_u64(payload64, &p->p0, &p->p1);

  /* ruído absorvido: acumula + mistura leve (não é crypto; é telemetria determinística) */
  {
    u32 mix = (u32)noise_hint + (u32)(payload64 ^ (payload64>>32));
    u32 shift = cfg ? (u32)cfg->noise_mix_shift : 0u;
    st->noise_acc += (mix >> shift);
  }
  p->noise = (u16)(st->noise_acc & 0xFFFFu);

  p->aux = aux;

  /* checksum do ponto (sem depender de struct packing externo) */
  {
    u8 buf[24];
    /* serialização mínima fixa */
    buf[0]=(u8)(p->idx); buf[1]=(u8)(p->idx>>8); buf[2]=(u8)(p->idx>>16); buf[3]=(u8)(p->idx>>24);
    buf[4]=p->state; buf[5]=p->slot10; buf[6]=p->sym20; buf[7]=p->p0;
    buf[8]=p->p1; buf[9]=(u8)p->noise; buf[10]=(u8)(p->noise>>8);
    buf[11]=(u8)(p->aux); buf[12]=(u8)(p->aux>>8); buf[13]=(u8)(p->aux>>16); buf[14]=(u8)(p->aux>>24);
    /* payload64 entra no CRC só como “sombra” */
    buf[15]=(u8)(payload64); buf[16]=(u8)(payload64>>8); buf[17]=(u8)(payload64>>16); buf[18]=(u8)(payload64>>24);
    buf[19]=(u8)(payload64>>32); buf[20]=(u8)(payload64>>40); buf[21]=(u8)(payload64>>48); buf[22]=(u8)(payload64>>56);
    buf[23]=(u8)(noise_hint);

    p->crc16 = raf_crc16(buf, 24);
  }

  if(raf_is_attractor42_cfg(p, cfg)) st->attract++;
  raf_update_best42(st, p, cfg);

  /* avança ring */
  st->head = (st->head + 1u) % RAF_POINTS_MAX;
  st->count++;
}

/* ==== Motor BITRAF: 4 ciclos (input/process/output/semântica) ==== */
typedef struct {
  raf_state_t s;
  u32 t;      /* “tempo”/tick lógico (não clock físico) */
  u32 phase;  /* 0..3 */
  u32 slot_seed;
  u32 sym_seed;
  u32 aux;
} raf_machine_t;

static void raf_machine_init(raf_machine_t *m){
  if(!m) rmr_panic("raf_machine NULL");
  m->s = RAF_D;
  m->t = 0;
  m->phase = 0;
  m->slot_seed = 0;
  m->sym_seed  = 0;
  m->aux = 0;
}

/* transição determinística (simetria assimétrica): alterna sentido por paridade */
static raf_state_t raf_next_state(raf_state_t cur, u8 p0, u8 p1){
  /* se p0^p1=0: avança; senão: retorna (reverso) */
  u8 dir = (u8)(p0 ^ p1);
  if(dir==0){
    /* D->I->P->R->D */
    return (raf_state_t)((cur + 1) & 3);
  } else {
    /* D<-I<-P<-R<-D */
    return (raf_state_t)((cur + 3) & 3);
  }
}

static void raf_step(raf_store_t *st, raf_machine_t *m, u64 payload64, u16 noise_hint){
  if(!st || !m) rmr_panic("raf_step NULL");

  /* fase = 4 ciclos absorvente (input/process/output/semântica) */
  switch(m->phase & 3u){
    case 0: /* INPUT: coleta/endereça */
      m->slot_seed = m->t;
      m->sym_seed  = (m->t * 7u) + 14u; /* 7+14 como “painel reverso” */
      m->aux = (m->t << 16) ^ (u32)payload64;
      break;

    case 1: /* PROCESSO: mistura determinística */
      m->slot_seed ^= (u32)(payload64) ^ (u32)noise_hint;
      m->sym_seed  ^= (u32)(payload64>>32) + (u32)(m->s * 42u);
      break;

    case 2: /* SAÍDA: grava ponto (append-only) */
      raf_append_point(st, m->s, m->slot_seed, m->sym_seed, payload64, noise_hint, m->aux, NULL);
      break;

    case 3: /* SEMÂNTICA: muda estado pelo “barramento” (paridades) */
    {
      u8 p0,p1;
      raf_dual_parity_u64(payload64, &p0, &p1);
      m->s = raf_next_state(m->s, p0, p1);
      break;
    }
  }

  m->phase++;
  if((m->phase & 3u)==0u) m->t++; /* só avança “tempo” ao fechar 4 ciclos */
}

static void raf_step_cfg(
  raf_store_t *st,
  raf_machine_t *m,
  u64 payload64,
  u16 noise_hint,
  raf_config_t *cfg
){
  if(!st || !m || !cfg) rmr_panic("raf_step_cfg NULL");

  switch(m->phase & 3u){
    case 0:
      m->slot_seed = m->t;
      m->sym_seed  = (m->t * cfg->seed_mul) + cfg->seed_add;
      m->aux = (m->t << 16) ^ (u32)payload64;
      break;
    case 1:
      m->slot_seed ^= (u32)(payload64) ^ (u32)noise_hint;
      m->sym_seed  ^= (u32)(payload64>>32) + (u32)(m->s * 42u);
      break;
    case 2:
      raf_append_point(st, m->s, m->slot_seed, m->sym_seed, payload64, noise_hint, m->aux, cfg);
      break;
    case 3:
    {
      u8 p0,p1;
      raf_dual_parity_u64(payload64, &p0, &p1);
      m->s = raf_next_state(m->s, p0, p1);
      break;
    }
  }

  m->phase++;
  if((m->phase & 3u)==0u){
    m->t++;
    if((m->t & 0xFFu) == 0u){
      raf_bench_update(st, cfg);
    }
    raf_autotune_config(cfg, st);
  }
}

/* ==== Exemplo hosted (opcional) ==== */
/* Se quiser testar em ambiente com libc, plugue write/panic e rode. */
#ifdef RAF_HOSTED_TEST
#include <stdio.h>
static u32 host_write(void*ctx, const u8*buf, u32 len){ (void)ctx; return (u32)fwrite(buf,1,len,stdout); }
static void host_panic(void*ctx, const char*msg){ (void)ctx; fputs(msg, stderr); fputc('\n', stderr); }
int main(void){
  struct RMR_API api = { host_write, host_panic, NULL, NULL };
  rmr_bind_api(&api);

  raf_store_t st; rmr_memset(&st,0,sizeof(st));
  raf_machine_t m; raf_machine_init(&m);
  raf_config_t cfg; raf_config_init(&cfg);
  rmr_hw_caps_t caps; rmr_hw_caps_detect(&caps);
  raf_config_autoadapt(&cfg, &caps);

  for(u32 i=0;i<1000;i++){
    u64 payload = (0x9E3779B97F4A7C15ULL * (u64)(i+1)) ^ ((u64)i<<33);
    raf_step_cfg(&st,&m,payload,(u16)(i*3u), &cfg);
  }

  char out[196];
  int n = snprintf(out,sizeof(out),
    "pts=%u attract42=%u noise=%u head=%u best0={idx:%u score:%u}\n",
    st.count, st.attract, st.noise_acc, st.head,
    (st.best_count>0u)?st.best[0].idx:0u, (st.best_count>0u)?st.best[0].score:0u);
  rmr_write_bytes((const u8*)out,(u32)n);
  return 0;
}
#endif
