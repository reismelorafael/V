/*
 * rafcode_phi_emit_word.S
 * Núcleo ASM: grava opcode_hex no buffer de saída (palavra 32-bit) e retorna 1 em sucesso.
 * Assinatura C:
 * raf_u32 rafphi_emit_word_asm(raf_u32 opcode_hex, raf_u32 *out_words, raf_u32 cap_words, raf_u32 write_index);
 */

#if defined(__aarch64__)
    .text
    .align 2
    .global rafphi_emit_word_asm
    .type rafphi_emit_word_asm, %function
rafphi_emit_word_asm:
    /* w0=opcode_hex, x1=out_words, w2=cap_words, w3=write_index */
    cmp     w3, w2
    b.hs    1f
    add     x4, x1, w3, uxtw #2
    str     w0, [x4]
    mov     w0, #1
    ret
1:
    mov     w0, #0
    ret
#endif

#if defined(__x86_64__)
    .text
    .global rafphi_emit_word_asm
    .type rafphi_emit_word_asm, @function
rafphi_emit_word_asm:
    /* edi=opcode_hex, rsi=out_words, edx=cap_words, ecx=write_index */
    cmpl    %edx, %ecx
    jae     .Lfail_x64
    movl    %edi, (%rsi,%rcx,4)
    movl    $1, %eax
    ret
.Lfail_x64:
    xorl    %eax, %eax
    ret
#endif
