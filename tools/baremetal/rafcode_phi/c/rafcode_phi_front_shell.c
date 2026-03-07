#include "rafcode_phi_abi.h"

static raf_u32 rafphi_ascii_eq(const char *a, raf_u32 a_len, const char *b) {
  raf_u32 i = 0u;
  while (i < a_len && b[i] != '\0') {
    if (a[i] != b[i]) {
      return 0u;
    }
    i++;
  }
  return (i == a_len && b[i] == '\0') ? 1u : 0u;
}

raf_u32 rafphi_encode_token_to_hex(const char *token, raf_u32 token_len, raf_u32 *out_hex) {
  if (!token || token_len == 0u || !out_hex) {
    return 0u;
  }

  if (rafphi_ascii_eq(token, token_len, "NOP")) {
    *out_hex = RAFPHI_OP_NOP;
    return 1u;
  }
  if (rafphi_ascii_eq(token, token_len, "RET")) {
    *out_hex = RAFPHI_OP_RET_A64;
    return 1u;
  }
  if (rafphi_ascii_eq(token, token_len, "BRK")) {
    *out_hex = RAFPHI_OP_BRK_A64;
    return 1u;
  }
  if (rafphi_ascii_eq(token, token_len, "HLT")) {
    *out_hex = RAFPHI_OP_HLT_A64;
    return 1u;
  }

  return 0u;
}

/* CRC32C simplificado autoral (polinômio castagnoli refletido). */
static raf_u32 rafphi_crc32c_u32(raf_u32 crc, raf_u32 v) {
  raf_u32 x = crc ^ v;
  raf_u32 i;
  for (i = 0u; i < 32u; i++) {
    raf_u32 lsb = x & 1u;
    x >>= 1u;
    if (lsb) {
      x ^= 0x82F63B78u;
    }
  }
  return x;
}

rafphi_emit_stats_t rafphi_emit_block_hex(const char **tokens, const raf_u32 *token_lens, raf_u32 token_count,
                                          raf_u32 *out_words, raf_u32 cap_words) {
  rafphi_emit_stats_t stats;
  stats.accepted = 0u;
  stats.rejected = 0u;
  stats.crc32c = 0u;

  if (!tokens || !token_lens || !out_words || cap_words == 0u) {
    return stats;
  }

  raf_u32 write_index = 0u;
  raf_u32 i;
  for (i = 0u; i < token_count; i++) {
    raf_u32 hex = 0u;
    if (!rafphi_encode_token_to_hex(tokens[i], token_lens[i], &hex)) {
      stats.rejected++;
      continue;
    }

    raf_u32 written = rafphi_emit_word_asm(hex, out_words, cap_words, write_index);
    if (written == 0u) {
      stats.rejected++;
      continue;
    }

    write_index += written;
    stats.accepted++;
    stats.crc32c = rafphi_crc32c_u32(stats.crc32c, hex);
  }

  return stats;
}
