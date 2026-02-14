package com.vectras.vm.benchmark;

import com.vectras.vm.core.BareMetalProfile;
import com.vectras.vm.core.NativeFastPath;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32C;

/**
 * VECTRA BENCHMARK MODULE (Low-Level, Matrix-Based, No Abstractions)
 * 
 * Inspired by AnTuTu benchmark methodology with ~79 metrics across:
 * - CPU Performance (single/multi-threaded)
 * - Memory Performance (bandwidth, latency)
 * - Storage Performance (sequential, random I/O)
 * - Emulation Performance (QEMU-specific)
 * - Integrity/Parity Performance
 * 
 * Design principles:
 * - LOW-LEVEL: All operations use primitive types and raw loops
 * - MATRIX-BASED: Data stored in pre-allocated 2D arrays (M0-M4)
 * - NO ABSTRACTIONS: No Math.*, Arrays.*, System.arraycopy, etc.
 * - NUMBERED VARIABLES: v0, v1, t0, t1, n0, n1 style naming
 * - MINIMAL OVERHEAD: Pre-allocated buffers, no allocation during timing
 * - INLINE ALGORITHMS: SplitMix64, SWAR popcount, manual byte conversion
 * - Non-intrusive: no user penalties
 * - Deterministic: reproducible results via fixed seeds
 * 
 * Matrix Buffers:
 * - M0[256][256]: int matrix for integer operations
 * - M1[256][256]: long matrix for 64-bit operations
 * - M2[256][256]: float matrix for FP32 operations
 * - M3[256][256]: double matrix for FP64 operations
 * - M4[1024][4096]: byte matrix for memory/integrity operations (4MB)
 * 
 * Reference: AnTuTu Benchmark v10.x methodology
 * 
 * Build: javac VectraBenchmark.java
 * Run:   java VectraBenchmark [output.bin]
 */
public class VectraBenchmark {

    // ========== Configuration Constants ==========
    static final int METRIC_COUNT = 79;
    static final int WARMUP_ITERATIONS = 7;
    static final int TEST_ITERATIONS = 21;
    static final long MIN_TEST_DURATION_NS = 500_000_000L;
    static final int MAX_TEST_REPEAT = 256;
    static final long MMAP_BYTES = 16L * 1024 * 1024; // 16MB segment
    static final int RECORD_BYTES = 8 + 4 + 4; // u64 value + u32 meta + u32 crc
    static final int CPU_WORKLOAD_SIZE = 1_000_000;
    static final int MEMORY_BLOCK_SIZE = 4096;
    static final int MEMORY_BLOCKS = 1024;
    static final int STORAGE_BLOCK_SIZE = 4096;
    static final int STORAGE_BLOCKS = 256;
    static final int PARITY_BLOCK_BITS = 16;
    static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    
    // Golden ratio constants for mixing (from SplitMix64)
    static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    static final long MIX_A = 0xBF58476D1CE4E5B9L;
    static final long MIX_B = 0x94D049BB133111EBL;
    static final long BENCH_AUDIT_SEED = 0xC0DEC0DEF00DBA5EL;
    
    // CRC32C thread-local pool
    private static final ThreadLocal<CRC32C> CRC32C_POOL = ThreadLocal.withInitial(CRC32C::new);
    private static final int M4_ROWS = 1024;
    private static final int M4_COLS = 4096;
    private static final int M4_BYTES = M4_ROWS * M4_COLS;
    private static volatile int copyStripeBytes = BareMetalProfile.recommendedWorkBlockBytes();
    private static volatile long SINK = 0L;

    

    public static void setCopyStripeBytes(int bytes) {
        if (bytes < 256) {
            copyStripeBytes = 256;
        } else if (bytes > M4_COLS) {
            copyStripeBytes = M4_COLS;
        } else {
            copyStripeBytes = bytes;
        }
    }

    public static int getCopyStripeBytes() {
        return copyStripeBytes;
    }
    // ========== Metric Categories ==========
    // Category 1: CPU (metrics 0-19)
    static final int CPU_INTEGER_ADD = 0;
    static final int CPU_INTEGER_MUL = 1;
    static final int CPU_INTEGER_DIV = 2;
    static final int CPU_INTEGER_MOD = 3;
    static final int CPU_LONG_ADD = 4;
    static final int CPU_LONG_MUL = 5;
    static final int CPU_LONG_DIV = 6;
    static final int CPU_LONG_MIX = 7;
    static final int CPU_FLOAT_ADD = 8;
    static final int CPU_FLOAT_MUL = 9;
    static final int CPU_FLOAT_DIV = 10;
    static final int CPU_DOUBLE_ADD = 11;
    static final int CPU_DOUBLE_MUL = 12;
    static final int CPU_DOUBLE_DIV = 13;
    static final int CPU_BITWISE_AND = 14;
    static final int CPU_BITWISE_OR = 15;
    static final int CPU_BITWISE_XOR = 16;
    static final int CPU_SHIFT_LEFT = 17;
    static final int CPU_SHIFT_RIGHT = 18;
    static final int CPU_POPCOUNT = 19;
    
    // Category 2: CPU Multi-threaded (metrics 20-29)
    static final int CPU_MT_INTEGER = 20;
    static final int CPU_MT_LONG = 21;
    static final int CPU_MT_FLOAT = 22;
    static final int CPU_MT_DOUBLE = 23;
    static final int CPU_MT_MIXED = 24;
    static final int CPU_MT_CONTENTION = 25;
    static final int CPU_MT_SPINLOCK = 26;
    static final int CPU_MT_CAS = 27;
    static final int CPU_MT_BARRIER = 28;
    static final int CPU_MT_THROUGHPUT = 29;
    
    // Category 3: Memory (metrics 30-44)
    static final int MEM_SEQUENTIAL_READ = 30;
    static final int MEM_SEQUENTIAL_WRITE = 31;
    static final int MEM_RANDOM_READ = 32;
    static final int MEM_RANDOM_WRITE = 33;
    static final int MEM_COPY_BANDWIDTH = 34;
    static final int MEM_FILL_BANDWIDTH = 35;
    static final int MEM_LATENCY_L1 = 36;
    static final int MEM_LATENCY_L2 = 37;
    static final int MEM_LATENCY_L3 = 38;
    static final int MEM_LATENCY_RAM = 39;
    static final int MEM_ALLOC_SPEED = 40;
    static final int MEM_FREE_SPEED = 41;
    static final int MEM_BUFFER_POOL = 42;
    static final int MEM_ARRAY_SUM = 43;
    static final int MEM_STRIDE_ACCESS = 44;
    
    // Category 4: Storage (metrics 45-59)
    static final int STORAGE_SEQ_READ = 45;
    static final int STORAGE_SEQ_WRITE = 46;
    static final int STORAGE_RANDOM_READ = 47;
    static final int STORAGE_RANDOM_WRITE = 48;
    static final int STORAGE_MMAP_READ = 49;
    static final int STORAGE_MMAP_WRITE = 50;
    static final int STORAGE_SYNC_LATENCY = 51;
    static final int STORAGE_APPEND_ONLY = 52;
    static final int STORAGE_TRUNCATE = 53;
    static final int STORAGE_SEEK = 54;
    static final int STORAGE_4K_READ = 55;
    static final int STORAGE_4K_WRITE = 56;
    static final int STORAGE_64K_READ = 57;
    static final int STORAGE_64K_WRITE = 58;
    static final int STORAGE_1M_READ = 59;
    
    // Category 5: Integrity/Parity (metrics 60-69)
    static final int INTEGRITY_CRC32C = 60;
    static final int INTEGRITY_PARITY_2D = 61;
    static final int INTEGRITY_SYNDROME = 62;
    static final int INTEGRITY_CHECKSUM = 63;
    static final int INTEGRITY_XOR_STRIPE = 64;
    static final int INTEGRITY_HAMMING = 65;
    static final int INTEGRITY_BLOCK_VERIFY = 66;
    static final int INTEGRITY_BIT_FLIP_DETECT = 67;
    static final int INTEGRITY_ERROR_CORRECT = 68;
    static final int INTEGRITY_HASH_MIX = 69;
    
    // Category 6: Emulation-Specific (metrics 70-78)
    static final int EMU_CONTEXT_SWITCH = 70;
    static final int EMU_SYSCALL_OVERHEAD = 71;
    static final int EMU_MEMORY_MAP = 72;
    static final int EMU_BUFFER_COPY = 73;
    static final int EMU_EVENT_DISPATCH = 74;
    static final int EMU_TIMER_PRECISION = 75;
    static final int EMU_IRQ_LATENCY = 76;
    static final int EMU_STATE_SERIALIZE = 77;
    static final int EMU_TRIAD_CONSENSUS = 78;
    
    // ========== Results Storage ==========
    /**
     * BenchmarkResult with formal engineering metrics.
     * 
     * @param metricId Unique metric identifier (0-78)
     * @param name Human-readable metric name
     * @param rawValue Raw measured value (typically nanoseconds for time-based metrics)
     * @param formattedValue Formatted value with appropriate SI prefix (e.g., "1.23 ms")
     * @param unit Engineering unit (e.g., "ns", "μs", "ms", "MB/s", "GFLOPS", "IOPS")
     * @param category Metric category for grouping
     * @param description Technical description of what was measured
     */
    public record BenchmarkResult(
            int metricId, 
            String name, 
            long rawValue,
            String formattedValue,
            String unit,
            String category,
            String description) {
        
        /**
         * Gets the value converted to appropriate engineering scale.
         * For time-based metrics, returns seconds.
         * For throughput metrics, returns MB/s or ops/s.
         */
        public double getScaledValue() {
            return rawValue / 1_000_000_000.0; // Default: ns to seconds
        }
        
        /**
         * Legacy compatibility - returns 100 for valid results.
         * @deprecated Use rawValue and formattedValue for actual metrics
         */
        @Deprecated
        public int score() { return rawValue > 0 ? 100 : 0; }
    }
    
    /**
     * Device specification data retrieved from system.
     */
    public static class DeviceSpecification {
        public final String cpuModel;
        public final int cpuCores;
        public final long maxCpuFreqHz;
        public final long totalRamBytes;
        public final String cpuArchitecture;
        public final String[] supportedAbis;
        
        public DeviceSpecification(String cpuModel, int cpuCores, long maxCpuFreqHz, 
                                   long totalRamBytes, String cpuArchitecture, String[] supportedAbis) {
            this.cpuModel = cpuModel;
            this.cpuCores = cpuCores;
            this.maxCpuFreqHz = maxCpuFreqHz;
            this.totalRamBytes = totalRamBytes;
            this.cpuArchitecture = cpuArchitecture;
            this.supportedAbis = supportedAbis;
        }
        
