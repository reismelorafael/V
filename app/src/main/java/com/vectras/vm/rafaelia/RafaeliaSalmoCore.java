package com.vectras.vm.rafaelia;

/**
 * Low-level deterministic SALMO core derived from the symbolic RAFAELIA block.
 *
 * No dynamic allocation in hot-path methods and branch-minimal arithmetic.
 */
public final class RafaeliaSalmoCore {
  private RafaeliaSalmoCore() {
  }

  public static final int ANCHOR_SENE_35 = 35;
  public static final int BASE_60 = 60;
  public static final int COLLAPSE_STATE_9096 = 9096;
  public static final long PHOS_VECTOR = 0x3CF0L;
  public static final int CLOCK_LIMIT_70 = 70;
  public static final float LIGHT_FLUID = 0.866025f;

  public static long anchor() {
    return ANCHOR_SENE_35;
  }

  public static long collapseEnergy(long noise) {
    return noise + COLLAPSE_STATE_9096 + ANCHOR_SENE_35;
  }

  public static long phosActivation() {
    return PHOS_VECTOR * BASE_60;
  }

  public static long seneIntersection(long value) {
    return value ^ ANCHOR_SENE_35;
  }

  public static long resetPhase70(long value) {
    return Math.floorMod(value, CLOCK_LIMIT_70);
  }

  public static long cityLuminance(long cpu, long ram, long disk) {
    long base = phosActivation();
    long fluid = (long) (LIGHT_FLUID * 1_000_000.0f);
    long triad = (cpu & 0xFFFFL) ^ ((ram & 0xFFFFL) << 1) ^ ((disk & 0xFFFFL) << 2);
    long folded = seneIntersection(base ^ fluid ^ triad);
    return resetPhase70(folded);
  }

  public static long pulseSignature(long cpu, long ram, long disk, int rhoScore, int syn) {
    long energy = collapseEnergy(rhoScore + syn);
    long lum = cityLuminance(cpu, ram, disk);
    return (energy << 8) ^ lum ^ (anchor() << 1);
  }
}
