#define _XOPEN_SOURCE 700
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

static int ensure_dir(const char *path) {
    if (mkdir(path, 0755) == 0) return 0;
    if (errno == EEXIST) return 0;
    return -1;
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <spill_dir> <size_mb>\n", argv[0]);
        return 1;
    }

    const char *spill_dir = argv[1];
    long size_mb = strtol(argv[2], NULL, 10);
    if (size_mb <= 0) {
        fprintf(stderr, "invalid size_mb\n");
        return 2;
    }

    if (ensure_dir(spill_dir) != 0) {
        perror("mkdir spill_dir");
        return 3;
    }

    char file_path[1024];
    int n = snprintf(file_path, sizeof(file_path), "%s/spill.bin", spill_dir);
    if (n <= 0 || n >= (int)sizeof(file_path)) {
        fprintf(stderr, "path too long\n");
        return 4;
    }

    int fd = open(file_path, O_CREAT | O_RDWR, 0644);
    if (fd < 0) {
        perror("open spill.bin");
        return 5;
    }

    off_t bytes = (off_t)size_mb * 1024 * 1024;
    if (ftruncate(fd, bytes) != 0) {
        perror("ftruncate spill.bin");
        close(fd);
        return 6;
    }

    if (fsync(fd) != 0) {
        perror("fsync spill.bin");
        close(fd);
        return 7;
    }

    close(fd);

    printf("spill_allocator:ok\n");
    printf("spill_file:%s\n", file_path);
    printf("spill_bytes:%lld\n", (long long)bytes);
    return 0;
}
