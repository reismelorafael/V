package com.vectras.vm.rafaelia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32C;

/**
 * RAFAELIA MVP (Java low-level)
 * - BitStack append-only via mmap
 * - 4x4 (16 bits) + parity 2D (8 bits) => ECC-lite
 * - "IRQ" modeled as high-priority events (preempt-like)
 *
 * Build:  javac RafaeliaMvp.java
 * Run:    java RafaeliaMvp ./bitstack.bin
 */
public class RafaeliaMvp {

  // ========== Config ==========
  static final int BLOCK_BITS = 16;          // 4x4
  static final int PARITY_BITS = 8;          // 4 row + 4 col
  static final int RECORD_BYTES = 8 + 4 + 4; // u64 payload + u32 meta + u32 crc32c
  static final long MMAP_BYTES = 64L * 1024 * 1024; // 64MB default segment
  static final double BIT_DROP_PROBABILITY = 0.02;
  static final int RADIO_PRIORITY = 9;
  static final int TIMER_PRIORITY = 3;
  static final long RADIO_INITIAL_DELAY_MS = 50;
  static final long RADIO_PERIOD_MS = 80;
  static final long TIMER_INITIAL_DELAY_MS = 10;
  static final long TIMER_PERIOD_MS = 20;
  static final long MAX_TICKS = 20_000;
  static final long RAM_MIX_CONSTANT = 0x9E3779B9L;
  static final long RAM_MASK = 0xFFFFFFFFFFFFL;
  static final int RHO_SLEEP_THRESHOLD = 6;
  static final long RHO_SLEEP_MS = 2L;
  static final int IRQ_QUEUE_CAPACITY = 1024;
  static final long IRQ_POLL_TIMEOUT_MS = 1_000L;
  private static final ThreadLocal<CRC32C> CRC32C_POOL = ThreadLocal.withInitial(CRC32C::new);

  // meta layout (u32):
  // [  0.. 7] parity (8 bits)
  // [  8..15] whoOut (2 bits used) + flags
  // [ 16..31] rhoScore (rough)
  static int packMeta(int parity8, int whoOut, int rhoScore) {
    int meta = 0;
    meta |= (parity8 & 0xFF);
    meta |= ((whoOut & 0x3) << 8);
    meta |= ((rhoScore & 0xFFFF) << 16);
    return meta;
  }

  static int packMetaSalmo(int parity8, int whoOut, int rhoScore, long salmoSignature) {
    int base = packMeta(parity8, whoOut, rhoScore);
    int salmoTag = (int) ((salmoSignature >>> 3) & 0x3F);
    return base ^ (salmoTag << 10);
  }

  // ========== “IRQ” Event model ==========
  enum EventType { RADIO_4G, TIMER, IO, VOICE, MANUAL }
  record Event(EventType type, long tNanos, int priority, int payload) {}

  static final class IrqBus {
    // higher priority first
    private final PriorityBlockingQueue<Event> q = new PriorityBlockingQueue<>(
      IRQ_QUEUE_CAPACITY, (a,b) -> Integer.compare(b.priority(), a.priority())
    );

    void fire(EventType t, int priority, int payload) {
      q.offer(new Event(t, System.nanoTime(), priority, payload));
    }

    Event poll(long timeout, TimeUnit unit) throws InterruptedException {
      return q.poll(timeout, unit);
    }

    Event take() throws InterruptedException { return q.take(); }
    boolean hasPending() { return !q.isEmpty(); }
  }

  // ========== BitStack (mmap append-only) ==========
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

      // Ensure file size
      if (ch.size() < mmapBytes) raf.setLength(mmapBytes);

