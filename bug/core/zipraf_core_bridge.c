/* zipraf_core_bridge.c — ZIPRAF ↔ RMR BRIDGE
 * ∆RAFAELIA_CORE·Ω
 * Adapts zipraf_core.c functions to the RMR JNI result layout
 * zr_open, zr_triple_complete, zr_crc32c, zr_geo4x4_trace, zr_virt_size
 * ─────────────────────────────────────────────────────── */
#include "rmr_unified_kernel.h"
#include "rmr_lowlevel.h"

/* ── Result type expected by zipraf_jni.c ── */
typedef struct {
    int      cell_count;
    int      virtual_count;
    long long virt_size;
    int      parity_a;
    int      parity_b;
} zr_result_t;

/* ── CRC32C table (initialized once) ── */
static rmr_u32 s_ztbl[256];
static int s_ztbl_ready = 0;
static void s_init_tbl(void) {
    if (s_ztbl_ready) return;
    for (int i = 0; i < 256; i++) {
        rmr_u32 c = (rmr_u32)i;
        for (int j = 0; j < 8; j++)
            c = (c >> 1) ^ ((c & 1u) ? 0x82F63B78u : 0u);
        s_ztbl[i] = c;
    }
    s_ztbl_ready = 1;
}

static rmr_u32 s_crc32c(rmr_u32 crc, const rmr_u8 *d, rmr_u32 n) {
    s_init_tbl();
    crc = ~crc;
    for (rmr_u32 i = 0; i < n; i++)
        crc = (crc >> 8) ^ s_ztbl[(crc ^ d[i]) & 0xFFu];
    return ~crc;
}

/* ── LE helpers ── */
static rmr_u16 le16(const rmr_u8 *d, rmr_u32 o) {
    return (rmr_u16)(d[o] | (d[o+1] << 8));
}
static rmr_u32 le32(const rmr_u8 *d, rmr_u32 o) {
    return d[o] | ((rmr_u32)d[o+1]<<8) | ((rmr_u32)d[o+2]<<16) | ((rmr_u32)d[o+3]<<24);
}

/* ── zr_open: parse byte blob → fill result struct ── */
int zr_open(void *result_out, const rmr_u8 *data, rmr_u32 len) {
    if (!result_out || !data || len < 4) return -1;
    zr_result_t *res = (zr_result_t*)result_out;
    res->cell_count    = 0;
    res->virtual_count = 0;
    res->virt_size     = 0;
    res->parity_a      = 0;
    res->parity_b      = 0;

    int cell_count = 0;
    int pa = 0, pb = 0;

    /* Detect format */
    rmr_u32 sig = le32(data, 0);

    if (sig == 0x04034B50u || sig == 0x02014B50u) {
        /* ─── ZIP ─── */
        int eocd_off = -1;
        rmr_u32 scan_start = len > 65558u ? len - 65558u : 0u;
        for (rmr_u32 i = len - 4u; i >= scan_start && i < len; i--) {
            if (le32(data, i) == 0x06054B50u) { eocd_off = (int)i; break; }
            if (i == 0) break;
        }
        if (eocd_off < 0) return -2;

        rmr_u32 cdir_off = le32(data, (rmr_u32)eocd_off + 16u);
        if (cdir_off >= len) return -3;

        rmr_u32 p = cdir_off;
        while (cell_count < 1000 && p + 46u <= len) {
            if (le32(data, p) != 0x02014B50u) break;
            rmr_u16 fn_len    = le16(data, p + 28u);
            rmr_u16 extra_len = le16(data, p + 30u);
            rmr_u16 cmt_len   = le16(data, p + 32u);
            rmr_u32 crc32     = le32(data, p + 16u);
            rmr_u32 comp_size = le32(data, p + 20u);

            pa ^= (int)(crc32 & 0xFFu);
            pb ^= (int)((crc32 >> 8u) & 0xFFu);
            (void)comp_size;

            cell_count++;
            p += 46u + fn_len + extra_len + cmt_len;
        }

    } else if (len >= 265u && data[257]=='u' && data[258]=='s' &&
               data[259]=='t' && data[260]=='a' && data[261]=='r') {
        /* ─── TAR ─── */
        rmr_u32 off = 0u;
        while (off + 512u <= len && cell_count < 1000) {
            if (data[off] == 0) break;
            rmr_u32 crc = s_crc32c(0u, data + off, 512u);
            pa ^= (int)(crc & 0xFFu);
            pb ^= (int)((crc >> 8u) & 0xFFu);

            /* file size from octal field at +124 */
            rmr_u64 fsize = 0;
            for (int k = 0; k < 12; k++) {
                rmr_u8 c = data[off + 124u + (rmr_u32)k];
                if (c < '0' || c > '7') break;
                fsize = (fsize << 3) | (rmr_u64)(c - '0');
            }
            off += 512u + (rmr_u32)(((fsize + 511u) / 512u) * 512u);
            cell_count++;
        }

    } else {
        /* ─── RAW ─── */
        rmr_u32 crc = s_crc32c(0u, data, len < 4096u ? len : 4096u);
        pa = (int)(crc & 0xFFu);
        pb = (int)((crc >> 8u) & 0xFFu);
        cell_count = 1;
    }

    /* virtual cells fill up to 1000 */
    int virt_count = 1000 - cell_count;
    if (virt_count < 0) virt_count = 0;

    /* virtual size amplification */
    rmr_u64 vs = rmr_ll_virt_size((rmr_u64)len, (rmr_u32)cell_count, 0u);

    res->cell_count    = cell_count;
    res->virtual_count = virt_count;
    res->virt_size     = (long long)vs;
    res->parity_a      = pa;
    res->parity_b      = pb;

    return cell_count > 0 ? cell_count : 1;
}

