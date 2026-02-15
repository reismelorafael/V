package com.vectras.vm.rafaelia;

import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HD Cache MVP - HD as memory + Block Cache L1/L2/L3 + TTL + Retries + Drops + Harmonic Scheduler
 * 
 * <p>Pure bytes, append-only store, deterministic metadata.
 * Implements multi-tier caching with byte budgets and harmonic scheduling.
 * 
 * <p>Key features:
 * <ul>
 *   <li>L1/L2/L3 tiered cache with configurable byte budgets</li>
 *   <li>Append-only block store with alignment and integrity verification</li>
 *   <li>TTL (Time-to-Live) for event expiration</li>
 *   <li>Retry mechanism for failed operations</li>
 *   <li>Drop control for queue overflow</li>
 *   <li>Harmonic scheduler for layer-based scheduling</li>
 * </ul>
 * 
 * @author Rafaelia
 */
public final class HdCacheMvp {
    private static final String TAG = "HdCacheMvp";

    // ========== Configuration ==========
    /** Block alignment on disk (page size) */
    public static final int BLOCK_ALIGN = 4096;
    /** Maximum payload per block (256KB) */
    public static final int MAX_BLOCK_BYTES = 256 * 1024;
    
    /** L1 cache budget (2MB) - hottest, fastest */
    public static final long L1_BUDGET = 2L * 1024 * 1024;
    /** L2 cache budget (16MB) - warm */
    public static final long L2_BUDGET = 16L * 1024 * 1024;
    /** L3 cache budget (128MB) - large, slowest RAM tier */
    public static final long L3_BUDGET = 128L * 1024 * 1024;
    
    /** Default TTL in seconds */
    public static final int DEFAULT_TTL_SEC = 30;
    /** Maximum retry attempts */
    public static final int MAX_RETRIES = 3;
    /** Drop threshold for queue overflow */
    public static final int DROP_IF_QUEUE_GT = 10_000;
    
    /** Harmonic frequencies for scheduling (symbolic -> scheduling weights) */
    public static final int[] HARMONICS = {12, 144, 288, 144000, 777, 555, 963, 999};
    
    /** Nanoseconds per second for time conversion */
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    
    // Block format constants
    private static final byte[] MAGIC = {'R', 'B', 'F', '0'};
    private static final byte VERSION = 1;
    private static final int HEADER_SIZE = 4 + 1 + 1 + 2 + 4 + 32; // magic + ver + flags + resv + paylen + hash

    private HdCacheMvp() {
        // Utility class - prevent instantiation
    }

    // ========== Event Key ==========
    /**
     * Unique identifier for an event within a layer.
     */
    public static final class EventKey {
        private final String layer;
        private final String eid;

        public EventKey(String layer, String eid) {
            this.layer = layer;
            this.eid = eid;
        }

        public String getLayer() {
            return layer;
        }

        public String getEid() {
            return eid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EventKey eventKey = (EventKey) o;
            return layer.equals(eventKey.layer) && eid.equals(eventKey.eid);
        }

        @Override
        public int hashCode() {
            return 31 * layer.hashCode() + eid.hashCode();
        }

        @Override
        public String toString() {
            return "EventKey{layer='" + layer + "', eid='" + eid + "'}";
        }
    }

    // ========== Event Status ==========
    /**
     * Status of an event in its lifecycle.
     */
    public enum EventStatus {
        NEW,
        HOT,
        COLD,
        EXPIRED,
        DROPPED,
        DONE,
        RETRYING
    }

    // ========== Event Metadata ==========
    /**
     * Metadata for an event including TTL, retries, and disk location.
     */
    public static final class EventMeta {
        private final String layer;
        private final String eid;
        private final long createdNs;
        private final int ttlSec;
        private int retriesLeft;
        private final int payloadLen;
        private final String payloadHash;
        private final long diskOff;
        private final int diskLen;
        private EventStatus status;

        public EventMeta(String layer, String eid, long createdNs, int ttlSec,
                         int retriesLeft, int payloadLen, String payloadHash,
                         long diskOff, int diskLen, EventStatus status) {
            this.layer = layer;
            this.eid = eid;
            this.createdNs = createdNs;
            this.ttlSec = ttlSec;
            this.retriesLeft = retriesLeft;
            this.payloadLen = payloadLen;
            this.payloadHash = payloadHash;
            this.diskOff = diskOff;
            this.diskLen = diskLen;
            this.status = status;
        }

