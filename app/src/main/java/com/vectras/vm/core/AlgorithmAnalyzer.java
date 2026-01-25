package com.vectras.vm.core;

/**
 * AlgorithmAnalyzer: Heuristic analysis framework for code optimization.
 * 
 * <p>This class provides tools for analyzing algorithms and data structures:
 * - Complexity analysis (time/space)
 * - Performance profiling and pattern detection
 * - Cache efficiency analysis
 * - Branch prediction analysis
 * - Memory access pattern optimization
 * </p>
 * 
 * <h2>Analysis Categories:</h2>
 * <ul>
 *   <li>Temporal Analysis: Execution time patterns</li>
 *   <li>Spatial Analysis: Memory usage patterns</li>
 *   <li>Locality Analysis: Cache behavior</li>
 *   <li>Branching Analysis: Control flow patterns</li>
 *   <li>Parallelism Analysis: Concurrency opportunities</li>
 * </ul>
 * 
 * @author Vectras Team
 * @version 1.0.0
 */
public final class AlgorithmAnalyzer {

    // ========== Constants ==========
    
    /** Cache line size (typical) */
    private static final int CACHE_LINE_SIZE = 64;
    
    /** L1 cache size estimate (KB) */
    private static final int L1_CACHE_KB = 32;
    
    /** L2 cache size estimate (KB) */
    private static final int L2_CACHE_KB = 256;
    
    /** Page size for memory analysis */
    private static final int PAGE_SIZE = 4096;

    private static final ThreadLocal<CacheLineScratch> CACHE_LINE_SCRATCH =
            ThreadLocal.withInitial(CacheLineScratch::new);

    private static final class CacheLineScratch {
        private int[] lineStamps;
        private int stamp;

        private int[] ensureCapacity(int length) {
            if (lineStamps == null || lineStamps.length < length) {
                lineStamps = new int[length];
                stamp = 1;
            } else if (stamp == Integer.MAX_VALUE) {
                java.util.Arrays.fill(lineStamps, 0);
                stamp = 1;
            } else {
                stamp++;
            }
            return lineStamps;
        }
    }
    
    // ========== Private Constructor ==========
    
    private AlgorithmAnalyzer() {
        throw new AssertionError("AlgorithmAnalyzer is a utility class");
    }

    // ========== Complexity Analysis ==========
    
    /**
     * Analyzes time complexity by measuring growth rate.
     * 
     * @param operation Operation to analyze
     * @param sizes Array of input sizes to test
     * @return Complexity estimate (1=O(1), 2=O(log n), 3=O(n), 4=O(n log n), 5=O(n²))
     */
    public static int estimateTimeComplexity(TimedOperation operation, int[] sizes) {
        if (sizes.length < 3) return -1;
        
        long[] times = new long[sizes.length];
        
        // Measure execution times
        for (int i = 0; i < sizes.length; i++) {
            // Warmup
            operation.execute(sizes[i]);
            
            // Measure
            long start = System.nanoTime();
            operation.execute(sizes[i]);
            times[i] = System.nanoTime() - start;
        }
        
        // Analyze growth pattern
        double[] ratios = new double[sizes.length - 1];
        double[] sizeRatios = new double[sizes.length - 1];
        
        for (int i = 1; i < sizes.length; i++) {
            ratios[i - 1] = (double) times[i] / times[i - 1];
            sizeRatios[i - 1] = (double) sizes[i] / sizes[i - 1];
        }
        
        // Average ratio
        double avgRatio = 0.0;
        double avgSizeRatio = 0.0;
        for (int i = 0; i < ratios.length; i++) {
            avgRatio += ratios[i];
            avgSizeRatio += sizeRatios[i];
        }
        avgRatio /= ratios.length;
        avgSizeRatio /= sizeRatios.length;
        
        // Classify complexity
        if (avgRatio < 1.2) return 1; // O(1)
        if (avgRatio < avgSizeRatio * 0.5) return 2; // O(log n)
        if (avgRatio < avgSizeRatio * 1.5) return 3; // O(n)
        if (avgRatio < avgSizeRatio * 2.0) return 4; // O(n log n)
        return 5; // O(n²) or worse
    }
    
    /**
     * Estimates space complexity by tracking allocations.
     * 
     * @param operation Operation to analyze
     * @param size Input size
     * @return Space complexity estimate in bytes
     */
    public static long estimateSpaceComplexity(TimedOperation operation, int size) {
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC to get clean baseline
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        long before = runtime.totalMemory() - runtime.freeMemory();
        operation.execute(size);
        long after = runtime.totalMemory() - runtime.freeMemory();
        
        return Math.max(0, after - before);
    }

    // ========== Cache Analysis ==========
    
