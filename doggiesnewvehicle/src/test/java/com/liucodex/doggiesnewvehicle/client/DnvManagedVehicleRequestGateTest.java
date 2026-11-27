package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvManagedVehicleRequestGateTest {
  @Test
  void switchingSelectionRejectsLateAndDuplicateReplies() {
    var gate = new DnvManagedVehicleRequestGate();
    var old = gate.begin(Optional.of(selection()));
    gate.invalidate();
    var current = gate.begin(Optional.of(selection()));
    assertFalse(gate.accept(reply(old)));
    assertTrue(gate.waiting());
    assertTrue(gate.accept(reply(current)));
    assertFalse(gate.accept(reply(current)));
    assertFalse(gate.waiting());
  }

  @Test
  void replyCannotUseMatchingRequestIdWithDifferentCoreOrDimension() {
    var gate = new DnvManagedVehicleRequestGate();
    var selection = selection();
    var query = gate.begin(Optional.of(selection));
    for (var wrong : List.of(
        new DnvManagedVehicleDirectory.Selection(
            selection.vehicleId(), selection.dimension(), UUID.randomUUID(), 1L),
        new DnvManagedVehicleDirectory.Selection(
            selection.vehicleId(), "minecraft:the_nether", selection.coreId(), 1L),
        new DnvManagedVehicleDirectory.Selection(
            selection.vehicleId(), selection.dimension(), selection.coreId(), 2L))) {
      assertFalse(gate.accept(reply(new DnvManagedVehicleDirectory.Query(
          query.requestId(), Optional.of(wrong)))));
    }
    assertTrue(gate.accept(reply(query)));
  }

  @Test
  void timeoutUnlocksRefreshAndRejectsTheTimedOutReply() {
    var gate = new DnvManagedVehicleRequestGate();
    var query = gate.begin(Optional.of(selection()));
    for (int tick = 0; tick < DnvManagedVehicleRequestGate.EXPIRY_TICKS; tick++) {
      gate.tick();
    }
    assertTrue(gate.expired());
    assertTrue(gate.refreshDue());
    assertFalse(gate.waiting());
    assertFalse(gate.accept(reply(query)));
  }

  @Test
  void pageExitAndNewScreenRejectPreviousScreenRequests() {
    var gate = new DnvManagedVehicleRequestGate();
    var query = gate.begin(Optional.empty());
    gate.invalidate();
    assertFalse(gate.accept(reply(query)));
    var reopened = new DnvManagedVehicleRequestGate();
    reopened.begin(Optional.empty());
    assertFalse(reopened.accept(reply(query)));
  }

  @Test
  void acceptedSampleExpiresEvenWhenRefreshDoesNotReturn() {
    var gate = new DnvManagedVehicleRequestGate();
    assertTrue(gate.accept(reply(gate.begin(Optional.of(selection())))));
    for (int tick = 0; tick < 20; tick++) {
      gate.tick();
    }
    assertTrue(gate.refreshDue());
    gate.begin(Optional.of(selection()));
    for (int tick = 0; tick < 40; tick++) {
      gate.tick();
    }
    assertTrue(gate.expired());
  }

  private static DnvManagedVehicleDirectory.Selection selection() {
    return new DnvManagedVehicleDirectory.Selection(
        UUID.randomUUID(), "minecraft:overworld", UUID.randomUUID(), 1L);
  }

  private static DnvManagedVehicleDirectory.View reply(DnvManagedVehicleDirectory.Query query) {
    return new DnvManagedVehicleDirectory.View(query.requestId(), false, "NONE", List.of(),
        query.selected().map(selection -> new DnvManagedVehicleDirectory.Detail(
            selection, DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE, false, 0L, List.of())));
  }
}
