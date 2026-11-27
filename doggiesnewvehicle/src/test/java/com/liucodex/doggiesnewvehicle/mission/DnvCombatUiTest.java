package com.liucodex.doggiesnewvehicle.mission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvCombatUiTest {
  @Test
  void entityIdentityContainsOnlyDimensionAndUuid() {
    var components = DnvCombatUi.TargetIdentity.class.getRecordComponents();
    assertEquals(List.of("dimension", "entityId"),
        Arrays.stream(components).map(java.lang.reflect.RecordComponent::getName).toList());
    assertEquals(String.class, components[0].getType());
    assertEquals(UUID.class, components[1].getType());
  }

  @Test
  void requestPayloadMustMatchItsExplicitAction() {
    var identity = new DnvCombatUi.TargetIdentity("minecraft:overworld", UUID.randomUUID());
    assertDoesNotThrow(() -> new DnvCombatUi.Request(
        UUID.randomUUID(), 1, selection(), 0, UUID.randomUUID(), DnvCombatUi.Action.DESIGNATE_ENTITY,
        Optional.of(identity)));
    assertDoesNotThrow(() -> new DnvCombatUi.Request(
        UUID.randomUUID(), 2, selection(), 0, UUID.randomUUID(), DnvCombatUi.Action.CANCEL, Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new DnvCombatUi.Request(
        UUID.randomUUID(), 3, selection(), 0, UUID.randomUUID(), DnvCombatUi.Action.REFRESH,
        Optional.of(identity)));
    assertThrows(IllegalArgumentException.class, () -> new DnvCombatUi.Request(
        UUID.randomUUID(), 4, selection(), 0, UUID.randomUUID(), DnvCombatUi.Action.DESIGNATE_ENTITY,
        Optional.empty()));
  }

  @Test
  void ordersAndProjectionListsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new DnvCombatUi.Order(
        UUID.randomUUID(), 1, Optional.empty(), true, DnvCombatUi.Phase.INTENT_ACCEPTED, "NONE", 20));
    var capability = new DnvCombatUi.Capability(false, true, "NONE");
    assertThrows(IllegalArgumentException.class, () -> new DnvCombatUi.View(
        UUID.randomUUID(), 1, selection(), "NONE", 0, 0,
        Collections.nCopies(DnvCombatUi.MAX_CAPABILITIES + 1, capability), List.of(), Optional.empty()));
  }

  private static DnvManagedVehicleDirectory.Selection selection() {
    return new DnvManagedVehicleDirectory.Selection(
        UUID.randomUUID(), "minecraft:overworld", UUID.randomUUID(), 7);
  }
}
