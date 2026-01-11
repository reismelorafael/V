package com.vectras.vm.core;

import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

/**
 * PerformanceMonitor: Utility class for measuring VM performance metrics.
 * 
 * <p>This class provides non-intrusive performance measurement capabilities
 * following ISO 8000/9001 standards for software quality.</p>
 * 
 * <h2>Metrics Measured:</h2>
 * <ul>
 *   <li>Boot time: Time from VM start to ready state</li>
 *   <li>Disk throughput: Sequential read/write speeds</li>
 *   <li>Input latency: Response time for user input</li>
 *   <li>Memory usage: Current and peak memory consumption</li>
 * </ul>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li>Non-intrusive: Minimal impact on measured operations</li>
 *   <li>Accurate: Uses high-resolution timers when available</li>
 *   <li>Thread-safe: Safe for concurrent access</li>
 * </ul>
 * 
 * @author Vectras Team
 * @version 1.0.0
 */
public final class PerformanceMonitor {
    
    private static final String TAG = "PerformanceMonitor";
    
    // Measurement state
    private volatile long bootStartTime = 0;
    private volatile long bootEndTime = 0;
    private volatile long lastInputTime = 0;
    private volatile long lastInputLatency = 0;
    private volatile long peakMemoryUsage = 0;
    
    // Singleton instance
    private static volatile PerformanceMonitor instance;
    
    private PerformanceMonitor() {
        // Private constructor for singleton
    }
    
    /**
     * Gets the singleton instance of PerformanceMonitor.
     * 
     * @return the PerformanceMonitor instance
     */
    public static PerformanceMonitor getInstance() {
        if (instance == null) {
            synchronized (PerformanceMonitor.class) {
                if (instance == null) {
                    instance = new PerformanceMonitor();
                }
            }
        }
        return instance;
    }
    
    // ========== Boot Time Measurement ==========
    
    /**
     * Marks the start of VM boot process.
     */
    public void markBootStart() {
        bootStartTime = SystemClock.elapsedRealtimeNanos();
        bootEndTime = 0;
        Log.d(TAG, "Boot started at: " + bootStartTime);
    }
    
    /**
     * Marks the end of VM boot process.
     */
    public void markBootEnd() {
        bootEndTime = SystemClock.elapsedRealtimeNanos();
        Log.d(TAG, "Boot completed at: " + bootEndTime);
    }
    
    /**
     * Gets the boot time in milliseconds.
     * 
     * @return boot time in ms, or -1 if not measured
     */
    public long getBootTimeMs() {
        if (bootStartTime == 0 || bootEndTime == 0) {
            return -1;
        }
        return TimeUnit.NANOSECONDS.toMillis(bootEndTime - bootStartTime);
    }
    
    /**
     * Gets the boot time in seconds.
     * 
     * @return boot time in seconds, or -1 if not measured
     */
    public double getBootTimeSec() {
        long ms = getBootTimeMs();
        return ms >= 0 ? ms / 1000.0 : -1;
    }
    
    // ========== Disk Throughput Measurement ==========
    
    /**
     * Result class for disk benchmark operations.
     */
    public static class DiskBenchmarkResult {
        public final long sequentialReadMBps;
        public final long sequentialWriteMBps;
        public final long randomReadIOPS;
        public final long randomWriteIOPS;
        public final long testDurationMs;
        
        public DiskBenchmarkResult(long seqReadMBps, long seqWriteMBps, 
                                   long randReadIOPS, long randWriteIOPS, 
                                   long durationMs) {
            this.sequentialReadMBps = seqReadMBps;
            this.sequentialWriteMBps = seqWriteMBps;
            this.randomReadIOPS = randReadIOPS;
            this.randomWriteIOPS = randWriteIOPS;
            this.testDurationMs = durationMs;
        }
        
