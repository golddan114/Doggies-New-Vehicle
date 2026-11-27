package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities.BallisticCapability;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Production port, exact native queue/admission/index block; world, gun config and geometry are fixtures. */
class DnvStormTurretExecutionTest {
  @TempDir Path temp;

  @Test void nativeManualAdmissionMarksOnlyTheHostsActualMainGun() throws Exception {
    var f = new ExactQueuedShotFixture(temp);
    f.vehicle.percentFraction = .125F;
    var admit = f.adapter.getClass().getMethod("admit", ExactQueuedShotFixture.Level.class,
        ExactQueuedShotFixture.Entity.class, ExactQueuedShotFixture.Parameters.class);
    var projectile = new ExactQueuedShotFixture.Entity(); projectile.level = f.level;
    var parameters = new ExactQueuedShotFixture.Parameters(f.vehicle, null, f.level, f.vehicle.muzzle,
        f.vehicle.direction, f.vehicle.gun, 0, true, null, Vec3.ZERO);
    assertEquals(true, admit.invoke(null, f.level, projectile, parameters));
    assertEquals(.125F, projectile.percentFraction);
    var unrelated = new ExactQueuedShotFixture.Entity(); unrelated.level = f.level;
    var otherGun = new ExactQueuedShotFixture.Parameters(f.vehicle, null, f.level, f.vehicle.muzzle,
        f.vehicle.direction, new ExactQueuedShotFixture.Data(), 0, true, null, Vec3.ZERO);
    assertEquals(true, admit.invoke(null, f.level, unrelated, otherGun));
    assertEquals(0, unrelated.percentFraction);
  }

  @ParameterizedTest @ValueSource(ints = {0, 1})
  void sustainedFireKeepsFractionalCadenceAlternatingMuzzlesAndOneFeCharge(int queueDelay) throws Exception {
    var f = new DnvTurretExecutionTest.Fixture(temp, true);
    f.queue.vehicle.percentFraction = .001F;
    List<Long> fired = new ArrayList<>();
    List<Double> muzzles = new ArrayList<>();
    long due = -1;
    for (long tick = 100; tick < 123; tick++) {
      f.queue.level.now = tick;
      if (due == tick) {
        f.queue.run();
        record(f, fired, muzzles);
        due = -1;
      }
      if (f.port.pending()) continue;
      var frame = f.aim();
      if (!frame.readiness().cooldownReady()) continue;
      assertEquals("QUEUED", f.port.tryFire(frame).reason());
      assertEquals(fired.size(), f.queue.vehicle.energyWrites);
      if (queueDelay == 0) {
        f.queue.run();
        record(f, fired, muzzles);
      } else due = tick + 1;
    }
    assertTrue(fired.size() >= 9, fired.toString());
    for (int i = 1; i < fired.size(); i++) {
      long gap = fired.get(i) - fired.get(i - 1);
      assertEquals(i % 2 == 1 ? 3 : 2, gap, fired.toString());
    }
    for (int i = 0; i < muzzles.size(); i++) {
      assertEquals(4 + (i % 2 == 0 ? 0.306176 : -0.306176), muzzles.get(i), 1.0E-12);
    }
    assertEquals(fired.size(), f.accepted);
    assertEquals(60000 - fired.size() * 2500, f.queue.vehicle.energy);
    assertEquals(fired.size(), f.queue.vehicle.energyWrites);
    assertEquals(fired.size(), f.queue.vehicle.gun.ammoWrites);
    assertEquals(fired.size(), f.queue.vehicle.sound);
    assertEquals(fired.size() % 2, f.queue.vehicle.gun.fireIndex.get());
  }

  private static void record(DnvTurretExecutionTest.Fixture f, List<Long> fired, List<Double> muzzles) {
    var receipt = f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow();
    assertEquals("WORLD_ACCEPTED", receipt.reason());
    var admission = receipt.admission().orElseThrow();
    assertEquals("doggievehicle:storm_bullet", f.queue.level.entities.get(admission.projectileId()).type);
    assertEquals(.001F, f.queue.level.entities.get(admission.projectileId()).percentFraction);
    fired.add(receipt.evaluatedTick());
    muzzles.add(admission.position().z());
  }