    /**
     * Analyzes cache efficiency by measuring memory access patterns.
     * Returns cache miss ratio estimate (0.0 = all hits, 1.0 = all misses).
     * 
     * @param data Data array to analyze
     * @param accessPattern Access indices
     * @return Cache miss ratio estimate
     */
    public static double analyzeCacheEfficiency(byte[] data, int[] accessPattern) {
        if (data == null || accessPattern == null || accessPattern.length == 0) {
            return 0.0;
        }
        
        // Track unique cache lines accessed
        int cacheLineCount = data.length / CACHE_LINE_SIZE + 1;
        CacheLineScratch scratch = CACHE_LINE_SCRATCH.get();
        int[] cacheLineStamps = scratch.ensureCapacity(cacheLineCount);
        int stamp = scratch.stamp;
        int sequentialHits = 0;
        int totalAccesses = accessPattern.length;
        
        int lastLine = -1;
        for (int idx : accessPattern) {
            if (idx < 0 || idx >= data.length) continue;
            
            int line = idx / CACHE_LINE_SIZE;
            
            // Sequential access likely cached
            if (line == lastLine || line == lastLine + 1) {
                sequentialHits++;
            }
            
            cacheLineStamps[line] = stamp;
            lastLine = line;
        }
        
        // Estimate: more unique lines + less sequential = more misses
        int uniqueLines = 0;
        for (int i = 0; i < cacheLineCount; i++) {
            if (cacheLineStamps[i] == stamp) {
                uniqueLines++;
            }
        }
        
        double sequentialRatio = (double) sequentialHits / totalAccesses;
        double uniqueRatio = (double) uniqueLines / totalAccesses;
        
        // Cache miss estimate
        return Math.max(0.0, Math.min(1.0, uniqueRatio * (1.0 - sequentialRatio)));
    }
    
    /**
     * Suggests optimal data structure layout for cache efficiency.
     * 
     * @param elementSize Size of each element in bytes
     * @param accessPattern Expected access pattern
     * @return Recommended layout (0=AoS, 1=SoA, 2=AoSoA)
     */
    public static int suggestDataLayout(int elementSize, AccessPattern accessPattern) {
        // Array of Structures (AoS) - good for scattered access
        // Structure of Arrays (SoA) - good for vectorization
        // Array of Structures of Arrays (AoSoA) - hybrid
        
        switch (accessPattern) {
            case SEQUENTIAL:
                return elementSize > CACHE_LINE_SIZE ? 1 : 0; // SoA if large
            case RANDOM:
                return 0; // AoS for random access
            case STRIDED:
                return 2; // AoSoA for strided patterns
            case VECTORIZED:
                return 1; // SoA for SIMD
            default:
                return 0;
        }
    }

    // ========== Branch Analysis ==========
    
    /**
     * Analyzes branch prediction efficiency.
     * Measures pattern predictability in conditional branches.
     * 
     * @param branches Array of branch outcomes (true/false)
     * @return Predictability score (0.0 = unpredictable, 1.0 = perfectly predictable)
     */
    public static double analyzeBranchPredictability(boolean[] branches) {
        if (branches == null || branches.length < 2) return 1.0;
        
        // Count pattern matches for simple 2-bit predictor
        int correct = 0;
        int total = 0;
        
        // 2-bit saturating counter predictor simulation
        int predictor = 1; // Start with weak taken
        
        for (boolean branch : branches) {
            int prediction = (predictor >= 2) ? 1 : 0;
            int actual = branch ? 1 : 0;
            
            if (prediction == actual) {
                correct++;
            }
            
            // Update predictor
            if (branch) {
                predictor = Math.min(3, predictor + 1);
            } else {
                predictor = Math.max(0, predictor - 1);
            }
            
            total++;
        }
        
        return (double) correct / total;
    }
    
    /**
     * Suggests branchless alternative feasibility.
     * 
     * @param branchCount Number of branches in code path
     * @param branchPredictability Predictability score (0.0-1.0)
     * @return true if branchless likely better
     */
    public static boolean suggestBranchless(int branchCount, double branchPredictability) {
        // Branchless beneficial if:
        // - Many branches (>3) with low predictability (<0.8)
        // - Or very low predictability (<0.5) even with few branches
        return (branchCount > 3 && branchPredictability < 0.8) ||
               (branchPredictability < 0.5);
    }

    // ========== Parallelism Analysis ==========
    
    /**
     * Analyzes data dependency for parallel potential.
     * 
     * @param dependencies Array where dependencies[i] = j means i depends on j
     * @return Parallelism factor (1.0 = fully serial, higher = more parallel)
     */
    public static double analyzeParallelism(int[] dependencies) {
        if (dependencies == null || dependencies.length == 0) return 1.0;
        
        int n = dependencies.length;
        int[] depth = new int[n];
        
        // Calculate critical path depth
        for (int i = 0; i < n; i++) {
            int dep = dependencies[i];
            if (dep >= 0 && dep < n) {
                depth[i] = depth[dep] + 1;
            }
        }
        
        // Find maximum depth
        int maxDepth = 0;
        for (int d : depth) {
            maxDepth = Math.max(maxDepth, d);
        }
        
        // Parallelism = work / critical path length
        return (double) n / (maxDepth + 1);
    }
    