      map = ch.map(FileChannel.MapMode.READ_WRITE, 0, mmapBytes);
      map.order(ByteOrder.LITTLE_ENDIAN);
    }

    long appendRecord(long payloadU64, int metaU32) {
      int crc = crc32c(payloadU64, metaU32);
      long pos = writePos.getAndAdd(RECORD_BYTES);
      if (pos + RECORD_BYTES > map.capacity()) {
        // Minimal MVP: no segment rollover; in real version, chain segments.
        throw new IllegalStateException(
            "BitStack full (capacity=" + map.capacity()
                + " bytes, pos=" + pos + ", recordBytes=" + RECORD_BYTES + ").");
      }
      if (pos > Integer.MAX_VALUE - RECORD_BYTES) {
        throw new IllegalStateException("BitStack offset exceeds supported range (pos=" + pos + ").");
      }
      int p = (int) pos;
      map.putLong(p, payloadU64);
      map.putInt(p + 8, metaU32);
      map.putInt(p + 12, crc);
      return pos;
    }

    // optional: flush (fsync-ish)
    void flush() { map.force(); }

    @Override public void close() throws IOException {
      flush();
      ch.close();
      raf.close();
    }

    static int crc32c(long payloadU64, int metaU32) {
      CRC32C c = CRC32C_POOL.get();
      c.reset();
      // little-endian feed
      for (int i = 0; i < 8; i++) c.update((int)((payloadU64 >>> (8*i)) & 0xFF));
      for (int i = 0; i < 4; i++) c.update((metaU32 >>> (8*i)) & 0xFF);
      return (int) c.getValue();
    }
  }

  // ========== 4x4 Matrix packing ==========
  // We pack 16 bits in the low 16 bits of a long.
  // Coordinate (x,y) in [0..3]. idx = (y<<2)|x.
  static int idx(int x, int y) { return (y << 2) | x; }

  static int getBit16(int bits16, int x, int y) {
    int i = idx(x,y);
    return (bits16 >>> i) & 1;
  }

  static int setBit16(int bits16, int x, int y, int v) {
    int i = idx(x,y);
    int mask = 1 << i;
    return (v == 0) ? (bits16 & ~mask) : (bits16 | mask);
  }

  // parity 2D: 4 row parity + 4 col parity = 8 bits
  // parity bit = XOR of bits in that row/col (even parity)
  static int parity2D8(int bits16) {
    int parity = 0;

    // rows
    for (int y = 0; y < 4; y++) {
      int p = 0;
      for (int x = 0; x < 4; x++) p ^= getBit16(bits16, x, y);
      parity |= (p & 1) << y;          // bits 0..3
    }
    // cols
    for (int x = 0; x < 4; x++) {
      int p = 0;
      for (int y = 0; y < 4; y++) p ^= getBit16(bits16, x, y);
      parity |= (p & 1) << (4 + x);    // bits 4..7
    }
    return parity & 0xFF;
  }

  // Very small “syndrome”: compare stored parity vs computed parity
  // Returns mismatch count (0..8)
  static int syndromePopcount(int storedParity8, int computedParity8) {
    int diff = (storedParity8 ^ computedParity8) & 0xFF;
    return Integer.bitCount(diff);
  }

  // “2 inside 1 outside” classification
  // whoOut: 0=CPU out,1=RAM out,2=DISK out,3=NONE/UNKNOWN
  static int whoOutTriad(long cpu, long ram, long disk) {
    if (cpu == ram && cpu != disk) return 2; // disk out
    if (cpu == disk && cpu != ram) return 1; // ram out
    if (ram == disk && ram != cpu) return 0; // cpu out
    return 3;
  }

  // ========== MVP loop ==========
  public static void main(String[] args) throws Exception {
    File path = new File(args.length > 0 ? args[0] : "./bitstack.bin");

    IrqBus irq = new IrqBus();

    // Simulate “4G IRQ” bursts + timers
    ScheduledExecutorService sch = Executors.newScheduledThreadPool(2);
    sch.scheduleAtFixedRate(
        () -> irq.fire(EventType.RADIO_4G, RADIO_PRIORITY, (int) (Math.random() * 0xFFFF)),
        RADIO_INITIAL_DELAY_MS,
        RADIO_PERIOD_MS,
        TimeUnit.MILLISECONDS);
    sch.scheduleAtFixedRate(
        () -> irq.fire(EventType.TIMER, TIMER_PRIORITY, (int) (System.nanoTime() & 0xFFFF)),
        TIMER_INITIAL_DELAY_MS,
        TIMER_PERIOD_MS,
        TimeUnit.MILLISECONDS);

    try (BitStack bs = new BitStack(path, MMAP_BYTES)) {

      // Three-point state (CPU/RAM/DISK) — disk is what we append; cpu/ram are “views”
      long cpuState = 0, ramState = 0, diskState = 0;

      // 4-cycle loop
      long tick = 0;
      while (tick < MAX_TICKS) {

        // (1) Input: take IRQ/event with priority
        Event ev = irq.poll(IRQ_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (ev == null) {
          continue;
        }

        // Build a 4x4 bits payload from event (toy mapping)
        // Seed from payload; introduce noise by random bit drop sometimes
        int bits16 = ev.payload() & 0xFFFF;

        // simulate leak/bit-missing: 2% chance drop one bit
        if (Math.random() < BIT_DROP_PROBABILITY) {
          int drop = (int)(Math.random() * 16);
          bits16 &= ~(1 << drop);
        }

        // (2) Processing: compute parity + syndrome vs last known parity
        int computedParity8 = parity2D8(bits16);

        // last parity stored in lower byte of diskState (for demo)
        int storedParity8 = (int)(diskState & 0xFF);
        int syn = syndromePopcount(storedParity8, computedParity8);

        // rhoScore: treat syndrome + event type as “info not understood yet”
        int rhoScore = syn + (ev.type() == EventType.RADIO_4G ? 2 : 0);

        // update triad states (toy): cpu mixes, ram holds, disk appends
        cpuState = mix64(cpuState, bits16, ev.type().ordinal(), ev.priority());
        ramState = ((ramState << 1) ^ (bits16 * RAM_MIX_CONSTANT)) & RAM_MASK;

        // diskState carries parity in low byte + some hash in upper bits
        diskState = (diskState & ~0xFFL) | (computedParity8 & 0xFFL);
        diskState ^= (cpuState >>> 7);

        int whoOut = whoOutTriad(cpuState, ramState, diskState);

        // (3) Output: append record (payload + meta + crc32c)
        // payloadU64: pack bits16 + event info
        long payloadU64 = ((long)(bits16 & 0xFFFF))
            | ((long)(ev.type().ordinal() & 0xFF) << 16)
            | ((long)(ev.priority() & 0xFF) << 24)
            | ((long)(syn & 0xFF) << 32)
            | ((long)(tick & 0xFFFFFFFFL) << 40);

        long salmoSignature = RafaeliaSalmoCore.pulseSignature(cpuState, ramState, diskState, rhoScore, syn);
        int meta = packMetaSalmo(computedParity8, whoOut, rhoScore, salmoSignature);
        RafaeliaKernelV22.SystemState<Long, Long, Integer> state =
            new RafaeliaKernelV22.SystemState<>(cpuState, ramState, whoOut);

        double u = rhoScore;
        double uHat = syn;
        double lambda = RafaeliaKernelV22.lambda(u, uHat);
        double epsilon = RafaeliaKernelV22.epsilon(u - uHat, lambda);
        double localTemp = RafaeliaKernelV22.localTemp(1.0, 0.1, lambda, 0.05, syn, 0.01, Math.abs(whoOut));
        double salmoLuminance = RafaeliaSalmoCore.cityLuminance(cpuState, ramState, diskState);
        double xi = RafaeliaKernelV22.abortVector(rhoScore, syn);
        boolean abort = RafaeliaKernelV22.shouldAbort(xi, RHO_SLEEP_THRESHOLD);

        double[] probs = routeProbs(ev.priority(), ev.type().ordinal());
        int route = RafaeliaKernelV22.routeMax(probs);
        double[][] gotas = kernelGotas(cpuState, ramState, diskState);
        double[] mix = RafaeliaKernelV22.mixWeighted(probs, gotas);
        double[][] distances = kernelDistances(mix);
        double[][] kappas = kernelKappas(route);
        double potential = RafaeliaKernelV22.graphPotential(distances, kappas);
        double[] grad = kernelGrad(mix);
        double[] next = RafaeliaKernelV22.attractorStep(mix, grad, 0.01);

        double deltaSimpson = RafaeliaKernelV22.deltaSimpson(epsilon,
            new double[]{lambda, epsilon}, new double[]{0.5, 0.5});
        double deltaBelady = RafaeliaKernelV22.deltaBelady(syn, rhoScore);
        double mirage = RafaeliaKernelV22.mirageVariance(new double[]{lambda, epsilon, localTemp});
        double score = RafaeliaKernelV22.score(1.0, epsilon, 1.0, localTemp, 1.0, state.action,
            1.0, deltaSimpson + deltaBelady + mirage + (salmoLuminance / RafaeliaSalmoCore.CLOCK_LIMIT_70));

        if (!abort) {
          bs.appendRecord(payloadU64, meta);
        }

        // (4) Next input: adapt (very minimal)
        // If rho high, simulate “lower harmonic” by brief sleep (stabilize)
        if (rhoScore >= RHO_SLEEP_THRESHOLD || abort) Thread.sleep(RHO_SLEEP_MS);

        // occasionally flush
        if ((tick & 0x3FF) == 0) bs.flush();

        // small console trace
        if ((tick % 500) == 0) {
          System.out.printf("tick=%d ev=%s pr=%d bits=0x%04X parity=%02X syn=%d rho=%d whoOut=%d%n",
              tick, ev.type(), ev.priority(), bits16, computedParity8, syn, rhoScore, whoOut);
          System.out.printf("route=%d lambda=%.2f eps=%.2f temp=%.2f U=%.2f salmo=%.0f score=%.2f next=[%.1f,%.1f,%.1f]%n",
              route, lambda, epsilon, localTemp, potential, salmoLuminance, score, next[0], next[1], next[2]);
        }

        tick++;
      }
    } finally {
      sch.shutdown();
      if (!sch.awaitTermination(2, TimeUnit.SECONDS)) {
        sch.shutdownNow();
      }
    }
  }

  // Simple 64-bit mixer (bitwise heavy) — engine-like core
  static long mix64(long s, int bits16, int t, int pr) {
    long x = s ^ ((long)bits16 * 0xD6E8FEB86659FD93L);
    x ^= (long)t * 0x9E3779B97F4A7C15L;
    x ^= (long)pr * 0xBF58476D1CE4E5B9L;
    x ^= (x >>> 30);
    x *= 0xBF58476D1CE4E5B9L;
    x ^= (x >>> 27);
    x *= 0x94D049BB133111EBL;
    x ^= (x >>> 31);
    return x;
  }

  static double[] routeProbs(int priority, int typeOrdinal) {
    double a = 1.0 + (priority % 5);
    double b = 1.0 + (typeOrdinal % 3);
    double c = 1.0 + ((priority + typeOrdinal) % 7);
    double sum = a + b + c;
    return new double[]{a / sum, b / sum, c / sum};
  }

  static double[][] kernelGotas(long cpu, long ram, long disk) {
    double c = (double) (cpu & 0xFFFF);
    double r = (double) (ram & 0xFFFF);
    double d = (double) (disk & 0xFFFF);
    return new double[][]{
        {c, r, d},
        {r, d, c},
        {d, c, r}
    };
  }

  static double[][] kernelDistances(double[] v) {
    double[][] out = new double[v.length][v.length];
    for (int i = 0; i < v.length; i++) {
      for (int j = 0; j < v.length; j++) {
        out[i][j] = Math.abs(v[i] - v[j]);
      }
    }
    return out;
  }

  static double[][] kernelKappas(int route) {
    double base = 1.0 + (route % 3);
    return new double[][]{
        {0.0, base, base / 2.0},
        {base, 0.0, base * 1.5},
        {base / 2.0, base * 1.5, 0.0}
    };
  }

  static double[] kernelGrad(double[] v) {
    return new double[]{
        v[0] - v[1],
        v[1] - v[2],
        v[2] - v[0]
    };
  }
}
