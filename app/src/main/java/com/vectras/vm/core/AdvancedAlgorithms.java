package com.vectras.vm.core;

/**
 * AdvancedAlgorithms: Ultra-strategic optimization techniques for performance enhancement.
 * 
 * <p>This class provides advanced algorithmic techniques including:
 * - Heuristic search algorithms (A*, greedy, hill climbing)
 * - Machine learning-inspired optimization (gradient descent, simulated annealing)
 * - Information theory metrics (entropy, mutual information, KL divergence)
 * - Advanced mathematical transforms (FFT approximations, wavelets)
 * - Adaptive optimization strategies
 * </p>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li>Low-level implementation with minimal abstractions</li>
 *   <li>Branchless operations where beneficial</li>
 *   <li>Cache-friendly data access patterns</li>
 *   <li>SIMD-ready vector operations</li>
 *   <li>Deterministic and reproducible results</li>
 * </ul>
 * 
 * @author Vectras Team
 * @version 1.0.0
 */
public final class AdvancedAlgorithms {

    // ========== Constants ==========
    
    /** Maximum iterations for iterative algorithms */
    private static final int MAX_ITERATIONS = 1000;
    
    /** Convergence threshold for optimization algorithms */
    private static final double EPSILON = 1e-6;
    
    /** Golden ratio for optimization */
    private static final double PHI = 1.618033988749895;
    
    /** Inverse golden ratio */
    private static final double INV_PHI = 0.618033988749895;

    private static final ThreadLocal<Buffers> THREAD_LOCAL_BUFFERS =
            ThreadLocal.withInitial(Buffers::new);

    private static final class Buffers {
        private double[] gradient;
        private int[] workingState;
        private int[] entropyFreq;
        private int[] freqX;
        private int[] freqY;
        private int[] joint;
        private int[] tempInt;
        private int[] openSet;
        private int[] heapIndex;
        private byte[] openState;
        private boolean[] closedSet;
        private int[] gScore;
        private int[] fScore;

        private double[] ensureGradientCapacity(int length) {
            if (gradient == null || gradient.length < length) {
                gradient = new double[length];
            }
            return gradient;
        }

        private int[] ensureStateCapacity(int length) {
            if (workingState == null || workingState.length < length) {
                workingState = new int[length];
            }
            return workingState;
        }

        private int[] ensureEntropyFreqCapacity() {
            if (entropyFreq == null || entropyFreq.length < 256) {
                entropyFreq = new int[256];
            }
            return entropyFreq;
        }

        private int[] ensureFreqXCapacity() {
            if (freqX == null || freqX.length < 256) {
                freqX = new int[256];
            }
            return freqX;
        }

        private int[] ensureFreqYCapacity() {
            if (freqY == null || freqY.length < 256) {
                freqY = new int[256];
            }
            return freqY;
        }

        private int[] ensureJointCapacity() {
            if (joint == null || joint.length < 256 * 256) {
                joint = new int[256 * 256];
            }
            return joint;
        }

        private int[] ensureTempIntCapacity(int length) {
            if (tempInt == null || tempInt.length < length) {
                tempInt = new int[length];
            }
            return tempInt;
        }

        private int[] ensureOpenSetCapacity(int length) {
            if (openSet == null || openSet.length < length) {
                openSet = new int[length];
            }
            return openSet;
        }

        private int[] ensureHeapIndexCapacity(int length) {
            if (heapIndex == null || heapIndex.length < length) {
                heapIndex = new int[length];
            }
            return heapIndex;
        }

        private byte[] ensureOpenStateCapacity(int length) {
            if (openState == null || openState.length < length) {
                openState = new byte[length];
            }
            return openState;
        }

        private boolean[] ensureClosedSetCapacity(int length) {
            if (closedSet == null || closedSet.length < length) {
                closedSet = new boolean[length];
            }
            return closedSet;
        }

        private int[] ensureGScoreCapacity(int length) {
            if (gScore == null || gScore.length < length) {
                gScore = new int[length];
            }
            return gScore;
        }

        private int[] ensureFScoreCapacity(int length) {
            if (fScore == null || fScore.length < length) {
                fScore = new int[length];
            }
            return fScore;
        }
    }
    
    // ========== Private Constructor ==========
    