  @ParameterizedTest @ValueSource(strings = {"world", "queue", "manual", "inactive", "owner", "pvp", "target",
      "provider", "energy", "los", "aim", "gun", "seek", "removed", "expired", "cancel", "rpm", "index",
      "configuredProjectile", "actualProjectile", "projectileAmount", "nativeEventManual", "gravity", "velocity", "spread", "cost"})
  void rejectionNeverChargesFeOrAdvancesTheNativeMuzzleOrVisualSequence(String change) throws Exception {
    var f = new DnvTurretExecutionTest.Fixture(temp, true);
    if (change.equals("queue")) ExactQueuedShotFixture.Mod.Companion.accept = false;
    f.port.tryFire(f.aim());
    switch (change) {
      case "world" -> f.queue.level.accept = false;
      case "queue" -> { }
      case "manual" -> f.queue.vehicle.passenger = new ExactQueuedShotFixture.Entity();
      case "inactive" -> f.active = false;
      case "owner" -> f.queue.vehicle.owner = new ExactQueuedShotFixture.Entity();
      case "pvp" -> f.queue.target.pvp = true;
      case "target" -> f.targetAllowed = false;
      case "provider" -> ExactQueuedShotFixture.Coordinator.stamp = new Object();
      case "energy" -> f.queue.vehicle.energy = 499;
      case "los" -> f.queue.vehicle.lineClear = false;
      case "aim" -> f.queue.vehicle.direction = new Vec3(0, 1, 0);
      case "gun" -> f.queue.vehicle.gun = new ExactQueuedShotFixture.Data();
      case "seek" -> f.queue.vehicle.vehicleData.config.seek = new ExactQueuedShotFixture.Seek();
      case "removed" -> f.queue.level.entities.remove(f.queue.vehicle.id);
      case "expired" -> f.queue.level.now += 3;
      case "cancel" -> f.port.invalidate();
      case "rpm" -> f.queue.vehicle.rpm = 600;
      case "index" -> f.queue.vehicle.gun.fireIndex.set(1);
      case "configuredProjectile" -> f.queue.vehicle.gun.configuredProjectile = "superbwarfare:cannon_shell";
      case "actualProjectile" -> f.queue.vehicle.gun.projectileType = "superbwarfare:cannon_shell";
      case "projectileAmount" -> f.queue.vehicle.gun.projectileAmount = 2;
      case "gravity" -> f.queue.vehicle.gun.gravity = 0.05;
      case "velocity" -> f.queue.vehicle.gun.velocity = 12;
      case "spread" -> f.queue.vehicle.gun.spread = 1;
      case "cost" -> f.queue.vehicle.gun.cost = 1;
      case "nativeEventManual" -> f.queue.vehicle.gun.beforeShot =
          () -> f.queue.vehicle.passenger = new ExactQueuedShotFixture.Entity();
      default -> fail(change);
    }
    if (!change.equals("queue")) f.queue.run();
    var receipt = f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow();
    assertFalse(receipt.admission().map(a -> a.accepted()).orElse(false));
    assertEquals(0, f.queue.vehicle.energyWrites + f.queue.vehicle.sound + f.accepted + f.queue.vehicle.gun.ammoWrites);
    assertEquals(change.equals("index") ? 1 : 0, f.queue.vehicle.gun.fireIndex.get());
    assertFalse(f.port.pending());
  }