/* ── zr_triple_complete ── */
int zr_triple_complete(rmr_u64 *off, rmr_u32 *len, rmr_u32 *crc,
                       rmr_u32 valid,
                       const rmr_u8 *data, rmr_u32 dlen) {
    return rmr_ll_triple_complete(off, len, crc, valid, data, dlen);
}

/* ── zr_crc32c ── */
rmr_u32 zr_crc32c(rmr_u32 seed, const rmr_u8 *data, rmr_u32 len) {
    return rmr_lowlevel_crc32c_hw(seed, data, len);
}

/* ── zr_geo4x4_trace ── */
rmr_u32 zr_geo4x4_trace(const rmr_u16 *cells, rmr_u32 count) {
    return rmr_ll_geo4x4_trace(cells, count);
}

/* ── zr_virt_size ── */
rmr_u64 zr_virt_size(rmr_u64 phys, rmr_u32 cells, rmr_u32 paths) {
    return rmr_ll_virt_size(phys, cells, paths);
}

/* ── rmr_ll_geo4x4_trace (needed by ll_ops) ── */
/* forward declare ll_ops implementation */
extern rmr_u32 rmr_ll_geo4x4_trace(const rmr_u16 *cells, rmr_u32 count);
extern rmr_u64 rmr_ll_virt_size(rmr_u64 phys_size, rmr_u32 cell_count, rmr_u32 path_count);
extern int     rmr_ll_triple_complete(rmr_u64*, rmr_u32*, rmr_u32*, rmr_u32, const rmr_u8*, rmr_u32);

/* RAFAELIA-CHANGED-FILES integration: standalone CRC32 (IEEE) helper for ZIP diagnostics. */
rmr_u32 zr_crc32_ieee(rmr_u32 crc, const rmr_u8 *d, rmr_u32 n) {
    static rmr_u32 tbl[256];
    static int ready = 0;
    if (!ready) {
        for (int i = 0; i < 256; i++) {
            rmr_u32 c = (rmr_u32)i;
            for (int j = 0; j < 8; j++) {
                c = (c >> 1) ^ ((c & 1u) ? 0xEDB88320u : 0u);
            }
            tbl[i] = c;
        }
        ready = 1;
    }
    crc = ~crc;
    for (rmr_u32 i = 0; i < n; i++) {
        crc = (crc >> 8) ^ tbl[(crc ^ d[i]) & 0xFFu];
    }
    return ~crc;
}
