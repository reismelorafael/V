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

#ifndef NULL
#define NULL ((void*)0)
#endif

/* ==== Backend mínimo (pluga em stdout/UART/MMIO) ==== */
struct RMR_API {
  u32 (*write)(void *ctx, const u8 *buf, u32 len);
  void (*panic)(void *ctx, const char *msg);
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

/* ==== util sem libc ==== */
static void rmr_memset(void *dst, u8 v, u32 n){
  u8 *d=(u8*)dst;
  while(n--) *d++=v;
}
static void rmr_memcpy(void *dst, const void *src, u32 n){
  u8 *d=(u8*)dst; const u8*s=(const u8*)src;
  while(n--) *d++=*s++;
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

typedef struct {
  u32 idx;
  u16 score;
} raf_best_t;

/* ==== Anel de pontos ==== */
#ifndef RAF_POINTS_MAX
#define RAF_POINTS_MAX 4096u
#endif

typedef struct {
  raf_point_t pts[RAF_POINTS_MAX];
  u32 head;      /* próximo a escrever */
  u32 count;     /* total escrito (pode saturar) */
  u32 attract;   /* contador de atrator 42 */
  u32 noise_acc; /* acumulador de ruído global */
  raf_best_t best[RAF_BEST_MAX];
  u32 best_count;
} raf_store_t;

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

/* ==== Score para Top-42 (plasticidade) ==== */
static u16 raf_point_score(const raf_point_t *p){
  /* Heurística determinística: privilegia coerência (paridades),
     atrator 42, baixo ruído e simetria de símbolos. */
  u16 score = 0;
  u16 noise_pen = (u16)(p->noise & 0x00FFu);
  score += (u16)(255u - noise_pen);
  score += (p->p0 == p->p1) ? 64u : 0u;
  score += (p->slot10 == (p->sym20 % 10u)) ? 32u : 0u;
  score += raf_is_attractor42(p) ? 128u : 0u;
  return score;
}

static void raf_update_best42(raf_store_t *st, const raf_point_t *p){
  if(!st || !p) return;
  u16 score = raf_point_score(p);
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
  u32 aux
){
  if(!st) rmr_panic("raf_store NULL");
  raf_point_t *p = &st->pts[st->head];

  p->idx   = st->count;
  p->state = (u8)state;
  p->slot10= raf_slot10(slot_seed);
  p->sym20 = raf_base20(sym_seed);

  raf_dual_parity_u64(payload64, &p->p0, &p->p1);

  /* ruído absorvido: acumula + mistura leve (não é crypto; é telemetria determinística) */
  st->noise_acc += (u32)noise_hint + (u32)(payload64 ^ (payload64>>32));
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

  if(raf_is_attractor42(p)) st->attract++;
  raf_update_best42(st, p);

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
      raf_append_point(st, m->s, m->slot_seed, m->sym_seed, payload64, noise_hint, m->aux);
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

/* ==== Exemplo hosted (opcional) ==== */
/* Se quiser testar em ambiente com libc, plugue write/panic e rode. */
#ifdef RAF_HOSTED_TEST
#include <stdio.h>
static u32 host_write(void*ctx, const u8*buf, u32 len){ (void)ctx; return (u32)fwrite(buf,1,len,stdout); }
static void host_panic(void*ctx, const char*msg){ (void)ctx; fputs(msg, stderr); fputc('\n', stderr); }
int main(void){
  struct RMR_API api = { host_write, host_panic, NULL };
  rmr_bind_api(&api);

  raf_store_t st; rmr_memset(&st,0,sizeof(st));
  raf_machine_t m; raf_machine_init(&m);

  for(u32 i=0;i<1000;i++){
    u64 payload = (0x9E3779B97F4A7C15ULL * (u64)(i+1)) ^ ((u64)i<<33);
    raf_step(&st,&m,payload,(u16)(i*3u));
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
