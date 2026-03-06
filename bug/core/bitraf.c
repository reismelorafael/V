/* bitraf.c — BITRAF: BIT-level Routing And Fabric
 * ∆RAFAELIA_CORE·Ω  ZIPRAFΩ
 * 64-slot bit dispatch + ZIPRAF kernel integration
 * Connects: ZIPRAF storage ↔ VECTRA dispatch ↔ RMR kernel
 * ─────────────────────────────────────────────────────── */
#include "rmr_unified_kernel.h"
#include "rmr_lowlevel.h"

/* ── BITRAF state ── */
#define BITRAF_SLOTS        64u
#define BITRAF_RING_SIZE    256u
#define BITRAF_MAGIC        0x42495452u  /* "BITR" */

typedef struct {
    rmr_u32 slot;
    rmr_u32 value;
    rmr_u32 timestamp;
    rmr_u32 flags;
} bitraf_event_t;

typedef struct {
    rmr_u32       magic;
    rmr_u64       route_mask;         /* active slots bitmask */
    rmr_u32       slot_crc[BITRAF_SLOTS];   /* per-slot CRC state */
    rmr_u64       slot_phi[BITRAF_SLOTS];   /* per-slot phi-state */
    bitraf_event_t ring[BITRAF_RING_SIZE];  /* event ring */
    rmr_u32       ring_head;
    rmr_u32       ring_tail;
    rmr_u32       tick;
    rmr_u32       coherence;
} bitraf_state_t;

static bitraf_state_t s_bitraf = { .magic = 0 };

/* ── init ── */
int bitraf_init(rmr_u32 seed) {
    bitraf_state_t *b = &s_bitraf;
    /* zero */
    for (rmr_u8 *p = (rmr_u8*)b; p < (rmr_u8*)b + sizeof(*b); p++) *p = 0;
    b->magic     = BITRAF_MAGIC;
    b->coherence = seed ^ RMR_PHI32;

    /* init slot phi-states via Fibonacci-Rafael */
    for (rmr_u32 i = 0; i < BITRAF_SLOTS; i++) {
        b->slot_phi[i] = rmr_jni_fib_rafael((int)(i & 31)) ^ (rmr_u64)seed;
        b->slot_crc[i] = (rmr_u32)(b->slot_phi[i] & 0xFFFFFFFFu);
    }

    /* activate all 64 slots */
    b->route_mask = 0xFFFFFFFFFFFFFFFFull;
    return RMR_KERNEL_OK;
}

/* ── push event into ring ── */
int bitraf_push(rmr_u32 slot, rmr_u32 value, rmr_u32 flags) {
    if (s_bitraf.magic != BITRAF_MAGIC) return RMR_KERNEL_ERR_INIT;
    if (slot >= BITRAF_SLOTS) return RMR_KERNEL_ERR_STATE;

    bitraf_state_t *b = &s_bitraf;
    rmr_u32 next = (b->ring_head + 1u) % BITRAF_RING_SIZE;
    if (next == b->ring_tail) return -2; /* ring full */

    b->ring[b->ring_head].slot      = slot;
    b->ring[b->ring_head].value     = value;
    b->ring[b->ring_head].timestamp = b->tick++;
    b->ring[b->ring_head].flags     = flags;
    b->ring_head = next;

    /* update slot phi-state */
    b->slot_phi[slot] = rmr_lowlevel_phi_step(b->slot_phi[slot], b->coherence);
    b->slot_crc[slot] = rmr_lowlevel_crc32c_hw(b->slot_crc[slot],
                                                (const rmr_u8*)&value, 4u);
    return RMR_KERNEL_OK;
}

/* ── pop event from ring ── */
int bitraf_pop(bitraf_event_t *out) {
    if (!out || s_bitraf.magic != BITRAF_MAGIC) return -1;
    bitraf_state_t *b = &s_bitraf;
    if (b->ring_head == b->ring_tail) return 0; /* empty */
    *out = b->ring[b->ring_tail];
    b->ring_tail = (b->ring_tail + 1u) % BITRAF_RING_SIZE;
    return 1;
}

/* ── route: select best slot for a given data block ── */
rmr_u32 bitraf_route(const rmr_u8 *data, rmr_u32 len) {
    if (!data || !len || s_bitraf.magic != BITRAF_MAGIC) return 0u;
    bitraf_state_t *b = &s_bitraf;

    /* compute block CRC → map to slot */
    rmr_u32 crc = rmr_lowlevel_crc32c_hw(0u, data, len);
    rmr_u32 slot = (crc ^ (crc >> 6u)) & (BITRAF_SLOTS - 1u);

    /* find active slot (walk forward if slot inactive) */
    for (rmr_u32 i = 0; i < BITRAF_SLOTS; i++) {
        rmr_u32 s = (slot + i) % BITRAF_SLOTS;
        if (b->route_mask & (1ULL << s)) return s;
    }
    return 0u;
}

/* ── slot coherence score ── */
rmr_u32 bitraf_slot_coherence(rmr_u32 slot) {
    if (slot >= BITRAF_SLOTS || s_bitraf.magic != BITRAF_MAGIC) return 0u;
    /* coherence = phi alignment of slot state */
    rmr_u64 phi = s_bitraf.slot_phi[slot] * (rmr_u64)RMR_PHI32;
    return (rmr_u32)(phi >> 48u); /* top 16 bits as score */
}

/* ── ZIPRAF integration: map zipraf cell triple → bitraf slot ── */
rmr_u32 bitraf_zipraf_slot(rmr_u64 offset, rmr_u32 length, rmr_u32 crc32) {
    rmr_u32 h = rmr_lowlevel_fold32(
        (rmr_u32)(offset & 0xFFFFFFFFu),
        (rmr_u32)(offset >> 32u),
        length,
        crc32
    );
    return h & (BITRAF_SLOTS - 1u);
}

/* RAFAELIA-CHANGED-FILES integration: deterministic bit-count helper for BITRAF diagnostics. */
rmr_u32 bitraf_popcount32(rmr_u32 value) {
    rmr_u32 c = 0u;
    while (value) {
        c += value & 1u;
        value >>= 1u;
    }
    return c;
}

rmr_u32 bitraf_route_popcount(void) {
    if (s_bitraf.magic != BITRAF_MAGIC) return 0u;
    rmr_u32 low = (rmr_u32)(s_bitraf.route_mask & 0xFFFFFFFFu);
    rmr_u32 high = (rmr_u32)(s_bitraf.route_mask >> 32u);
    return bitraf_popcount32(low) + bitraf_popcount32(high);
}
