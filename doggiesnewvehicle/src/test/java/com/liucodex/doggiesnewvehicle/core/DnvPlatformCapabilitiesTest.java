package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DnvPlatformCapabilitiesTest {
  @Test
  void fourFamiliesAreCapabilitiesInsteadOfSeparateAiImplementations() {
    var turret = DnvPlatformCapabilities.defaults(
        DnvPlatformCapabilities.PlatformFamily.TURRET);
    var ground = DnvPlatformCapabilities.defaults(
        DnvPlatformCapabilities.PlatformFamily.GROUND);
    var surface = DnvPlatformCapabilities.defaults(
        DnvPlatformCapabilities.PlatformFamily.SURFACE);
    var air = DnvPlatformCapabilities.defaults(
        DnvPlatformCapabilities.PlatformFamily.AIR);

    assertFalse(turret.movementActuator());
    assertTrue(ground.supports(DnvPlatformCapabilities.MovementMedium.GROUND));
    assertTrue(surface.supports(DnvPlatformCapabilities.MovementMedium.SURFACE));
    assertTrue(air.supports(DnvPlatformCapabilities.SensorCapability.ALTITUDE));
  }

  @Test
  void mixedPlatformsComposeMediaWithoutDuplicatingTheCore() {
    DnvPlatformCapabilities amphibious = new DnvPlatformCapabilities(
        DnvPlatformCapabilities.PlatformFamily.GROUND,
        Set.of(DnvPlatformCapabilities.MovementMedium.GROUND,
            DnvPlatformCapabilities.MovementMedium.SURFACE),
        Set.of(DnvPlatformCapabilities.SensorCapability.LOCAL_ENTITIES,
            DnvPlatformCapabilities.SensorCapability.WATER_SURFACE),
        Set.of(DnvPlatformCapabilities.BallisticCapability.DIRECT),
        true, 4, 4, 20, 10, 10, 40);

    assertTrue(amphibious.supports(DnvPlatformCapabilities.MovementMedium.GROUND));
    assertTrue(amphibious.supports(DnvPlatformCapabilities.MovementMedium.SURFACE));
  }

  @Test
  void unsafeTickIntervalsAndStationCountsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new DnvPlatformCapabilities(
        DnvPlatformCapabilities.PlatformFamily.GROUND,
        Set.of(DnvPlatformCapabilities.MovementMedium.GROUND),
        Set.of(), Set.of(), true, 33, 9, 1, 1, 1, 1));
  }
}
