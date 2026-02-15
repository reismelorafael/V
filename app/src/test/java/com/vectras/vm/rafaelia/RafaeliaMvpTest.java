package com.vectras.vm.rafaelia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

import org.junit.Test;

public class RafaeliaMvpTest {

  @Test
  public void resolveSeedUsesFixedDefaultForBenchmark() {
    long seed = RafaeliaMvp.resolveSeed(RafaeliaMvp.MODE_BENCHMARK, null);
    assertEquals(RafaeliaMvp.BENCHMARK_DEFAULT_SEED, seed);
  }

  @Test
  public void resolveSeedUsesProvidedSeedForAnyMode() {
    long expected = 0x1234ABCDL;
    assertEquals(expected, RafaeliaMvp.resolveSeed(RafaeliaMvp.MODE_BENCHMARK, expected));
    assertEquals(expected, RafaeliaMvp.resolveSeed(RafaeliaMvp.MODE_FUZZ, expected));
  }

  @Test
  public void parseMainConfigSupportsModeSeedAndPath() {
    RafaeliaMvp.RuntimeConfig config = RafaeliaMvp.parseMainConfig(
        new String[] {"mode=benchmark", "seed=0x10", "./custom.bin"});

    assertEquals(RafaeliaMvp.MODE_BENCHMARK, config.mode());
    assertEquals(Long.valueOf(16L), config.providedSeed());
    assertEquals(16L, config.resolvedSeed());
    assertEquals("./custom.bin", config.path().getPath());
  }

  @Test
  public void parseMainConfigDefaultsToFuzzAndVariableSeed() {
    RafaeliaMvp.RuntimeConfig configA = RafaeliaMvp.parseMainConfig(new String[] {});
    RafaeliaMvp.RuntimeConfig configB = RafaeliaMvp.parseMainConfig(new String[] {});

    assertEquals(RafaeliaMvp.MODE_FUZZ, configA.mode());
    assertNotEquals(RafaeliaMvp.BENCHMARK_DEFAULT_SEED, configA.resolvedSeed());
    assertNotEquals(configA.resolvedSeed(), configB.resolvedSeed());
  }

  @Test
  public void parityForSingleBitSetsRowAndColumn() {
    int parity = RafaeliaMvp.parity2D8(1);
    assertEquals(0x11, parity);
  }

  @Test
  public void bitStackAppendsPayloadAndCrc() throws Exception {
    File tmp = File.createTempFile("rafaelia", ".bin");
    tmp.deleteOnExit();

    long payload = 0x1122334455667788L;
    int meta = 0xAABBCCDD;

    try (RafaeliaMvp.BitStack bs = new RafaeliaMvp.BitStack(tmp, 1024 * 1024)) {
      bs.appendRecord(payload, meta);
      bs.flush();
    }

    try (RandomAccessFile raf = new RandomAccessFile(tmp, "r")) {
      long payloadRead = raf.readLong();
      int metaRead = raf.readInt();
      int crcRead = raf.readInt();

      assertEquals(payload, payloadRead);
      assertEquals(meta, metaRead);
      assertEquals(RafaeliaMvp.BitStack.crc32c(payload, meta), crcRead);
    } finally {
      Files.deleteIfExists(tmp.toPath());
    }
  }

  @Test
  public void deterministicRngSameSeedProducesSameSequence() {
    RafaeliaMvp.DeterministicRng rngA = new RafaeliaMvp.SplittableDeterministicRng(0x1234ABCDL);
    RafaeliaMvp.DeterministicRng rngB = new RafaeliaMvp.SplittableDeterministicRng(0x1234ABCDL);

    for (int i = 0; i < 64; i++) {
      assertEquals(rngA.nextInt(1024), rngB.nextInt(1024));
      assertEquals(rngA.nextDouble(), rngB.nextDouble(), 0.0);
    }
  }

  @Test
  public void deterministicRngDifferentSeedProducesDifferentSequence() {
    RafaeliaMvp.DeterministicRng rngA = new RafaeliaMvp.SplittableDeterministicRng(1L);
    RafaeliaMvp.DeterministicRng rngB = new RafaeliaMvp.SplittableDeterministicRng(2L);

    int a = rngA.nextInt(1 << 16);
    int b = rngB.nextInt(1 << 16);

    assertNotEquals(a, b);
  }
}
