package com.vectras.vm.rafaelia;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RafaeliaSalmoCoreTest {
  @Test
  public void testResetPhase70KeepsRangeAndHandlesNegative() {
    assertEquals(0L, RafaeliaSalmoCore.resetPhase70(70L));
    assertEquals(69L, RafaeliaSalmoCore.resetPhase70(-1L));
    assertEquals(0L, RafaeliaSalmoCore.resetPhase70(-70L));
  }

  @Test
  public void testCityLuminanceDeterministicPath() {
    long value = RafaeliaSalmoCore.cityLuminance(0x1234L, 0x5678L, 0x9ABCL);
    assertEquals(19L, value);
  }

  @Test
  public void testPulseSignatureStable() {
    long signature = RafaeliaSalmoCore.pulseSignature(0x1111L, 0x2222L, 0x3333L, 7, 5);
    assertEquals(2337828L, signature);
  }
}
