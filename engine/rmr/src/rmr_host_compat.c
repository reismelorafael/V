#include "rmr_host_compat.h"

#include <stdarg.h>
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
