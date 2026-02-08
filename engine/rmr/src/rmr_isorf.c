/* rmr_isorf.c - ISOraf: armazenamento lógico denso com físico esparso (sem compressão) */
#include "rmr_isorf.h"

static void RmR_ZeroWords(u64 *p, u32 n){
  for(u32 i=0;i<n;i++) p[i] = 0u;
}

void RmR_ISOraf_Init(
  RmR_ISOraf_Store *st,
  RmR_ISOraf_Page *pages,
  u32 page_count,
  u64 *data_words,
  u32 data_word_count,
  u32 page_bits
){
  if(!st || !pages || !data_words) return;
  st->pages = pages;
  st->page_count = page_count;
  st->data_words = data_words;
  st->data_word_count = data_word_count;
  st->data_word_used = 0u;
  st->page_bits = (page_bits == 0u) ? 4096u : page_bits;
  for(u32 i=0;i<page_count;i++){
    pages[i].base_bit = 0u;
    pages[i].word_offset = 0u;
    pages[i].word_count = 0u;
    pages[i].used = 0u;
  }
  RmR_ZeroWords(data_words, data_word_count);
}

static RmR_ISOraf_Page *RmR_ISOraf_Find(RmR_ISOraf_Store *st, u64 base_bit){
  for(u32 i=0;i<st->page_count;i++){
    if(st->pages[i].used && st->pages[i].base_bit == base_bit) return &st->pages[i];
  }
  return (RmR_ISOraf_Page*)0;
}

static RmR_ISOraf_Page *RmR_ISOraf_Alloc(RmR_ISOraf_Store *st, u64 base_bit){
  u32 words = (st->page_bits + 63u) >> 6;
  if(st->data_word_used + words > st->data_word_count) return (RmR_ISOraf_Page*)0;
  for(u32 i=0;i<st->page_count;i++){
    if(!st->pages[i].used){
      st->pages[i].used = 1u;
      st->pages[i].base_bit = base_bit;
      st->pages[i].word_offset = st->data_word_used;
      st->pages[i].word_count = words;
      RmR_ZeroWords(&st->data_words[st->data_word_used], words);
      st->data_word_used += words;
      return &st->pages[i];
    }
  }
  return (RmR_ISOraf_Page*)0;
}

u8 RmR_ISOraf_SetBit(RmR_ISOraf_Store *st, u64 bit_index, u8 value){
  if(!st) return 0u;
  u64 base_bit = (bit_index / (u64)st->page_bits) * (u64)st->page_bits;
  RmR_ISOraf_Page *p = RmR_ISOraf_Find(st, base_bit);
  if(!p){
    if(value == 0u) return 1u;
    p = RmR_ISOraf_Alloc(st, base_bit);
    if(!p) return 0u;
  }
  u64 bit_in_page = bit_index - base_bit;
  u32 word = (u32)(bit_in_page >> 6);
  u32 bit = (u32)(bit_in_page & 63u);
  if(word >= p->word_count) return 0u;
  u32 w = p->word_offset + word;
  if(value) st->data_words[w] |= (1ULL << bit);
  else st->data_words[w] &= ~(1ULL << bit);
  return 1u;
}

u8 RmR_ISOraf_GetBit(const RmR_ISOraf_Store *st, u64 bit_index){
  if(!st) return 0u;
  u64 base_bit = (bit_index / (u64)st->page_bits) * (u64)st->page_bits;
  for(u32 i=0;i<st->page_count;i++){
    if(st->pages[i].used && st->pages[i].base_bit == base_bit){
      u64 bit_in_page = bit_index - base_bit;
      u32 word = (u32)(bit_in_page >> 6);
      u32 bit = (u32)(bit_in_page & 63u);
      if(word >= st->pages[i].word_count) return 0u;
      u32 w = st->pages[i].word_offset + word;
      return (u8)((st->data_words[w] >> bit) & 1u);
    }
  }
  return 0u;
}

void RmR_ISOraf_StatsGet(const RmR_ISOraf_Store *st, RmR_ISOraf_Stats *out){
  if(!st || !out) return;
  u32 used = 0u;
  for(u32 i=0;i<st->page_count;i++){
    if(st->pages[i].used) used++;
  }
  out->pages_used = used;
  out->physical_bits = (u64)st->data_word_used * 64u;
  out->logical_bits = (u64)used * (u64)st->page_bits;
}
