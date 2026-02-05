package com.vectras.vm.rafaelia;

import java.util.Arrays;

/**
 * RAFAELIA_KERNEL_SPEC_V22 - minimal deterministic operators.
 *
 * This implementation mirrors the spec contract with lightweight, pure functions.
 */
public final class RafaeliaKernelV22 {
  private RafaeliaKernelV22() {
  }

  public static final class SystemState<T, U, V> {
    public final T data;
    public final U model;
    public final V action;

    public SystemState(T data, U model, V action) {
      this.data = data;
      this.model = model;
      this.action = action;
    }
  }

  public static double lambda(double u, double uHat) {
    return Math.max(0.0, u - uHat);
  }

  public static double sigmoid(double x) {
    return 1.0 / (1.0 + Math.exp(-x));
  }

  public static double epsilon(double dUdt, double lambda) {
    return sigmoid(dUdt) * lambda;
  }

  public static double localTemp(double t0, double beta, double lambda, double alpha, double coh,
                                 double gamma, double mass) {
    double numerator = 1.0 + beta * lambda;
    double denom = (1.0 + alpha * coh) * (1.0 + gamma * mass);
    return t0 * numerator / denom;
  }

  public static double abortVector(double cb, double eNeed) {
    return Math.max(0.0, cb - eNeed);
  }

  public static boolean shouldAbort(double xi, double xiMax) {
    return xi > xiMax;
  }

  public static double capDominance(double w, double wCap) {
    return Math.min(w, wCap);
  }

  public static int routeMax(double[] probabilities) {
    int idx = 0;
    double best = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < probabilities.length; i++) {
      double p = probabilities[i];
      if (p > best) {
        best = p;
        idx = i;
      }
    }
    return idx;
  }

  public static double[] mixWeighted(double[] probabilities, double[][] vectors) {
    if (probabilities.length != vectors.length) {
      throw new IllegalArgumentException("probabilities/vectors length mismatch");
    }
    int len = vectors[0].length;
    double[] out = new double[len];
    for (int i = 0; i < vectors.length; i++) {
      if (vectors[i].length != len) {
        throw new IllegalArgumentException("vector length mismatch at index " + i);
      }
      double w = probabilities[i];
      for (int j = 0; j < len; j++) {
        out[j] += w * vectors[i][j];
      }
    }
    return out;
  }

  public static double graphPotential(double[][] distances, double[][] kappas) {
    if (distances.length != kappas.length) {
      throw new IllegalArgumentException("matrix size mismatch");
    }
    double sum = 0.0;
    for (int i = 0; i < distances.length; i++) {
      if (distances[i].length != kappas[i].length) {
        throw new IllegalArgumentException("matrix row mismatch at index " + i);
      }
      for (int j = i + 1; j < distances[i].length; j++) {
        sum += kappas[i][j] * distances[i][j];
      }
    }
    return sum;
  }

  public static double[] attractorStep(double[] v, double[] grad, double eta) {
    if (v.length != grad.length) {
      throw new IllegalArgumentException("vector/grad length mismatch");
    }
    double[] next = Arrays.copyOf(v, v.length);
    for (int i = 0; i < v.length; i++) {
      next[i] -= eta * grad[i];
    }
    return next;
  }

  public static double deltaSimpson(double trendA, double[] trendsByGroup, double[] weights) {
    if (trendsByGroup.length != weights.length) {
      throw new IllegalArgumentException("trends/weights length mismatch");
    }
    double sum = 0.0;
    for (int i = 0; i < trendsByGroup.length; i++) {
      sum += weights[i] * trendsByGroup[i];
    }
    return Math.abs(trendA - sum);
  }

  public static double deltaBelady(int faultsM1, int faultsM2) {
    return Math.max(0.0, faultsM2 - faultsM1);
  }

  public static double mirageVariance(double[] outcomes) {
    if (outcomes.length == 0) {
      return 0.0;
    }
    double mean = 0.0;
    for (double v : outcomes) {
      mean += v;
    }
    mean /= outcomes.length;
    double var = 0.0;
    for (double v : outcomes) {
      double d = v - mean;
      var += d * d;
    }
    return var / outcomes.length;
  }

  public static double score(double wa, double a, double wc, double c, double wh, double h,
                             double wp, double p) {
    return wa * a + wc * c + wh * h - wp * p;
  }
}
