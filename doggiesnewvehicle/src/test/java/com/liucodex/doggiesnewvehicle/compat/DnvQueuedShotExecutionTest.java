package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvDeferredShot;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DnvQueuedShotExecutionTest {
  @TempDir Path temp;

  @Test void admittedNativeTurretRoundReceivesItsOwnersDirectPenetration() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.vehicle.directFraction = .35F;
    fixture.submit(() -> null);
    fixture.run();
    var id = fixture.receipts.get(0).admission().orElseThrow().projectileId();
    assertEquals(.35F, fixture.level.entities.get(id).armorFraction);
    assertTrue(fixture.level.entities.get(id).dnvOrigin);
  }

  @Test void targetlessIntentIsRejectedBeforeTheSharedTurretQueue() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    assertThrows(NullPointerException.class, () -> fixture.submit(null, () -> null));
    assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
    assertEquals(0, fixture.receipts.size());
    assertEquals(0, fixture.level.admissions + fixture.vehicle.gun.ammoWrites);
  }

  @Test void admittedProjectileSurvivesControlRevocationWithoutFurtherAuthorityChecks() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    var checks = new AtomicInteger();
    var denied = new AtomicReference<String>();
    fixture.submit(() -> { checks.incrementAndGet(); return denied.get(); });
    fixture.run();
    var receipt = fixture.receipts.get(0);
    assertEquals(DnvDeferredShot.State.ACCEPTED, receipt.state());
    var id = receipt.admission().orElseThrow().projectileId();
    var projectile = fixture.level.entities.get(id);
    int checksAtLaunch = checks.get();
    denied.set("MANUAL_TAKEOVER");
    fixture.clear();
    fixture.level.now += 500;
    fixture.expire();
    assertSame(projectile, fixture.level.entities.get(id));
    assertFalse(projectile.removed);
    assertEquals(checksAtLaunch, checks.get());
    assertEquals(1, fixture.vehicle.gun.ammoWrites);
    assertEquals(1, fixture.receipts.size());
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void exactQueueAndAdmissionTailReportWorldBooleanRatherThanUpstreamTrue(boolean accepted) throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    var checks = new AtomicInteger();
    fixture.submit(() -> { checks.incrementAndGet(); return null; });
    assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size(), "even delay zero must queue");
    assertEquals(0, checks.get());
    assertEquals(0, fixture.vehicle.recoil);
    assertEquals(0, fixture.vehicle.sound);
    assertEquals(0, fixture.vehicle.gun.ammoWrites);
    fixture.level.now++;
    fixture.level.accept = accepted;
    fixture.vehicle.muzzle = new Vec3(8, 9, 10);
    fixture.vehicle.direction = new Vec3(0, 0, -1);
    fixture.run();
    assertEquals(2, checks.get(), "queue execution and post-event admission must each revalidate");
    assertEquals(1, fixture.level.admissions);
    assertEquals(accepted ? 1 : 0, fixture.vehicle.gun.ammoWrites);
    var receipt = fixture.receipts.get(0);
    assertEquals(accepted ? "WORLD_ACCEPTED" : "WORLD_REJECTED", receipt.reason());
    var admission = receipt.admission().orElseThrow();
    assertEquals(8, admission.position().x());
    assertEquals(9, admission.position().y());
    assertEquals(10, admission.position().z());
    assertEquals(-(double) 16.4667F, admission.velocity().z());
    assertEquals(accepted, fixture.level.entities.containsKey(admission.projectileId()));
  }

  @ParameterizedTest @ValueSource(strings = {"MISSION_CHANGED", "CORE_OR_CONTROL_CHANGED", "PROVIDER_OR_RELATION_CHANGED"})
  void lateRejectionPreventsNativeCallbackAndEffects(String reason) throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    var permission = new AtomicReference<String>();
    fixture.submit(permission::get);
    permission.set(reason);
    fixture.run();
    assertEquals(reason, fixture.receipts.get(0).reason());
    assertEquals(0, fixture.vehicle.gun.calls);
    assertEquals(0, fixture.level.admissions);
    assertEquals(0, fixture.vehicle.recoil + fixture.vehicle.sound + fixture.vehicle.gun.ammoWrites);
  }

  @Test void preShootEventRevocationAndMuzzleMovementFailBeforeWorldAdmission() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    var reason = new AtomicReference<String>();
    fixture.vehicle.gun.beforeShot = () -> reason.set("MANUAL_TAKEOVER");
    fixture.submit(reason::get);
    fixture.run();
    assertEquals("MANUAL_TAKEOVER", fixture.receipts.get(0).reason());
    assertEquals(0, fixture.level.admissions + fixture.vehicle.gun.ammoWrites);
    fixture.vehicle.gun.beforeShot = () -> fixture.vehicle.muzzle = new Vec3(19, 20, 21);
    fixture.submit(() -> null);
    fixture.run();
    assertEquals("ADMISSION_CONTEXT_CHANGED", fixture.receipts.get(1).reason());
    assertEquals(0, fixture.level.admissions + fixture.vehicle.gun.ammoWrites);
  }

  @Test void cancelledExpiredAndDuplicateCallbacksCannotReachNativeShoot() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.submit(() -> null);
    Runnable work = ExactQueuedShotFixture.Mod.Companion.work.remove(0);
    fixture.clear();
    work.run();
    work.run();
    assertEquals(1, fixture.receipts.size());
    assertEquals("LIFECYCLE_INVALIDATED", fixture.receipts.get(0).reason());
    fixture.submit(() -> null);
    fixture.level.now += 3;
    fixture.expire();
    fixture.run();
    assertEquals("EXPIRED", fixture.receipts.get(1).reason());
    assertEquals(0, fixture.vehicle.gun.calls);
  }

  @Test void entityReplacementNativeNotReadyAndQueueRejectionProduceDifferentReceipts() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.submit(() -> null);
    fixture.level.entities.remove(fixture.vehicle.id);
    fixture.run();
    assertEquals("SOURCE_CHANGED", fixture.receipts.get(0).reason());
    fixture.level.entities.put(fixture.vehicle.id, fixture.vehicle);
    fixture.vehicle.gun.ready = false;
    fixture.submit(() -> null);
    fixture.run();
    assertEquals("NO_ADMISSION", fixture.receipts.get(1).reason());
    ExactQueuedShotFixture.Mod.Companion.accept = false;
    fixture.submit(() -> null);
    assertEquals("QUEUE_REJECTED", fixture.receipts.get(2).reason());
    assertEquals(0, fixture.vehicle.gun.calls + fixture.level.admissions);
  }

  @Test void exceptionReleasesThreadScopeAndRetainsFailure() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.submit(() -> { throw new IllegalStateException("provider failed"); });
    fixture.run();
    assertEquals(DnvDeferredShot.State.FAILED, fixture.receipts.get(0).state());
    fixture.submit(() -> null);
    fixture.run();
    assertEquals(DnvDeferredShot.State.ACCEPTED, fixture.receipts.get(1).state());
  }

  @Test void unscopedNativeCallStillUsesItsOriginalEffects() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.vehicle.vehicleShoot(null, "Cannon40", fixture.target.id, fixture.target.position);
    assertEquals(1, fixture.vehicle.recoil);
    assertEquals(1, fixture.vehicle.sound);
    fixture.run();
    assertTrue(fixture.receipts.isEmpty());
    assertEquals(1, fixture.level.admissions);
  }

  @Test void exactPassengerBranchIgnoresGunRegistersAndFollowsOnlyOccupantOrVehicleView() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.vehicle.velocity = new Vec3(0, 0, 1);
    for (float angle : new float[] {-90, -20, 0, 45, 179}) {
      fixture.vehicle.gunX = angle;
      fixture.vehicle.gunY = -angle;
      assertEquals(new Vec3(0, 0, 1), fixture.vehicle.getVectorFromString("Passenger", 1, 1));
    }
    var occupant = new ExactQueuedShotFixture.Entity();
    occupant.velocity = new Vec3(1, 0, 0);
    fixture.vehicle.passenger = occupant;
    assertEquals(occupant.velocity, fixture.vehicle.getVectorFromString("Passenger", 1, 1));
    occupant.velocity = new Vec3(0, -1, 0);
    assertEquals(occupant.velocity, fixture.vehicle.getVectorFromString("Passenger", 1, 1));
    fixture.vehicle.passenger = null;
    assertEquals(fixture.vehicle.velocity, fixture.vehicle.getVectorFromString("Passenger", 1, 1));
  }

  @Test void lifecycleInvalidationInsideWorldCallbackRetainsTheReturnedAdmission() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.level.duringAdmission = () -> {
      try { fixture.clear(); } catch (Exception exception) { throw new AssertionError(exception); }
      assertTrue(fixture.receipts.isEmpty(), "completion must await the world boolean");
    };
    fixture.submit(() -> null);
    fixture.run();
    assertEquals(1, fixture.receipts.size());
    assertEquals("LIFECYCLE_INVALIDATED", fixture.receipts.get(0).reason());
    assertTrue(fixture.receipts.get(0).admission().orElseThrow().accepted());
  }

  @Test void pendingRequestIsBoundedAndHasItsOwnRejectionReceipt() throws Exception {
    var fixture = new ExactQueuedShotFixture(temp);
    fixture.submit(() -> null);
    fixture.submit(() -> null);
    assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size());
    assertEquals("SHOT_ALREADY_PENDING", fixture.receipts.get(0).reason());
    fixture.run();
    assertEquals("WORLD_ACCEPTED", fixture.receipts.get(1).reason());
    assertNotEquals(fixture.receipts.get(0).intent().operationId(), fixture.receipts.get(1).intent().operationId());
  }

  @Test void exactAiWriterTouchesRegistersWithoutWritingEntityViewAngles() throws Exception {
    try (var zip = new java.util.zip.ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      var node = new org.objectweb.asm.tree.ClassNode();
      new org.objectweb.asm.ClassReader(zip.getInputStream(zip.getEntry(ExactQueuedShotFixture.SBW
          + "entity/vehicle/utils/VehicleWeaponUtils.class"))).accept(node, 0);
      var method = node.methods.stream().filter(m -> m.name.equals("passengerWeaponAutoAimFormVector")).findFirst().orElseThrow();
      var calls = java.util.Arrays.stream(method.instructions.toArray()).filter(org.objectweb.asm.tree.MethodInsnNode.class::isInstance)
          .map(org.objectweb.asm.tree.MethodInsnNode.class::cast).toList();
      assertTrue(calls.stream().anyMatch(c -> c.owner.equals(ExactQueuedShotFixture.VEHICLE) && c.name.equals("setGunXRot")));
      assertTrue(calls.stream().anyMatch(c -> c.owner.equals(ExactQueuedShotFixture.VEHICLE) && c.name.equals("setGunYRot")));
      assertFalse(calls.stream().anyMatch(c -> c.name.equals("getNthEntity") || c.name.equals("m_146922_") || c.name.equals("m_146926_")));
    }
  }
}