        public String getLayer() { return layer; }
        public String getEid() { return eid; }
        public long getCreatedNs() { return createdNs; }
        public int getTtlSec() { return ttlSec; }
        public int getRetriesLeft() { return retriesLeft; }
        public void setRetriesLeft(int retriesLeft) { this.retriesLeft = retriesLeft; }
        public int getPayloadLen() { return payloadLen; }
        public String getPayloadHash() { return payloadHash; }
        public long getDiskOff() { return diskOff; }
        public int getDiskLen() { return diskLen; }
        public EventStatus getStatus() { return status; }
        public void setStatus(EventStatus status) { this.status = status; }

        /**
         * Converts metadata to JSON string for index storage.
         */
        public String toJson() {
            return String.format(
                "{\"layer\":\"%s\",\"eid\":\"%s\",\"createdNs\":%d,\"ttlSec\":%d," +
                "\"retriesLeft\":%d,\"payloadLen\":%d,\"payloadHash\":\"%s\"," +
                "\"diskOff\":%d,\"diskLen\":%d,\"status\":\"%s\"}",
                layer, eid, createdNs, ttlSec, retriesLeft, payloadLen,
                payloadHash, diskOff, diskLen, status.name()
            );
        }
    }

    // ========== Block Store ==========
    /**
     * Append-only block store with alignment and integrity verification.
     * 
     * <p>Block format:
     * <pre>
     * [MAGIC 4B][VER 1B][FLAGS 1B][RESV 2B][PAYLEN 4B][HASH 32B][PAYLOAD][PADDING]
     * </pre>
     */
    public static final class BlockStore implements Closeable {
        private final RandomAccessFile raf;
        private final FileChannel channel;
        private final File indexFile;
        private final ReentrantLock lock = new ReentrantLock();
        private long size;

        /**
         * Creates or opens a block store.
         * 
         * @param storePath path to the data file
         * @param indexPath path to the index file
         * @throws IOException if file operations fail
         */
        public BlockStore(File storePath, File indexPath) throws IOException {
            File parent = storePath.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            
            this.raf = new RandomAccessFile(storePath, "rw");
            this.channel = raf.getChannel();
            this.indexFile = indexPath;
            this.size = channel.size();
            
            // Ensure index exists
            if (!indexPath.exists()) {
                indexPath.createNewFile();
            }
        }

        /**
         * Appends a block to the store.
         * 
         * @param payload the data to store
         * @return array of [offset, length, hash] where hash is hex-encoded
         * @throws IOException if write fails
         * @throws IllegalArgumentException if payload exceeds MAX_BLOCK_BYTES
         */
        public Object[] appendBlock(byte[] payload) throws IOException {
            if (payload.length > MAX_BLOCK_BYTES) {
                throw new IllegalArgumentException(
                    "payload too big: " + payload.length + " > " + MAX_BLOCK_BYTES);
            }

            byte[] hash = sha256(payload);
            String hashHex = bytesToHex(hash);

            // Build header: MAGIC(4) + VER(1) + FLAGS(1) + RESV(2) + PAYLEN(4) + HASH(32)
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            header.order(ByteOrder.BIG_ENDIAN);
            header.put(MAGIC);
            header.put(VERSION);
            header.put((byte) 0); // flags
            header.putShort((short) 0); // reserved
            header.putInt(payload.length);
            header.put(hash);

            // Calculate total size with padding
            int rawLen = HEADER_SIZE + payload.length;
            int paddedLen = alignUp(rawLen, BLOCK_ALIGN);
            
            ByteBuffer block = ByteBuffer.allocate(paddedLen);
            block.put(header.array());
            block.put(payload);
            // Remaining bytes are already zero (padding)
            block.flip();

            lock.lock();
            try {
                long offset = size;
                channel.position(offset);
                while (block.hasRemaining()) {
                    channel.write(block);
                }
                channel.force(true);
                size += paddedLen;
                return new Object[]{offset, paddedLen, hashHex};
            } finally {
                lock.unlock();
            }
        }

