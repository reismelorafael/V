/* ═══════════════════════════════════════════════════════════════════
   rmr_baremetal_compat.h — Substituição stdlib para baremetal
   Zero deps externas. Implementação inline + bump allocator.
   ═══════════════════════════════════════════════════════════════════ */
#ifndef RMR_BAREMETAL_COMPAT_H
#define RMR_BAREMETAL_COMPAT_H

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

#define RMR_ARENA_SIZE (1024u * 1024u)

extern uint8_t  rmr_arena[RMR_ARENA_SIZE];
extern uint32_t rmr_arena_ptr;

static inline void* rmr_malloc(size_t bytes) {
    bytes = (bytes + 7u) & ~7u;
    if (rmr_arena_ptr + bytes > RMR_ARENA_SIZE) return NULL;
    void *p = (void*)(rmr_arena + rmr_arena_ptr);
    rmr_arena_ptr += (uint32_t)bytes;
    return p;
}

static inline void rmr_free(void *p) { (void)p; }

static inline void* rmr_realloc(void *ptr, size_t bytes) {
    void *np;
    if (!ptr) return rmr_malloc(bytes);
    if (bytes == 0u) return NULL;
    np = rmr_malloc(bytes);
    if (!np) return NULL;
    return np;
}

static inline void* rmr_memset(void *dst, int val, size_t n) {
    uint8_t *d = (uint8_t*)dst;
    uint8_t  v = (uint8_t)val;
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

static inline const char *rmr_strstr(const char *s, const char *needle) {
    size_t i;
    size_t nlen;
    if (!s || !needle) return NULL;
    nlen = rmr_strlen(needle);
    if (nlen == 0u) return s;
    for (; *s; ++s) {
        for (i = 0u; i < nlen && s[i] == needle[i]; ++i) {}
        if (i == nlen) return s;
    }
    return NULL;
}

static inline int rmr_snprintf(char *out, size_t out_len, const char *fmt, ...) {
    size_t i;
    if (!out || out_len == 0u) return 0;
    if (!fmt) {
        out[0] = '\0';
        return 0;
    }
    for (i = 0u; i + 1u < out_len && fmt[i] != '\0'; ++i) out[i] = fmt[i];
    out[i] = '\0';
    return (int)i;
}

#ifndef RMR_UART_PUTS
#define RMR_UART_PUTS(s) ((void)(s))
#endif

static inline int rmr_printf_stub(const char *fmt, ...) {
    RMR_UART_PUTS(fmt);
    return 0;
}
static inline int rmr_fprintf_stub(void *f, const char *fmt, ...) {
    (void)f; (void)fmt; return 0;
}

typedef void rmr_file_t;

typedef struct {
    uint64_t st_dev;
    uint64_t st_ino;
    uint64_t st_size;
} rmr_stat_t;

typedef struct {
    long tv_sec;
    long tv_nsec;
} rmr_timespec_t;

static inline rmr_file_t* rmr_fopen(const char *p, const char *m)  { (void)p;(void)m; return NULL; }
static inline int         rmr_fclose(rmr_file_t *f)                 { (void)f; return 0; }
static inline size_t      rmr_fread(void *b,size_t s,size_t n,rmr_file_t*f){(void)b;(void)s;(void)n;(void)f;return 0;}
static inline size_t      rmr_fwrite(const void*b,size_t s,size_t n,rmr_file_t*f){(void)b;(void)s;(void)n;(void)f;return 0;}
static inline int         rmr_feof(rmr_file_t *f)                   { (void)f; return 1; }
static inline int         rmr_fflush(rmr_file_t *f)                 { (void)f; return 0; }
static inline int         rmr_stat(const char *p, rmr_stat_t *s)    { (void)p;(void)s; return -1; }
static inline int         rmr_clock_gettime_monotonic(rmr_timespec_t *t) {
    if (t) { t->tv_sec = 0; t->tv_nsec = 0; }
    return 0;
}

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

#ifndef RMR_ABORT_USE_RISCV_WFI
#define RMR_ABORT_USE_RISCV_WFI 1
#endif
#ifndef RMR_ABORT_USE_X86_HLT
#define RMR_ABORT_USE_X86_HLT 1
#endif

static inline RMR_NORETURN void rmr_abort(void) {
    for (;;) {
#if defined(__aarch64__) || defined(__arm__)
        __asm__ __volatile__("wfi");
#elif defined(__x86_64__) || defined(__i386__)
#if RMR_ABORT_USE_X86_HLT
        __asm__ __volatile__("hlt");
#else
        __asm__ __volatile__("pause");
#endif
#elif defined(__riscv)
#if RMR_ABORT_USE_RISCV_WFI
        __asm__ __volatile__("wfi");
#else
        __asm__ __volatile__("" ::: "memory");
#endif
#elif defined(__mips__)
        __asm__ __volatile__("wait");
#elif defined(__powerpc__) || defined(__ppc__) || defined(__PPC__)
        __asm__ __volatile__("or 27,27,27");
#elif defined(__sparc__)
        __asm__ __volatile__("nop");
#elif defined(__loongarch__)
        __asm__ __volatile__("idle 0");
#else
        __asm__ __volatile__("" ::: "memory");
#endif
    }
}

/* Legacy aliases for source files ainda em transição */
#define malloc(n)            rmr_malloc(n)
#define free(p)              rmr_free(p)
#define realloc(p,n)         rmr_realloc(p,n)
#define memset(d,v,n)        rmr_memset(d,v,n)
#define memcpy(d,s,n)        rmr_memcpy(d,s,n)
#define strlen(s)            rmr_strlen(s)
#define snprintf(...)       rmr_snprintf(__VA_ARGS__)
#define strstr(s,n)          rmr_strstr(s,n)

#ifndef RMR_BAREMETAL_COMPAT_IMPL
extern uint8_t  rmr_arena[];
extern uint32_t rmr_arena_ptr;
#endif

#endif
