package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvPhysicalAimChannelAuthority;
import com.liucodex.doggiesnewvehicle.core.DnvSbwWeaponDiagnostics;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvSbwWeaponDiagnosticsTest {
  @Test
  void projectionIsLowFrequencyBoundedAndDisableable() {
    DnvSbwWeaponDiagnostics diagnostics = new DnvSbwWeaponDiagnostics();
    assertTrue(diagnostics.beginFrame(100L, 9));
    for (int index = 0; index < DnvSbwWeaponDiagnostics.MAX_ENTRIES + 10; index++) {
      diagnostics.record(draft("weapon_" + index));
    }
    diagnostics.completeFrame();

    assertEquals(100L, diagnostics.snapshot().capturedGameTime());
    assertEquals(9, diagnostics.snapshot().candidateCount());
    assertEquals(DnvSbwWeaponDiagnostics.MAX_ENTRIES, diagnostics.snapshot().weapons().size());
    assertFalse(diagnostics.beginFrame(119L, 10));
    assertTrue(diagnostics.beginFrame(120L, 10));

    diagnostics.setEnabled(false);
    assertFalse(diagnostics.enabled());
    assertFalse(diagnostics.beginFrame(140L, 1));
    assertTrue(diagnostics.snapshot().weapons().isEmpty());
  }

  @Test
  void lastFireResultIsReadOnlyBoundedStateRatherThanALog() {
    DnvSbwWeaponDiagnostics diagnostics = new DnvSbwWeaponDiagnostics();
    diagnostics.recordFireResult("seat_1", "Cannon40", "FIRED", 200L);
    diagnostics.beginFrame(200L, 1);
    diagnostics.record(draft("Cannon40"));
    diagnostics.completeFrame();

    DnvSbwWeaponDiagnostics.WeaponSnapshot weapon =
        diagnostics.snapshot().weapons().get(0);
    assertEquals("FIRED", weapon.lastFireResult());
    assertEquals(200L, weapon.lastFireGameTime());
  }

  private static DnvSbwWeaponDiagnostics.WeaponDraft draft(String weapon) {
    return new DnvSbwWeaponDiagnostics.WeaponDraft(
        "seat_1",
        weapon,
        Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        "TARGET_AUTHORIZED",
        -90.0F,
        -20.0F,
        "sbw:passenger",
        DnvPhysicalAimChannelAuthority.Owner.AI,
        DnvPhysicalAimChannelAuthority.Reason.AI_AUTHORIZED,
        -85.0F,
        -19.0F,
        -90.0F,
        -20.0F,
        3,
        true,
        true,
        12,
        true,
        0,
        "READY");
  }
}
