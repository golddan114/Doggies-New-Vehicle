package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvCombatUiCorrelationTest {
  @Test
  void reusedVehicleAndCoreRejectAnOldCoreRevision() {
    UUID requestId = UUID.randomUUID();
    DnvManagedVehicleDirectory.Selection selected = selection();
    var cursor = new DnvCombatUiCorrelation.Cursor(requestId, 7, selected);
    var newer = new DnvManagedVehicleDirectory.Selection(
        selected.vehicleId(), selected.dimension(), selected.coreId(),
        selected.coreRevision() + 1);
    assertEquals(DnvCombatUiCorrelation.Decision.IDENTITY_MISMATCH,
        DnvCombatUiCorrelation.evaluate(cursor, view(requestId, 7, newer)));
  }

  @Test
  void onlyTheExactRequestSequenceAndSelectionMayUpdateTheDraft() {
    var selection = selection();
    UUID requestId = UUID.randomUUID();
    var cursor = new DnvCombatUiCorrelation.Cursor(requestId, 7, selection);
    assertEquals(DnvCombatUiCorrelation.Decision.ACCEPT,
        DnvCombatUiCorrelation.evaluate(cursor, view(requestId, 7, selection)));
    assertEquals(DnvCombatUiCorrelation.Decision.REQUEST_MISMATCH,
        DnvCombatUiCorrelation.evaluate(cursor, view(UUID.randomUUID(), 7, selection)));
    assertEquals(DnvCombatUiCorrelation.Decision.SEQUENCE_MISMATCH,
        DnvCombatUiCorrelation.evaluate(cursor, view(requestId, 6, selection)));
    assertEquals(DnvCombatUiCorrelation.Decision.IDENTITY_MISMATCH,
        DnvCombatUiCorrelation.evaluate(cursor, view(requestId, 7, selection())));
  }

  @Test
  void timeoutStartsAtTheDeclaredBoundary() {
    assertFalse(DnvCombatUiCorrelation.timedOut(DnvCombatUiCorrelation.TIMEOUT_TICKS - 1));
    assertTrue(DnvCombatUiCorrelation.timedOut(DnvCombatUiCorrelation.TIMEOUT_TICKS));
  }

  private static DnvCombatUi.View view(
      UUID requestId, long sequence, DnvManagedVehicleDirectory.Selection selection) {
    return new DnvCombatUi.View(
        requestId, sequence, selection, "NONE", 0, 0, List.of(), List.of(), Optional.empty());
  }

  private static DnvManagedVehicleDirectory.Selection selection() {
    return new DnvManagedVehicleDirectory.Selection(
        UUID.randomUUID(), "minecraft:overworld", UUID.randomUUID(), 2);
  }
}