        @Override
        public String toString() {
            return String.format(
                "DiskBenchmark[SeqRead=%dMB/s, SeqWrite=%dMB/s, RandRead=%dIOPS, RandWrite=%dIOPS, Duration=%dms]",
                sequentialReadMBps, sequentialWriteMBps, randomReadIOPS, randomWriteIOPS, testDurationMs
            );
        }
    }
    
    /**
     * Runs a disk throughput benchmark.
     * 
     * <p>This is a non-intrusive test that creates a temporary file
     * and measures read/write speeds.</p>
     * 
     * @param testDir directory for test file (should be on the target storage)
     * @param testSizeMB size of test file in megabytes
     * @return benchmark results
     */
    public DiskBenchmarkResult runDiskBenchmark(File testDir, int testSizeMB) {
        if (!testDir.exists() || !testDir.isDirectory()) {
            Log.e(TAG, "Invalid test directory: " + testDir);
            return new DiskBenchmarkResult(0, 0, 0, 0, 0);
        }
        
        File testFile = new File(testDir, "vectras_diskbench_" + System.currentTimeMillis() + ".tmp");
        long startTime = SystemClock.elapsedRealtimeNanos();
        
        try {
            int bufferSize = 1024 * 1024; // 1 MB buffer
            byte[] buffer = new byte[bufferSize];
            
            // Fill buffer with pattern
            for (int i = 0; i < bufferSize; i++) {
                buffer[i] = (byte) (i & 0xFF);
            }
            
            // Sequential write test
            long writeStartNs = SystemClock.elapsedRealtimeNanos();
            try (RandomAccessFile raf = new RandomAccessFile(testFile, "rw")) {
                for (int i = 0; i < testSizeMB; i++) {
                    raf.write(buffer);
                }
                raf.getFD().sync();
            }
            long writeEndNs = SystemClock.elapsedRealtimeNanos();
            long writeDurationNs = writeEndNs - writeStartNs;
            
            // Sequential read test
            long readStartNs = SystemClock.elapsedRealtimeNanos();
            try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
                for (int i = 0; i < testSizeMB; i++) {
                    raf.readFully(buffer);
                }
            }
            long readEndNs = SystemClock.elapsedRealtimeNanos();
            long readDurationNs = readEndNs - readStartNs;
            
            // Random read/write test (4K blocks)
            int blockSize = 4096;
            int numOps = 1000;
            byte[] smallBuffer = new byte[blockSize];
            java.util.Random random = new java.util.Random(42); // Fixed seed for reproducibility
            
            // Random read
            long randReadStartNs = SystemClock.elapsedRealtimeNanos();
            try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
                for (int i = 0; i < numOps; i++) {
                    long pos = (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % (testFile.length() - blockSize);
                    raf.seek(pos);
                    raf.read(smallBuffer);
                }
            }
            long randReadEndNs = SystemClock.elapsedRealtimeNanos();
            long randReadDurationNs = randReadEndNs - randReadStartNs;
            
            // Random write
            long randWriteStartNs = SystemClock.elapsedRealtimeNanos();
            try (RandomAccessFile raf = new RandomAccessFile(testFile, "rw")) {
                for (int i = 0; i < numOps; i++) {
                    long pos = (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % (testFile.length() - blockSize);
                    raf.seek(pos);
                    raf.write(smallBuffer);
                }
                raf.getFD().sync();
            }
            long randWriteEndNs = SystemClock.elapsedRealtimeNanos();
            long randWriteDurationNs = randWriteEndNs - randWriteStartNs;
            
            // Calculate results
            long seqWriteMBps = (testSizeMB * 1_000_000_000L) / writeDurationNs;
            long seqReadMBps = (testSizeMB * 1_000_000_000L) / readDurationNs;
            long randReadIOPS = (numOps * 1_000_000_000L) / randReadDurationNs;
            long randWriteIOPS = (numOps * 1_000_000_000L) / randWriteDurationNs;
            
            long endTime = SystemClock.elapsedRealtimeNanos();
            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            
            return new DiskBenchmarkResult(seqReadMBps, seqWriteMBps, randReadIOPS, randWriteIOPS, totalDurationMs);
            
        } catch (IOException e) {
            Log.e(TAG, "Disk benchmark failed", e);
            return new DiskBenchmarkResult(0, 0, 0, 0, 0);
        } finally {
            // Clean up test file
            if (testFile.exists()) {
                testFile.delete();
            }
        }
    }
    
