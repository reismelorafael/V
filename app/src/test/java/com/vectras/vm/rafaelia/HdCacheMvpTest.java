package com.vectras.vm.rafaelia;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for HdCacheMvp - HD Cache MVP implementation.
 */
public class HdCacheMvpTest {

    private File tempStoreFile;
    private File tempIndexFile;

    @Before
    public void setUp() throws IOException {
        tempStoreFile = File.createTempFile("hdcache_store", ".dat");
        tempIndexFile = File.createTempFile("hdcache_index", ".jsonl");
        tempStoreFile.deleteOnExit();
        tempIndexFile.deleteOnExit();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempStoreFile.toPath());
        Files.deleteIfExists(tempIndexFile.toPath());
    }

    // ========== EventKey Tests ==========
    
    @Test
    public void eventKeyEquality() {
        HdCacheMvp.EventKey k1 = new HdCacheMvp.EventKey("layer1", "eid1");
        HdCacheMvp.EventKey k2 = new HdCacheMvp.EventKey("layer1", "eid1");
        HdCacheMvp.EventKey k3 = new HdCacheMvp.EventKey("layer1", "eid2");
        
        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
        assertNotEquals(k1, k3);
    }

    // ========== BlockStore Tests ==========
    
    @Test
    public void blockStoreAppendAndRead() throws IOException {
        byte[] payload = "test payload data".getBytes(StandardCharsets.UTF_8);
        
        try (HdCacheMvp.BlockStore store = new HdCacheMvp.BlockStore(tempStoreFile, tempIndexFile)) {
            Object[] result = store.appendBlock(payload);
            
            long offset = (Long) result[0];
            int length = (Integer) result[1];
            String hash = (String) result[2];
            
            assertEquals(0L, offset);
            assertTrue(length >= payload.length);
            assertNotNull(hash);
            assertEquals(64, hash.length()); // SHA-256 hex = 64 chars
            
            // Read back
            byte[] readPayload = store.readBlock(offset);
            assertArrayEquals(payload, readPayload);
        }
    }

    @Test
    public void blockStoreMultipleBlocks() throws IOException {
        try (HdCacheMvp.BlockStore store = new HdCacheMvp.BlockStore(tempStoreFile, tempIndexFile)) {
            byte[] p1 = "first block".getBytes(StandardCharsets.UTF_8);
            byte[] p2 = "second block with more data".getBytes(StandardCharsets.UTF_8);
            byte[] p3 = "third".getBytes(StandardCharsets.UTF_8);
            
            Object[] r1 = store.appendBlock(p1);
            Object[] r2 = store.appendBlock(p2);
            Object[] r3 = store.appendBlock(p3);
            
            // Verify all blocks can be read
            assertArrayEquals(p1, store.readBlock((Long) r1[0]));
            assertArrayEquals(p2, store.readBlock((Long) r2[0]));
            assertArrayEquals(p3, store.readBlock((Long) r3[0]));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void blockStoreRejectsOversizedPayload() throws IOException {
        try (HdCacheMvp.BlockStore store = new HdCacheMvp.BlockStore(tempStoreFile, tempIndexFile)) {
            byte[] oversized = new byte[HdCacheMvp.MAX_BLOCK_BYTES + 1];
            store.appendBlock(oversized);
        }
    }

    // ========== TierCache Tests ==========
    
    @Test
    public void tierCachePutAndGet() {
        HdCacheMvp.TierCache cache = new HdCacheMvp.TierCache(1024);
        HdCacheMvp.EventKey k = new HdCacheMvp.EventKey("layer", "eid1");
        byte[] data = new byte[]{1, 2, 3, 4};
        
        cache.put(k, data);
        
        byte[] retrieved = cache.get(k);
        assertArrayEquals(data, retrieved);
        assertEquals(4L, cache.getUsed());
    }

    @Test
    public void tierCacheEvictsWhenOverBudget() {
        HdCacheMvp.TierCache cache = new HdCacheMvp.TierCache(100);
        
        // Fill cache beyond budget
        for (int i = 0; i < 20; i++) {
            HdCacheMvp.EventKey k = new HdCacheMvp.EventKey("layer", "eid" + i);
            byte[] data = new byte[10]; // 10 bytes each
            cache.put(k, data);
        }
        
        // Used should not exceed budget
        assertTrue(cache.getUsed() <= cache.getBudget());
    }

    @Test
    public void tierCachePopOldestReturnsCorrectItem() {
        HdCacheMvp.TierCache cache = new HdCacheMvp.TierCache(1024);
        
        HdCacheMvp.EventKey k1 = new HdCacheMvp.EventKey("layer", "first");
        HdCacheMvp.EventKey k2 = new HdCacheMvp.EventKey("layer", "second");
        
        cache.put(k1, new byte[]{1});
        cache.put(k2, new byte[]{2});
        
        Map.Entry<HdCacheMvp.EventKey, byte[]> oldest = cache.popOldest();
        assertNotNull(oldest);
        assertEquals(k1, oldest.getKey());
    }

    // ========== L123Cache Tests ==========
    
    @Test
    public void l123CacheGetPromotesFromL3ToL2() {
        HdCacheMvp.L123Cache cache = new HdCacheMvp.L123Cache(100, 100, 100);
        HdCacheMvp.EventKey k = new HdCacheMvp.EventKey("layer", "eid");
        byte[] data = new byte[]{1, 2, 3};
        
        // Put directly in L3
        cache.getL3().put(k, data);
        
        // Get should promote to L2
        byte[] retrieved = cache.get(k);
        assertArrayEquals(data, retrieved);
        assertNotNull(cache.getL2().get(k));
    }

    @Test
    public void l123CachePutHotGoesToL1() {
        HdCacheMvp.L123Cache cache = new HdCacheMvp.L123Cache(100, 100, 100);
        HdCacheMvp.EventKey k = new HdCacheMvp.EventKey("layer", "eid");
        byte[] data = new byte[]{1, 2, 3};
        
        cache.putHot(k, data);
        
        assertNotNull(cache.getL1().get(k));
        assertNull(cache.getL2().get(k));
        assertNull(cache.getL3().get(k));
    }

    @Test
    public void l123CacheDemoteCycleMovesOverflow() {
        // Very small L1, bigger L2/L3
        HdCacheMvp.L123Cache cache = new HdCacheMvp.L123Cache(20, 100, 200);
        
        // Fill L1 beyond budget
        for (int i = 0; i < 10; i++) {
            HdCacheMvp.EventKey k = new HdCacheMvp.EventKey("layer", "eid" + i);
            cache.putHot(k, new byte[10]);
        }
        
        // Run demotion
        cache.demoteCycle();
        
        // L1 should now be at or under budget
        assertTrue(cache.getL1().getUsed() <= cache.getL1().getBudget());
    }

    // ========== HarmonicScheduler Tests ==========
    
    @Test
    public void harmonicSchedulerWeightCalculation() {
        HdCacheMvp.HarmonicScheduler scheduler = new HdCacheMvp.HarmonicScheduler(HdCacheMvp.HARMONICS);
        
        assertEquals(2, scheduler.weight(12));
        assertEquals(4, scheduler.weight(144));
        assertEquals(4, scheduler.weight(288));
        assertEquals(8, scheduler.weight(777));
        assertEquals(16, scheduler.weight(963));
        assertEquals(64, scheduler.weight(144000));
    }

    @Test
    public void harmonicSchedulerGeneratesSchedule() {
        HdCacheMvp.HarmonicScheduler scheduler = new HdCacheMvp.HarmonicScheduler(HdCacheMvp.HARMONICS);
        
        Map<String, Integer> layers = new HashMap<>();
        layers.put("low", 12);
        layers.put("medium", 500);
        layers.put("high", 1000);
        
        List<String> schedule = scheduler.nextCycleLayers(layers);
        
        assertNotNull(schedule);
        assertTrue(schedule.size() > 0);
        // High frequency layer should appear more times
        int highCount = 0, lowCount = 0;
        for (String s : schedule) {
            if ("high".equals(s)) highCount++;
            if ("low".equals(s)) lowCount++;
        }
        assertTrue(highCount > lowCount);
    }

    // ========== Engine Tests ==========
    
    @Test
    public void engineAddLayerAndIngest() throws IOException {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);
            
            byte[] payload = "test event data".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);
            
            assertNotNull(key);
            assertEquals("test_layer", key.getLayer());
            
            // Verify metadata exists
            HdCacheMvp.EventMeta meta = engine.getMeta().get(key);
            assertNotNull(meta);
            assertEquals(HdCacheMvp.EventStatus.NEW, meta.getStatus());
        }
    }

    @Test
    public void engineFetchPayloadFromCache() throws IOException {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);
            
            byte[] payload = "cached data".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);
            
            // Fetch should return from cache (L1)
            byte[] fetched = engine.fetchPayload(key);
            assertArrayEquals(payload, fetched);
        }
    }


    @Test
    public void engineProcessOneRealProcessingSuccess() throws IOException {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);

            byte[] payload = "rafaelia-real-processing".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);

            engine.processOne(key);
            HdCacheMvp.EventMeta meta = engine.getMeta().get(key);
            assertEquals(HdCacheMvp.EventStatus.DONE, meta.getStatus());

            // Idempotent path: DONE item is ignored, status must remain DONE.
            engine.processOne(key);
            assertEquals(HdCacheMvp.EventStatus.DONE, meta.getStatus());
        }
    }

    @Test
    public void engineProcessOneRetriesAreDecrementalOnTransientError() throws Exception {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);

            byte[] payload = "retry-me".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);
            HdCacheMvp.EventMeta meta = engine.getMeta().get(key);
            int retriesBefore = meta.getRetriesLeft();

            clearAllCacheTiers(engine, key);
            corruptMagic(meta.getDiskOff());

            engine.processOne(key);

            assertEquals(HdCacheMvp.EventStatus.RETRYING, meta.getStatus());
            assertEquals(retriesBefore - 1, meta.getRetriesLeft());
        }
    }

    @Test
    public void engineProcessOneTransitionsToDroppedAfterRetryExhaustion() throws Exception {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);

            byte[] payload = "drop-after-retries".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);
            HdCacheMvp.EventMeta meta = engine.getMeta().get(key);

            clearAllCacheTiers(engine, key);
            corruptMagic(meta.getDiskOff());

            for (int i = 0; i <= HdCacheMvp.MAX_RETRIES; i++) {
                engine.processOne(key);
            }

            assertEquals(HdCacheMvp.EventStatus.DROPPED, meta.getStatus());
            assertEquals(0, meta.getRetriesLeft());
        }
    }

    @Test
    public void engineProcessOnePreventsDoneWhenProcessingIncomplete() throws Exception {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);

            byte[] payload = "tamper-hash".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);
            HdCacheMvp.EventMeta meta = engine.getMeta().get(key);

            forcePayloadHash(meta, "0000000000000000000000000000000000000000000000000000000000000000");

            engine.processOne(key);

            assertEquals(HdCacheMvp.EventStatus.DROPPED, meta.getStatus());
            assertNotEquals(HdCacheMvp.EventStatus.DONE, meta.getStatus());
        }
    }
    @Test
    public void engineProcessOneCompletesEvent() throws IOException {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("test_layer", 100);
            
            byte[] payload = "process me".getBytes(StandardCharsets.UTF_8);
            HdCacheMvp.EventKey key = engine.ingest("test_layer", payload);
            
            engine.processOne(key);
            
            HdCacheMvp.EventMeta meta = engine.getMeta().get(key);
            assertEquals(HdCacheMvp.EventStatus.DONE, meta.getStatus());
        }
    }

    @Test
    public void engineTickProcessesEvents() throws IOException {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.addLayer("layer_a", 144);
            engine.addLayer("layer_b", 288);
            
            // Ingest multiple events
            for (int i = 0; i < 10; i++) {
                byte[] payload = ("event " + i).getBytes(StandardCharsets.UTF_8);
                engine.ingest("layer_a", payload);
                engine.ingest("layer_b", payload);
            }
            
            int initialSize = engine.getTotalQueueSize();
            
            // Run tick
            engine.tick(100);
            
            // Queue should be smaller after processing
            assertTrue(engine.getTotalQueueSize() < initialSize);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void engineRejectsUnknownLayer() throws IOException {
        try (HdCacheMvp.Engine engine = new HdCacheMvp.Engine(tempStoreFile, tempIndexFile)) {
            engine.ingest("unknown_layer", new byte[]{1, 2, 3});
        }
    }

    // ========== Utility Tests ==========
    
    @Test
    public void alignUpWorks() {
        assertEquals(4096, HdCacheMvp.alignUp(1, 4096));
        assertEquals(4096, HdCacheMvp.alignUp(4096, 4096));
        assertEquals(8192, HdCacheMvp.alignUp(4097, 4096));
    }

    @Test
    public void sha256ProducesCorrectLength() {
        byte[] hash = HdCacheMvp.sha256("test".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, hash.length);
    }

    @Test
    public void hexConversionRoundTrips() {
        byte[] original = new byte[]{0x01, (byte)0xAB, (byte)0xCD, (byte)0xEF};
        String hex = HdCacheMvp.bytesToHex(original);
        byte[] converted = HdCacheMvp.hexToBytes(hex);
        assertArrayEquals(original, converted);
    }

    // ========== EventMeta Tests ==========
    
    @Test
    public void eventMetaToJsonProducesValidJson() {
        HdCacheMvp.EventMeta meta = new HdCacheMvp.EventMeta(
            "test_layer", "eid123", 1000000L, 30, 3,
            100, "abc123", 0L, 4096, HdCacheMvp.EventStatus.NEW
        );
        
        String json = meta.toJson();
        
        assertTrue(json.contains("\"layer\":\"test_layer\""));
        assertTrue(json.contains("\"eid\":\"eid123\""));
        assertTrue(json.contains("\"status\":\"NEW\""));
    }

    private void clearAllCacheTiers(HdCacheMvp.Engine engine, HdCacheMvp.EventKey key) throws Exception {
        removeFromTier(engine.getCache().getL1(), key);
        removeFromTier(engine.getCache().getL2(), key);
        removeFromTier(engine.getCache().getL3(), key);
    }

    @SuppressWarnings("unchecked")
    private void removeFromTier(HdCacheMvp.TierCache tier, HdCacheMvp.EventKey key) throws Exception {
        Field mapField = HdCacheMvp.TierCache.class.getDeclaredField("map");
        mapField.setAccessible(true);
        Map<HdCacheMvp.EventKey, byte[]> map = (Map<HdCacheMvp.EventKey, byte[]>) mapField.get(tier);
        map.remove(key);

        Field usedField = HdCacheMvp.TierCache.class.getDeclaredField("used");
        usedField.setAccessible(true);
        long used = 0;
        for (byte[] value : map.values()) {
            used += value.length;
        }
        usedField.setLong(tier, used);
    }

    private void corruptMagic(long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(tempStoreFile, "rw")) {
            raf.seek(offset);
            raf.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        }
    }

    private void forcePayloadHash(HdCacheMvp.EventMeta meta, String hash) throws Exception {
        Field hashField = HdCacheMvp.EventMeta.class.getDeclaredField("payloadHash");
        hashField.setAccessible(true);
        hashField.set(meta, hash);
    }

}
