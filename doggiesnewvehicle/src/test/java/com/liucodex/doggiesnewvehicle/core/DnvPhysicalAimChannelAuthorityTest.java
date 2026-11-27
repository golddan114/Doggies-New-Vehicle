package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvPhysicalAimChannelAuthorityTest {
  private static final UUID DRIVER =
      UUID.fromString("c1000000-0000-0000-0000-000000000001");

  @Test
  void realManualInputPreemptsSharedChannelThenAiRecoversWithinBound() {
    DnvPhysicalAimChannelAuthority authority =
        new DnvPhysicalAimChannelAuthority("sbw:passenger");

    DnvPhysicalAimChannelAuthority.Decision idle = authority.observe(observation(
        100L, Optional.of(DRIVER), true, false, false));
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.AI, idle.owner());
    assertTrue(authority.authorizeAiCommand(idle.revision(), 100L, 100L));

    DnvPhysicalAimChannelAuthority.Decision firing = authority.observe(observation(
        101L, Optional.of(DRIVER), true, false, true));
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.MANUAL, firing.owner());
    assertEquals(DnvPhysicalAimChannelAuthority.Reason.MANUAL_INPUT, firing.reason());
    assertFalse(authority.authorizeAiCommand(firing.revision(), 101L, 101L));

    assertEquals(
        DnvPhysicalAimChannelAuthority.Owner.MANUAL,
        authority.observe(observation(108L, Optional.of(DRIVER), true, false, false)).owner());
    DnvPhysicalAimChannelAuthority.Decision recovered = authority.observe(observation(
        109L, Optional.of(DRIVER), true, false, false));
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.AI, recovered.owner());
    assertTrue(authority.authorizeAiCommand(recovered.revision(), 109L, 109L));
    assertFalse(authority.authorizeAiCommand(recovered.revision(), 109L, 112L));
  }

  @Test
  void leavingUnloadingRestartAndUncertainSignalsFailClosedWithoutGhostLease() {
    DnvPhysicalAimChannelAuthority authority =
        new DnvPhysicalAimChannelAuthority("sbw:passenger");
    authority.observe(observation(20L, Optional.of(DRIVER), true, true, false));

    DnvPhysicalAimChannelAuthority.Decision left = authority.observe(observation(
        21L, Optional.empty(), true, false, false));
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.AI, left.owner());

    DnvPhysicalAimChannelAuthority.Decision uncertain = authority.observe(observation(
        22L, Optional.of(DRIVER), false, false, false));
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.NONE, uncertain.owner());
    assertEquals(
        DnvPhysicalAimChannelAuthority.Reason.MANUAL_SIGNAL_UNCERTAIN,
        uncertain.reason());

    authority.clear();
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.NONE, authority.snapshot().owner());
    DnvPhysicalAimChannelAuthority restarted =
        new DnvPhysicalAimChannelAuthority("sbw:passenger");
    assertEquals(DnvPhysicalAimChannelAuthority.Owner.NONE, restarted.snapshot().owner());
    assertEquals(
        DnvPhysicalAimChannelAuthority.Owner.NONE,
        restarted.observe(new DnvPhysicalAimChannelAuthority.Observation(
            0L, false, true, true, Optional.empty(), true, false, false)).owner());
  }

  private static DnvPhysicalAimChannelAuthority.Observation observation(
      long gameTime,
      Optional<UUID> driver,
      boolean reliable,
      boolean manualAim,
      boolean manualFire) {
    return new DnvPhysicalAimChannelAuthority.Observation(
        gameTime, true, true, true, driver, reliable, manualAim, manualFire);
  }
}
