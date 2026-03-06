#ifndef RMR_HOST_COMPAT_H
#define RMR_HOST_COMPAT_H

#include <stddef.h>
#include "zero.h"

typedef struct rmr_file rmr_file_t;

typedef struct {
  long tv_sec;
  long tv_nsec;
} rmr_timespec_t;

typedef struct {
  unsigned long long st_dev;
  unsigned long long st_ino;
  unsigned long long st_size;
} rmr_stat_t;

void *rmr_malloc(size_t bytes);
void rmr_free(void *ptr);
void *rmr_realloc(void *ptr, size_t bytes);
void *rmr_memset(void *dst, int value, size_t len);
void *rmr_memcpy(void *dst, const void *src, size_t len);
size_t rmr_strlen(const char *s);
int rmr_snprintf(char *out, size_t out_len, const char *fmt, ...);
const char *rmr_strstr(const char *s, const char *needle);
int rmr_stat(const char *path, rmr_stat_t *st);
int rmr_clock_gettime_monotonic(rmr_timespec_t *ts);

rmr_file_t *rmr_fopen(const char *path, const char *mode);
int rmr_fclose(rmr_file_t *file);
size_t rmr_fread(void *buf, size_t size, size_t count, rmr_file_t *file);
size_t rmr_fwrite(const void *buf, size_t size, size_t count, rmr_file_t *file);
int rmr_feof(rmr_file_t *file);
int rmr_fflush(rmr_file_t *file);

#endif
