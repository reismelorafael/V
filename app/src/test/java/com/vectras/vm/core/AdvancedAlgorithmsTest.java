package com.vectras.vm.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for AdvancedAlgorithms class.
 */
public class AdvancedAlgorithmsTest {

    private static final double DELTA = 1e-6;

    @Test
    public void testComputeEntropy() {
        // Uniform distribution (high entropy)
        byte[] uniform = new byte[256];
        for (int i = 0; i < 256; i++) {
            uniform[i] = (byte) i;
        }
        double entropyUniform = AdvancedAlgorithms.computeEntropy(uniform);
        assertTrue("Uniform distribution should have high entropy", entropyUniform > 7.5);

        // All same (zero entropy)
        byte[] constant = new byte[256];
        double entropyConstant = AdvancedAlgorithms.computeEntropy(constant);
        assertEquals("Constant data should have zero entropy", 0.0, entropyConstant, DELTA);

        // Empty array
        assertEquals(0.0, AdvancedAlgorithms.computeEntropy(new byte[0]), DELTA);
        assertEquals(0.0, AdvancedAlgorithms.computeEntropy(null), DELTA);
    }

    @Test
    public void testApproximateKolmogorovComplexity() {
        // Highly compressible (low complexity)
        byte[] repetitive = new byte[100];
        for (int i = 0; i < 100; i++) {
            repetitive[i] = 42;
        }
        double complexityLow = AdvancedAlgorithms.approximateKolmogorovComplexity(repetitive);
        assertTrue("Repetitive data should have low complexity", complexityLow < 0.1);

        // Random-like (high complexity)
        byte[] random = new byte[100];
        for (int i = 0; i < 100; i++) {
            random[i] = (byte) i;
        }
        double complexityHigh = AdvancedAlgorithms.approximateKolmogorovComplexity(random);
        assertTrue("Random-like data should have high complexity", complexityHigh > 0.5);

        // Edge cases
        assertEquals(0.0, AdvancedAlgorithms.approximateKolmogorovComplexity(new byte[0]), DELTA);
        assertEquals(0.0, AdvancedAlgorithms.approximateKolmogorovComplexity(null), DELTA);
    }

    @Test
    public void testMutualInformation() {
        // Identical sequences (maximum MI)
        byte[] x = {1, 2, 3, 4, 5};
        byte[] y = {1, 2, 3, 4, 5};
        double mi = AdvancedAlgorithms.mutualInformation(x, y);
        assertTrue("Identical sequences should have high MI", mi > 0.0);

        // Independent sequences (low MI)
        byte[] z = {10, 20, 30, 40, 50};
        double miIndep = AdvancedAlgorithms.mutualInformation(x, z);
        assertTrue("Independent sequences should have lower MI", miIndep >= 0.0);

        // Edge cases
        assertEquals(0.0, AdvancedAlgorithms.mutualInformation(null, y), DELTA);
        assertEquals(0.0, AdvancedAlgorithms.mutualInformation(x, null), DELTA);
        assertEquals(0.0, AdvancedAlgorithms.mutualInformation(x, new byte[10]), DELTA);
    }

    @Test
    public void testGoldenSectionSearch() {
        // Find minimum of x^2 - 4x + 4 = (x-2)^2, minimum at x=2
        AdvancedAlgorithms.UnivariateFunction quadratic = new AdvancedAlgorithms.UnivariateFunction() {
            @Override
            public double evaluate(double x) {
                return (x - 2) * (x - 2);
            }
        };

        double min = AdvancedAlgorithms.goldenSectionSearch(quadratic, 0, 4, 1e-3);
        assertEquals("Should find minimum near x=2", 2.0, min, 0.01);
    }

    @Test
    public void testSimulatedAnnealing() {
        // Simple optimization: minimize sum of absolute differences from target
        int[] target = {5, 5, 5, 5, 5};
        int[] initial = {0, 0, 0, 0, 0};

        AdvancedAlgorithms.CostFunction cost = new AdvancedAlgorithms.CostFunction() {
            @Override
            public double evaluate(int[] state) {
                double sum = 0;
                for (int i = 0; i < state.length; i++) {
                    sum += Math.abs(state[i] - target[i]);
                }
                return sum;
            }
        };

        AdvancedAlgorithms.NeighborFunction neighbor = new AdvancedAlgorithms.NeighborFunction() {
            @Override
            public int[] generate(int[] currentState) {
                int[] next = currentState.clone();
                int idx = (int) (Math.random() * next.length);
                next[idx] += Math.random() > 0.5 ? 1 : -1;
                return next;
            }
        };

        int[] result = AdvancedAlgorithms.simulatedAnnealing(initial, cost, neighbor, 100.0, 0.95, 1000);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same length", initial.length, result.length);
        
        double finalCost = cost.evaluate(result);
        double initialCost = cost.evaluate(initial);
        assertTrue("Should improve cost", finalCost <= initialCost);
    }

