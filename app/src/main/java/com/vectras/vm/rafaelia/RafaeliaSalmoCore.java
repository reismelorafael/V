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
  public static final int LIGHT_FLUID_SCALED = 866_025;

  private static final long TRIAD_MASK = 0xFFFFL;
  private static final int RAM_SHIFT = 1;
  private static final int DISK_SHIFT = 2;

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
    long mod = value % CLOCK_LIMIT_70;
    return mod < 0 ? mod + CLOCK_LIMIT_70 : mod;
  }

  public static long cityLuminance(long cpu, long ram, long disk) {
    long base = phosActivation();
    long triad = (cpu & TRIAD_MASK)
        ^ ((ram & TRIAD_MASK) << RAM_SHIFT)
        ^ ((disk & TRIAD_MASK) << DISK_SHIFT);
    long fluid = LIGHT_FLUID_SCALED;
    long folded = seneIntersection(base ^ fluid ^ triad);
    return resetPhase70(folded);
  }

  public static long pulseSignature(long cpu, long ram, long disk, int rhoScore, int syn) {
    long energy = collapseEnergy(rhoScore + syn);
    long lum = cityLuminance(cpu, ram, disk);
    return (energy << 8) ^ lum ^ (anchor() << 1);
  }
}
