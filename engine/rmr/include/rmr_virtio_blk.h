#ifndef RMR_VIRTIO_BLK_H
#define RMR_VIRTIO_BLK_H

#include "rmr_hw_detect.h"
#include "rmr_types.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_VIRTIO_QUEUE_SIZE 256u
#define RMR_VIRTIO_SECTOR_SIZE 512u
#define RMR_VIRTIO_ALIGN 4096u

typedef struct {
  u64 sector;
  u32 n_sectors;
  u8 write;
  u8 *host_buffer;
} RmR_VirtioReq;

typedef struct {
  int fd;
  u32 sector_size;
  u64 total_sectors;
  u32 align_bytes;
  u64 read_bytes_total;
  u64 write_bytes_total;
  u64 read_iops;
  u64 write_iops;
  u64 window_start_ns;
  u64 window_ops;
} RmR_VirtioBlkDev;

int RmR_VirtioBlk_Open(RmR_VirtioBlkDev *dev, const char *path, const RmR_HW_Info *hw);
int RmR_VirtioBlk_Process(RmR_VirtioBlkDev *dev, const RmR_VirtioReq *req);
int RmR_VirtioBlk_Flush(RmR_VirtioBlkDev *dev);
u32 RmR_VirtioBlk_CurrentIOPS(const RmR_VirtioBlkDev *dev);

#ifdef __cplusplus
}
#endif

#endif