    @Test
    public void testGradientDescent() {
        // Minimize (x-1)^2 + (y-2)^2, minimum at (1, 2)
        double[] initial = {0.0, 0.0};

        AdvancedAlgorithms.GradientFunction gradient = new AdvancedAlgorithms.GradientFunction() {
            @Override
            public void evaluate(double[] point, double[] grad) {
                grad[0] = 2 * (point[0] - 1);
                grad[1] = 2 * (point[1] - 2);
            }
        };

        double[] result = AdvancedAlgorithms.gradientDescent(initial, gradient, 0.1, 100);
        
        assertEquals("X should converge to 1", 1.0, result[0], 0.1);
        assertEquals("Y should converge to 2", 2.0, result[1], 0.1);
    }

    @Test
    public void testAStarSearch() {
        // Simple heuristic: Manhattan distance in 2D grid
        AdvancedAlgorithms.HeuristicFunction heuristic = new AdvancedAlgorithms.HeuristicFunction() {
            @Override
            public int estimate(int node, int goal) {
                // Assume 10x10 grid
                int x1 = node % 10, y1 = node / 10;
                int x2 = goal % 10, y2 = goal / 10;
                return Math.abs(x1 - x2) + Math.abs(y1 - y2);
            }

            @Override
            public int[] getNeighbors(int node) {
                // 4-connected grid neighbors
                int x = node % 10, y = node / 10;
                int[] neighbors = new int[4];
                int count = 0;
                if (x > 0) neighbors[count++] = node - 1;
                if (x < 9) neighbors[count++] = node + 1;
                if (y > 0) neighbors[count++] = node - 10;
                if (y < 9) neighbors[count++] = node + 10;
                
                int[] result = new int[count];
                System.arraycopy(neighbors, 0, result, 0, count);
                return result;
            }
        };

        int pathLength = AdvancedAlgorithms.aStarSearch(0, 99, heuristic, 100);
        assertTrue("Should find path in grid", pathLength > 0);
        assertEquals("Manhattan path length should be 18", 18, pathLength);
    }


    @Test
    public void testAStarSearchInvalidInputs() {
        AdvancedAlgorithms.HeuristicFunction heuristic = new AdvancedAlgorithms.HeuristicFunction() {
            @Override
            public int estimate(int node, int goal) {
                return 0;
            }

            @Override
            public int[] getNeighbors(int node) {
                return null;
            }
        };

        assertEquals(-1, AdvancedAlgorithms.aStarSearch(-1, 5, heuristic, 10));
        assertEquals(-1, AdvancedAlgorithms.aStarSearch(1, 11, heuristic, 10));
        assertEquals(-1, AdvancedAlgorithms.aStarSearch(1, 2, null, 10));
    }

    @Test
    public void testFastHadamardTransform() {
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        int[] original = data.clone();

        AdvancedAlgorithms.fastHadamardTransform(data);
        
        // Transform should modify data
        boolean changed = false;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != original[i]) {
                changed = true;
                break;
            }
        }
        assertTrue("FHT should modify data", changed);

        // Apply twice should return to scaled original
        AdvancedAlgorithms.fastHadamardTransform(data);
        for (int i = 0; i < data.length; i++) {
            assertEquals("Double FHT should scale original", original[i] * 8, data[i]);
        }
    }

    @Test
    public void testWalshSequencyOrder() {
        int[] data = {0, 1, 2, 3, 4, 5, 6, 7};
        int[] original = data.clone();

        AdvancedAlgorithms.walshSequencyOrder(data);
        
        // Should reorder data
        boolean reordered = false;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != original[i]) {
                reordered = true;
                break;
            }
        }
        assertTrue("Should reorder data", reordered);
    }

    @Test
    public void testFastHadamardTransformInvalidSize() {
        int[] data = {1, 2, 3}; // Not power of 2
        
        try {
            AdvancedAlgorithms.fastHadamardTransform(data);
            fail("Should throw exception for non-power-of-2 size");
        } catch (IllegalArgumentException e) {
            // Expected
            assertTrue(e.getMessage().contains("power of 2"));
        }
    }

    @Test
    public void testCannotInstantiate() {
        try {
            // Use reflection to try instantiating
            java.lang.reflect.Constructor<?> constructor = AdvancedAlgorithms.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Should not be able to instantiate utility class");
        } catch (Exception e) {
            // Expected - should throw AssertionError wrapped in InvocationTargetException
            assertTrue(e.getCause() instanceof AssertionError);
        }
    }
}
