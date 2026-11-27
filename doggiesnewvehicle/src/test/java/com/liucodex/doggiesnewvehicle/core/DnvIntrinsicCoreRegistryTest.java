package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DnvIntrinsicCoreRegistryTest {
  @Test
  void installsTheSameSharedCoreFromAServerProfile() {
    DnvIntrinsicCoreRegistry registry = new DnvIntrinsicCoreRegistry();
    DnvIntrinsicCoreRegistry.Profile profile = profile("dnv:storm", 1L, true);
    assertTrue(registry.publish(profile).accepted());

    DnvIntrinsicCoreRegistry.InstalledCore installed = registry.install("dnv:storm").orElseThrow();
    assertEquals(DnvAutonomyCore.class, installed.core().getClass());
    assertTrue(installed.active(registry));
    assertEquals(DnvPlatformCapabilities.PlatformFamily.TURRET,
        installed.profile().capabilities().family());
  }

  @Test
  void staleReplacementAndProfileRevocationFailClosed() {
    DnvIntrinsicCoreRegistry registry = new DnvIntrinsicCoreRegistry();
    registry.publish(profile("dnv:storm", 2L, true));
    DnvIntrinsicCoreRegistry.InstalledCore installed = registry.install("dnv:storm").orElseThrow();
    assertFalse(registry.publish(profile("dnv:storm", 1L, true)).accepted());
    assertTrue(registry.publish(profile("dnv:storm", 3L, true)).accepted());
    assertFalse(installed.active(registry));
    assertTrue(registry.revoke("dnv:storm", 3L).accepted());
    assertTrue(registry.install("dnv:storm").isEmpty());
  }

  @Test
  void disabledProfilePreservesManualUseButCannotInstallAutonomy() {
    DnvIntrinsicCoreRegistry registry = new DnvIntrinsicCoreRegistry();
    registry.publish(profile("dnv:changfeng", 1L, false));
    assertTrue(registry.install("dnv:changfeng").isEmpty());
    assertEquals(DnvIntrinsicCoreRegistry.Reason.PROFILE_DISABLED,
        registry.inspect("dnv:changfeng").reason());
  }

  private static DnvIntrinsicCoreRegistry.Profile profile(
      String id, long revision, boolean enabled) {
    return new DnvIntrinsicCoreRegistry.Profile(
        DnvIntrinsicCoreRegistry.CURRENT_SCHEMA,
        id,
        "dnv:built_in_turrets",
        revision,
        "dnv:neutral_defense",
        enabled,
        8.0D,
        180.0D,
        DnvPlatformCapabilities.defaults(DnvPlatformCapabilities.PlatformFamily.TURRET),
        Set.of(DnvStationEngagementCore.TargetingMode.INDEPENDENT,
            DnvStationEngagementCore.TargetingMode.DESIGNATED_ONLY));
  }
}
