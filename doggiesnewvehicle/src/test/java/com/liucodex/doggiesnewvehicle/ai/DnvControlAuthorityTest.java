package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvControlAuthorityTest {
  private static final UUID PILOT = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID GUNNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Test
  void playerPilotDoesNotDisableAutomaticTurrets() {
    DnvControlAuthority authority = authority();
    assertTrue(authority.claimFlight(PILOT, 100L, 40, 0L).accepted());

    assertEquals(DnvControlAuthority.ControlOwner.PLAYER, authority.flightOwner(100L));
    DnvControlAuthority.StationAuthority nose = authority.weaponAuthority("nose", 100L);
    assertEquals(DnvControlAuthority.ControlOwner.AI, nose.owner());
    assertTrue(nose.mayTrack());
    assertTrue(nose.mayFire());
  }

  @Test
  void weaponLeaseIsPerStationAndCannotBeStolen() {
    DnvControlAuthority authority = authority();
    assertTrue(authority.claimWeapon("nose", GUNNER, 10L, 20, 0L).accepted());

    assertEquals(
        DnvControlAuthority.ControlOwner.REMOTE_MANUAL_FIRE,
        authority.weaponAuthority("nose", 10L).owner());
    assertEquals(
        DnvControlAuthority.ControlOwner.AI,
        authority.weaponAuthority("tail", 10L).owner());
    DnvControlAuthority.MutationResult conflict =
        authority.claimWeapon("nose", OTHER, 10L, 20, authority.revision());
    assertFalse(conflict.accepted());
    assertEquals(DnvControlAuthority.RejectReason.OCCUPIED, conflict.reason());
  }

  @Test
  void holdFireRevokesStationLeaseWithoutChangingFlightOwner() {
    DnvControlAuthority authority = authority();
    authority.claimFlight(PILOT, 0L, 40, 0L);
    authority.claimWeapon("nose", GUNNER, 0L, 40, 1L);
    assertTrue(
        authority.setWeaponMode(
            "nose", DnvControlAuthority.WeaponMode.HOLD_FIRE, 2L).accepted());

    DnvControlAuthority.StationAuthority station = authority.weaponAuthority("nose", 1L);
    assertEquals(DnvControlAuthority.ControlOwner.NONE, station.owner());
    assertFalse(station.mayTrack());
    assertFalse(station.mayFire());
    assertEquals(DnvControlAuthority.ControlOwner.PLAYER, authority.flightOwner(1L));
  }

  @Test
  void revisionRejectsLatePacketsAndLeaseExpiryReturnsOnlyItsOwnerToAi() {
    DnvControlAuthority authority = authority();
    authority.claimFlight(PILOT, 100L, 5, 0L);
    DnvControlAuthority.MutationResult stale =
        authority.claimWeapon("nose", GUNNER, 100L, 5, 0L);
    assertFalse(stale.accepted());
    assertEquals(DnvControlAuthority.RejectReason.STALE_REVISION, stale.reason());

    authority.claimWeapon("nose", GUNNER, 100L, 10, authority.revision());
    assertTrue(authority.expireLeases(105L));
    assertEquals(DnvControlAuthority.ControlOwner.AI, authority.flightOwner(105L));
    assertEquals(
        DnvControlAuthority.ControlOwner.REMOTE_MANUAL_FIRE,
        authority.weaponAuthority("nose", 105L).owner());
  }

  @Test
  void disconnectAndRestartCleanupArePlayerIsolated() {
    DnvControlAuthority authority = authority();
    authority.claimFlight(PILOT, 0L, 40, 0L);
    authority.claimWeapon("nose", GUNNER, 0L, 40, 1L);
    authority.claimWeapon("tail", OTHER, 0L, 40, 2L);

    assertTrue(authority.clearPlayer(GUNNER));
    assertEquals(DnvControlAuthority.ControlOwner.PLAYER, authority.flightOwner(1L));
    assertEquals(DnvControlAuthority.ControlOwner.AI,
        authority.weaponAuthority("nose", 1L).owner());
    assertEquals(DnvControlAuthority.ControlOwner.REMOTE_MANUAL_FIRE,
        authority.weaponAuthority("tail", 1L).owner());

    assertTrue(authority.clearAllPlayerLeases());
    assertEquals(DnvControlAuthority.ControlOwner.AI, authority.flightOwner(1L));
    assertEquals(DnvControlAuthority.ControlOwner.AI,
        authority.weaponAuthority("tail", 1L).owner());
  }

  @Test
  void manualOnlyStationFailsSafeWhenEmpty() {
    DnvControlAuthority authority =
        new DnvControlAuthority(List.of(new DnvControlAuthority.StationDefinition(
            "manual", true, DnvControlAuthority.WeaponMode.MANUAL_ONLY)));
    DnvControlAuthority.StationAuthority empty = authority.weaponAuthority("manual", 0L);
    assertEquals(DnvControlAuthority.ControlOwner.NONE, empty.owner());
    assertFalse(empty.mayFire());

    assertTrue(authority.claimWeapon("manual", GUNNER, 0L, 20, 0L).accepted());
    assertTrue(authority.weaponAuthority("manual", 1L).mayFire());
  }

  @Test
  void physicalSeatPreemptsRemoteLeaseAndAiWaitsAfterVacating() {
    DnvControlAuthority authority = authority();
    authority.claimWeapon("nose", GUNNER, 100L, 100, 0L);

    assertTrue(authority.synchronizePhysicalOccupant(
        "nose", java.util.Optional.of(OTHER), 101L, 10, 1L).accepted());
    DnvControlAuthority.StationAuthority occupied = authority.weaponAuthority("nose", 101L);
    assertEquals(DnvControlAuthority.ControlOwner.PLAYER, occupied.owner());
    assertEquals(OTHER, occupied.playerId().orElseThrow());

    assertTrue(authority.synchronizePhysicalOccupant(
        "nose", java.util.Optional.empty(), 110L, 10, 2L).accepted());
    assertEquals(
        DnvControlAuthority.ControlOwner.NONE,
        authority.weaponAuthority("nose", 119L).owner());
    assertEquals(
        DnvControlAuthority.ControlOwner.AI,
        authority.weaponAuthority("nose", 120L).owner());
  }

  @Test
  void physicalSeatHonorsHoldFireAndRejectsUnboundedResumeDelay() {
    DnvControlAuthority authority = authority();
    authority.setWeaponMode("nose", DnvControlAuthority.WeaponMode.HOLD_FIRE, 0L);
    assertTrue(authority.synchronizePhysicalOccupant(
        "nose", java.util.Optional.of(GUNNER), 0L, 5, 1L).accepted());
    DnvControlAuthority.StationAuthority occupied = authority.weaponAuthority("nose", 0L);
    assertEquals(DnvControlAuthority.ControlOwner.PLAYER, occupied.owner());
    assertFalse(occupied.mayTrack());
    assertFalse(occupied.mayFire());

    DnvControlAuthority.MutationResult invalid = authority.synchronizePhysicalOccupant(
        "tail", java.util.Optional.empty(), 0L,
        DnvControlAuthority.MAX_AI_RESUME_DELAY_TICKS + 1, authority.revision());
    assertFalse(invalid.accepted());
    assertEquals(DnvControlAuthority.RejectReason.INVALID_RESUME_DELAY, invalid.reason());
  }

  private static DnvControlAuthority authority() {
    return new DnvControlAuthority(
        List.of(
            new DnvControlAuthority.StationDefinition(
                "nose", true, DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
            new DnvControlAuthority.StationDefinition(
                "tail", true, DnvControlAuthority.WeaponMode.MANUAL_PREFERRED)));
  }
}