    /**
     * Suggests optimal thread count for task.
     * 
     * @param taskCount Number of tasks
     * @param taskGranularity Size of each task (smaller = finer grain)
     * @return Recommended thread count
     */
    public static int suggestThreadCount(int taskCount, int taskGranularity) {
        int cores = Runtime.getRuntime().availableProcessors();
        
        // Fine-grained tasks: use fewer threads to reduce overhead
        if (taskGranularity < 1000) {
            return Math.min(cores, Math.max(1, taskCount / 10));
        }
        
        // Coarse-grained tasks: can use more threads
        return Math.min(cores, Math.max(1, taskCount / 2));
    }

    // ========== Performance Metrics ==========
    
    /**
     * Analyzes instruction-level parallelism potential.
     * 
     * @param operations Array of operation types
     * @return ILP score (1.0 = fully serial, higher = more ILP)
     */
    public static double analyzeILP(OperationType[] operations) {
        if (operations == null || operations.length < 2) return 1.0;
        
        // Count independent operations
        int independent = 0;
        OperationType prev = operations[0];
        
        for (int i = 1; i < operations.length; i++) {
            OperationType curr = operations[i];
            
            // Assume independence if different types (simplification)
            if (curr != prev) {
                independent++;
            }
            
            prev = curr;
        }
        
        return 1.0 + ((double) independent / operations.length);
    }
    
    /**
     * Estimates vectorization potential (SIMD).
     * 
     * @param dataSize Size of data array
     * @param elementSize Size of each element
     * @param hasDependencies Whether loop has data dependencies
     * @return Vectorization speedup estimate (1.0 = no benefit, 4.0 = 4x speedup)
     */
    public static double estimateVectorizationPotential(
            int dataSize, int elementSize, boolean hasDependencies) {
        
        if (hasDependencies) return 1.0;
        
        // Estimate SIMD width
        int simdWidth = 128 / (elementSize * 8); // 128-bit SIMD
        if (dataSize < simdWidth * 4) return 1.0; // Too small
        
        // Potential speedup (realistic: 60-80% of theoretical)
        return Math.min(4.0, simdWidth * 0.7);
    }

    // ========== Utility Methods ==========
    
    /**
     * Generates optimization report for algorithm.
     * 
     * @param name Algorithm name
     * @param timeComplexity Time complexity code (1-5)
     * @param spaceComplexity Space in bytes
     * @param cacheEfficiency Cache miss ratio (0.0-1.0)
     * @param parallelism Parallelism factor
     * @return Human-readable report
     */
    public static String generateReport(
            String name,
            int timeComplexity,
            long spaceComplexity,
            double cacheEfficiency,
            double parallelism) {
        
        StringBuilder report = new StringBuilder();
        report.append("=== Algorithm Analysis: ").append(name).append(" ===\n");
        
        // Time complexity
        String[] complexities = {"O(1)", "O(log n)", "O(n)", "O(n log n)", "O(n²)"};
        report.append("Time Complexity: ");
        if (timeComplexity >= 1 && timeComplexity <= 5) {
            report.append(complexities[timeComplexity - 1]);
        } else {
            report.append("Unknown");
        }
        report.append("\n");
        
        // Space complexity
        report.append("Space Complexity: ");
        if (spaceComplexity < 1024) {
            report.append(spaceComplexity).append(" bytes");
        } else if (spaceComplexity < 1024 * 1024) {
            report.append(spaceComplexity / 1024).append(" KB");
        } else {
            report.append(spaceComplexity / (1024 * 1024)).append(" MB");
        }
        report.append("\n");
        
        // Cache efficiency
        report.append("Cache Efficiency: ");
        report.append(String.format("%.1f%%", (1.0 - cacheEfficiency) * 100));
        report.append(" hit rate\n");
        
        // Parallelism
        report.append("Parallelism Factor: ");
        report.append(String.format("%.2fx", parallelism));
        report.append("\n");
        
        // Recommendations
        report.append("\nRecommendations:\n");
        if (cacheEfficiency > 0.3) {
            report.append("- Consider improving data locality\n");
        }
        if (parallelism > 2.0) {
            report.append("- Good candidate for parallelization\n");
        }
        if (timeComplexity >= 5) {
            report.append("- Consider algorithmic optimization\n");
        }
        
        return report.toString();
    }

    // ========== Interfaces ==========
    
    public interface TimedOperation {
        void execute(int size);
    }
    
    public enum AccessPattern {
        SEQUENTIAL,
        RANDOM,
        STRIDED,
        VECTORIZED
    }
    
    public enum OperationType {
        LOAD,
        STORE,
        ALU,
        FPU,
        BRANCH,
        CALL
    }
}
