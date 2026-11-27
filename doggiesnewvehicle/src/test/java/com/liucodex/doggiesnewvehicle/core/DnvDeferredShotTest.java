package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DnvDeferredShotTest {
  private static final Point MUZZLE = new Point(2, 3, 4);
  private static final Point VELOCITY = new Point(5, 0, 0);

  static DnvDeferredShot shot(String weapon) {
    return new DnvDeferredShot(new DnvDeferredShot.Intent(UUID.randomUUID(), UUID.randomUUID(), "station_2", weapon,
        UUID.randomUUID(), UUID.randomUUID(), 4, 5, 6, 7, 100, 0, MUZZLE));
  }

  @ParameterizedTest @ValueSource(strings = {"CORE_CHANGED", "MANUAL_TAKEOVER", "MISSION_CHANGED", "PROVIDER_REMOVED",
      "TARGET_FRIENDLY", "AMMO_CHANGED", "MUZZLE_BLOCKED", "LIFECYCLE_INVALIDATED"})
  void observesRevocationAfterQueuingWithoutCallingExecutor(String reason) {
    var shot = shot("other_platform_weapon");
    var permission = new AtomicReference<String>();
    var calls = new AtomicInteger();
    shot.queued(true);
    assertEquals(DnvDeferredShot.State.QUEUED, shot.receipt().state());
    permission.set(reason);
    shot.execute(101, permission::get, calls::incrementAndGet);
    assertEquals(reason, shot.receipt().reason());
    assertEquals(0, calls.get());
    assertTrue(shot.receipt().admission().isEmpty());
  }

  @ParameterizedTest @ValueSource(longs = {99, 103, Long.MAX_VALUE})
  void rejectsLateAndRegressedTimeBeforeAnyNativeEffect(long tick) {
    var shot = shot("weapon");
    shot.queued(true);
    shot.execute(tick, () -> { fail("expired permission must not be consulted"); return null; }, () -> fail("expired shot"));
    assertEquals("EXPIRED", shot.receipt().reason());
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void onlyTheWorldBooleanCanSignAdmission(boolean accepted) {
    var shot = shot("weapon");
    UUID projectile = UUID.randomUUID();
    AtomicInteger nativeCalls = new AtomicInteger();
    shot.queued(true);
    shot.execute(102, () -> null, () -> {
      assertEquals(accepted, shot.admit(projectile, MUZZLE, VELOCITY, () -> {
        nativeCalls.incrementAndGet();
        return accepted;
      }));
      // Upstream returning true here must not change a world rejection into success.
    });
    assertEquals(accepted ? "WORLD_ACCEPTED" : "WORLD_REJECTED", shot.receipt().reason());
    assertEquals(1, nativeCalls.get());
    var receipt = shot.receipt().admission().orElseThrow();
    assertEquals(projectile, receipt.projectileId());
    assertEquals(MUZZLE, receipt.position());
    assertEquals(VELOCITY, receipt.velocity());
    assertEquals(accepted, receipt.accepted());
    shot.execute(102, () -> null, () -> fail("duplicate callback"));
    assertFalse(shot.admit(UUID.randomUUID(), MUZZLE, VELOCITY, () -> { fail("duplicate world call"); return true; }));
  }

  @Test void voidQueueAndNoProjectileRemainExplicitlyUnconfirmed() {
    var shot = shot("weapon");
    shot.queued(true);
    shot.execute(100, () -> null, () -> {});
    assertEquals("NO_ADMISSION", shot.receipt().reason());
    assertTrue(shot.receipt().admission().isEmpty());
    var rejected = shot("weapon");
    assertFalse(rejected.queued(false));
    rejected.execute(100, () -> null, () -> fail("queue rejected"));
    assertEquals("QUEUE_REJECTED", rejected.receipt().reason());
  }

  @Test void reentrantWorldCallbackAndExecutionNeverDuplicateTheOwner() {
    var shot = shot("weapon");
    shot.queued(true);
    shot.execute(100, () -> null, () -> {
      shot.execute(100, () -> null, () -> fail("reentrant executor"));
      shot.admit(UUID.randomUUID(), MUZZLE, VELOCITY, () -> {
        assertFalse(shot.admit(UUID.randomUUID(), MUZZLE, VELOCITY, () -> { fail("reentrant world call"); return true; }));
        return true;
      });
    });
    assertEquals(DnvDeferredShot.State.ACCEPTED, shot.receipt().state());
  }

  @Test void invalidationDuringRevalidationAndRestartDoNotRestorePermission() {
    var shot = shot("weapon");
    shot.queued(true);
    shot.execute(100, () -> { shot.cancel("UNLOAD"); return null; }, () -> fail("invalidated within check"));
    assertEquals("UNLOAD", shot.receipt().reason());
    assertFalse(shot.queued(true));
    var restored = shot("weapon");
    restored.execute(100, () -> null, () -> fail("new object isn't queued"));
    assertEquals(DnvDeferredShot.State.CREATED, restored.receipt().state());
  }

  @Test void exceptionAfterAdmissionCannotEraseAWorldEntity() {
    var shot = shot("weapon");
    shot.queued(true);
    shot.execute(100, () -> null, () -> {
      shot.admit(UUID.randomUUID(), MUZZLE, VELOCITY, () -> true);
      throw new IllegalStateException("native afterShoot failed");
    });
    assertEquals(DnvDeferredShot.State.FAILED, shot.receipt().state());
    assertTrue(shot.receipt().admission().orElseThrow().accepted());
  }

  @Test void admissionExceptionIsUnknownRatherThanAFalseWorldReply() {
    var shot = shot("weapon");
    shot.queued(true);
    shot.execute(100, () -> null, () -> shot.admit(UUID.randomUUID(), MUZZLE, VELOCITY,
        () -> { throw new IllegalStateException("world callback failed"); }));
    assertEquals(DnvDeferredShot.Acceptance.UNKNOWN, shot.receipt().admission().orElseThrow().acceptance());
    assertEquals(DnvDeferredShot.State.FAILED, shot.receipt().state());
  }
}
