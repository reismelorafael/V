#ifndef RAFCODE_PHI_ABI_H
#define RAFCODE_PHI_ABI_H

/*
 * RAFCODE❤️PHI ABI
 * C (casca) -> ASM (núcleo) com saída em palavras hex determinísticas.
 */

/* Evita dependência de libc/stdint: tipos fixos locais. */
typedef unsigned char      raf_u8;
typedef unsigned short     raf_u16;
typedef unsigned int       raf_u32;
typedef unsigned long long raf_u64;

typedef enum {
  RAFPHI_ARCH_UNKNOWN = 0,
  RAFPHI_ARCH_AARCH64 = 1,
  RAFPHI_ARCH_X86_64  = 2
} rafphi_arch_t;

typedef enum {
  RAFPHI_OP_NOP     = 0x00000000u,
  RAFPHI_OP_RET_A64 = 0xD65F03C0u,
  RAFPHI_OP_BRK_A64 = 0xD4200000u,
  RAFPHI_OP_HLT_A64 = 0xD4400000u
} rafphi_opcode_t;

typedef struct {
  raf_u32 opcode_hex;
  raf_u32 flags;
} rafphi_emit_word_t;

typedef struct {
  raf_u32 accepted;
  raf_u32 rejected;
  raf_u32 crc32c;
} rafphi_emit_stats_t;

/* Hook ASM: serializa uma palavra hex para buffer de saída. */
extern raf_u32 rafphi_emit_word_asm(raf_u32 opcode_hex, raf_u32 *out_words, raf_u32 cap_words, raf_u32 write_index);

/* Casca C: parser mínimo de mnemônicos autorais para hex. */
raf_u32 rafphi_encode_token_to_hex(const char *token, raf_u32 token_len, raf_u32 *out_hex);

/* Casca C: emite bloco de tokens em formato hex determinístico. */
rafphi_emit_stats_t rafphi_emit_block_hex(const char **tokens, const raf_u32 *token_lens, raf_u32 token_count,
                                          raf_u32 *out_words, raf_u32 cap_words);

#endif