    // ========== Input Latency Measurement ==========
    
    /**
     * Records the start of an input event.
     * Call this when user input is detected.
     */
    public void markInputStart() {
        lastInputTime = SystemClock.elapsedRealtimeNanos();
    }
    
    /**
     * Records the completion of input processing.
     * Call this when the input has been processed and displayed.
     */
    public void markInputEnd() {
        if (lastInputTime > 0) {
            lastInputLatency = SystemClock.elapsedRealtimeNanos() - lastInputTime;
        }
    }
    
    /**
     * Gets the last measured input latency in milliseconds.
     * 
     * @return input latency in ms, or -1 if not measured
     */
    public long getLastInputLatencyMs() {
        return lastInputLatency > 0 ? TimeUnit.NANOSECONDS.toMillis(lastInputLatency) : -1;
    }
    
    /**
     * Gets the last measured input latency in microseconds.
     * 
     * @return input latency in μs, or -1 if not measured
     */
    public long getLastInputLatencyUs() {
        return lastInputLatency > 0 ? TimeUnit.NANOSECONDS.toMicros(lastInputLatency) : -1;
    }
    
    // ========== Memory Usage Tracking ==========
    
    /**
     * Updates and returns current memory usage statistics.
     * 
     * @return current memory usage in bytes
     */
    public long updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        
        if (usedMemory > peakMemoryUsage) {
            peakMemoryUsage = usedMemory;
        }
        
        return usedMemory;
    }
    
    /**
     * Gets the peak memory usage recorded.
     * 
     * @return peak memory usage in bytes
     */
    public long getPeakMemoryUsage() {
        return peakMemoryUsage;
    }
    
    /**
     * Gets current memory usage in megabytes.
     * 
     * @return current memory in MB
     */
    public double getCurrentMemoryMB() {
        return updateMemoryUsage() / (1024.0 * 1024.0);
    }
    
    /**
     * Gets peak memory usage in megabytes.
     * 
     * @return peak memory in MB
     */
    public double getPeakMemoryMB() {
        return peakMemoryUsage / (1024.0 * 1024.0);
    }
    
    // ========== Comprehensive Report ==========
    
    /**
     * Generates a comprehensive performance report.
     * 
     * @return formatted performance report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════╗\n");
        sb.append("║         VECTRAS VM PERFORMANCE REPORT                ║\n");
        sb.append("╠══════════════════════════════════════════════════════╣\n");
        
        // Boot time
        double bootSec = getBootTimeSec();
        if (bootSec >= 0) {
            sb.append(String.format("║ Boot Time:           %10.2f seconds            ║\n", bootSec));
        } else {
            sb.append("║ Boot Time:           Not measured                   ║\n");
        }
        
        // Input latency
        long inputLatencyUs = getLastInputLatencyUs();
        if (inputLatencyUs >= 0) {
            sb.append(String.format("║ Input Latency:       %10d μs                  ║\n", inputLatencyUs));
        } else {
            sb.append("║ Input Latency:       Not measured                   ║\n");
        }
        
        // Memory
        sb.append(String.format("║ Current Memory:      %10.2f MB                  ║\n", getCurrentMemoryMB()));
        sb.append(String.format("║ Peak Memory:         %10.2f MB                  ║\n", getPeakMemoryMB()));
        
        sb.append("╚══════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
    
    /**
     * Resets all measurements.
     */
    public void reset() {
        bootStartTime = 0;
        bootEndTime = 0;
        lastInputTime = 0;
        lastInputLatency = 0;
        peakMemoryUsage = 0;
        Log.d(TAG, "Performance monitor reset");
    }
}
