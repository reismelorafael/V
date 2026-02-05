package com.vectras.vm.rafaelia;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RafaeliaKernelV22Test {
  @Test
  public void testLambdaEpsilon() {
    double lambda = RafaeliaKernelV22.lambda(10.0, 7.0);
    assertEquals(3.0, lambda, 0.0001);
    double epsilon = RafaeliaKernelV22.epsilon(0.0, lambda);
    assertEquals(1.5, epsilon, 0.0001);
  }

  @Test
  public void testLocalTempAndAbort() {
    double temp = RafaeliaKernelV22.localTemp(100.0, 0.2, 2.0, 0.1, 3.0, 0.05, 4.0);
    assertEquals(100.0 * (1.0 + 0.2 * 2.0) / ((1.0 + 0.1 * 3.0) * (1.0 + 0.05 * 4.0)), temp, 0.0001);
    double xi = RafaeliaKernelV22.abortVector(9.0, 4.0);
    assertEquals(5.0, xi, 0.0001);
    assertTrue(RafaeliaKernelV22.shouldAbort(xi, 4.9));
    assertFalse(RafaeliaKernelV22.shouldAbort(xi, 5.0));
  }

  @Test
  public void testRoutingAndMix() {
    double[] p = {0.1, 0.7, 0.2};
    assertEquals(1, RafaeliaKernelV22.routeMax(p));
    double[][] vectors = {{1.0, 0.0}, {0.0, 1.0}, {1.0, 1.0}};
    double[] mixed = RafaeliaKernelV22.mixWeighted(p, vectors);
    assertArrayEquals(new double[]{0.3, 0.9}, mixed, 0.0001);
  }

  @Test
  public void testGraphAndParadoxOps() {
    double[][] distances = {
        {0.0, 1.0, 2.0},
        {1.0, 0.0, 3.0},
        {2.0, 3.0, 0.0}
    };
    double[][] kappas = {
        {0.0, 2.0, 1.0},
        {2.0, 0.0, 4.0},
        {1.0, 4.0, 0.0}
    };
    assertEquals(2.0 * 1.0 + 1.0 * 2.0 + 4.0 * 3.0, RafaeliaKernelV22.graphPotential(distances, kappas), 0.0001);
    assertEquals(0.5, RafaeliaKernelV22.deltaSimpson(1.0, new double[]{0.0, 1.0}, new double[]{0.5, 0.5}), 0.0001);
    assertEquals(2.0, RafaeliaKernelV22.deltaBelady(3, 5), 0.0001);
    assertEquals(0.0, RafaeliaKernelV22.mirageVariance(new double[]{2.0, 2.0}), 0.0001);
  }

  @Test
  public void testScoreAndAttractor() {
    assertEquals(5.0, RafaeliaKernelV22.score(1.0, 4.0, 1.0, 3.0, 1.0, 2.0, 1.0, 4.0), 0.0001);
    double[] next = RafaeliaKernelV22.attractorStep(new double[]{1.0, 2.0}, new double[]{0.5, -1.0}, 0.1);
    assertArrayEquals(new double[]{0.95, 2.1}, next, 0.0001);
  }
}
