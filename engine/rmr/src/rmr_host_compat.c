#define _POSIX_C_SOURCE 200809L

#include "rmr_host_compat.h"

#include <stdarg.h>

#if defined(RMR_BUILD_HOST_TOOLING) && (RMR_BUILD_HOST_TOOLING == 1)

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>

typedef struct rmr_file {
  FILE *handle;
} rmr_file_t;

void *rmr_malloc(size_t bytes) { return malloc(bytes); }
void rmr_free(void *ptr) { free(ptr); }
void *rmr_realloc(void *ptr, size_t bytes) { return realloc(ptr, bytes); }
void *rmr_memset(void *dst, int value, size_t len) { return memset(dst, value, len); }
void *rmr_memcpy(void *dst, const void *src, size_t len) { return memcpy(dst, src, len); }
size_t rmr_strlen(const char *s) { return s ? strlen(s) : 0u; }

int rmr_snprintf(char *out, size_t out_len, const char *fmt, ...) {
  va_list ap;
  int rc;
  va_start(ap, fmt);
  rc = vsnprintf(out, out_len, fmt, ap);
  va_end(ap);
  return rc;
}

const char *rmr_strstr(const char *s, const char *needle) { return strstr(s, needle); }

int rmr_stat(const char *path, rmr_stat_t *st) {
  struct stat host;
  if (!path || !st) return -1;
  if (stat(path, &host) != 0) return -1;
  st->st_dev = (unsigned long long)host.st_dev;
  st->st_ino = (unsigned long long)host.st_ino;
  st->st_size = (unsigned long long)host.st_size;
  return 0;
}

int rmr_clock_gettime_monotonic(rmr_timespec_t *ts) {
  struct timespec host;
  if (!ts) return -1;
  if (clock_gettime(CLOCK_MONOTONIC, &host) != 0) return -1;
  ts->tv_sec = host.tv_sec;
  ts->tv_nsec = host.tv_nsec;
  return 0;
}

rmr_file_t *rmr_fopen(const char *path, const char *mode) {
  FILE *f;
  rmr_file_t *bridge;
  if (!path || !mode) return NULL;
  f = fopen(path, mode);
  if (!f) return NULL;
  bridge = (rmr_file_t *)malloc(sizeof(*bridge));
  if (!bridge) {
    fclose(f);
    return NULL;
  }
  bridge->handle = f;
  return bridge;
}

int rmr_fclose(rmr_file_t *file) {
  int rc;
  if (!file) return -1;
  rc = fclose(file->handle);
  free(file);
  return rc;
}

size_t rmr_fread(void *buf, size_t size, size_t count, rmr_file_t *file) {
  if (!file || !file->handle) return 0u;
  return fread(buf, size, count, file->handle);
}

size_t rmr_fwrite(const void *buf, size_t size, size_t count, rmr_file_t *file) {
  if (!file || !file->handle) return 0u;
  return fwrite(buf, size, count, file->handle);
}

int rmr_feof(rmr_file_t *file) {
  if (!file || !file->handle) return 1;
  return feof(file->handle);
}

int rmr_fflush(rmr_file_t *file) {
  if (!file || !file->handle) return -1;
  return fflush(file->handle);
}

#else

typedef struct rmr_file {
  unsigned int marker;
} rmr_file_t;

#define RMR_HOST_COMPAT_STUB_ARENA_SIZE (1024u * 1024u)

static unsigned char rmr_host_stub_arena[RMR_HOST_COMPAT_STUB_ARENA_SIZE];
static size_t rmr_host_stub_arena_ptr = 0u;

static size_t rmr_host_align8(size_t v) { return (v + 7u) & ~(size_t)7u; }

void *rmr_malloc(size_t bytes) {
  size_t aligned = rmr_host_align8(bytes);
  void *out;
  if (aligned == 0u) return NULL;
  if (rmr_host_stub_arena_ptr > (RMR_HOST_COMPAT_STUB_ARENA_SIZE - aligned)) return NULL;
  out = (void *)(rmr_host_stub_arena + rmr_host_stub_arena_ptr);
  rmr_host_stub_arena_ptr += aligned;
  return out;
}

void rmr_free(void *ptr) { (void)ptr; }

void *rmr_realloc(void *ptr, size_t bytes) {
  if (!ptr) return rmr_malloc(bytes);
  if (bytes == 0u) return NULL;
  return rmr_malloc(bytes);
}

void *rmr_memset(void *dst, int value, size_t len) {
  unsigned char *d = (unsigned char *)dst;
  size_t i;
  for (i = 0u; i < len; ++i) d[i] = (unsigned char)value;
  return dst;
}

void *rmr_memcpy(void *dst, const void *src, size_t len) {
  unsigned char *d = (unsigned char *)dst;
  const unsigned char *s = (const unsigned char *)src;
  size_t i;
  if (d <= s || d >= s + len) {
    for (i = 0u; i < len; ++i) d[i] = s[i];
  } else {
    for (i = len; i > 0u; --i) d[i - 1u] = s[i - 1u];
  }
  return dst;
}

size_t rmr_strlen(const char *s) {
  size_t n = 0u;
  if (!s) return 0u;
  while (s[n] != '\0') ++n;
  return n;
}

int rmr_snprintf(char *out, size_t out_len, const char *fmt, ...) {
  size_t i;
  (void)fmt;
  if (!out || out_len == 0u) return 0;
  for (i = 0u; i + 1u < out_len; ++i) out[i] = '\0';
  out[0] = '\0';
  return 0;
}

const char *rmr_strstr(const char *s, const char *needle) {
  size_t i;
  size_t nlen;
  if (!s || !needle) return NULL;
  nlen = rmr_strlen(needle);
  if (nlen == 0u) return s;
  for (; *s; ++s) {
    for (i = 0u; i < nlen && s[i] == needle[i]; ++i) {
    }
    if (i == nlen) return s;
  }
  return NULL;
}

int rmr_stat(const char *path, rmr_stat_t *st) {
  (void)path;
  if (!st) return -1;
  st->st_dev = 0u;
  st->st_ino = 0u;
  st->st_size = 0u;
  return -1;
}

int rmr_clock_gettime_monotonic(rmr_timespec_t *ts) {
  if (!ts) return -1;
  ts->tv_sec = 0;
  ts->tv_nsec = 0;
  return 0;
}

rmr_file_t *rmr_fopen(const char *path, const char *mode) {
  (void)path;
  (void)mode;
  return NULL;
}

int rmr_fclose(rmr_file_t *file) {
  (void)file;
  return 0;
}

size_t rmr_fread(void *buf, size_t size, size_t count, rmr_file_t *file) {
  (void)buf;
  (void)size;
  (void)count;
  (void)file;
  return 0u;
}

size_t rmr_fwrite(const void *buf, size_t size, size_t count, rmr_file_t *file) {
  (void)buf;
  (void)size;
  (void)count;
  (void)file;
  return 0u;
}

int rmr_feof(rmr_file_t *file) {
  (void)file;
  return 1;
}

int rmr_fflush(rmr_file_t *file) {
  (void)file;
  return 0;
}

#endif
