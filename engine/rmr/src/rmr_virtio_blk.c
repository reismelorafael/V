#define _XOPEN_SOURCE 700
#include "rmr_virtio_blk.h"

#include "rmr_cycles.h"
#include "zero_compat.h"

#include <fcntl.h>
#include <unistd.h>

static u64 rmr_now_ns(void) {
  return RmR_ReadCycles();
}

int RmR_VirtioBlk_Open(RmR_VirtioBlkDev *dev, const char *path, const RmR_HW_Info *hw) {
  int flags = O_RDWR;
  if (!dev || !path) return -1;
#if defined(O_DIRECT)
  flags |= O_DIRECT;
#endif
  rmr_mem_set(dev, 0u, sizeof(*dev));
  dev->fd = open(path, flags, 0644);
  if (dev->fd < 0) {
    dev->fd = open(path, O_RDWR, 0644);
    if (dev->fd < 0) return -1;
  }
  dev->sector_size = RMR_VIRTIO_SECTOR_SIZE;
  dev->align_bytes = (hw && hw->align_bytes) ? hw->align_bytes : RMR_VIRTIO_ALIGN;
  dev->window_start_ns = rmr_now_ns();
  return 0;
}

int RmR_VirtioBlk_Process(RmR_VirtioBlkDev *dev, const RmR_VirtioReq *req) {
  off_t offset;
  size_t bytes;
  ssize_t rc;
  if (!dev || !req || dev->fd < 0 || !req->host_buffer) return -1;
  offset = (off_t)(req->sector * dev->sector_size);
  bytes = (size_t)req->n_sectors * dev->sector_size;

  if (req->write) {
    rc = pwrite(dev->fd, req->host_buffer, bytes, offset);
    if (rc < 0 || (size_t)rc != bytes) return -1;
    dev->write_bytes_total += (u64)bytes;
    dev->write_iops += 1u;
  } else {
    rc = pread(dev->fd, req->host_buffer, bytes, offset);
    if (rc < 0 || (size_t)rc != bytes) return -1;
    dev->read_bytes_total += (u64)bytes;
    dev->read_iops += 1u;
  }

  dev->window_ops += 1u;
  return 0;
}

int RmR_VirtioBlk_Flush(RmR_VirtioBlkDev *dev) {
  if (!dev || dev->fd < 0) return -1;
  return fsync(dev->fd);
}

u32 RmR_VirtioBlk_CurrentIOPS(const RmR_VirtioBlkDev *dev) {
  u64 now;
  u64 elapsed;
  if (!dev) return 0u;
  now = rmr_now_ns();
  elapsed = (now > dev->window_start_ns) ? (now - dev->window_start_ns) : 1u;
  if (!elapsed) return 0u;
  return (u32)((dev->window_ops * 1000000000ull) / elapsed);
}
