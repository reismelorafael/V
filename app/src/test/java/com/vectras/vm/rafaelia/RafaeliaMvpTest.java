package com.vectras.vm.rafaelia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

import org.junit.Test;

public class RafaeliaMvpTest {

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
  public void deterministicRngSameSeedProducesEqualMixedSequence() {
    RafaeliaMvp.DeterministicRng first = new RafaeliaMvp.DeterministicRng(0x1234ABCDL);
    RafaeliaMvp.DeterministicRng second = new RafaeliaMvp.DeterministicRng(0x1234ABCDL);

    for (int i = 0; i < 2048; i++) {
      assertEquals(first.nextInt(65536), second.nextInt(65536));
      assertEquals(first.nextDouble(), second.nextDouble(), 0.0d);
    }
  }

  @Test
  public void deterministicRngDifferentSeedsDivergeInMixedSequence() {
    RafaeliaMvp.DeterministicRng first = new RafaeliaMvp.DeterministicRng(0x1234ABCDL);
    RafaeliaMvp.DeterministicRng second = new RafaeliaMvp.DeterministicRng(0x1234ABCEL);

    boolean foundDifference = false;
    for (int i = 0; i < 2048; i++) {
      int aInt = first.nextInt(65536);
      int bInt = second.nextInt(65536);
      double aDouble = first.nextDouble();
      double bDouble = second.nextDouble();
      if (aInt != bInt || Double.compare(aDouble, bDouble) != 0) {
        foundDifference = true;
        break;
      }
    }

    assertTrue(foundDifference);
  }

  @Test
  public void payloadDropHelperIsRepeatableWithInjectedRng() {
    RafaeliaMvp.DeterministicRng first = new RafaeliaMvp.DeterministicRng(0xCAFEBABEL);
    RafaeliaMvp.DeterministicRng second = new RafaeliaMvp.DeterministicRng(0xCAFEBABEL);

    for (int i = 0; i < 1024; i++) {
      int payload = (i * 131) ^ 0x5A5A;
      int p1 = RafaeliaMvp.generatePayloadWithOptionalDrop(payload, 0.25, first);
      int p2 = RafaeliaMvp.generatePayloadWithOptionalDrop(payload, 0.25, second);
      assertEquals(p1, p2);
    }
  }
}