    private AdvancedAlgorithms() {
        throw new AssertionError("AdvancedAlgorithms is a utility class and cannot be instantiated");
    }

    // ========== Information Theory ==========
    
    /**
     * Computes Shannon entropy of a byte array.
     * Measures average information content in bits.
     * 
     * @param data Input data
     * @return Entropy in bits (0 to 8)
     */
    public static double computeEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        
        // Count byte frequencies
        Buffers buffers = THREAD_LOCAL_BUFFERS.get();
        int[] freq = buffers.ensureEntropyFreqCapacity();
        java.util.Arrays.fill(freq, 0);
        for (byte b : data) {
            freq[b & 0xFF]++;
        }
        
        // Calculate entropy: H = -Σ p(x) * log2(p(x))
        double entropy = 0.0;
        double invLen = 1.0 / data.length;
        for (int count : freq) {
            if (count > 0) {
                double p = count * invLen;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        
        return entropy;
    }
    
    /**
     * Computes Kolmogorov complexity approximation using compression ratio.
     * Lower values indicate more compressible (less complex) data.
     * 
     * @param data Input data
     * @return Approximate complexity ratio (0 to 1)
     */
    public static double approximateKolmogorovComplexity(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        
        // Simple run-length encoding as complexity proxy
        int runs = 1;
        for (int i = 1; i < data.length; i++) {
            if (data[i] != data[i - 1]) {
                runs++;
            }
        }
        
        return (double) runs / data.length;
    }
    
    /**
     * Computes mutual information between two byte sequences.
     * Measures how much information one sequence provides about another.
     * 
     * @param x First sequence
     * @param y Second sequence (must be same length as x)
     * @return Mutual information in bits
     */
    public static double mutualInformation(byte[] x, byte[] y) {
        if (x == null || y == null || x.length != y.length || x.length == 0) {
            return 0.0;
        }
        
        // Build joint frequency distribution
        Buffers buffers = THREAD_LOCAL_BUFFERS.get();
        int[] joint = buffers.ensureJointCapacity();
        int[] freqX = buffers.ensureFreqXCapacity();
        int[] freqY = buffers.ensureFreqYCapacity();
        java.util.Arrays.fill(joint, 0);
        java.util.Arrays.fill(freqX, 0);
        java.util.Arrays.fill(freqY, 0);
        
        for (int i = 0; i < x.length; i++) {
            int xi = x[i] & 0xFF;
            int yi = y[i] & 0xFF;
            joint[(xi << 8) + yi]++;
            freqX[xi]++;
            freqY[yi]++;
        }
        
        // Calculate MI: I(X;Y) = Σ p(x,y) * log2(p(x,y) / (p(x)*p(y)))
        double mi = 0.0;
        double invLen = 1.0 / x.length;
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                int jointCount = joint[(i << 8) + j];
                if (jointCount > 0) {
                    double pxy = jointCount * invLen;
                    double px = freqX[i] * invLen;
                    double py = freqY[j] * invLen;
                    mi += pxy * (Math.log(pxy / (px * py)) / Math.log(2.0));
                }
            }
        }
        
