/* rmr_isorf.h - ISOraf: armazenamento lógico denso com físico esparso (sem compressão) */
#ifndef RMR_ISORF_H
#define RMR_ISORF_H

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

typedef struct {
  u64 base_bit;
  u32 word_offset;
  u32 word_count;
  u8  used;
} RmR_ISOraf_Page;

typedef struct {
  RmR_ISOraf_Page *pages;
  u32 page_count;
  u64 *data_words;
  u32 data_word_count;
  u32 data_word_used;
  u32 page_bits;
} RmR_ISOraf_Store;

typedef struct {
  u64 logical_bits;
  u64 physical_bits;
  u32 pages_used;
} RmR_ISOraf_Stats;

void RmR_ISOraf_Init(
  RmR_ISOraf_Store *st,
  RmR_ISOraf_Page *pages,
  u32 page_count,
  u64 *data_words,
  u32 data_word_count,
  u32 page_bits
);

u8 RmR_ISOraf_SetBit(RmR_ISOraf_Store *st, u64 bit_index, u8 value);
u8 RmR_ISOraf_GetBit(const RmR_ISOraf_Store *st, u64 bit_index);
void RmR_ISOraf_StatsGet(const RmR_ISOraf_Store *st, RmR_ISOraf_Stats *out);

#endif