        /**
         * Reads a block from the store.
         * 
         * @param offset the offset where the block starts
         * @return the payload data
         * @throws IOException if read fails or integrity check fails
         */
        public byte[] readBlock(long offset) throws IOException {
            lock.lock();
            try {
                ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
                header.order(ByteOrder.BIG_ENDIAN);
                channel.position(offset);
                int bytesRead = channel.read(header);
                if (bytesRead < HEADER_SIZE) {
                    throw new IOException("Failed to read block header");
                }
                header.flip();

                // Verify magic
                byte[] magic = new byte[4];
                header.get(magic);
                for (int i = 0; i < 4; i++) {
                    if (magic[i] != MAGIC[i]) {
                        throw new IOException("bad block header: invalid magic");
                    }
                }

                byte ver = header.get();
                if (ver != VERSION) {
                    throw new IOException("bad block header: version mismatch");
                }

                header.get(); // flags (ignored)
                header.getShort(); // reserved (ignored)
                int payloadLen = header.getInt();
                byte[] storedHash = new byte[32];
                header.get(storedHash);

                // Read payload
                ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
                int payloadRead = channel.read(payloadBuf);
                if (payloadRead < payloadLen) {
                    throw new IOException("Failed to read payload");
                }
                byte[] payload = payloadBuf.array();

                // Verify hash
                byte[] computedHash = sha256(payload);
                for (int i = 0; i < 32; i++) {
                    if (storedHash[i] != computedHash[i]) {
                        throw new IOException("hash mismatch (disk corruption or wrong offset)");
                    }
                }

                return payload;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Writes event metadata to the index file.
         * 
         * @param meta the metadata to write
         * @throws IOException if write fails
         */
        public void writeIndex(EventMeta meta) throws IOException {
            lock.lock();
            try (java.io.FileWriter fw = new java.io.FileWriter(indexFile, true)) {
                fw.write(meta.toJson() + "\n");
                fw.flush();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            lock.lock();
            try {
                channel.close();
                raf.close();
            } finally {
                lock.unlock();
            }
        }

        public long getSize() {
            return size;
        }
    }

    // ========== Tier Cache ==========
    /**
     * Single-tier cache with byte budget and FIFO eviction.
     */
    public static final class TierCache {
        private final long budget;
        private long used;
        private final Map<EventKey, byte[]> items;
        private final Deque<EventKey> queue;
        private final ReentrantLock lock = new ReentrantLock();

        public TierCache(long budget) {
            this.budget = budget;
            this.used = 0;
            this.items = new HashMap<>();
            this.queue = new ArrayDeque<>();
        }

        /**
         * Puts an item into the cache, evicting if necessary.
         */
        public void put(EventKey k, byte[] v) {
            lock.lock();
            try {
                if (items.containsKey(k)) {
                    // Replace existing
                    used -= items.get(k).length;
                    items.put(k, v);
                    used += v.length;
                } else {
                    items.put(k, v);
                    queue.addLast(k);
                    used += v.length;
                }
                evict();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Gets an item from the cache.
         */
        public byte[] get(EventKey k) {
            lock.lock();
            try {
                return items.get(k);
            } finally {
                lock.unlock();
            }
        }

        /**
         * Removes and returns the oldest item.
         */
        public Map.Entry<EventKey, byte[]> popOldest() {
            lock.lock();
            try {
                while (!queue.isEmpty()) {
                    EventKey k = queue.pollFirst();
                    byte[] v = items.remove(k);
                    if (v != null) {
                        used -= v.length;
                        return new java.util.AbstractMap.SimpleEntry<>(k, v);
                    }
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        private void evict() {
            while (used > budget) {
                if (popOldest() == null) {
                    break;
                }
            }
        }

        public long getUsed() {
            lock.lock();
            try {
                return used;
            } finally {
                lock.unlock();
            }
        }

        public long getBudget() {
            return budget;
        }
    }

    // ========== L1/L2/L3 Cache ==========
    /**
     * Three-level cache with automatic promotion and demotion.
     */
    public static final class L123Cache {
        private final TierCache l1;
        private final TierCache l2;
        private final TierCache l3;

        public L123Cache() {
            this(L1_BUDGET, L2_BUDGET, L3_BUDGET);
        }

        public L123Cache(long l1Budget, long l2Budget, long l3Budget) {
            this.l1 = new TierCache(l1Budget);
            this.l2 = new TierCache(l2Budget);
            this.l3 = new TierCache(l3Budget);
        }

        /**
         * Puts an item directly into L1 (hot cache).
         */
        public void putHot(EventKey k, byte[] v) {
            l1.put(k, v);
        }

        /**
         * Gets an item, promoting through cache tiers if found.
         */
        public byte[] get(EventKey k) {
            byte[] v = l1.get(k);
            if (v != null) {
                return v;
            }
            v = l2.get(k);
            if (v != null) {
                l1.put(k, v); // Promote to L1
                return v;
            }
            v = l3.get(k);
            if (v != null) {
                l2.put(k, v); // Promote to L2
                return v;
            }
            return null;
        }

        /**
         * Runs demotion cycle to move overflow down tiers.
         */
        public void demoteCycle() {
            // Move overflow from L1 -> L2
            while (l1.getUsed() > l1.getBudget()) {
                Map.Entry<EventKey, byte[]> item = l1.popOldest();
                if (item == null) break;
                l2.put(item.getKey(), item.getValue());
            }
            // Move overflow from L2 -> L3
            while (l2.getUsed() > l2.getBudget()) {
                Map.Entry<EventKey, byte[]> item = l2.popOldest();
                if (item == null) break;
                l3.put(item.getKey(), item.getValue());
            }
            // Evict from L3 if over budget
            while (l3.getUsed() > l3.getBudget()) {
                if (l3.popOldest() == null) break;
            }
        }

        public TierCache getL1() { return l1; }
        public TierCache getL2() { return l2; }
        public TierCache getL3() { return l3; }
    }

    // ========== Harmonic Scheduler ==========
    /**
     * Scheduler that assigns weights based on layer frequencies.
     * Higher frequency layers get more processing slots.
     */
    public static final class HarmonicScheduler {
        private final int[] harmonics;
        private long tick;

        public HarmonicScheduler(int[] harmonics) {
            this.harmonics = harmonics.clone();
            java.util.Arrays.sort(this.harmonics);
            this.tick = 0;
        }

        /**
         * Calculates weight for a frequency value.
         */
        public int weight(int freq) {
            if (freq >= 100000) return 64;
            if (freq >= 1000) return 16;
            if (freq >= 500) return 8;
            if (freq >= 100) return 4;
            return 2;
        }

        /**
         * Generates the layer schedule for the next cycle.
         */
        public List<String> nextCycleLayers(Map<String, Integer> layerFreqs) {
            tick++;
            List<String> layers = new ArrayList<>(layerFreqs.keySet());
            List<String> out = new ArrayList<>();

            // Pulse direction: alternating up/down
            boolean up = ((tick / 8) % 2) == 0;
            
            // Sort by frequency
            if (up) {
                Collections.sort(layers, (a, b) -> layerFreqs.get(b) - layerFreqs.get(a));
            } else {
                Collections.sort(layers, (a, b) -> layerFreqs.get(a) - layerFreqs.get(b));
            }

            // Build schedule with weights
            for (String layer : layers) {
                int w = weight(layerFreqs.get(layer));
                for (int i = 0; i < w; i++) {
                    out.add(layer);
                }
            }

            // Deterministic rotation
            if (!out.isEmpty()) {
                int rot = (int) (tick % out.size());
                List<String> rotated = new ArrayList<>();
                rotated.addAll(out.subList(rot, out.size()));
                rotated.addAll(out.subList(0, rot));
                return rotated;
            }
            return out;
        }

        public long getTick() {
            return tick;
        }
    }

    // ========== Engine ==========
    /**
     * Main engine: event ingest -> store -> caches -> process -> TTL/retry/drop.
     */
    public static final class Engine implements Closeable {
        private static final class PermanentProcessingException extends Exception {
            PermanentProcessingException(String message) {
                super(message);
            }
        }

        private final BlockStore store;
        private final L123Cache cache;
        private final HarmonicScheduler scheduler;
        private final Map<String, Integer> layerFreqs;
        private final Map<String, Deque<EventKey>> queues;
        private final Map<EventKey, EventMeta> meta;
        private final AtomicLong eventCounter = new AtomicLong(0);
        private final ReentrantLock lock = new ReentrantLock();

        public Engine(File storePath, File indexPath) throws IOException {
            this.store = new BlockStore(storePath, indexPath);
            this.cache = new L123Cache();
            this.scheduler = new HarmonicScheduler(HARMONICS);
            this.layerFreqs = new ConcurrentHashMap<>();
            this.queues = new ConcurrentHashMap<>();
            this.meta = new ConcurrentHashMap<>();
        }

        /**
         * Adds a new layer with specified frequency.
         */
        public void addLayer(String layer, int freq) {
            layerFreqs.put(layer, freq);
            queues.put(layer, new ArrayDeque<>());
        }

        /**
         * Ingests a new event into the system.
         */
        public EventKey ingest(String layer, byte[] payload, int ttlSec) throws IOException {
            if (!layerFreqs.containsKey(layer)) {
                throw new IllegalArgumentException("unknown layer: " + layer);
            }

            // Use UUID for better uniqueness across restarts
            String eid = UUID.randomUUID().toString();
            Object[] result = store.appendBlock(payload);
            long offset = (Long) result[0];
            int diskLen = (Integer) result[1];
            String hash = (String) result[2];

            EventKey k = new EventKey(layer, eid);
            EventMeta m = new EventMeta(
                layer, eid, System.nanoTime(), ttlSec, MAX_RETRIES,
                payload.length, hash, offset, diskLen, EventStatus.NEW
            );

            meta.put(k, m);
            store.writeIndex(m);

            // Put in L1 as hot
            cache.putHot(k, payload);
            
            lock.lock();
            try {
                Deque<EventKey> q = queues.get(layer);
                if (q == null) {
                    throw new IllegalStateException("missing queue for layer: " + layer);
                }
                q.addLast(k);
            } finally {
                lock.unlock();
            }

            // Drop control - check total queue size
            if (getTotalQueueSize() > DROP_IF_QUEUE_GT) {
                dropOldestGlobal();
            }

            return k;
        }

        /**
         * Ingests with default TTL.
         */
        public EventKey ingest(String layer, byte[] payload) throws IOException {
            return ingest(layer, payload, DEFAULT_TTL_SEC);
        }

        private void dropOldestGlobal() throws IOException {
            lock.lock();
            try {
                String maxLayer = null;
                int maxSize = 0;
                for (Map.Entry<String, Deque<EventKey>> entry : queues.entrySet()) {
                    Deque<EventKey> queue = entry.getValue();
                    int size = queue == null ? 0 : queue.size();
                    if (size > maxSize) {
                        maxSize = size;
                        maxLayer = entry.getKey();
                    }
                }
                if (maxLayer == null) {
                    return;
                }
                Deque<EventKey> queue = queues.get(maxLayer);
                if (queue == null || queue.isEmpty()) {
                    return;
                }
                EventKey k = queue.pollFirst();
                if (k == null) {
                    return;
                }
                EventMeta m = meta.get(k);
                if (m == null) {
                    Log.w(TAG, "Dropped event has no metadata: " + k);
                    return;
                }
                m.setStatus(EventStatus.DROPPED);
                store.writeIndex(m);
            } finally {
                lock.unlock();
            }
        }

        private boolean isExpired(EventMeta m) {
            long ageNs = System.nanoTime() - m.getCreatedNs();
            long ageSec = ageNs / NANOS_PER_SECOND;
            return ageSec > m.getTtlSec();
        }

        /**
         * Fetches payload from cache or disk.
         */
        public byte[] fetchPayload(EventKey k) throws IOException {
            byte[] v = cache.get(k);
            if (v != null) {
                return v;
            }
            // Read from disk
            EventMeta m = meta.get(k);
            if (m == null) {
                throw new IllegalArgumentException("Unknown event: " + k);
            }
            v = store.readBlock(m.getDiskOff());
            // Warm into L3 cache
            cache.getL3().put(k, v);
            return v;
        }

        /**
         * Processes a single event.
         */
        public void processOne(EventKey k) throws IOException {
            EventMeta m = meta.get(k);
            if (m == null) return;

            EventStatus status = m.getStatus();
            if (status == EventStatus.DROPPED || 
                status == EventStatus.DONE || 
                status == EventStatus.EXPIRED) {
                return;
            }

            if (isExpired(m)) {
                m.setStatus(EventStatus.EXPIRED);
                store.writeIndex(m);
                return;
            }

            try {
                byte[] payload = fetchPayload(k);
                boolean processed = processRafaeliaPayload(m, payload);
                if (!processed) {
                    throw new IOException("Rafaelia payload processing did not confirm success");
                }
                m.setStatus(EventStatus.DONE);
                store.writeIndex(m);

            } catch (PermanentProcessingException e) {
                m.setStatus(EventStatus.DROPPED);
                store.writeIndex(m);
                Log.w(TAG, "Permanent processing error for " + k + ": " + e.getMessage());
            } catch (IOException e) {
                // Retry path
                if (m.getRetriesLeft() > 0) {
                    m.setRetriesLeft(m.getRetriesLeft() - 1);
                    m.setStatus(EventStatus.RETRYING);
                    store.writeIndex(m);
                    // Requeue
                    lock.lock();
                    try {
                        Deque<EventKey> q = queues.get(m.getLayer());
                        if (q != null) {
                            q.addLast(k);
                        } else {
                            m.setStatus(EventStatus.DROPPED);
                            store.writeIndex(m);
                            Log.w(TAG, "Retry queue missing for layer: " + m.getLayer());
                        }
                    } finally {
                        lock.unlock();
                    }
                } else {
                    m.setStatus(EventStatus.DROPPED);
                    store.writeIndex(m);
                }
            }
        }

        /**
         * Deterministic/idempotent Rafaelia payload processing + validation.
         */
        private boolean processRafaeliaPayload(EventMeta m, byte[] payload)
                throws PermanentProcessingException {
            if (payload == null) {
                throw new PermanentProcessingException("Null payload");
            }
            if (payload.length != m.getPayloadLen()) {
                throw new PermanentProcessingException(
                    "Payload length mismatch: " + payload.length + " != " + m.getPayloadLen());
            }

            String payloadHash = bytesToHex(sha256(payload));
            if (!payloadHash.equals(m.getPayloadHash())) {
                throw new PermanentProcessingException("Payload hash mismatch");
            }

            long fold = 0x9E3779B97F4A7C15L;
            for (int i = 0; i < payload.length; i++) {
                long v = payload[i] & 0xFFL;
                fold ^= (v + 0x9E3779B9L + (fold << 6) + (fold >>> 2));
                fold += (i + 1L) * (v + 1L);
            }

            String receipt = String.format("%016x:%s", fold, payloadHash.substring(0, 16));
            String expected = String.format("%016x:%s", fold, m.getPayloadHash().substring(0, 16));
            return receipt.equals(expected);
        }

        /**
         * Runs one tick of the processing loop.
         */
        public void tick(int steps) throws IOException {
            List<String> schedule = scheduler.nextCycleLayers(layerFreqs);

            int n = 0;
            for (String layer : schedule) {
                if (n >= steps) break;
                
                EventKey k;
                lock.lock();
                try {
                    Deque<EventKey> q = queues.get(layer);
                    if (q == null || q.isEmpty()) continue;
                    k = q.pollFirst();
                } finally {
                    lock.unlock();
                }
                
                if (k != null) {
                    processOne(k);
                    n++;
                }
            }

            // Demote cache tiers
            cache.demoteCycle();
        }

        /**
         * Default tick with 256 steps.
         */
        public void tick() throws IOException {
            tick(256);
        }

        @Override
        public void close() throws IOException {
            store.close();
        }

        public L123Cache getCache() { return cache; }
        public HarmonicScheduler getScheduler() { return scheduler; }
        public Map<String, Integer> getLayerFreqs() { return layerFreqs; }
        public Map<EventKey, EventMeta> getMeta() { return meta; }
        public int getTotalQueueSize() {
            lock.lock();
            try {
                int total = 0;
                for (Deque<EventKey> q : queues.values()) {
                    if (q != null) {
                        total += q.size();
                    }
                }
                return total;
            } finally {
                lock.unlock();
            }
        }
    }

    // ========== Utility Methods ==========
    
    /**
     * Aligns a value up to the nearest multiple of alignment.
     */
    static int alignUp(int x, int a) {
        return ((x + (a - 1)) / a) * a;
    }

    /**
     * Computes SHA-256 hash of data.
     */
    static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Converts bytes to hex string.
     */
    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Converts hex string to bytes.
     */
    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