  @Test void cannonConsumerCannotAdmitStormBulletAndDoesNotInheritItsDirectCapability() throws Exception {
    var f = new DnvTurretExecutionTest.Fixture(temp);
    f.port.tryFire(f.aim());
    f.queue.vehicle.gun.projectileType = "doggievehicle:storm_bullet";
    f.queue.run();
    assertEquals("ADMISSION_CONTEXT_CHANGED", f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow().reason());
    assertEquals(0, f.queue.vehicle.energyWrites + f.queue.level.admissions);
    var definition = new DnvTurretDefinition("doggievehicle:storm_turret", "doggiesnewvehicle:storm",
        Map.of("vehicle", Set.of("Main")), false, "doggievehicle:storm_bullet", BallisticCapability.DIRECT);
    assertEquals(Set.of(BallisticCapability.DIRECT), definition.profile(1, 2, 72).capabilities().ballistics());
    assertFalse(definition.remoteManualOptIn());
  }

  @Test void pauseAndClockRollbackKeepAcceptedCooldownAndResumeWithoutBurstCatchup() throws Exception {
    var f = new DnvTurretExecutionTest.Fixture(temp, true);
    f.port.tryFire(f.aim());
    f.queue.run();
    f.port.invalidate();
    f.queue.level.now++;
    assertEquals("COOLDOWN", f.port.tryFire(f.aim()).reason());
    f.queue.level.now = 200;
    assertTrue(f.port.tryFire(f.aim()).accepted());
    f.queue.run();
    f.queue.level.now = 201;
    assertEquals("COOLDOWN", f.port.tryFire(f.aim()).reason());
    f.queue.level.now = 190;
    assertEquals("COOLDOWN", f.port.tryFire(f.aim()).reason());
    assertEquals(2, f.queue.vehicle.energyWrites);
  }

  @Test void acceptedShotSurvivesInvalidationAndRepeatedCallbackWithoutDoubleCharge() throws Exception {
    var f = new DnvTurretExecutionTest.Fixture(temp, true);
    f.port.tryFire(f.aim());
    f.queue.level.duringAdmission = f.port::invalidate;
    var work = ExactQueuedShotFixture.Mod.Companion.work.remove(0);
    work.run();
    work.run();
    assertEquals(57500, f.queue.vehicle.energy);
    assertEquals(1, f.accepted);
    assertEquals(1, f.queue.vehicle.gun.fireIndex.get());
    assertEquals(1, f.queue.level.admissions);
  }

  @Test void existingStormResourceRetainsItsDistinctWeaponAndGameplayValues() throws Exception {
    var vehicle = JsonParser.parseString(Files.readString(Path.of(
        "src/artillery/resources/data/doggievehicle/sbw/vehicles/storm_turret.json"))).getAsJsonObject();
    var seek = vehicle.getAsJsonObject("SeekInfo");
    assertEquals(150, seek.get("SeekEnergyCost").getAsInt());
    assertEquals(2, seek.get("MinSeekRange").getAsInt());
    assertEquals(72, seek.get("MaxSeekRange").getAsInt());
    var gun = vehicle.getAsJsonObject("Weapons").getAsJsonObject("Main");
    assertEquals("doggievehicle:storm_bullet", gun.get("Projectile").getAsString());
    assertEquals(2500, gun.get("AmmoCostPerShoot").getAsInt());
    assertEquals(480, gun.get("RPM").getAsInt());
    assertEquals(0, gun.get("Gravity").getAsDouble());
    assertEquals(36, gun.get("Velocity").getAsDouble());
    assertEquals(2, gun.getAsJsonObject("ShootPos").getAsJsonArray("Positions").size());
  }

  @Test void mutableGunConfigurationCannotReuseTheInspectedAimOrChangeInsideTheNativeEvent() throws Exception {
    var f = new DnvTurretExecutionTest.Fixture(temp, true);
    var frame = f.aim();
    f.queue.vehicle.gun.velocity = 12;
    assertEquals("CONFIG_CHANGED", f.port.tryFire(frame).reason());
    f.port.tryFire(f.aim());
    f.queue.vehicle.gun.beforeShot = () -> f.queue.vehicle.gun.spread = 0.9;
    f.queue.run();
    assertEquals(0, f.queue.level.admissions + f.queue.vehicle.energyWrites);
  }
}
