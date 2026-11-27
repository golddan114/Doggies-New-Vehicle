package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities.BallisticCapability;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Real compiled turret adapter + exact transformed SBW queue; world/config/relationship are fixtures. */
class DnvTurretExecutionTest {
  @TempDir Path temp;

  @Test void acceptedShotChargesOnceOnlyAfterWorldAdmissionAndRetainsNativeProjectile() throws Exception {
    var f = new Fixture(temp);
    var frame = f.aim();
    assertEquals(1, frame.profileRevision());
    assertTrue(frame.readiness().ammoAvailable());
    assertEquals("QUEUED", f.port.tryFire(frame).reason());
    assertEquals(60000, f.queue.vehicle.energy);
    assertEquals(0, f.queue.vehicle.recoil + f.accepted);
    Runnable callback = ExactQueuedShotFixture.Mod.Companion.work.remove(0);
    callback.run();
    callback.run();
    assertEquals(40000, f.queue.vehicle.energy);
    assertEquals(1, f.queue.vehicle.energyWrites);
    assertEquals(1, f.queue.vehicle.recoil);
    assertEquals(1, f.queue.vehicle.sound);
    assertEquals(1, f.accepted);
    var receipt = f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow();
    assertEquals("WORLD_ACCEPTED", receipt.reason());
    assertEquals("vehicle", receipt.intent().station());
    assertEquals("Main", receipt.intent().weapon());
    var projectile = f.queue.level.entities.get(receipt.admission().orElseThrow().projectileId());
    f.port.invalidate();
    f.queue.level.now += 1000;
    f.active = false;
    f.port.inspect(f.queue.target.id);
    assertSame(projectile, f.queue.level.entities.get(projectile.id));
    assertEquals(1, f.queue.vehicle.energyWrites);
  }

  @ParameterizedTest @ValueSource(strings = {"world", "queue", "native"})
  void nativeOrWorldRejectionHasReceiptWithoutFeOrEffects(String kind) throws Exception {
    var f = new Fixture(temp);
    if (kind.equals("queue")) ExactQueuedShotFixture.Mod.Companion.accept = false;
    f.port.tryFire(f.aim());
    if (kind.equals("world")) f.queue.level.accept = false;
    if (kind.equals("native")) f.queue.vehicle.gun.ready = false;
    if (!kind.equals("queue")) f.queue.run();
    String expected = switch (kind) {
      case "world" -> "WORLD_REJECTED";
      case "native" -> "WEAPON_NOT_READY";
      default -> "QUEUE_REJECTED";
    };
    assertEquals(expected, f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow().reason());
    assertEquals(60000, f.queue.vehicle.energy);
    assertEquals(0, f.queue.vehicle.energyWrites + f.queue.vehicle.recoil + f.queue.vehicle.sound + f.accepted);
  }

  @ParameterizedTest @ValueSource(strings = {"manual", "inactive", "owner", "target", "pvp", "provider",
      "energy", "los", "aim", "gun", "seek", "removed", "expired", "cancel"})
  void changedLiveConditionsCancelThePendingNativeShot(String change) throws Exception {
    var f = new Fixture(temp);
    f.port.tryFire(f.aim());
    switch (change) {
      case "manual" -> f.queue.vehicle.passenger = new ExactQueuedShotFixture.Entity();
      case "inactive" -> f.active = false;
      case "owner" -> f.queue.vehicle.owner = new ExactQueuedShotFixture.Entity();
      case "target" -> f.targetAllowed = false;
      case "pvp" -> f.queue.target.pvp = true;
      case "provider" -> ExactQueuedShotFixture.Coordinator.stamp = new Object();
      case "energy" -> f.queue.vehicle.energy = 19999;
      case "los" -> f.queue.vehicle.lineClear = false;
      case "aim" -> f.queue.vehicle.direction = new Vec3(0, 1, 0);
      case "gun" -> f.queue.vehicle.gun = new ExactQueuedShotFixture.Data();
      case "seek" -> f.queue.vehicle.vehicleData.config.seek = new ExactQueuedShotFixture.Seek();
      case "removed" -> f.queue.level.entities.remove(f.queue.vehicle.id);
      case "expired" -> f.queue.level.now += 3;
      case "cancel" -> f.port.invalidate();
      default -> fail(change);
    }
    f.queue.run();
    assertEquals(0, f.queue.level.admissions, change);
    assertEquals(0, f.queue.vehicle.energyWrites + f.queue.vehicle.recoil + f.accepted, change);
    assertTrue(f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow().admission().isEmpty());
  }

