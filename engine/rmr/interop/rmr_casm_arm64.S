/* ═══════════════════════════════════════════════════════
   rmr_casm_arm64.S — RMR CASM BRIDGE ARM64 (BAREMETAL)
   Ficheiro FALTANDO no zip original — adicionado aqui.
   ψ = intenção de metal | Verbo em ARM64
   Compatível: ARMv8-A AArch64 | QEMU virt | RPi4
   ═══════════════════════════════════════════════════════ */

.arch armv8-a+crc
.text

/* ─── rmr_casm_bridge_marker ──────────────────────────────
   Retorna 0x41534D41 ("ASM A" = ARM64 marker)
   Equivalente x86: 0x41534D31 ("ASM 1")
   Detectado em vectra_core_accel.c: VECTRA_HAS_CASM_MARKER=1
   ────────────────────────────────────────────────────── */
.global rmr_casm_bridge_marker
.type   rmr_casm_bridge_marker, %function
rmr_casm_bridge_marker:
    mov     w0, #0x4D41        /* "MA" low 16 bits */
    movk    w0, #0x4153, lsl #16   /* "SA" high 16 bits → "ASMA" */
    ret
.size rmr_casm_bridge_marker, .-rmr_casm_bridge_marker

/* ─── rmr_casm_xor_fold32_arm64 ──────────────────────────
   Parâmetros: x0=ptr, x1=len (bytes)
   Retorna: w0 = XOR fold 32-bit de todos os bytes
   BUG FIX: loop tail sem guard de len==0 no x86 original;
            arm64 reescrito com bounds claros.
   ────────────────────────────────────────────────────── */
.global rmr_casm_xor_fold32_arm64
.type   rmr_casm_xor_fold32_arm64, %function
rmr_casm_xor_fold32_arm64:
    cbz     x0, .Lxor_zero      /* ptr == NULL → 0 */
    cbz     x1, .Lxor_zero      /* len == 0 → 0 */

    mov     w2, #0              /* accumulador = 0 */
    mov     x3, #0              /* índice i = 0 */

    /* Bloco de 4 bytes enquanto i+4 <= len */
.Lxor_word:
    add     x4, x3, #4
    cmp     x4, x1
    b.hi    .Lxor_byte          /* menos de 4 bytes restantes */
    ldr     w4, [x0, x3]        /* carrega 4 bytes (little-endian) */
    eor     w2, w2, w4
    add     x3, x3, #4
    b       .Lxor_word

    /* Bytes individuais restantes */
.Lxor_byte:
    cmp     x3, x1
    b.ge    .Lxor_done
    ldrb    w4, [x0, x3]
    /* posição no word: (i & 3) * 8 bits de shift */
    and     w5, w3, #3
    lsl     w5, w5, #3
    lsl     w4, w4, w5
    eor     w2, w2, w4
    add     x3, x3, #1
    b       .Lxor_byte

.Lxor_done:
    mov     w0, w2
    ret

.Lxor_zero:
    mov     w0, #0
    ret
.size rmr_casm_xor_fold32_arm64, .-rmr_casm_xor_fold32_arm64

/* ─── rmr_casm_phi_step_arm64 ────────────────────────────
   R(t+1) = R(t) × PHI32 (Knuth multiplicative hash)
   x0=state_lo, x1=state_hi, w2=coherence
   Retorna: x0 = novo state (64-bit)
   ────────────────────────────────────────────────────── */
.global rmr_casm_phi_step_arm64
.type   rmr_casm_phi_step_arm64, %function
rmr_casm_phi_step_arm64:
    /* PHI32 = 0x9E3779B9 */
    mov     w3, #0x79B9
    movk    w3, #0x9E37, lsl #16
    /* state = (x0 | x1<<32) × PHI32 */
    orr     x0, x0, x1, lsl #32
    mul     x0, x0, x3
    /* mistura coerência: state ^= coherence × PHI32 */
    uxtw    x2, w2
    mul     x2, x2, x3
    eor     x0, x0, x2
    /* garante não-zero (singularidade) */
    cbnz    x0, .Lphi_ok
    mov     x0, #1
.Lphi_ok:
    ret
.size rmr_casm_phi_step_arm64, .-rmr_casm_phi_step_arm64

/* ─── rmr_casm_crc32c_byte_arm64 ─────────────────────────
   HW CRC32C via instrução ARM8.0-CRC: crc32cb
   w0=crc, w1=byte → w0=crc_out
   Fallback SW se instrução não disponível (QEMU sem CRC ext)
   ────────────────────────────────────────────────────── */
.global rmr_casm_crc32c_byte_arm64
.type   rmr_casm_crc32c_byte_arm64, %function
rmr_casm_crc32c_byte_arm64:
#if defined(__ARM_FEATURE_CRC32)
    crc32cb w0, w0, w1
    ret
#else
    /* SW CRC32C Castagnoli — poly 0x82F63B78 */
    eor     w0, w0, w1, uxtb
    mov     w2, #8              /* 8 bits */
.Lcrc_loop:
    tst     w0, #1
    lsr     w0, w0, #1
    b.eq    .Lcrc_skip
    /* poly = 0x82F63B78 */
    mov     w3, #0x3B78
    movk    w3, #0x82F6, lsl #16
    eor     w0, w0, w3
.Lcrc_skip:
    subs    w2, w2, #1
    b.ne    .Lcrc_loop
    ret
#endif
.size rmr_casm_crc32c_byte_arm64, .-rmr_casm_crc32c_byte_arm64

/* Stack não-executável (ABI padrão) */
.section .note.GNU-stack,"",@progbits

.global rmr_spiral_arm64
.type   rmr_spiral_arm64, %function
rmr_spiral_arm64:
    mov     x1, #65536
    cbz     x0, .Lspiral_done
    mov     x2, #56756
.Lspiral_loop:
    mul     x3, x1, x2
    lsr     x1, x3, #16
    subs    x0, x0, #1
    bne     .Lspiral_loop
.Lspiral_done:
    mov     x0, x1
    ret
.size rmr_spiral_arm64, .-rmr_spiral_arm64

.global rmr_fib_rafael_step_arm64
.type   rmr_fib_rafael_step_arm64, %function
rmr_fib_rafael_step_arm64:
    mov     x1, #56756
    mul     x2, x0, x1
    lsr     x2, x2, #16
    mov     x3, #6752
    movk    x3, #3, lsl #16
    subs    x0, x2, x3
    csel    x0, xzr, x0, lo
    ret
.size rmr_fib_rafael_step_arm64, .-rmr_fib_rafael_step_arm64
