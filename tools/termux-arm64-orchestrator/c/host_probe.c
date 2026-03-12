#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <time.h>
#include <unistd.h>

static long probe_page_size(void) {
  long value = sysconf(_SC_PAGESIZE);
  if (value <= 0) {
    value = 4096;
  }
  return value;
}

static void read_cpu_features(char *out, size_t out_len) {
  FILE *fp = fopen("/proc/cpuinfo", "r");
  if (!fp) {
    snprintf(out, out_len, "unavailable");
    return;
  }

  char line[1024];
  while (fgets(line, sizeof(line), fp)) {
    if (strncmp(line, "Features", 8) == 0 || strncmp(line, "flags", 5) == 0) {
      char *sep = strchr(line, ':');
      if (!sep) {
        sep = strchr(line, '\t');
      }
      if (!sep) {
        continue;
      }

      sep++;
      while (*sep == ' ' || *sep == '\t') {
        sep++;
      }

      size_t n = strcspn(sep, "\r\n");
      if (n >= out_len) {
        n = out_len - 1;
      }
      memcpy(out, sep, n);
      out[n] = '\0';
      fclose(fp);
      return;
    }
  }

  fclose(fp);
  snprintf(out, out_len, "unavailable");
}

static int ensure_dir(const char *path) {
  struct stat st;
  if (stat(path, &st) == 0) {
    return S_ISDIR(st.st_mode) ? 0 : -1;
  }
  if (mkdir(path, 0755) == 0) {
    return 0;
  }
  return -1;
}

static bool probe_write_permission(const char *dir_path, bool *exec_ok) {
  char file_path[4096];
  snprintf(file_path, sizeof(file_path), "%s/%s", dir_path, ".host_probe_write_test");

  FILE *fp = fopen(file_path, "wb");
  if (!fp) {
    *exec_ok = false;
    return false;
  }

  const char payload[] = "host_probe";
  size_t wrote = fwrite(payload, 1, sizeof(payload) - 1, fp);
  fclose(fp);

  if (wrote != sizeof(payload) - 1) {
    unlink(file_path);
    *exec_ok = false;
    return false;
  }

  if (chmod(file_path, 0755) == 0 && access(file_path, X_OK) == 0) {
    *exec_ok = true;
  } else {
    *exec_ok = false;
  }

  unlink(file_path);
  return true;
}

int main(int argc, char **argv) {
  const char *spill_dir = (argc > 1 && argv[1] && argv[1][0] != '\0') ? argv[1] : ".build-spill";

  struct utsname uts;
  memset(&uts, 0, sizeof(uts));
  if (uname(&uts) != 0) {
    snprintf(uts.machine, sizeof(uts.machine), "unknown");
  }

  char cpu_features[1024];
  read_cpu_features(cpu_features, sizeof(cpu_features));

  bool spill_dir_ready = ensure_dir(spill_dir) == 0;
  bool spill_dir_exec = false;
  bool spill_dir_write = spill_dir_ready && probe_write_permission(spill_dir, &spill_dir_exec);

  time_t now = time(NULL);

  printf("timestamp=%ld\n", (long)now);
  printf("architecture=%s\n", uts.machine[0] ? uts.machine : "unknown");
  printf("page_size=%ld\n", probe_page_size());
  printf("cpu_features=%s\n", cpu_features);
  printf("spill_dir=%s\n", spill_dir);
  printf("spill_dir_ready=%d\n", spill_dir_ready ? 1 : 0);
  printf("spill_dir_writable=%d\n", spill_dir_write ? 1 : 0);
  printf("spill_dir_executable=%d\n", spill_dir_exec ? 1 : 0);
  printf("home_dir=%s\n", getenv("HOME") ? getenv("HOME") : "");
  printf("tmp_dir=%s\n", getenv("TMPDIR") ? getenv("TMPDIR") : "");
  printf("errno_last=%d\n", errno);

  return spill_dir_write ? 0 : 2;
}