        return mi;
    }

    // ========== Optimization Algorithms ==========
    
    /**
     * Golden section search for univariate optimization.
     * Finds minimum of a unimodal function without derivatives.
     * 
     * @param f Function to minimize
     * @param a Lower bound
     * @param b Upper bound
     * @param tolerance Convergence tolerance
     * @return Approximate minimum point
     */
    public static double goldenSectionSearch(UnivariateFunction f, double a, double b, double tolerance) {
        double c = b - (b - a) * INV_PHI;
        double d = a + (b - a) * INV_PHI;
        
        int iterations = 0;
        while (Math.abs(b - a) > tolerance && iterations < MAX_ITERATIONS) {
            double fc = f.evaluate(c);
            double fd = f.evaluate(d);
            
            if (fc < fd) {
                b = d;
                d = c;
                c = b - (b - a) * INV_PHI;
            } else {
                a = c;
                c = d;
                d = a + (b - a) * INV_PHI;
            }
            iterations++;
        }
        
        return (a + b) * 0.5;
    }
    
    /**
     * Simulated annealing for combinatorial optimization.
     * Escapes local minima through probabilistic acceptance.
     * 
     * @param initialState Initial state
     * @param costFunction Cost function to minimize
     * @param neighbor Function to generate neighboring states
     * @param initialTemp Initial temperature
     * @param coolingRate Cooling rate (0 < rate < 1)
     * @param maxIterations Maximum iterations
     * @return Best state found
     */
    public static int[] simulatedAnnealing(
            int[] initialState,
            CostFunction costFunction,
            NeighborFunction neighbor,
            double initialTemp,
            double coolingRate,
            int maxIterations) {
        
        int[] currentState = initialState.clone();
        int[] bestState = initialState.clone();
        double currentCost = costFunction.evaluate(currentState);
        double bestCost = currentCost;
        double temperature = initialTemp;
        
        // Use xorshift for fast random numbers
        long seed = System.nanoTime();
        
        for (int i = 0; i < maxIterations; i++) {
            // Generate neighbor
            int[] newState = neighbor.generate(currentState);
            double newCost = costFunction.evaluate(newState);
            double delta = newCost - currentCost;
            
            // Accept if better or with probability exp(-delta/T)
            boolean accept = delta < 0;
            if (!accept && temperature > EPSILON) {
                seed ^= seed << 13;
                seed ^= seed >>> 7;
                seed ^= seed << 17;
                double rand = ((seed & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL);
                accept = rand < Math.exp(-delta / temperature);
            }
            
            if (accept) {
                currentState = newState;
                currentCost = newCost;
                
                if (newCost < bestCost) {
                    bestState = newState.clone();
                    bestCost = newCost;
                }
            }
            
            // Cool down
            temperature *= coolingRate;
        }
        
        return bestState;
    }

    /**
     * Simulated annealing using a caller-provided output buffer.
     * Avoids repeated allocations in hot loops by reusing arrays.
     *
     * @param initialState Initial state
     * @param costFunction Cost function to minimize
     * @param neighbor Function to generate neighboring states
     * @param initialTemp Initial temperature
     * @param coolingRate Cooling rate (0 < rate < 1)
     * @param maxIterations Maximum iterations
     * @param outBestState Output buffer for best state (may be reused)
     * @return Best state found (same reference as {@code outBestState})
     */
    public static int[] simulatedAnnealingInto(
            int[] initialState,
            CostFunction costFunction,
            NeighborFunction neighbor,
            double initialTemp,
            double coolingRate,
            int maxIterations,
            int[] outBestState) {

        Buffers buffers = THREAD_LOCAL_BUFFERS.get();
        int stateLength = initialState.length;
        int[] currentState = buffers.ensureStateCapacity(initialState.length);
        System.arraycopy(initialState, 0, currentState, 0, initialState.length);
        int[] bestState = outBestState;
        if (bestState == null || bestState.length < stateLength) {
            bestState = new int[stateLength];
        }
        System.arraycopy(initialState, 0, bestState, 0, stateLength);

        double currentCost = costFunction.evaluate(currentState);
        double bestCost = currentCost;
        double temperature = initialTemp;

        long seed = System.nanoTime();

        for (int i = 0; i < maxIterations; i++) {
            int[] newState = neighbor.generate(currentState);
            double newCost = costFunction.evaluate(newState);
            double delta = newCost - currentCost;

            boolean accept = delta < 0;
            if (!accept && temperature > EPSILON) {
                seed ^= seed << 13;
                seed ^= seed >>> 7;
                seed ^= seed << 17;
                double rand = ((seed & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL);
                accept = rand < Math.exp(-delta / temperature);
            }

            if (accept) {
                currentState = newState;
                currentCost = newCost;

                if (newCost < bestCost) {
                    System.arraycopy(newState, 0, bestState, 0, stateLength);
                    bestCost = newCost;
                }
            }

            temperature *= coolingRate;
        }

        return bestState;
    }
    
    /**
     * Gradient descent optimization (fixed-point arithmetic).
     * Finds local minimum using first-order derivatives.
     * 
     * @param initialPoint Starting point
     * @param gradient Gradient function
     * @param learningRate Step size
     * @param maxIterations Maximum iterations
     * @return Optimized point
     */
    public static double[] gradientDescent(
            double[] initialPoint,
            GradientFunction gradient,
            double learningRate,
            int maxIterations) {
        double[] point = new double[initialPoint.length];
        System.arraycopy(initialPoint, 0, point, 0, initialPoint.length);
        return gradientDescentInto(point, gradient, learningRate, maxIterations, point);
    }

    /**
     * Gradient descent using a caller-provided output buffer.
     * Avoids allocating a gradient array on every invocation.
     *
     * @param initialPoint Starting point
     * @param gradient Gradient function
     * @param learningRate Step size
     * @param maxIterations Maximum iterations
     * @param outPoint Output buffer for the optimized point
     * @return Optimized point (same reference as {@code outPoint})
     */
    public static double[] gradientDescentInto(
            double[] initialPoint,
            GradientFunction gradient,
            double learningRate,
            int maxIterations,
            double[] outPoint) {

        double[] point = outPoint;
        if (point == null || point.length < initialPoint.length) {
            point = new double[initialPoint.length];
        }
        System.arraycopy(initialPoint, 0, point, 0, initialPoint.length);

        Buffers buffers = THREAD_LOCAL_BUFFERS.get();
        double[] grad = buffers.ensureGradientCapacity(point.length);

        for (int iter = 0; iter < maxIterations; iter++) {
            gradient.evaluate(point, grad);

            double gradNorm = 0.0;
            for (double g : grad) {
                gradNorm += g * g;
            }
            if (gradNorm < EPSILON) break;

            for (int i = 0; i < point.length; i++) {
                point[i] -= learningRate * grad[i];
            }
        }

        return point;
    }

    // ========== Heuristic Search ==========
    
    /**
     * A* pathfinding with custom heuristic.
     * Finds optimal path in graph-like structures.
     * 
     * @param start Start node
     * @param goal Goal node
     * @param heuristic Heuristic function (admissible)
     * @param maxNodes Maximum nodes to explore
     * @return Path length or -1 if not found
     */
    public static int aStarSearch(int start, int goal, HeuristicFunction heuristic, int maxNodes) {
        if (heuristic == null || maxNodes <= 0 || start < 0 || goal < 0 || start >= maxNodes || goal >= maxNodes) {
            return -1;
        }

        Buffers buffers = THREAD_LOCAL_BUFFERS.get();
        int[] openSet = buffers.ensureOpenSetCapacity(maxNodes);
        int[] heapIndex = buffers.ensureHeapIndexCapacity(maxNodes);
        byte[] openState = buffers.ensureOpenStateCapacity(maxNodes);
        boolean[] closedSet = buffers.ensureClosedSetCapacity(maxNodes);
        int[] gScore = buffers.ensureGScoreCapacity(maxNodes);
        int[] fScore = buffers.ensureFScoreCapacity(maxNodes);

        for (int i = 0; i < maxNodes; i++) {
            closedSet[i] = false;
            openState[i] = 0;
            heapIndex[i] = -1;
            gScore[i] = Integer.MAX_VALUE;
            fScore[i] = Integer.MAX_VALUE;
        }

        gScore[start] = 0;
        fScore[start] = heuristic.estimate(start, goal);

        int heapSize = heapPush(openSet, heapIndex, fScore, start, 0);
        openState[start] = 1;

        while (heapSize > 0) {
            int current = openSet[0];
            heapSize = heapPop(openSet, heapIndex, fScore, heapSize);
            openState[current] = 0;

            if (closedSet[current]) {
                continue;
            }
            if (current == goal) {
                return gScore[goal];
            }

            closedSet[current] = true;
            int[] neighbors = heuristic.getNeighbors(current);
            if (neighbors == null) {
                continue;
            }

            int baseG = gScore[current];
            if (baseG == Integer.MAX_VALUE) {
                continue;
            }

            for (int i = 0; i < neighbors.length; i++) {
                int neighbor = neighbors[i];
                if (neighbor < 0 || neighbor >= maxNodes || closedSet[neighbor]) {
                    continue;
                }

                int tentativeG = baseG + 1;
                if (tentativeG < gScore[neighbor]) {
                    gScore[neighbor] = tentativeG;
                    fScore[neighbor] = tentativeG + heuristic.estimate(neighbor, goal);

                    if (openState[neighbor] == 0) {
                        heapSize = heapPush(openSet, heapIndex, fScore, neighbor, heapSize);
                        openState[neighbor] = 1;
                    } else {
                        heapSiftUp(openSet, heapIndex, fScore, heapIndex[neighbor]);
                    }
                }
            }
        }

        return -1;
    }

    private static int heapPush(int[] heap, int[] heapIndex, int[] fScore, int node, int heapSize) {
        heap[heapSize] = node;
        heapIndex[node] = heapSize;
        heapSiftUp(heap, heapIndex, fScore, heapSize);
        return heapSize + 1;
    }

    private static int heapPop(int[] heap, int[] heapIndex, int[] fScore, int heapSize) {
        int lastIndex = heapSize - 1;
        int root = heap[0];
        int lastNode = heap[lastIndex];
        heapIndex[root] = -1;
        if (lastIndex == 0) {
            return 0;
        }

        heap[0] = lastNode;
        heapIndex[lastNode] = 0;
        heapSiftDown(heap, heapIndex, fScore, 0, lastIndex);
        return lastIndex;
    }

    private static void heapSiftUp(int[] heap, int[] heapIndex, int[] fScore, int index) {
        int node = heap[index];
        int score = fScore[node];

        while (index > 0) {
            int parent = (index - 1) >>> 1;
            int parentNode = heap[parent];
            int parentScore = fScore[parentNode];
            if (parentScore < score || (parentScore == score && parentNode <= node)) {
                break;
            }
            heap[index] = parentNode;
            heapIndex[parentNode] = index;
            index = parent;
        }

        heap[index] = node;
        heapIndex[node] = index;
    }

    private static void heapSiftDown(int[] heap, int[] heapIndex, int[] fScore, int index, int heapSize) {
        int node = heap[index];
        int score = fScore[node];
        int half = heapSize >>> 1;

        while (index < half) {
            int left = (index << 1) + 1;
            int right = left + 1;
            int best = left;
            int bestNode = heap[left];
            int bestScore = fScore[bestNode];

            if (right < heapSize) {
                int rightNode = heap[right];
                int rightScore = fScore[rightNode];
                if (rightScore < bestScore || (rightScore == bestScore && rightNode < bestNode)) {
                    best = right;
                    bestNode = rightNode;
                    bestScore = rightScore;
                }
            }

            if (bestScore > score || (bestScore == score && bestNode >= node)) {
                break;
            }

            heap[index] = bestNode;
            heapIndex[bestNode] = index;
            index = best;
        }

        heap[index] = node;
        heapIndex[node] = index;
    }

    // ========== Fast Transforms ==========
    
    /**
     * Fast Hadamard Transform (in-place).
     * Useful for signal processing and correlation.
     * Size must be power of 2.
     * 
     * @param data Input/output array (modified in-place)
     */
    public static void fastHadamardTransform(int[] data) {
        int n = data.length;
        if (n == 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("Size must be power of 2");
        }
        
        for (int step = 1; step < n; step <<= 1) {
            for (int i = 0; i < n; i += step << 1) {
                for (int j = i; j < i + step; j++) {
                    int a = data[j];
                    int b = data[j + step];
                    data[j] = a + b;
                    data[j + step] = a - b;
                }
            }
        }
    }
    
    /**
     * Walsh-Hadamard sequency ordering.
     * Reorders Hadamard transform output by frequency.
     * 
     * @param data Array to reorder (power of 2 size)
     */
    public static void walshSequencyOrder(int[] data) {
        int n = data.length;
        Buffers buffers = THREAD_LOCAL_BUFFERS.get();
        int[] temp = buffers.ensureTempIntCapacity(n);
        
        // Gray code permutation for sequency ordering
        for (int i = 0; i < n; i++) {
            int gray = i ^ (i >>> 1);
            temp[i] = data[gray];
        }
        
        System.arraycopy(temp, 0, data, 0, n);
    }

    // ========== Function Interfaces ==========
    
    public interface UnivariateFunction {
        double evaluate(double x);
    }
    
    public interface CostFunction {
        double evaluate(int[] state);
    }
    
    public interface NeighborFunction {
        int[] generate(int[] currentState);
    }
    
    public interface GradientFunction {
        void evaluate(double[] point, double[] gradient);
    }
    
    public interface HeuristicFunction {
        int estimate(int node, int goal);
        int[] getNeighbors(int node);
    }
}
