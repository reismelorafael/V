/* ═══════════════════════════════════════════════════════════════════
   rmr_baremetal_compat.h — Substituição stdlib para baremetal
   Zero deps externas. Implementação inline + bump allocator.
   Inclua ANTES de qualquer outra header que use stdlib.

   BUG FIX GLOBAL: arquivos originais usavam malloc/free/memset/
   memcpy/printf de stdlib — proibido em baremetal sem libc.
   Este header fornece substitutos determinísticos próprios.
   ═══════════════════════════════════════════════════════════════════ */
#ifndef RMR_BAREMETAL_COMPAT_H
#define RMR_BAREMETAL_COMPAT_H

/* ── Tipos primitivos independentes de stdint.h ── */
#if !defined(RMR_TYPES_DEFINED)
#define RMR_TYPES_DEFINED
typedef unsigned char      uint8_t;
typedef unsigned short     uint16_t;
typedef unsigned int       uint32_t;
typedef unsigned long long uint64_t;
typedef signed   int       int32_t;
typedef signed   long long int64_t;
typedef unsigned long long size_t;
typedef unsigned long long uintptr_t;
#define NULL ((void*)0)
#define SIZE_MAX  ((size_t)-1)
#endif

/* ── Bump allocator global (1MB pool baremetal) ──
   Precisa ser inicializado por kernel_main antes de uso.
   BUG FIX: sem arena → malloc retornava NULL sem aviso   */
#define RMR_ARENA_SIZE (1024u * 1024u)

extern uint8_t  rmr_arena[RMR_ARENA_SIZE];
extern uint32_t rmr_arena_ptr;

static inline void* rmr_malloc(size_t bytes) {
    /* alinha 8 bytes */
    bytes = (bytes + 7u) & ~7u;
    if (rmr_arena_ptr + bytes > RMR_ARENA_SIZE) return NULL;
    void *p = (void*)(rmr_arena + rmr_arena_ptr);
    rmr_arena_ptr += (uint32_t)bytes;
    return p;
}

/* free no-op: bump allocator não libera (baremetal intencional) */
static inline void rmr_free(void *p) { (void)p; }

/* ── mem funções ── */
static inline void* rmr_memset(void *dst, int val, size_t n) {
    uint8_t *d = (uint8_t*)dst;
    uint8_t  v = (uint8_t)val;
    /* BUG FIX: fill word-by-word para performance */
    size_t i = 0;
    while (i < n && ((uintptr_t)(d+i) & 7u)) { d[i++] = v; }
    uint64_t w = v; w |= w<<8; w |= w<<16; w |= w<<32;
    while (i + 8u <= n) { *(uint64_t*)(d+i) = w; i += 8u; }
    while (i < n)  d[i++] = v;
    return dst;
}

static inline void* rmr_memcpy(void *dst, const void *src, size_t n) {
    uint8_t *d = (uint8_t*)dst;
    const uint8_t *s = (const uint8_t*)src;
    /* BUG FIX: direção depende de overlap */
    if (d <= s || d >= s + n) {
        size_t i = 0;
        while (i < n && (((uintptr_t)(d+i)|(uintptr_t)(s+i)) & 7u)) { d[i]=s[i]; i++; }
        while (i + 8u <= n) { *(uint64_t*)(d+i)=*(const uint64_t*)(s+i); i+=8u; }
        while (i < n) { d[i]=s[i]; i++; }
    } else {
        size_t i = n;
        while (i) { i--; d[i]=s[i]; }
    }
    return dst;
}

static inline size_t rmr_strlen(const char *s) {
    if (!s) return 0;
    size_t n = 0; while (s[n]) n++; return n;
}

/* ── stdio stub: printf → discarded em baremetal ──
   Para debug real, redirecionar para uart_puts:
   Defina RMR_UART_PUTS antes de incluir este header.  */
#ifndef RMR_UART_PUTS
#define RMR_UART_PUTS(s) ((void)(s))
#endif

/* printf stub — silencia todos os printfs em baremetal */
static inline int rmr_printf_stub(const char *fmt, ...) {
    RMR_UART_PUTS(fmt); /* emite formato cru se UART disponível */
    return 0;
}
static inline int rmr_fprintf_stub(void *f, const char *fmt, ...) {
    (void)f; (void)fmt; return 0;
}

/* FILE stubs — operações de arquivo impossíveis em baremetal */
typedef void RMR_FILE;
#define RMR_NULL_FILE ((RMR_FILE*)NULL)