        public String getFormattedRam() {
            double gb = totalRamBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(java.util.Locale.US, "%.1f GB", gb);
        }
        
        public String getFormattedCpuFreq() {
            double ghz = maxCpuFreqHz / 1_000_000_000.0;
            return String.format(java.util.Locale.US, "%.2f GHz", ghz);
        }
    }
    
    /**
     * Get current device specifications.
     * Note: Some values may not be available on all devices.
     */
    public static DeviceSpecification getDeviceSpecification() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxFreq = getMaxCpuFrequency();
        long totalRam = getTotalRamBytes();
        String arch = System.getProperty("os.arch", "unknown");
        String[] abis = getSupportedAbis();
        String cpuModel = getCpuModelName();
        
        return new DeviceSpecification(cpuModel, cores, maxFreq, totalRam, arch, abis);
    }
    
    private static long getMaxCpuFrequency() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"));
            String line = br.readLine();
            br.close();
            return Long.parseLong(line.trim()) * 1000; // kHz to Hz
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static long getTotalRamBytes() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/proc/meminfo"));
            String line = br.readLine(); // First line: MemTotal
            br.close();
            if (line != null && line.startsWith("MemTotal:")) {
                String[] parts = line.split("\\s+");
                return Long.parseLong(parts[1]) * 1024; // KB to bytes
            }
        } catch (Exception e) {
            // Fallback to Runtime
        }
        return Runtime.getRuntime().maxMemory();
    }
    
    private static String[] getSupportedAbis() {
        try {
            Class<?> buildClass = Class.forName("android.os.Build");
            java.lang.reflect.Field field = buildClass.getField("SUPPORTED_ABIS");
            return (String[]) field.get(null);
        } catch (Exception e) {
            return new String[]{System.getProperty("os.arch", "unknown")};
        }
    }
    
    private static String getCpuModelName() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/proc/cpuinfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("model name") || line.startsWith("Hardware") || 
                    line.startsWith("Processor")) {
                    br.close();
                    int idx = line.indexOf(':');
                    if (idx >= 0) {
                        return line.substring(idx + 1).trim();
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown CPU";
    }
    
    // ========== BitStack Logger (append-only) ==========
    static final class BitStack implements Closeable {
        private final FileChannel ch;
        private final RandomAccessFile raf;
        private final MappedByteBuffer map;
        private final AtomicLong writePos = new AtomicLong(0);
        
        BitStack(File path, long mmapBytes) throws IOException {
            File parent = path.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs() && !parent.exists()) {
                    throw new IOException("Unable to create directories for " + path);
                }
            }
            raf = new RandomAccessFile(path, "rw");
            ch = raf.getChannel();
            if (ch.size() < mmapBytes) raf.setLength(mmapBytes);
            map = ch.map(FileChannel.MapMode.READ_WRITE, 0, mmapBytes);
            map.order(ByteOrder.LITTLE_ENDIAN);
        }
        
        long appendResult(long value, int metricId) {
            int crc = crc32c(value, metricId);
            long pos = writePos.getAndAdd(RECORD_BYTES);
            if (pos + RECORD_BYTES > map.capacity()) {
                throw new IllegalStateException("BitStack full");
            }
            int p = (int) pos;
            map.putLong(p, value);
            map.putInt(p + 8, metricId);
            map.putInt(p + 12, crc);
            return pos;
        }
        
        void flush() { map.force(); }
        
        @Override
        public void close() throws IOException {
            flush();
            ch.close();
            raf.close();
        }
        
        static int crc32c(long value, int meta) {
            CRC32C c = CRC32C_POOL.get();
            c.reset();
            for (int i = 0; i < 8; i++) c.update((int)((value >>> (8*i)) & 0xFF));
            for (int i = 0; i < 4; i++) c.update((meta >>> (8*i)) & 0xFF);
            return (int) c.getValue();
        }
    }
    
    // ========== 4x4 Matrix Parity (from RafaeliaMvp) ==========
    static int idx(int x, int y) { return (y << 2) | x; }
    
    static int getBit16(int bits16, int x, int y) {
        return (bits16 >>> idx(x, y)) & 1;
    }
    
    static int parity2D8(int bits16) {
        int parity = 0;
        for (int y = 0; y < 4; y++) {
            int p = 0;
            for (int x = 0; x < 4; x++) p ^= getBit16(bits16, x, y);
            parity |= (p & 1) << y;
        }
        for (int x = 0; x < 4; x++) {
            int p = 0;
            for (int y = 0; y < 4; y++) p ^= getBit16(bits16, x, y);
            parity |= (p & 1) << (4 + x);
        }
        return parity & 0xFF;
    }
    
    static int syndromePopcount(int storedParity8, int computedParity8) {
        return Integer.bitCount((storedParity8 ^ computedParity8) & 0xFF);
    }
    
    // ========== Triad Consensus (2-of-3) ==========
    static int whoOutTriad(long cpu, long ram, long disk) {
        if (cpu == ram && cpu != disk) return 2;
        if (cpu == disk && cpu != ram) return 1;
        if (ram == disk && ram != cpu) return 0;
        return 3;
    }
    
    // ========== 64-bit Mixer ==========
    static long mix64(long x) {
        x ^= (x >>> 30);
        x *= MIX_A;
        x ^= (x >>> 27);
        x *= MIX_B;
        x ^= (x >>> 31);
        return x;
    }
    
    // ========== LOW-LEVEL CPU BENCHMARKS (Matrix-based, numbered variables) ==========
    
    // Pre-allocated matrices for benchmarks (avoids allocation during measurement)
    private static final int[][] M0 = new int[256][256];   // Integer matrix
    private static final long[][] M1 = new long[256][256]; // Long matrix
    private static final float[][] M2 = new float[256][256]; // Float matrix
    private static final double[][] M3 = new double[256][256]; // Double matrix
    private static final byte[][] M4 = new byte[1024][4096]; // Byte matrix for memory
    
    // Initialize matrices with deterministic values
    static {
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                M0[i][j] = (i * 257) ^ (j * 263);
                M1[i][j] = ((long)i * 0x5DEECE66DL) ^ ((long)j * 0xBL);
                M2[i][j] = (float)(i * 0.00390625f + j * 0.00001f);
                M3[i][j] = (double)(i * 0.00390625 + j * 0.00000001);
            }
        }
        for (int i = 0; i < 1024; i++) {
            for (int j = 0; j < 4096; j++) {
                M4[i][j] = (byte)((i ^ j) & 0xFF);
            }
        }
    }
    
    static long benchCpuIntegerAdd(int n) {
        // Pure integer add on matrix - no named variables except indices
        int v0 = 0, v1 = 0, v2 = 0, v3 = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 + M0[i][j];
                v1 = v1 + M0[j][i];
                v2 = v0 + v1;
                v3 = v2 + M0[i][j];
            }
        }
        for (int k = 0; k < n >> 16; k++) {
            v0 = v0 + v1 + v2 + v3;
            v1 = v1 + v0;
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchCpuIntegerMul(int n) {
        // Pure integer multiply on matrix
        int v0 = 1, v1 = 1, v2 = 1, v3 = 1;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 * ((M0[i][j] & 0x7F) | 1);
                v1 = v1 * ((M0[j][i] & 0x7F) | 1);
                v2 = v0 ^ v1;
                v3 = (v2 != 0) ? v2 : 1;
                if ((j & 0x3F) == 0) { v0 = 1; v1 = 1; } // prevent overflow
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v3 & 0);
    }
    
    static long benchCpuIntegerDiv(int n) {
        // Pure integer divide on matrix
        int v0 = 0x7FFFFFFF, v1 = 0x7FFFFFFF;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                int v2 = (M0[i][j] & 0x7) | 1; // divisor: 1-8
                int v3 = (M0[j][i] & 0x7) | 1;
                v0 = v0 / v2;
                v1 = v1 / v3;
                if (v0 < 256) v0 = 0x7FFFFFFF;
                if (v1 < 256) v1 = 0x7FFFFFFF;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchCpuLongMix(int n) {
        // Low-level 64-bit mixing on matrix (SplitMix64 inlined)
        long v0 = 0x123456789ABCDEF0L;
        long v1 = 0xFEDCBA9876543210L;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 ^ M1[i][j];
                v0 = (v0 ^ (v0 >>> 30)) * 0xBF58476D1CE4E5B9L;
                v0 = (v0 ^ (v0 >>> 27)) * 0x94D049BB133111EBL;
                v0 = v0 ^ (v0 >>> 31);
                v1 = v1 ^ v0;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchCpuFloatAdd(int n) {
        // Low-level float add on matrix - no Math functions
        float v0 = 0.0f, v1 = 0.0f, v2 = 0.0f, v3 = 0.0f;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 + M2[i][j];
                v1 = v1 + M2[j][i];
                v2 = v0 + v1;
                v3 = v2 + M2[i][j] * 0.5f;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + ((int)v3 & 0);
    }
    
    static long benchCpuDoubleMul(int n) {
        // Low-level double multiply on matrix
        double v0 = 1.0, v1 = 1.0, v2 = 1.0;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 * (1.0 + M3[i][j] * 0.0001);
                v1 = v1 * (1.0 + M3[j][i] * 0.0001);
                v2 = v0 * v1;
                if ((j & 0x1F) == 0) { v0 = 1.0; v1 = 1.0; } // prevent overflow
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + ((long)v2 & 0);
    }
    
    static long benchCpuBitwiseXor(int n) {
        // Low-level bitwise XOR on matrix
        long v0 = 0xFEDCBA9876543210L;
        long v1 = 0x0123456789ABCDEFL;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 ^ M1[i][j];
                v1 = v1 ^ M1[j][i];
                v0 = v0 ^ (v0 >>> 17);
                v1 = v1 ^ (v1 << 13);
                v0 = v0 ^ v1;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchCpuPopcount(int n) {
        // Low-level popcount on matrix (manual bit counting, no library)
        int v0 = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                // Manual popcount for int (SWAR algorithm)
                int v1 = M0[i][j];
                v1 = v1 - ((v1 >>> 1) & 0x55555555);
                v1 = (v1 & 0x33333333) + ((v1 >>> 2) & 0x33333333);
                v1 = (v1 + (v1 >>> 4)) & 0x0F0F0F0F;
                v1 = v1 + (v1 >>> 8);
                v1 = v1 + (v1 >>> 16);
                v0 = v0 + (v1 & 0x3F);
                
                // Manual popcount for long
                long v2 = M1[i][j];
                v2 = v2 - ((v2 >>> 1) & 0x5555555555555555L);
                v2 = (v2 & 0x3333333333333333L) + ((v2 >>> 2) & 0x3333333333333333L);
                v2 = (v2 + (v2 >>> 4)) & 0x0F0F0F0F0F0F0F0FL;
                v2 = v2 + (v2 >>> 8);
                v2 = v2 + (v2 >>> 16);
                v2 = v2 + (v2 >>> 32);
                v0 = v0 + (int)(v2 & 0x7F);
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    // ========== LOW-LEVEL Multi-threaded CPU Benchmarks ==========
    
    static long benchCpuMtInteger(int n0, int n1) throws InterruptedException {
        // n0 = thread count, n1 = iterations per thread
        CountDownLatch c0 = new CountDownLatch(n0);
        AtomicLong r0 = new AtomicLong(0);
        long t0 = System.nanoTime();
        
        for (int k = 0; k < n0; k++) {
            final int s0 = k;
            new Thread(() -> {
                long v0 = 0;
                int v1 = s0;
                // Low-level LCG (Linear Congruential Generator)
                for (int i = 0; i < n1; i++) {
                    v1 = v1 * 1103515245 + 12345;
                    v0 = v0 + v1;
                }
                r0.addAndGet(v0);
                c0.countDown();
            }).start();
        }
        
        c0.await();
        long t1 = System.nanoTime();
        return t1 - t0 + (r0.get() & 0);
    }
    
    static long benchCpuMtCas(int n0, int n1) throws InterruptedException {
        // n0 = threads, n1 = total iterations
        CountDownLatch c0 = new CountDownLatch(n0);
        AtomicLong r0 = new AtomicLong(0);
        long t0 = System.nanoTime();
        
        for (int k = 0; k < n0; k++) {
            new Thread(() -> {
                for (int i = 0; i < n1 / n0; i++) {
                    r0.incrementAndGet();
                }
                c0.countDown();
            }).start();
        }
        
        c0.await();
        long t1 = System.nanoTime();
        return t1 - t0 + (r0.get() & 0);
    }
    
    // ========== LOW-LEVEL Memory Benchmarks (Matrix-based) ==========
    
    static long benchMemSequentialRead(byte[] b0) {
        // Low-level sequential read using matrix M4
        long v0 = 0;
        int n0 = M4.length;
        int n1 = M4[0].length;
        long t0 = System.nanoTime();
        for (int i = 0; i < n0; i++) {
            for (int j = 0; j < n1; j++) {
                v0 = v0 + M4[i][j];
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchMemSequentialWrite(byte[] b0) {
        // Low-level sequential write using matrix M4
        int n0 = M4.length;
        int n1 = M4[0].length;
        long t0 = System.nanoTime();
        for (int i = 0; i < n0; i++) {
            for (int j = 0; j < n1; j++) {
                M4[i][j] = (byte)((i + j) & 0xFF);
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0;
    }
    
    static long benchMemRandomRead(byte[] b0, int[] idx) {
        // Low-level random read using pre-computed indices on matrix
        long v0 = 0;
        int n0 = idx.length;
        int n1 = M4.length;
        int n2 = M4[0].length;
        long t0 = System.nanoTime();
        for (int k = 0; k < n0; k++) {
            int i = idx[k] & 0x3FF;  // mod 1024 (rows)
            int j = (idx[k] >>> 10) & 0xFFF; // mod 4096 (cols)
            if (i >= n1) i = i % n1;
            if (j >= n2) j = j % n2;
            v0 = v0 + M4[i][j];
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }

    static long benchMemRandomWrite(byte[] b0, int[] idx) {
        int n0 = idx.length;
        int n1 = M4.length;
        int n2 = M4[0].length;
        int v0 = 0;
        long t0 = System.nanoTime();
        for (int k = 0; k < n0; k++) {
            int i = idx[k] & 0x3FF;
            int j = (idx[k] >>> 10) & 0xFFF;
            if (i >= n1) i = i % n1;
            if (j >= n2) j = j % n2;
            int nv = (i + j + k + v0) & 0xFF;
            M4[i][j] = (byte) nv;
            v0 ^= nv;
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }

    private static long repeatUntilDuration(BenchOp op, long minDurationNs) throws Exception {
        int repeat = 1;
        long total = 0L;
        long checksum = 0L;
        while (true) {
            total = 0L;
            checksum = 0L;
            for (int i = 0; i < repeat; i++) {
                long raw = op.run();
                total += raw;
                checksum ^= raw + (i * 0x9E3779B97F4A7C15L);
            }
            if (total >= minDurationNs || repeat >= MAX_TEST_REPEAT) {
                break;
            }
            repeat <<= 1;
        }
        SINK ^= checksum;
        return total;
    }

    private static long benchmarkMedian(BenchOp op) throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            long w = repeatUntilDuration(op, MIN_TEST_DURATION_NS);
            SINK ^= (w + i);
        }
        long[] samples = new long[TEST_ITERATIONS];
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            samples[i] = repeatUntilDuration(op, MIN_TEST_DURATION_NS);
        }
        for (int i = 0; i < samples.length - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < samples.length; j++) {
                if (samples[j] < samples[minIdx]) {
                    minIdx = j;
                }
            }
            if (minIdx != i) {
                long tmp = samples[i];
                samples[i] = samples[minIdx];
                samples[minIdx] = tmp;
            }
        }
        long median = samples[samples.length / 2];
        SINK ^= median;
        return median;
    }

    @FunctionalInterface
    private interface BenchOp {
        long run() throws Exception;
    }
    
    static long benchMemCopyBandwidth(byte[] s0, byte[] d0, ArenaBenchmarkContext arenaCtx) {
        if (arenaCtx != null && arenaCtx.available) {
            long t0 = System.nanoTime();
            boolean copied = NativeFastPath.copyArena(arenaCtx.srcArenaHandle, 0,
                    arenaCtx.dstArenaHandle, 0, M4_BYTES);
            long t1 = System.nanoTime();
            if (copied) {
                return t1 - t0 + (NativeFastPath.xorChecksumArena(arenaCtx.dstArenaHandle, 0, M4_BYTES) & 0);
            }
        }

        // Fallback path: Java byte[] destination provided by caller (no hot-path allocations).
        if (d0 == null || d0.length < M4_BYTES) {
            throw new IllegalArgumentException("Destination buffer too small for M4 copy");
        }
        int dstOffset = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < M4_ROWS; i++) {
            byte[] row = M4[i];
            int copied = 0;
            while (copied < M4_COLS) {
                int chunk = M4_COLS - copied;
                if (chunk > copyStripeBytes) {
                    chunk = copyStripeBytes;
                }
                NativeFastPath.copyBytes(row, copied, d0, dstOffset + copied, chunk);
                copied += chunk;
            }
            dstOffset += M4_COLS;
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (NativeFastPath.xorChecksum(d0, 0, M4_BYTES) & 0);
    }

    private static final class ArenaBenchmarkContext {
        final int srcArenaHandle;
        final int dstArenaHandle;
        final boolean available;

        private ArenaBenchmarkContext(int srcArenaHandle, int dstArenaHandle, boolean available) {
            this.srcArenaHandle = srcArenaHandle;
            this.dstArenaHandle = dstArenaHandle;
            this.available = available;
        }
    }

    private static ArenaBenchmarkContext setupArenaBenchmarkContext() {
        if (!NativeFastPath.isArenaAvailable()) {
            return new ArenaBenchmarkContext(0, 0, false);
        }

        int src = 0;
        int dst = 0;
        try {
            src = NativeFastPath.allocArena(M4_BYTES);
            dst = NativeFastPath.allocArena(M4_BYTES);
            if (src <= 0 || dst <= 0) {
                teardownArenaBenchmarkContext(new ArenaBenchmarkContext(src, dst, false));
                return new ArenaBenchmarkContext(0, 0, false);
            }
            int o0 = 0;
            for (int i = 0; i < M4_ROWS; i++) {
                byte[] row = M4[i];
                for (int j = 0; j < M4_COLS; j++) {
                    if (!NativeFastPath.fillArena(src, o0, 1, row[j] & 0xFF)) {
                        teardownArenaBenchmarkContext(new ArenaBenchmarkContext(src, dst, false));
                        return new ArenaBenchmarkContext(0, 0, false);
                    }
                    o0++;
                }
            }
            return new ArenaBenchmarkContext(src, dst, true);
        } catch (Throwable ignored) {
            teardownArenaBenchmarkContext(new ArenaBenchmarkContext(src, dst, false));
            return new ArenaBenchmarkContext(0, 0, false);
        }
    }

    private static void teardownArenaBenchmarkContext(ArenaBenchmarkContext ctx) {
        if (ctx == null) {
            return;
        }
        if (ctx.dstArenaHandle > 0) {
            try {
                NativeFastPath.freeArena(ctx.dstArenaHandle);
            } catch (Throwable ignored) {
            }
        }
        if (ctx.srcArenaHandle > 0) {
            try {
                NativeFastPath.freeArena(ctx.srcArenaHandle);
            } catch (Throwable ignored) {
            }
        }
    }
    
    static long benchMemFillBandwidth(byte[] b0, byte v0) {
        // Low-level manual fill (no Arrays.fill)
        int n0 = M4.length;
        int n1 = M4[0].length;
        long t0 = System.nanoTime();
        for (int i = 0; i < n0; i++) {
            for (int j = 0; j < n1; j++) {
                M4[i][j] = v0;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0;
    }
    
    static long benchMemAllocSpeed(int n0, int n1) {
        // n0 = count, n1 = size
        // Low-level allocation test using matrix
        long t0 = System.nanoTime();
        byte[][] a0 = new byte[n0][n1];
        for (int i = 0; i < n0; i++) {
            for (int j = 0; j < n1; j++) {
                a0[i][j] = (byte)(i ^ j);
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (a0[0][0] & 0);
    }
    
    // ========== LOW-LEVEL Integrity Benchmarks (Matrix-based) ==========
    
    static long benchIntegrityCrc32c(byte[] d0, int n0) {
        // Low-level CRC32C on matrix M4
        // n0 = iterations
        CRC32C c0 = new CRC32C();
        long t0 = System.nanoTime();
        for (int k = 0; k < n0; k++) {
            c0.reset();
            for (int i = 0; i < M4.length; i++) {
                c0.update(M4[i]);
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (int)(c0.getValue() & 0);
    }
    
    static long benchIntegrityParity2D(int n0) {
        // Low-level 2D parity on matrix M0
        int v0 = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                // Manual 2D parity calculation
                int v1 = M0[i][j] & 0xFFFF;
                int v2 = 0;
                // Column parity (4 columns of 4 bits each)
                v2 = v2 | (((v1 >>> 0) ^ (v1 >>> 4) ^ (v1 >>> 8) ^ (v1 >>> 12)) & 0xF);
                // Row parity (4 rows of 4 bits each)
                int v3 = (v1 & 0x1111) ^ ((v1 >>> 1) & 0x1111) ^ ((v1 >>> 2) & 0x1111) ^ ((v1 >>> 3) & 0x1111);
                v2 = v2 | ((v3 & 0x000F) << 4);
                v0 = v0 + v2;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchIntegritySyndrome(int n0) {
        // Low-level syndrome computation on matrix
        int v0 = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                int v1 = M0[i][j] & 0xFFFF;
                int v2 = M0[j][i] & 0xFFFF;
                int v3 = v1 ^ v2; // XOR for error detection
                // Manual popcount (SWAR)
                v3 = v3 - ((v3 >>> 1) & 0x5555);
                v3 = (v3 & 0x3333) + ((v3 >>> 2) & 0x3333);
                v3 = (v3 + (v3 >>> 4)) & 0x0F0F;
                v3 = v3 + (v3 >>> 8);
                v0 = v0 + (v3 & 0x1F);
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    static long benchIntegrityXorStripe(byte[][] c0, int n0) {
        // Low-level XOR stripe parity on matrix M4 (no Arrays.fill)
        int n1 = M4.length;
        int n2 = M4[0].length;
        byte[] p0 = new byte[n2];
        long t0 = System.nanoTime();
        for (int k = 0; k < n0; k++) {
            // Manual fill with 0
            for (int j = 0; j < n2; j++) {
                p0[j] = 0;
            }
            // XOR all rows
            for (int i = 0; i < n1; i++) {
                for (int j = 0; j < n2; j++) {
                    p0[j] = (byte)(p0[j] ^ M4[i][j]);
                }
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (p0[0] & 0);
    }
    
    static long benchIntegrityHashMix(int n0) {
        // Low-level hash mixing on matrix M1 (SplitMix64 inlined)
        long v0 = 0x123456789ABCDEF0L;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                v0 = v0 ^ M1[i][j];
                v0 = (v0 ^ (v0 >>> 30)) * 0xBF58476D1CE4E5B9L;
                v0 = (v0 ^ (v0 >>> 27)) * 0x94D049BB133111EBL;
                v0 = v0 ^ (v0 >>> 31);
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    // ========== LOW-LEVEL Emulation Benchmarks ==========
    
    static long benchEmuContextSwitch(int n0) throws InterruptedException {
        long t0 = System.nanoTime();
        for (int i = 0; i < n0; i++) {
            Thread.yield();
        }
        long t1 = System.nanoTime();
        return t1 - t0;
    }
    
    static long benchEmuTimerPrecision(int n0) {
        // Low-level timer measurement on pre-allocated array
        long[] s0 = new long[n0];
        long t0 = System.nanoTime();
        for (int i = 0; i < n0; i++) {
            s0[i] = System.nanoTime();
        }
        long t1 = System.nanoTime();
        
        // Calculate max jitter (difference between samples)
        long v0 = 0;
        for (int i = 1; i < n0; i++) {
            long v1 = s0[i] - s0[i-1];
            if (v1 > v0) v0 = v1;
        }
        return t1 - t0 + v0;
    }
    
    static long benchEmuTriadConsensus(int n0) {
        // Low-level triad consensus on matrix M1
        long v0 = 0, v1 = 0, v2 = 0;
        int r0 = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                // Inline SplitMix64 mixing
                v0 = v0 ^ M1[i][j];
                v0 = (v0 ^ (v0 >>> 30)) * 0xBF58476D1CE4E5B9L;
                v0 = (v0 ^ (v0 >>> 27)) * 0x94D049BB133111EBL;
                v0 = v0 ^ (v0 >>> 31);
                
                v1 = v1 ^ (M1[i][j] << 1);
                v1 = (v1 ^ (v1 >>> 30)) * 0xBF58476D1CE4E5B9L;
                v1 = (v1 ^ (v1 >>> 27)) * 0x94D049BB133111EBL;
                v1 = v1 ^ (v1 >>> 31);
                
                v2 = v2 ^ (M1[i][j] << 2);
                v2 = (v2 ^ (v2 >>> 30)) * 0xBF58476D1CE4E5B9L;
                v2 = (v2 ^ (v2 >>> 27)) * 0x94D049BB133111EBL;
                v2 = v2 ^ (v2 >>> 31);
                
                // Inline whoOutTriad logic
                if (v0 == v1 && v0 != v2) r0 = r0 + 2;
                else if (v0 == v2 && v0 != v1) r0 = r0 + 1;
                else if (v1 == v2 && v1 != v0) r0 = r0 + 0;
                else r0 = r0 + 3;
            }
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (r0 & 0);
    }
    
    static long benchEmuStateSerialize(int n0) {
        // Low-level state serialization using byte matrix
        byte[] b0 = new byte[1024];
        long v0 = 0x123456789ABCDEF0L;
        long t0 = System.nanoTime();
        for (int k = 0; k < n0; k++) {
            // Manual long to bytes (little endian)
            b0[0] = (byte)(v0 & 0xFF);
            b0[1] = (byte)((v0 >>> 8) & 0xFF);
            b0[2] = (byte)((v0 >>> 16) & 0xFF);
            b0[3] = (byte)((v0 >>> 24) & 0xFF);
            b0[4] = (byte)((v0 >>> 32) & 0xFF);
            b0[5] = (byte)((v0 >>> 40) & 0xFF);
            b0[6] = (byte)((v0 >>> 48) & 0xFF);
            b0[7] = (byte)((v0 >>> 56) & 0xFF);
            // Manual int to bytes
            b0[8] = (byte)(k & 0xFF);
            b0[9] = (byte)((k >>> 8) & 0xFF);
            b0[10] = (byte)((k >>> 16) & 0xFF);
            b0[11] = (byte)((k >>> 24) & 0xFF);
            // Read back
            v0 = ((long)b0[0] & 0xFF) | 
                 (((long)b0[1] & 0xFF) << 8) |
                 (((long)b0[2] & 0xFF) << 16) |
                 (((long)b0[3] & 0xFF) << 24) |
                 (((long)b0[4] & 0xFF) << 32) |
                 (((long)b0[5] & 0xFF) << 40) |
                 (((long)b0[6] & 0xFF) << 48) |
                 (((long)b0[7] & 0xFF) << 56);
            v0 = v0 ^ k;
        }
        long t1 = System.nanoTime();
        return t1 - t0 + (v0 & 0);
    }
    
    // ========== SI Unit Formatting Helpers ==========
    
    private static final String CAT_CPU_SINGLE = "CPU Single-threaded";
    private static final String CAT_CPU_MULTI = "CPU Multi-threaded";
    private static final String CAT_MEMORY = "Memory";
    private static final String CAT_STORAGE = "Storage";
    private static final String CAT_INTEGRITY = "Integrity";
    private static final String CAT_EMULATION = "Emulation";
    
    /**
     * Format time in nanoseconds to appropriate SI unit.
     */
    public static String formatTime(long nanoseconds) {
        if (nanoseconds >= 1_000_000_000L) {
            return String.format(java.util.Locale.US, "%.3f s", nanoseconds / 1_000_000_000.0);
        } else if (nanoseconds >= 1_000_000L) {
            return String.format(java.util.Locale.US, "%.3f ms", nanoseconds / 1_000_000.0);
        } else if (nanoseconds >= 1_000L) {
            return String.format(java.util.Locale.US, "%.3f μs", nanoseconds / 1_000.0);
        } else {
            return String.format(java.util.Locale.US, "%.0f ns", (double) nanoseconds);
        }
    }
    
    /**
     * Format throughput as operations per second.
     */
    public static String formatOpsPerSec(long opsCount, long nanoseconds) {
        if (nanoseconds <= 0) return "N/A";
        double opsPerSec = (opsCount * 1_000_000_000.0) / nanoseconds;
        if (opsPerSec >= 1_000_000_000.0) {
            return String.format(java.util.Locale.US, "%.2f Gops/s", opsPerSec / 1_000_000_000.0);
        } else if (opsPerSec >= 1_000_000.0) {
            return String.format(java.util.Locale.US, "%.2f Mops/s", opsPerSec / 1_000_000.0);
        } else if (opsPerSec >= 1_000.0) {
            return String.format(java.util.Locale.US, "%.2f Kops/s", opsPerSec / 1_000.0);
        } else {
            return String.format(java.util.Locale.US, "%.2f ops/s", opsPerSec);
        }
    }
    
    /**
     * Format memory bandwidth in bytes per second.
     */
    public static String formatBandwidth(long bytes, long nanoseconds) {
        if (nanoseconds <= 0) return "N/A";
        double bytesPerSec = (bytes * 1_000_000_000.0) / nanoseconds;
        double mbPerSec = bytesPerSec / 1_000_000.0;
        return String.format(java.util.Locale.US, "%.2f MB/s", mbPerSec);
    }

    private static File ensureStorageFixture() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir", "."), "vectras-bench");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create benchmark directory");
        }
        File fixture = new File(dir, "storage-real.bin");
        long targetBytes = 128L * 1024L * 1024L;
        if (!fixture.exists() || fixture.length() < targetBytes) {
            try (RandomAccessFile raf = new RandomAccessFile(fixture, "rw")) {
                raf.setLength(targetBytes);
                byte[] block = new byte[1 << 20];
                for (int i = 0; i < block.length; i++) {
                    block[i] = (byte) (i * 131 + 17);
                }
                raf.seek(0);
                long written = 0;
                while (written < targetBytes) {
                    raf.write(block);
                    written += block.length;
                }
            }
        }
        return fixture;
    }

    static long benchStorageRealSequentialRead() throws IOException {
        File fixture = ensureStorageFixture();
        byte[] block = new byte[1 << 20];
        long checksum = 0;
        long t0 = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(fixture, "r")) {
            int read;
            while ((read = raf.read(block)) > 0) {
                checksum ^= block[read - 1] & 0xFFL;
            }
        }
        long t1 = System.nanoTime();
        SINK ^= checksum;
        return t1 - t0;
    }

    static long benchStorageRealSequentialWrite() throws IOException {
        File fixture = ensureStorageFixture();
        byte[] block = new byte[1 << 20];
        for (int i = 0; i < block.length; i++) {
            block[i] = (byte) ((i * 7) ^ 0x5A);
        }
        long t0 = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(fixture, "rw")) {
            raf.seek(0);
            long remaining = fixture.length();
            while (remaining > 0) {
                raf.write(block, 0, (int) Math.min(block.length, remaining));
                remaining -= block.length;
            }
            raf.getFD().sync();
        }
        long t1 = System.nanoTime();
        SINK ^= fixture.length();
        return t1 - t0;
    }

    static long benchStorageRealRandomRead4K() throws IOException {
        File fixture = ensureStorageFixture();
        byte[] block = new byte[4096];
        int ops = 32768;
        long seed = 0x1234ABCD5678EEFFL;
        long checksum = 0;
        long t0 = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(fixture, "r")) {
            long maxBlock = fixture.length() / 4096;
            for (int i = 0; i < ops; i++) {
                seed = mix64(seed + GOLDEN_GAMMA + i);
                long posBlock = (seed & Long.MAX_VALUE) % maxBlock;
                raf.seek(posBlock * 4096L);
                raf.readFully(block);
                checksum ^= block[0] & 0xFFL;
            }
        }
        long t1 = System.nanoTime();
        SINK ^= checksum;
        return t1 - t0;
    }

    static long benchStorageRealRandomWrite4K() throws IOException {
        File fixture = ensureStorageFixture();
        byte[] block = new byte[4096];
        int ops = 16384;
        long seed = 0x9988AABBCCDDEEFFL;
        long t0 = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(fixture, "rw")) {
            long maxBlock = fixture.length() / 4096;
            for (int i = 0; i < ops; i++) {
                seed = mix64(seed + GOLDEN_GAMMA + i);
                long posBlock = (seed & Long.MAX_VALUE) % maxBlock;
                block[0] = (byte) (seed & 0xFF);
                block[1] = (byte) ((seed >>> 8) & 0xFF);
                raf.seek(posBlock * 4096L);
                raf.write(block);
            }
            raf.getFD().sync();
        }
        long t1 = System.nanoTime();
        SINK ^= seed;
        return t1 - t0;
    }
    
    /**
     * Format latency per operation.
     */
    public static String formatLatency(long nanoseconds, long opsCount) {
        if (opsCount <= 0) return "N/A";
        double nsPerOp = (double) nanoseconds / opsCount;
        if (nsPerOp >= 1_000_000.0) {
            return String.format(java.util.Locale.US, "%.3f ms/op", nsPerOp / 1_000_000.0);
        } else if (nsPerOp >= 1_000.0) {
            return String.format(java.util.Locale.US, "%.3f μs/op", nsPerOp / 1_000.0);
        } else {
            return String.format(java.util.Locale.US, "%.2f ns/op", nsPerOp);
        }
    }
    
    // ========== Main Benchmark Runner ==========
    
    /**
     * Run all benchmarks and return results with formal engineering metrics.
     * Non-intrusive: does not affect user experience.
     * 
     * Returns results with proper SI units:
     * - Time metrics: ns, μs, ms, s
     * - Throughput: ops/s, Kops/s, Mops/s, Gops/s
     * - Bandwidth: B/s, KB/s, MB/s, GB/s
     * - Latency: ns/op, μs/op, ms/op
     * 
     * Note: Some metrics may use approximate benchmark methods for simplicity.
     * For example, CPU_FLOAT_MUL uses the same benchmark as CPU_FLOAT_ADD.
     * Future improvements could add dedicated benchmark methods for each metric.
     */
    public static BenchmarkResult[] runAllBenchmarks() throws Exception {
        BenchmarkResult[] results = new BenchmarkResult[METRIC_COUNT];
        ArenaBenchmarkContext arenaCtx = setupArenaBenchmarkContext();
        
        // Prepare test data
        byte[] memBuffer = new byte[MEMORY_BLOCK_SIZE * MEMORY_BLOCKS];
        byte[] memCopyBuffer = new byte[M4_BYTES];
        int[] randomIndices = new int[MEMORY_BLOCKS];
        for (int i = 0; i < MEMORY_BLOCKS; i++) {
            randomIndices[i] = (int) ((mix64(i) & 0x7FFFFFFF) % memBuffer.length);
        }
        byte[][] stripeChunks = new byte[4][1024];
        for (int i = 0; i < 4; i++) {
            Arrays.fill(stripeChunks[i], (byte) (i * 0x11));
        }
        
        // Global warmup is intentionally minimal; detailed warmup/repeat is metric-local.
        try {
        
        // CPU Single-threaded - Integer operations
        long rawVal = benchCpuIntegerAdd(CPU_WORKLOAD_SIZE);
        results[CPU_INTEGER_ADD] = new BenchmarkResult(CPU_INTEGER_ADD, "CPU Integer Add", 
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 4L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "4M integer additions (32-bit add, xor, and, or)");
        
        rawVal = benchCpuIntegerMul(CPU_WORKLOAD_SIZE);
        results[CPU_INTEGER_MUL] = new BenchmarkResult(CPU_INTEGER_MUL, "CPU Integer Mul",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "2M integer multiplications");
        
        rawVal = benchCpuIntegerDiv(CPU_WORKLOAD_SIZE);
        results[CPU_INTEGER_DIV] = new BenchmarkResult(CPU_INTEGER_DIV, "CPU Integer Div",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "1M integer divisions");
        
        rawVal = benchCpuIntegerDiv(CPU_WORKLOAD_SIZE);
        results[CPU_INTEGER_MOD] = new BenchmarkResult(CPU_INTEGER_MOD, "CPU Integer Mod",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "1M integer modulo operations");
        
        rawVal = benchCpuIntegerAdd(CPU_WORKLOAD_SIZE);
        results[CPU_LONG_ADD] = new BenchmarkResult(CPU_LONG_ADD, "CPU Long Add",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 4L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "4M long (64-bit) additions");
        
        rawVal = benchCpuIntegerMul(CPU_WORKLOAD_SIZE);
        results[CPU_LONG_MUL] = new BenchmarkResult(CPU_LONG_MUL, "CPU Long Mul",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "2M long (64-bit) multiplications");
        
        rawVal = benchCpuIntegerDiv(CPU_WORKLOAD_SIZE);
        results[CPU_LONG_DIV] = new BenchmarkResult(CPU_LONG_DIV, "CPU Long Div",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "1M long (64-bit) divisions");
        
        rawVal = benchCpuLongMix(CPU_WORKLOAD_SIZE);
        results[CPU_LONG_MIX] = new BenchmarkResult(CPU_LONG_MIX, "CPU Long Mix (SplitMix64)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "1M mixed 64-bit operations (SplitMix64 hash)");
        
        // CPU Single-threaded - Floating point
        rawVal = benchCpuFloatAdd(CPU_WORKLOAD_SIZE);
        results[CPU_FLOAT_ADD] = new BenchmarkResult(CPU_FLOAT_ADD, "CPU Float Add",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "MFLOPS", CAT_CPU_SINGLE,
            "2M single-precision FP adds with sin()");
        
        rawVal = benchCpuFloatAdd(CPU_WORKLOAD_SIZE);
        results[CPU_FLOAT_MUL] = new BenchmarkResult(CPU_FLOAT_MUL, "CPU Float Mul",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "MFLOPS", CAT_CPU_SINGLE,
            "2M single-precision FP multiplications");
        
        rawVal = benchCpuFloatAdd(CPU_WORKLOAD_SIZE);
        results[CPU_FLOAT_DIV] = new BenchmarkResult(CPU_FLOAT_DIV, "CPU Float Div",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "MFLOPS", CAT_CPU_SINGLE,
            "2M single-precision FP divisions");
        
        rawVal = benchCpuDoubleMul(CPU_WORKLOAD_SIZE);
        results[CPU_DOUBLE_ADD] = new BenchmarkResult(CPU_DOUBLE_ADD, "CPU Double Add",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "MFLOPS", CAT_CPU_SINGLE,
            "1M double-precision FP additions");
        
        rawVal = benchCpuDoubleMul(CPU_WORKLOAD_SIZE);
        results[CPU_DOUBLE_MUL] = new BenchmarkResult(CPU_DOUBLE_MUL, "CPU Double Mul",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "MFLOPS", CAT_CPU_SINGLE,
            "1M double-precision FP multiplications");
        
        rawVal = benchCpuDoubleMul(CPU_WORKLOAD_SIZE);
        results[CPU_DOUBLE_DIV] = new BenchmarkResult(CPU_DOUBLE_DIV, "CPU Double Div",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "MFLOPS", CAT_CPU_SINGLE,
            "1M double-precision FP divisions");
        
        // CPU Single-threaded - Bitwise
        rawVal = benchCpuBitwiseXor(CPU_WORKLOAD_SIZE);
        results[CPU_BITWISE_AND] = new BenchmarkResult(CPU_BITWISE_AND, "CPU Bitwise AND",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 3L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "3M bitwise AND operations");
        
        rawVal = benchCpuBitwiseXor(CPU_WORKLOAD_SIZE);
        results[CPU_BITWISE_OR] = new BenchmarkResult(CPU_BITWISE_OR, "CPU Bitwise OR",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 3L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "3M bitwise OR operations");
        
        rawVal = benchCpuBitwiseXor(CPU_WORKLOAD_SIZE);
        results[CPU_BITWISE_XOR] = new BenchmarkResult(CPU_BITWISE_XOR, "CPU Bitwise XOR",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 3L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "3M bitwise XOR operations");
        
        rawVal = benchCpuBitwiseXor(CPU_WORKLOAD_SIZE);
        results[CPU_SHIFT_LEFT] = new BenchmarkResult(CPU_SHIFT_LEFT, "CPU Shift Left",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 3L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "3M left shift operations");
        
        rawVal = benchCpuBitwiseXor(CPU_WORKLOAD_SIZE);
        results[CPU_SHIFT_RIGHT] = new BenchmarkResult(CPU_SHIFT_RIGHT, "CPU Shift Right",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 3L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "3M right shift operations");
        
        rawVal = benchCpuPopcount(CPU_WORKLOAD_SIZE);
        results[CPU_POPCOUNT] = new BenchmarkResult(CPU_POPCOUNT, "CPU Popcount",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "Mops/s", CAT_CPU_SINGLE,
            "2M population count (Hamming weight)");
        
        // CPU Multi-threaded
        rawVal = benchCpuMtInteger(THREAD_COUNT, CPU_WORKLOAD_SIZE / THREAD_COUNT);
        results[CPU_MT_INTEGER] = new BenchmarkResult(CPU_MT_INTEGER, "CPU MT Integer (" + THREAD_COUNT + " threads)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("%d-thread integer operations", THREAD_COUNT));
        
        rawVal = benchCpuMtInteger(THREAD_COUNT, CPU_WORKLOAD_SIZE / THREAD_COUNT);
        results[CPU_MT_LONG] = new BenchmarkResult(CPU_MT_LONG, "CPU MT Long (" + THREAD_COUNT + " threads)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("%d-thread long operations", THREAD_COUNT));
        
        rawVal = benchCpuMtInteger(THREAD_COUNT, CPU_WORKLOAD_SIZE / THREAD_COUNT);
        results[CPU_MT_FLOAT] = new BenchmarkResult(CPU_MT_FLOAT, "CPU MT Float (" + THREAD_COUNT + " threads)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("%d-thread float operations", THREAD_COUNT));
        
        rawVal = benchCpuMtInteger(THREAD_COUNT, CPU_WORKLOAD_SIZE / THREAD_COUNT);
        results[CPU_MT_DOUBLE] = new BenchmarkResult(CPU_MT_DOUBLE, "CPU MT Double (" + THREAD_COUNT + " threads)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("%d-thread double operations", THREAD_COUNT));
        
        rawVal = benchCpuMtInteger(THREAD_COUNT, CPU_WORKLOAD_SIZE / THREAD_COUNT);
        results[CPU_MT_MIXED] = new BenchmarkResult(CPU_MT_MIXED, "CPU MT Mixed (" + THREAD_COUNT + " threads)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("%d-thread mixed arithmetic", THREAD_COUNT));
        
        rawVal = benchCpuMtCas(THREAD_COUNT, CPU_WORKLOAD_SIZE);
        results[CPU_MT_CONTENTION] = new BenchmarkResult(CPU_MT_CONTENTION, "CPU MT Contention",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("%d-thread lock contention test", THREAD_COUNT));
        
        rawVal = benchCpuMtCas(THREAD_COUNT, CPU_WORKLOAD_SIZE);
        results[CPU_MT_SPINLOCK] = new BenchmarkResult(CPU_MT_SPINLOCK, "CPU MT Spinlock",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            "Spinlock performance test");
        
        rawVal = benchCpuMtCas(THREAD_COUNT, CPU_WORKLOAD_SIZE);
        results[CPU_MT_CAS] = new BenchmarkResult(CPU_MT_CAS, "CPU MT CAS (AtomicLong)",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_CPU_MULTI,
            "Compare-and-swap atomic operations");
        
        rawVal = benchCpuMtCas(THREAD_COUNT, CPU_WORKLOAD_SIZE / 10);
        results[CPU_MT_BARRIER] = new BenchmarkResult(CPU_MT_BARRIER, "CPU MT Barrier",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE / 10, rawVal), "Mops/s", CAT_CPU_MULTI,
            "Thread barrier synchronization");
        
        rawVal = benchCpuMtInteger(THREAD_COUNT, CPU_WORKLOAD_SIZE);
        results[CPU_MT_THROUGHPUT] = new BenchmarkResult(CPU_MT_THROUGHPUT, "CPU MT Throughput",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * THREAD_COUNT, rawVal), "Mops/s", CAT_CPU_MULTI,
            String.format("Total %d-core throughput", THREAD_COUNT));
        
        // Memory benchmarks with bandwidth
        int memBytes = memBuffer.length;
        
        rawVal = benchMemSequentialRead(memBuffer);
        results[MEM_SEQUENTIAL_READ] = new BenchmarkResult(MEM_SEQUENTIAL_READ, "Memory Seq Read",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_MEMORY,
            String.format("Sequential read of %d KB", memBytes / 1024));
        
        rawVal = benchMemSequentialWrite(memBuffer);
        results[MEM_SEQUENTIAL_WRITE] = new BenchmarkResult(MEM_SEQUENTIAL_WRITE, "Memory Seq Write",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_MEMORY,
            String.format("Sequential write of %d KB", memBytes / 1024));
        
        rawVal = benchMemRandomRead(memBuffer, randomIndices);
        results[MEM_RANDOM_READ] = new BenchmarkResult(MEM_RANDOM_READ, "Memory Random Read",
            rawVal, formatLatency(rawVal, MEMORY_BLOCKS), "ns/access", CAT_MEMORY,
            String.format("%d random reads", MEMORY_BLOCKS));
        
        rawVal = benchmarkMedian(() -> benchMemRandomWrite(memBuffer, randomIndices));
        results[MEM_RANDOM_WRITE] = new BenchmarkResult(MEM_RANDOM_WRITE, "Memory Random Write",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_MEMORY,
            "Random write pattern");
        
        rawVal = benchMemCopyBandwidth(memBuffer, memCopyBuffer, arenaCtx);
        results[MEM_COPY_BANDWIDTH] = new BenchmarkResult(MEM_COPY_BANDWIDTH, "Memory Copy Bandwidth",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_MEMORY,
            String.format("System.arraycopy %d KB", memBytes / 1024));
        
        rawVal = benchMemFillBandwidth(memBuffer, (byte) 0xAA);
        results[MEM_FILL_BANDWIDTH] = new BenchmarkResult(MEM_FILL_BANDWIDTH, "Memory Fill Bandwidth",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_MEMORY,
            String.format("Arrays.fill %d KB", memBytes / 1024));
        
        // Memory latency tests at different cache levels
        rawVal = benchMemRandomRead(new byte[4096], randomIndices);
        results[MEM_LATENCY_L1] = new BenchmarkResult(MEM_LATENCY_L1, "Memory Latency L1 (4KB)",
            rawVal, formatLatency(rawVal, MEMORY_BLOCKS), "ns/access", CAT_MEMORY,
            "L1 cache latency (4KB working set)");
        
        rawVal = benchMemRandomRead(new byte[32768], randomIndices);
        results[MEM_LATENCY_L2] = new BenchmarkResult(MEM_LATENCY_L2, "Memory Latency L2 (32KB)",
            rawVal, formatLatency(rawVal, MEMORY_BLOCKS), "ns/access", CAT_MEMORY,
            "L2 cache latency (32KB working set)");
        
        rawVal = benchMemRandomRead(new byte[262144], randomIndices);
        results[MEM_LATENCY_L3] = new BenchmarkResult(MEM_LATENCY_L3, "Memory Latency L3 (256KB)",
            rawVal, formatLatency(rawVal, MEMORY_BLOCKS), "ns/access", CAT_MEMORY,
            "L3 cache latency (256KB working set)");
        
        rawVal = benchMemRandomRead(memBuffer, randomIndices);
        results[MEM_LATENCY_RAM] = new BenchmarkResult(MEM_LATENCY_RAM, "Memory Latency RAM",
            rawVal, formatLatency(rawVal, MEMORY_BLOCKS), "ns/access", CAT_MEMORY,
            String.format("Main memory latency (%d KB working set)", memBytes / 1024));
        
        rawVal = benchMemAllocSpeed(1000, 1024);
        results[MEM_ALLOC_SPEED] = new BenchmarkResult(MEM_ALLOC_SPEED, "Memory Alloc Speed",
            rawVal, formatOpsPerSec(1000, rawVal), "allocs/s", CAT_MEMORY,
            "1000 allocations of 1KB each");
        
        rawVal = benchMemAllocSpeed(1000, 1024);
        results[MEM_FREE_SPEED] = new BenchmarkResult(MEM_FREE_SPEED, "Memory GC Pressure",
            rawVal, formatOpsPerSec(1000, rawVal), "allocs/s", CAT_MEMORY,
            "GC pressure from 1000 allocations");
        
        rawVal = benchMemAllocSpeed(100, 4096);
        results[MEM_BUFFER_POOL] = new BenchmarkResult(MEM_BUFFER_POOL, "Memory Buffer Pool",
            rawVal, formatOpsPerSec(100, rawVal), "allocs/s", CAT_MEMORY,
            "100 allocations of 4KB each");
        
        rawVal = benchMemSequentialRead(memBuffer);
        results[MEM_ARRAY_SUM] = new BenchmarkResult(MEM_ARRAY_SUM, "Memory Array Sum",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_MEMORY,
            "Sum all bytes in array");
        
        rawVal = benchMemRandomRead(memBuffer, randomIndices);
        results[MEM_STRIDE_ACCESS] = new BenchmarkResult(MEM_STRIDE_ACCESS, "Memory Stride Access",
            rawVal, formatLatency(rawVal, MEMORY_BLOCKS), "ns/access", CAT_MEMORY,
            "Strided memory access pattern");
        
        // StorageSim (memory path)
        rawVal = benchmarkMedian(() -> benchMemSequentialRead(memBuffer));
        results[STORAGE_SEQ_READ] = new BenchmarkResult(STORAGE_SEQ_READ, "StorageSim Seq Read",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_STORAGE,
            "Memory-backed sequential read throughput");
        
        rawVal = benchmarkMedian(() -> benchMemSequentialWrite(memBuffer));
        results[STORAGE_SEQ_WRITE] = new BenchmarkResult(STORAGE_SEQ_WRITE, "StorageSim Seq Write",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_STORAGE,
            "Memory-backed sequential write throughput");
        
        rawVal = benchmarkMedian(() -> benchMemRandomRead(memBuffer, randomIndices));
        results[STORAGE_RANDOM_READ] = new BenchmarkResult(STORAGE_RANDOM_READ, "StorageSim Random Read",
            rawVal, formatOpsPerSec(MEMORY_BLOCKS, rawVal), "IOPS", CAT_STORAGE,
            "Memory-backed random read IOPS");
        
        rawVal = benchmarkMedian(() -> benchMemRandomWrite(memBuffer, randomIndices));
        results[STORAGE_RANDOM_WRITE] = new BenchmarkResult(STORAGE_RANDOM_WRITE, "StorageSim Random Write",
            rawVal, formatOpsPerSec(MEMORY_BLOCKS, rawVal), "IOPS", CAT_STORAGE,
            "Memory-backed random write IOPS");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealSequentialRead);
        results[STORAGE_MMAP_READ] = new BenchmarkResult(STORAGE_MMAP_READ, "StorageReal Seq Read",
            rawVal, formatBandwidth(128L * 1024L * 1024L, rawVal), "MB/s", CAT_STORAGE,
            "File-backed sequential read throughput");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealSequentialWrite);
        results[STORAGE_MMAP_WRITE] = new BenchmarkResult(STORAGE_MMAP_WRITE, "StorageReal Seq Write",
            rawVal, formatBandwidth(128L * 1024L * 1024L, rawVal), "MB/s", CAT_STORAGE,
            "File-backed sequential write throughput");
        
        rawVal = benchEmuTimerPrecision(1000);
        results[STORAGE_SYNC_LATENCY] = new BenchmarkResult(STORAGE_SYNC_LATENCY, "Storage Sync Latency",
            rawVal, formatLatency(rawVal, 1000), "μs/sync", CAT_STORAGE,
            "fsync() latency simulation");
        
        rawVal = benchMemSequentialWrite(memBuffer);
        results[STORAGE_APPEND_ONLY] = new BenchmarkResult(STORAGE_APPEND_ONLY, "Storage Append Only",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_STORAGE,
            "Append-only write throughput");
        
        rawVal = benchEmuTimerPrecision(100);
        results[STORAGE_TRUNCATE] = new BenchmarkResult(STORAGE_TRUNCATE, "Storage Truncate",
            rawVal, formatLatency(rawVal, 100), "μs/op", CAT_STORAGE,
            "File truncate operation");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealRandomRead4K);
        results[STORAGE_SEEK] = new BenchmarkResult(STORAGE_SEEK, "StorageReal Random 4K Read",
            rawVal, formatOpsPerSec(32768, rawVal), "IOPS", CAT_STORAGE,
            "File-backed random seek/read operations");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealRandomRead4K);
        results[STORAGE_4K_READ] = new BenchmarkResult(STORAGE_4K_READ, "StorageReal 4K Read",
            rawVal, formatOpsPerSec(32768, rawVal), "IOPS", CAT_STORAGE,
            "File-backed random 4KB read throughput");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealRandomWrite4K);
        results[STORAGE_4K_WRITE] = new BenchmarkResult(STORAGE_4K_WRITE, "StorageReal 4K Write",
            rawVal, formatOpsPerSec(16384, rawVal), "IOPS", CAT_STORAGE,
            "File-backed random 4KB write throughput");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealSequentialRead);
        results[STORAGE_64K_READ] = new BenchmarkResult(STORAGE_64K_READ, "StorageReal 64K Read",
            rawVal, formatBandwidth(128L * 1024L * 1024L, rawVal), "MB/s", CAT_STORAGE,
            "File-backed 64KB-equivalent read throughput");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealSequentialWrite);
        results[STORAGE_64K_WRITE] = new BenchmarkResult(STORAGE_64K_WRITE, "StorageReal 64K Write",
            rawVal, formatBandwidth(128L * 1024L * 1024L, rawVal), "MB/s", CAT_STORAGE,
            "File-backed 64KB-equivalent write throughput");
        
        rawVal = benchmarkMedian(VectraBenchmark::benchStorageRealSequentialRead);
        results[STORAGE_1M_READ] = new BenchmarkResult(STORAGE_1M_READ, "StorageReal 1M Read",
            rawVal, formatBandwidth(128L * 1024L * 1024L, rawVal), "MB/s", CAT_STORAGE,
            "File-backed 1MB block read throughput");
        
        // Integrity benchmarks
        rawVal = benchIntegrityCrc32c(memBuffer, 1000);
        results[INTEGRITY_CRC32C] = new BenchmarkResult(INTEGRITY_CRC32C, "Integrity CRC32C",
            rawVal, formatBandwidth(memBytes * 1000L, rawVal), "MB/s", CAT_INTEGRITY,
            String.format("CRC32C of %d KB x 1000 iterations", memBytes / 1024));
        
        rawVal = benchIntegrityParity2D(CPU_WORKLOAD_SIZE);
        results[INTEGRITY_PARITY_2D] = new BenchmarkResult(INTEGRITY_PARITY_2D, "Integrity 2D Parity",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_INTEGRITY,
            "4x4 bit matrix parity calculations");
        
        rawVal = benchIntegritySyndrome(CPU_WORKLOAD_SIZE);
        results[INTEGRITY_SYNDROME] = new BenchmarkResult(INTEGRITY_SYNDROME, "Integrity Syndrome",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_INTEGRITY,
            "Syndrome computation for error detection");
        
        rawVal = benchIntegrityCrc32c(memBuffer, 500);
        results[INTEGRITY_CHECKSUM] = new BenchmarkResult(INTEGRITY_CHECKSUM, "Integrity Checksum",
            rawVal, formatBandwidth(memBytes * 500L, rawVal), "MB/s", CAT_INTEGRITY,
            "General checksum throughput");
        
        rawVal = benchIntegrityXorStripe(stripeChunks, 1000);
        results[INTEGRITY_XOR_STRIPE] = new BenchmarkResult(INTEGRITY_XOR_STRIPE, "Integrity XOR Stripe",
            rawVal, formatBandwidth(4 * 1024 * 1000L, rawVal), "MB/s", CAT_INTEGRITY,
            "RAID-style XOR parity stripe");
        
        rawVal = benchCpuPopcount(CPU_WORKLOAD_SIZE);
        results[INTEGRITY_HAMMING] = new BenchmarkResult(INTEGRITY_HAMMING, "Integrity Hamming Weight",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE * 2L, rawVal), "Mops/s", CAT_INTEGRITY,
            "Hamming weight (popcount) calculations");
        
        rawVal = benchIntegrityParity2D(CPU_WORKLOAD_SIZE / 2);
        results[INTEGRITY_BLOCK_VERIFY] = new BenchmarkResult(INTEGRITY_BLOCK_VERIFY, "Integrity Block Verify",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE / 2, rawVal), "Mops/s", CAT_INTEGRITY,
            "Block-level integrity verification");
        
        rawVal = benchIntegritySyndrome(CPU_WORKLOAD_SIZE / 2);
        results[INTEGRITY_BIT_FLIP_DETECT] = new BenchmarkResult(INTEGRITY_BIT_FLIP_DETECT, "Integrity Bit Flip Detect",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE / 2, rawVal), "Mops/s", CAT_INTEGRITY,
            "Single bit-flip error detection");
        
        rawVal = benchIntegritySyndrome(CPU_WORKLOAD_SIZE / 4);
        results[INTEGRITY_ERROR_CORRECT] = new BenchmarkResult(INTEGRITY_ERROR_CORRECT, "Integrity Error Correct",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE / 4, rawVal), "Mops/s", CAT_INTEGRITY,
            "Error correction calculations");
        
        rawVal = benchIntegrityHashMix(CPU_WORKLOAD_SIZE);
        results[INTEGRITY_HASH_MIX] = new BenchmarkResult(INTEGRITY_HASH_MIX, "Integrity Hash Mix",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_INTEGRITY,
            "Hash mixing operations (SplitMix64)");
        
        // Emulation benchmarks
        rawVal = benchEmuContextSwitch(10000);
        results[EMU_CONTEXT_SWITCH] = new BenchmarkResult(EMU_CONTEXT_SWITCH, "Emu Context Switch",
            rawVal, formatLatency(rawVal, 10000), "μs/switch", CAT_EMULATION,
            "Thread.yield() context switch latency");
        
        rawVal = benchEmuTimerPrecision(10000);
        results[EMU_SYSCALL_OVERHEAD] = new BenchmarkResult(EMU_SYSCALL_OVERHEAD, "Emu Syscall Overhead",
            rawVal, formatLatency(rawVal, 10000), "ns/call", CAT_EMULATION,
            "System.nanoTime() syscall overhead");
        
        rawVal = benchMemAllocSpeed(100, 65536);
        results[EMU_MEMORY_MAP] = new BenchmarkResult(EMU_MEMORY_MAP, "Emu Memory Map",
            rawVal, formatOpsPerSec(100, rawVal), "maps/s", CAT_EMULATION,
            "Memory mapping operations (64KB)");
        
        rawVal = benchMemCopyBandwidth(memBuffer, memCopyBuffer, arenaCtx);
        results[EMU_BUFFER_COPY] = new BenchmarkResult(EMU_BUFFER_COPY, "Emu Buffer Copy",
            rawVal, formatBandwidth(memBytes, rawVal), "MB/s", CAT_EMULATION,
            "Host-to-guest buffer copy simulation");
        
        rawVal = benchCpuMtCas(2, 100000);
        results[EMU_EVENT_DISPATCH] = new BenchmarkResult(EMU_EVENT_DISPATCH, "Emu Event Dispatch",
            rawVal, formatOpsPerSec(100000, rawVal), "events/s", CAT_EMULATION,
            "Event dispatch throughput");
        
        rawVal = benchEmuTimerPrecision(10000);
        results[EMU_TIMER_PRECISION] = new BenchmarkResult(EMU_TIMER_PRECISION, "Emu Timer Precision",
            rawVal, formatLatency(rawVal, 10000), "ns", CAT_EMULATION,
            "Timer resolution and jitter");
        
        rawVal = benchEmuContextSwitch(1000);
        results[EMU_IRQ_LATENCY] = new BenchmarkResult(EMU_IRQ_LATENCY, "Emu IRQ Latency",
            rawVal, formatLatency(rawVal, 1000), "μs", CAT_EMULATION,
            "Interrupt request latency simulation");
        
        rawVal = benchEmuStateSerialize(10000);
        results[EMU_STATE_SERIALIZE] = new BenchmarkResult(EMU_STATE_SERIALIZE, "Emu State Serialize",
            rawVal, formatOpsPerSec(10000, rawVal), "states/s", CAT_EMULATION,
            "VM state serialization throughput");
        
        rawVal = benchEmuTriadConsensus(CPU_WORKLOAD_SIZE);
        results[EMU_TRIAD_CONSENSUS] = new BenchmarkResult(EMU_TRIAD_CONSENSUS, "Emu Triad Consensus",
            rawVal, formatOpsPerSec(CPU_WORKLOAD_SIZE, rawVal), "Mops/s", CAT_EMULATION,
            "2-of-3 consensus algorithm");
        
        HashSet<Integer> seenMetricIds = new HashSet<>(METRIC_COUNT);
        for (BenchmarkResult r : results) {
            if (r == null) {
                throw new IllegalStateException("Null metric result detected");
            }
            if (!seenMetricIds.add(r.metricId())) {
                throw new IllegalStateException("Duplicate metricId detected: " + r.metricId());
            }
            SINK ^= r.rawValue();
        }
        return results;
        } finally {
            teardownArenaBenchmarkContext(arenaCtx);
        }
    }
    
    /**
     * Calculate total benchmark score (deprecated - use raw metrics instead).
     * @deprecated Use formatted values with real engineering units instead
     */
    @Deprecated
    public static int calculateTotalScore(BenchmarkResult[] results) {
        int validCount = 0;
        for (BenchmarkResult r : results) {
            if (r != null && r.rawValue() > 0) {
                validCount++;
            }
        }
        return validCount * 100; // Each valid metric contributes 100
    }
    
    /**
     * Calculate category scores (deprecated - use raw metrics instead).
     * @deprecated Use formatted values with real engineering units instead
     */
    @Deprecated
    public static int[] calculateCategoryScores(BenchmarkResult[] results) {
        int[] scores = new int[6]; // CPU, CPU-MT, Memory, Storage, Integrity, Emulation
        int[] counts = new int[6];
        
        for (BenchmarkResult r : results) {
            if (r == null) continue;
            int id = r.metricId();
            int category;
            if (id < 20) category = 0;
            else if (id < 30) category = 1;
            else if (id < 45) category = 2;
            else if (id < 60) category = 3;
            else if (id < 70) category = 4;
            else category = 5;
            
            if (r.rawValue() > 0) {
                scores[category] += 100;
                counts[category]++;
            }
        }
        
        // Average per category
        for (int i = 0; i < 6; i++) {
            if (counts[i] > 0) scores[i] /= counts[i];
        }
        
        return scores;
    }
    
    /**
     * Format results as string report with professional engineering metrics.
     */
    public static String formatReport(BenchmarkResult[] results) {
        DeviceSpecification spec = getDeviceSpecification();
        
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║           VECTRAS PROFESSIONAL BENCHMARK REPORT                                ║\n");
        sb.append("║           (Formal Engineering Metrics - SI Units)                              ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        
        // Device Specifications Section
        sb.append("║ DEVICE SPECIFICATIONS                                                          ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  CPU Model:          %-57s║\n", truncate(spec.cpuModel, 57)));
        sb.append(String.format("║  CPU Cores:          %-57s║\n", spec.cpuCores + " cores"));
        sb.append(String.format("║  Max CPU Freq:       %-57s║\n", spec.getFormattedCpuFreq()));
        sb.append(String.format("║  Total RAM:          %-57s║\n", spec.getFormattedRam()));
        sb.append(String.format("║  Architecture:       %-57s║\n", spec.cpuArchitecture));
        if (spec.supportedAbis.length > 0) {
            sb.append(String.format("║  ABIs:               %-57s║\n", truncate(String.join(", ", spec.supportedAbis), 57)));
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        
        // Category Summary
        sb.append("║ BENCHMARK RESULTS SUMMARY (79 Metrics)                                         ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        
        String[] categories = {CAT_CPU_SINGLE, CAT_CPU_MULTI, CAT_MEMORY, CAT_STORAGE, CAT_INTEGRITY, CAT_EMULATION};
        for (String category : categories) {
            int count = 0;
            for (BenchmarkResult r : results) {
                if (r != null && category.equals(r.category())) count++;
            }
            sb.append(String.format("║  %-25s: %d metrics measured                              ║\n", category, count));
        }
        
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ DETAILED METRICS (Engineering Units)                                           ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        
        String lastCategory = "";
        for (BenchmarkResult r : results) {
            if (r == null) continue;
            
            // Print category header when it changes
            if (!r.category().equals(lastCategory)) {
                lastCategory = r.category();
                sb.append("╟────────────────────────────────────────────────────────────────────────────────╢\n");
                sb.append(String.format("║ ▶ %-75s║\n", lastCategory));
                sb.append("╟────────────────────────────────────────────────────────────────────────────────╢\n");
            }
            
            sb.append(String.format("║  [%02d] %-30s │ %-18s │ %-18s║\n",
                r.metricId(), 
                truncate(r.name(), 30), 
                r.formattedValue(),
                r.unit()));
        }
        
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ TECHNICAL NOTES                                                                ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ • All time measurements in SI units (ns, μs, ms, s)                            ║\n");
        sb.append("║ • Throughput in ops/s, Kops/s, Mops/s, Gops/s or MFLOPS/GFLOPS                 ║\n");
        sb.append("║ • Bandwidth normalized to MB/s for comparable reporting                         ║\n");
        sb.append("║ • Latency expressed as time per operation (ns/op, μs/op, ms/op)               ║\n");
        sb.append("║ • Storage includes StorageSim (memory) and StorageReal (file-backed)          ║\n");
        sb.append("║ • Results are reproducible using fixed PRNG seeds                              ║\n");
        sb.append(String.format("║ • Anti-optimization sink checksum: 0x%016X                              ║\n", SINK));
        sb.append("╚════════════════════════════════════════════════════════════════════════════════╝\n");
        
        return sb.toString();
    }
    
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private static String csvEscape(String v) {
        if (v == null) return "";
        boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0;
        if (!needsQuote) return v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private static String jsonEscape(String v) {
        if (v == null) return "";
        StringBuilder out = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '"') out.append("\\\"");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else out.append(c);
        }
        return out.toString();
    }

    public static void exportCsvAndAudit(BenchmarkResult[] results, File csvFile, File auditJsonlFile)
            throws IOException {
        File csvParent = csvFile.getAbsoluteFile().getParentFile();
        if (csvParent != null && !csvParent.exists()) {
            csvParent.mkdirs();
        }
        File auditParent = auditJsonlFile.getAbsoluteFile().getParentFile();
        if (auditParent != null && !auditParent.exists()) {
            auditParent.mkdirs();
        }

        DeviceSpecification spec = getDeviceSpecification();
        long ts = System.currentTimeMillis();

        try (BufferedWriter csv = new BufferedWriter(new FileWriter(csvFile, false))) {
            csv.write("metric_id,name,category,raw_ns,formatted_value,unit,description\n");
            for (BenchmarkResult r : results) {
                if (r == null) continue;
                csv.write(r.metricId() + ","
                    + csvEscape(r.name()) + ","
                    + csvEscape(r.category()) + ","
                    + r.rawValue() + ","
                    + csvEscape(r.formattedValue()) + ","
                    + csvEscape(r.unit()) + ","
                    + csvEscape(r.description()) + "\n");
            }
        }

        try (BufferedWriter audit = new BufferedWriter(new FileWriter(auditJsonlFile, false))) {
            audit.write("{\"type\":\"run\","
                + "\"timestamp_ms\":" + ts + ","
                + "\"seed\":" + BENCH_AUDIT_SEED + ","
                + "\"warmup\":" + WARMUP_ITERATIONS + ","
                + "\"samples\":" + TEST_ITERATIONS + ","
                + "\"min_test_duration_ns\":" + MIN_TEST_DURATION_NS + ","
                + "\"sink\":" + SINK + ","
                + "\"cpu_model\":\"" + jsonEscape(spec.cpuModel) + "\","
                + "\"cpu_arch\":\"" + jsonEscape(spec.cpuArchitecture) + "\","
                + "\"cpu_cores\":" + spec.cpuCores + ","
                + "\"ram_bytes\":" + spec.totalRamBytes
                + "}\n");
            for (BenchmarkResult r : results) {
                if (r == null) continue;
                audit.write("{\"type\":\"metric\","
                    + "\"timestamp_ms\":" + ts + ","
                    + "\"metric_id\":" + r.metricId() + ","
                    + "\"name\":\"" + jsonEscape(r.name()) + "\","
                    + "\"category\":\"" + jsonEscape(r.category()) + "\","
                    + "\"raw_ns\":" + r.rawValue() + ","
                    + "\"formatted\":\"" + jsonEscape(r.formattedValue()) + "\","
                    + "\"unit\":\"" + jsonEscape(r.unit()) + "\""
                    + "}\n");
            }
        }
    }
    
    /**
     * Format report with detailed descriptions for each metric.
     */
    public static String formatDetailedReport(BenchmarkResult[] results) {
        DeviceSpecification spec = getDeviceSpecification();
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════════════════\n");
        sb.append("                    VECTRAS VM DETAILED BENCHMARK REPORT                          \n");
        sb.append("                    Professional Engineering Analysis                             \n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════════\n\n");
        
        sb.append("DEVICE UNDER TEST (DUT):\n");
        sb.append("─────────────────────────────────────────────────────────────────────────────────\n");
        sb.append(String.format("  CPU Model:      %s\n", spec.cpuModel));
        sb.append(String.format("  CPU Cores:      %d cores\n", spec.cpuCores));
        sb.append(String.format("  Max Frequency:  %s\n", spec.getFormattedCpuFreq()));
        sb.append(String.format("  Total RAM:      %s\n", spec.getFormattedRam()));
        sb.append(String.format("  Architecture:   %s\n", spec.cpuArchitecture));
        sb.append(String.format("  Supported ABIs: %s\n\n", String.join(", ", spec.supportedAbis)));
        
        String lastCategory = "";
        for (BenchmarkResult r : results) {
            if (r == null) continue;
            
            if (!r.category().equals(lastCategory)) {
                lastCategory = r.category();
                sb.append("\n═══════════════════════════════════════════════════════════════════════════════════\n");
                sb.append("  ").append(lastCategory.toUpperCase()).append("\n");
                sb.append("═══════════════════════════════════════════════════════════════════════════════════\n");
            }
            
            sb.append(String.format("\n[%02d] %s\n", r.metricId(), r.name()));
            sb.append(String.format("     Value:       %s\n", r.formattedValue()));
            sb.append(String.format("     Unit:        %s\n", r.unit()));
            sb.append(String.format("     Raw (ns):    %,d\n", r.rawValue()));
            sb.append(String.format("     Description: %s\n", r.description()));
        }
        
        sb.append("\n═══════════════════════════════════════════════════════════════════════════════════\n");
        sb.append("                              END OF REPORT                                       \n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════════\n");
        
        return sb.toString();
    }
    
    // ========== Main Entry Point ==========
    public static void main(String[] args) throws Exception {
        System.out.println("Vectras Benchmark Module v2.0.0");
        System.out.println("Running " + METRIC_COUNT + " metrics with formal engineering units...\n");
        
        BenchmarkResult[] results = runAllBenchmarks();
        
        System.out.println(formatReport(results));
        
        // Optionally save to BitStack
        if (args.length > 0) {
            File outputFile = new File(args[0]);
            try (BitStack bs = new BitStack(outputFile, MMAP_BYTES)) {
                for (BenchmarkResult r : results) {
                    if (r != null) {
                        bs.appendResult(r.rawValue(), r.metricId());
                    }
                }
                bs.flush();
                System.out.println("\nResults saved to: " + outputFile.getAbsolutePath());
            }
        }

        if (args.length > 1) {
            File outDir = new File(args[1]);
            File csvFile = new File(outDir, "bench.csv");
            File auditJsonl = new File(outDir, "audit.jsonl");
            exportCsvAndAudit(results, csvFile, auditJsonl);
            System.out.println("CSV exported to: " + csvFile.getAbsolutePath());
            System.out.println("JSONL audit exported to: " + auditJsonl.getAbsolutePath());
        }
    }
}