  @Test void onlyTheCurrentUnmodifiedFrameMayAimOrQueueAndOnlyOnce() throws Exception {
    var f = new Fixture(temp);
    var old = f.aim();
    var current = f.aim();
    assertEquals("STALE_FRAME", f.port.tryFire(old).reason());
    var forged = new DnvTurretExecutionPort.Frame(current.platformId(), current.dimension(), current.profileId(),
        current.profileRevision(), current.controlRevision(), current.tick(), "other", "Main", current.targetId(),
        current.readiness(), current.reason(), current.lastReceipt());
    assertEquals("STALE_FRAME", f.port.tryFire(forged).reason());
    assertEquals("STALE_FRAME", f.port.applyAim(forged, new Point(1, 0, 0)).reason());
    assertTrue(f.port.tryFire(current).accepted());
    assertFalse(f.port.tryFire(current).accepted());
    assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size());
    assertEquals("SHOT_ALREADY_PENDING", f.port.tryFire(f.aim()).reason());
    f.port.invalidate();
    f.queue.run();
    var stale = f.aim();
    f.queue.level.now++;
    assertEquals("STALE_FRAME", f.port.tryFire(stale).reason());
    assertEquals(0, f.queue.level.admissions);
  }

  @Test void cadenceSettlementReadinessAndReloadRecoverWithoutRestoringOldPermission() throws Exception {
    var f = new Fixture(temp);
    var frame = f.aim();
    f.settled = false;
    assertEquals("NOT_SETTLED", f.port.tryFire(frame).reason());
    f.settled = true;
    f.queue.vehicle.gun.overHeat.value = true;
    assertEquals("WEAPON_NOT_READY", f.port.tryFire(frame).reason());
    f.queue.vehicle.gun.overHeat.value = false;
    assertTrue(f.port.tryFire(frame).accepted());
    f.queue.run();
    assertEquals("COOLDOWN", f.port.tryFire(f.aim()).reason());
    f.queue.level.now += 119;
    assertEquals("COOLDOWN", f.port.tryFire(f.aim()).reason());
    f.queue.level.now++;
    var beforeReload = f.aim();
    f.queue.vehicle.vehicleData.config.seek = new ExactQueuedShotFixture.Seek();
    assertEquals("PROFILE_CHANGED", f.port.tryFire(beforeReload).reason());
    var reloaded = f.aim();
    assertEquals(2, reloaded.profileRevision());
    assertTrue(f.port.tryFire(reloaded).accepted());
    f.queue.run();
    assertEquals(2, f.queue.vehicle.energyWrites);
  }

  @Test void manualTakeoverInsideNativeEventAndProviderCallbackCannotLeakAProjectile() throws Exception {
    var f = new Fixture(temp);
    f.port.tryFire(f.aim());
    f.queue.vehicle.gun.beforeShot = () -> f.queue.vehicle.passenger = new ExactQueuedShotFixture.Entity();
    f.queue.run();
    assertEquals(0, f.queue.level.admissions + f.queue.vehicle.energyWrites);
    f.queue.vehicle.passenger = null;
    f.queue.vehicle.gun.beforeShot = () -> {};
    var frame = f.aim();
    ExactQueuedShotFixture.Coordinator.beforeAuthorization =
        () -> f.queue.vehicle.passenger = new ExactQueuedShotFixture.Entity();
    assertFalse(f.port.tryFire(frame).accepted());
    assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
  }

  @Test void admissionCallbackInvalidationStillChargesTheAlreadyAcceptedProjectileExactlyOnce() throws Exception {
    var f = new Fixture(temp);
    f.port.tryFire(f.aim());
    f.queue.level.duringAdmission = f.port::invalidate;
    f.queue.run();
    assertEquals(1, f.queue.level.admissions);
    assertEquals(1, f.queue.vehicle.energyWrites);
    assertEquals(40000, f.queue.vehicle.energy);
    assertTrue(f.port.inspect(f.queue.target.id).lastReceipt().orElseThrow().admission().orElseThrow().accepted());
  }

  @Test void clockRollbackAndWorldReplacementInvalidateFramesWithoutBypassingCooldown() throws Exception {
    var f = new Fixture(temp);
    f.port.tryFire(f.aim());
    f.queue.run();
    f.queue.level.now--;
    assertEquals("COOLDOWN", f.port.inspect(f.queue.target.id).reason());
    assertEquals("COOLDOWN", f.port.tryFire(f.aim()).reason());
    f.queue.level.now += 120;
    var old = f.aim();
    f.queue.vehicle.level = new ExactQueuedShotFixture.Level();
    assertEquals("STALE_FRAME", f.port.tryFire(old).reason());
    assertEquals(1, f.queue.vehicle.energyWrites);
  }

  @Test void invalidDirectionAndLateProviderDenialLeaveResourcesUntouched() throws Exception {
    var f = new Fixture(temp);
    var frame = f.port.inspect(f.queue.target.id);
    assertEquals("INVALID_DIRECTION", f.port.applyAim(frame, new Point(0, 0, 0)).reason());
    assertEquals(0, f.queue.vehicle.aimWrites);
    frame = f.aim();
    ExactQueuedShotFixture.Coordinator.allowed = false;
    assertEquals("EXTERNAL_AUTHORIZATION_DENIED", f.port.tryFire(frame).reason());
    assertEquals(0, f.queue.vehicle.energyWrites);
    assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
    f.queue.vehicle.energy = 19999;
    assertEquals("OUT_OF_AMMO", f.port.inspect(f.queue.target.id).reason());
  }

  @Test void definitionMappingAndIntrinsicProfileExposeOnlyTheConnectedStation() {
    var definition = definition();
    assertFalse(definition.remoteManualOptIn());
    assertTrue(definition.owns("vehicle", "Main"));
    assertFalse(definition.owns("seat_1", "Main"));
    assertFalse(definition.owns("vehicle", "Cannon105"));
    assertEquals(1, definition.profile(1, 14, 96).capabilities().maxStations());
    assertEquals(1, definition.profile(1, 14, 96).capabilities().maxWeaponsPerStation());
    var registry = new com.liucodex.doggiesnewvehicle.core.DnvIntrinsicCoreRegistry();
    assertTrue(registry.publish(definition.profile(1, 14, 96)).accepted());
    var core = registry.install(definition.profileId()).orElseThrow();
    assertFalse(registry.publish(definition.profile(1, 14, 96)).accepted());
    assertTrue(registry.publish(definition.profile(2, 14, 96)).accepted());
    assertFalse(core.active(registry));
    assertThrows(IllegalArgumentException.class,
        () -> new DnvTurretDefinition("invalid", "dnv:x", Map.of("vehicle", Set.of("Main")), false,
            "superbwarfare:cannon_shell", BallisticCapability.ARC));
  }

  @ParameterizedTest @ValueSource(strings = {"ChangfengCannonEntity", "StormTurretEntity"})
  void compiledConsumersUseThePortAndAllUnleasedShootOverloadsStayInert(String entity) throws Exception {
    var node = new ClassNode();
    try (var input = getClass().getResourceAsStream(
        "/com/liucodex/doggiesnewvehicle/entity/" + entity + ".class")) {
      new ClassReader(input).accept(node, 0);
    }
    var autoAim = node.methods.stream().filter(m -> m.name.equals("autoAim")).findFirst().orElseThrow();
    var methods = java.util.Arrays.stream(autoAim.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
        .map(MethodInsnNode.class::cast).toList();
    for (String operation : Set.of("inspect", "applyAim", "tryFire")) {
      assertTrue(methods.stream().anyMatch(m -> m.owner.endsWith("/DnvTurretExecutionPort") && m.name.equals(operation)));
    }
    var resolver = node.methods.stream()
        .filter(m -> m.name.equals("resolveOrAcquireTarget"))
        .findFirst()
        .orElseThrow();
    var targetMethods = java.util.Arrays.stream(resolver.instructions.toArray())
        .filter(MethodInsnNode.class::isInstance)
        .map(MethodInsnNode.class::cast)
        .toList();
    assertTrue(targetMethods.stream().anyMatch(m ->
        m.owner.endsWith("/DnvSbwTurretExecution") && m.name.equals("targetCommand")));
    assertTrue(targetMethods.stream().anyMatch(m ->
        m.owner.endsWith("/DnvTurretTargetMemory") && m.name.equals("decide")));
    assertTrue(targetMethods.stream().anyMatch(m ->
        m.owner.endsWith("/DnvTurretTargetMemory$Candidate") && m.name.equals("command")));
    assertTrue(targetMethods.stream().anyMatch(m ->
        m.owner.endsWith("/DnvTurretTargetMemory$Candidate")
            && m.name.equals("autonomous")));
    assertEquals(3, node.methods.stream().filter(m -> m.name.equals("vehicleShoot")).count());
    node.methods.stream().filter(m -> m.name.equals("vehicleShoot")).forEach(m ->
        assertFalse(java.util.Arrays.stream(m.instructions.toArray()).anyMatch(MethodInsnNode.class::isInstance)));
  }

  private static DnvTurretDefinition definition() {
    return new DnvTurretDefinition("doggievehicle:50_changfeng_cannon", "doggiesnewvehicle:changfeng",
        Map.of("vehicle", Set.of("Main")), false, "superbwarfare:cannon_shell", BallisticCapability.ARC);
  }

  static final class Fixture {
    final ExactQueuedShotFixture queue;
    final DnvTurretExecutionPort port;
    boolean active = true, settled = true, targetAllowed = true;
    int accepted;
    Fixture(Path temp) throws Exception {
      this(temp, false);
    }
    Fixture(Path temp, boolean storm) throws Exception {
      queue = new ExactQueuedShotFixture(temp);
      var definition = storm ? new DnvTurretDefinition("doggievehicle:storm_turret", "doggiesnewvehicle:storm",
          Map.of("vehicle", Set.of("Main")), false, "doggievehicle:storm_bullet", BallisticCapability.DIRECT) : definition();
      queue.vehicle.type = definition.entityType();
      if (storm) {
        queue.vehicle.rpm = 480;
        queue.vehicle.gun.cost = 2500;
        queue.vehicle.gun.gravity = 0;
        queue.vehicle.gun.velocity = 36;
        queue.vehicle.gun.spread = 0.25;
        queue.vehicle.gun.projectileType = queue.vehicle.gun.configuredProjectile = definition.projectileType();
        queue.vehicle.twinMuzzles = true;
        queue.vehicle.gun.shootPos.muzzles = 2;
      }
      queue.vehicle.owner = new ExactQueuedShotFixture.Entity();
      queue.vehicle.owner.level = queue.level;
      String name = (ExactQueuedShotFixture.GENERATED + "Turret").replace('/', '.');
      Class<?> runtime = queue.loader.loadClass(name);
      Class<?> hooks = DnvTurretExecutionPort.Hooks.class;
      Object callbacks = Proxy.newProxyInstance(queue.loader, new Class<?>[] {hooks}, (proxy, method, args) -> {
        return switch (method.getName()) {
          case "active" -> active;
          case "settled" -> settled;
          case "targetAllowed" -> targetAllowed;
          case "submitNative" -> {
            try { queue.vehicle.vehicleShoot((ExactQueuedShotFixture.Entity) args[0], "Main", (UUID) args[1], (Vec3) args[2]); }
            catch (LinkageError error) { throw new AssertionError("Native queue fixture linkage", error); }
            yield null;
          }
          case "accepted" -> { accepted++; yield null; }
          default -> throw new AssertionError(method.getName());
        };
      });
      port = (DnvTurretExecutionPort) runtime.getConstructor(ExactQueuedShotFixture.Vehicle.class,
          DnvTurretDefinition.class, String.class, String.class, double.class, hooks)
          .newInstance(queue.vehicle, definition, "vehicle", "Main", storm ? 0.85 : 0.2, callbacks);
    }
    DnvTurretExecutionPort.Frame aim() {
      var frame = port.inspect(queue.target.id);
      var result = port.applyAim(frame, new Point(1, 0, 0));
      assertTrue(result.accepted(), result.reason());
      return frame;
    }
  }
}