static inline RMR_FILE* rmr_fopen(const char *p, const char *m)  { (void)p;(void)m; return NULL; }
static inline int       rmr_fclose(RMR_FILE *f)                   { (void)f; return 0; }
static inline size_t    rmr_fread(void *b,size_t s,size_t n,RMR_FILE*f){(void)b;(void)s;(void)n;(void)f;return 0;}
static inline size_t    rmr_fwrite(const void*b,size_t s,size_t n,RMR_FILE*f){(void)b;(void)s;(void)n;(void)f;return 0;}
static inline int       rmr_feof(RMR_FILE *f)                     { (void)f; return 1; }
static inline RMR_FILE* rmr_popen(const char *c, const char *m)   { (void)c;(void)m; return NULL; }
static inline int       rmr_pclose(RMR_FILE *f)                   { (void)f; return 0; }
static inline int       rmr_stat_stub(const char *p, void *s)     { (void)p;(void)s; return -1; }

/* ── Macro overrides: substitui símbolos stdlib → rmr_ ── */
#define malloc(n)            rmr_malloc(n)
#define free(p)              rmr_free(p)
#define calloc(n,s)          rmr_memset(rmr_malloc((n)*(s)), 0, (n)*(s))
#define memset(d,v,n)        rmr_memset(d,v,n)
#define memcpy(d,s,n)        rmr_memcpy(d,s,n)
#define memmove(d,s,n)       rmr_memcpy(d,s,n)
#define strlen(s)            rmr_strlen(s)
#define printf(...)          rmr_printf_stub(__VA_ARGS__)
#define fprintf(f,...)       rmr_fprintf_stub(f,__VA_ARGS__)
#define fopen(p,m)           rmr_fopen(p,m)
#define fclose(f)            rmr_fclose(f)
#define fread(b,s,n,f)       rmr_fread(b,s,n,f)
#define fwrite(b,s,n,f)      rmr_fwrite(b,s,n,f)
#define feof(f)              rmr_feof(f)
#define popen(c,m)           rmr_popen(c,m)
#define pclose(f)            rmr_pclose(f)

/* sys/stat.h stub */
#define stat(p,s)            rmr_stat_stub(p,s)
struct stat { int st_size; };

/* stdlib.h abort/exit stubs */
#if defined(__has_attribute)
#  if __has_attribute(noreturn)
#    define RMR_NORETURN __attribute__((noreturn))
#  else
#    define RMR_NORETURN
#  endif
#elif defined(__GNUC__) || defined(__clang__)
#  define RMR_NORETURN __attribute__((noreturn))
#else
#  define RMR_NORETURN
#endif

/* Portabilidade: cada arquitetura usa a instrução de idle/halt mais apropriada
   quando disponível; fallback mantém loop puro com barreira de memória para
   evitar otimizações agressivas e preservar comportamento seguro sem libc. */
static inline RMR_NORETURN void rmr_abort(void) {
    for (;;) {
#if defined(__aarch64__) || defined(__arm__)
        __asm__ __volatile__("wfi");
#elif defined(__x86_64__) || defined(__i386__)
        __asm__ __volatile__("hlt");
#elif defined(__riscv)
        __asm__ __volatile__("wfi");
#else
        __asm__ __volatile__("" ::: "memory");
#endif
    }
}
#define abort()   rmr_abort()
#define exit(c)   rmr_abort()

/* pthread stubs — baremetal single-core (sem pré-emptividade) */
typedef int pthread_mutex_t;
typedef int pthread_t;
#define PTHREAD_MUTEX_INITIALIZER 0
static inline int pthread_mutex_lock(pthread_mutex_t *m)   { (void)m; return 0; }
static inline int pthread_mutex_unlock(pthread_mutex_t *m) { (void)m; return 0; }
static inline int pthread_create(pthread_t *t, void *a, void *(*f)(void*), void *arg) {
    (void)t;(void)a;(void)f;(void)arg; return -1; /* sem threads em baremetal */
}
static inline int pthread_join(pthread_t t, void **r) { (void)t;(void)r; return 0; }

/* stdatomic stubs — single-core: sem necessidade de atomic */
#define _Atomic
#define ATOMIC_VAR_INIT(v)  (v)
#define atomic_load_explicit(p,mo)        (*(p))
#define atomic_store_explicit(p,v,mo)     (*(p)=(v))
#define atomic_load(p)                    (*(p))
#define atomic_store(p,v)                 (*(p)=(v))
#define atomic_exchange(p,v)              ({ int _o=*(p); *(p)=(v); _o; })
typedef int atomic_int;

/* time.h stub */
#define CLOCK_MONOTONIC 0
struct timespec { long tv_sec; long tv_nsec; };
static inline int clock_gettime(int c, struct timespec *t) {
    (void)c; if(t){ t->tv_sec=0; t->tv_nsec=0; } return 0;
}

/* Arena vars — definidos em rmr_baremetal_compat.c */
#ifndef RMR_BAREMETAL_COMPAT_IMPL
extern uint8_t  rmr_arena[];
extern uint32_t rmr_arena_ptr;
#endif

#endif /* RMR_BAREMETAL_COMPAT_H */
