#include <stdio.h>
#include <stdint.h>
#include <unistd.h>
#ifdef __linux__
#include <sys/auxv.h>
#endif

#ifndef HWCAP_ASIMD
#define HWCAP_ASIMD (1UL << 1)
#endif
#ifndef HWCAP_SVE
#define HWCAP_SVE (1UL << 22)
#endif
#ifndef HWCAP_NEON
#define HWCAP_NEON HWCAP_ASIMD
#endif

static int has_flag(unsigned long mask, unsigned long bit) {
    return (mask & bit) != 0UL;
}

int main(void) {
    unsigned long hwcap = 0UL;
    unsigned long hwcap2 = 0UL;

#ifdef __linux__
    hwcap = getauxval(AT_HWCAP);
#ifdef AT_HWCAP2
    hwcap2 = getauxval(AT_HWCAP2);
#endif
#endif

    long pagesz = sysconf(_SC_PAGESIZE);

    int neon = has_flag(hwcap, HWCAP_NEON);
    int asimd = has_flag(hwcap, HWCAP_ASIMD);
    int sve = has_flag(hwcap, HWCAP_SVE);

    printf("arch_probe:ok\n");
    printf("page_size:%ld\n", pagesz);
    printf("hwcap:0x%lx\n", hwcap);
    printf("hwcap2:0x%lx\n", hwcap2);
    printf("neon:%d\n", neon);
    printf("asimd:%d\n", asimd);
    printf("sve:%d\n", sve);
    return 0;
}
