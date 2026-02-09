package com.vectras.vm.core;

import java.util.Arrays;

/**
 * DeterministicRuntimeMatrix: commutative factor model for low-overhead runtime tuning.
 *
 * <p>Uses only platform/base runtime data and native fast-path hints to produce stable,
 * order-independent knobs for IOPS, latency, IRQ cadence and copy quantum.</p>
 */
public final class DeterministicRuntimeMatrix {

    public static final class Snapshot {
        public final int arch;
        public final int cores;
        public final int pointerBits;
        public final int pageBytes;
        public final int cacheLineBytes;
        public final int features;
        public final int ioQuantumBytes;
        public final int irqPeriodMicros;
        public final int workerParallelism;
        public final long deterministicProduct;

        private Snapshot(int arch, int cores, int pointerBits, int pageBytes, int cacheLineBytes,
                         int features, int ioQuantumBytes, int irqPeriodMicros,
                         int workerParallelism, long deterministicProduct) {
            this.arch = arch;
            this.cores = cores;
            this.pointerBits = pointerBits;
            this.pageBytes = pageBytes;
            this.cacheLineBytes = cacheLineBytes;
            this.features = features;
            this.ioQuantumBytes = ioQuantumBytes;
            this.irqPeriodMicros = irqPeriodMicros;
            this.workerParallelism = workerParallelism;
            this.deterministicProduct = deterministicProduct;
        }
    }

    private DeterministicRuntimeMatrix() {
        throw new AssertionError("No instances");
    }

    public static Snapshot capture() {
        int signature = NativeFastPath.getPlatformSignature();
        int arch = signature & 0xFF00;
        int bits = NativeFastPath.getPointerBits();
        int page = NativeFastPath.getNativePageBytes();
        int line = NativeFastPath.getNativeCacheLineBytes();
        int features = NativeFastPath.getFeatureMask();
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        int[] factors = new int[] {
                normalizeFactor(arch),
                normalizeFactor(bits),
                normalizeFactor(page),
                normalizeFactor(line),
                normalizeFactor(cores),
                normalizeFactor(features)
        };

        Arrays.sort(factors);
        long product = 1L;
        for (int factor : factors) {
            product = boundedMultiply(product, factor);
        }

        int ioQuantum = deriveIoQuantum(page, line, cores, features);
        int irqPeriod = deriveIrqPeriodMicros(line, cores, features);
        int workers = deriveParallelism(cores, features);

        return new Snapshot(arch, cores, bits, page, line, features, ioQuantum, irqPeriod, workers, product);
    }

    private static int deriveIoQuantum(int page, int line, int cores, int features) {
        int base = page * Math.max(1, cores >= 8 ? 8 : (cores >= 4 ? 4 : 2));
        if ((features & NativeFastPath.FEATURE_AVX2) != 0 || (features & NativeFastPath.FEATURE_NEON) != 0) {
            base <<= 1;
        }
        int align = Math.max(32, line);
        int rem = base & (align - 1);
        if (rem != 0) {
            base += align - rem;
        }
        return clamp(base, 4096, 1024 * 1024);
    }

    private static int deriveIrqPeriodMicros(int line, int cores, int features) {
        int base = 1500;
        if (cores >= 8) base -= 350;
        else if (cores >= 4) base -= 200;

        if ((features & NativeFastPath.FEATURE_CRC32) != 0) base -= 80;
        if ((features & NativeFastPath.FEATURE_AVX2) != 0 || (features & NativeFastPath.FEATURE_NEON) != 0) base -= 120;
        if (line >= 128) base += 70;

        return clamp(base, 500, 2500);
    }

    private static int deriveParallelism(int cores, int features) {
        int base = Math.max(1, cores - 1);
        if ((features & NativeFastPath.FEATURE_AVX2) != 0) {
            return Math.max(1, base - 1);
        }
        return base;
    }

    private static int normalizeFactor(int value) {
        if (value == Integer.MIN_VALUE) return 1;
        int v = Math.abs(value);
        return v == 0 ? 1 : v;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long boundedMultiply(long left, long right) {
        if (left == 0 || right == 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }
}
