package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class DnvJingzheTurretExecutionTest {
  @Test void openDropPathAllowsAnAutonomousLockThroughTheBuildingWall() throws Exception {
    var f = new JingzheExecutionFixture();
    f.vehicle.moduleState[0] = 2;
    f.vehicle.moduleCharge[0] = 1000;
    f.world.sideClear = false;
    f.call("tickCombatSystems");
    assertEquals(3, f.vehicle.moduleState[0]);
    assertEquals(f.target.id, f.decision(0).target().orElseThrow().targetId());
    assertEquals(1, f.world.clips, "only the local vertical drop ray");
    assertEquals(1, f.world.geometryChecks);
  }

  @Test void freshObstructionBeforeReleaseRejectsDamageWithoutConsumingCharge() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    var frame = f.aim(0);
    f.world.clear = false;
    assertFalse(f.port(0).tryFire(frame).accepted());
    assertEquals(0, f.target.hits + f.vehicle.energyWrites + f.vehicle.effects);
    assertEquals(1000, f.vehicle.moduleCharge[0]);
  }

  @Test void commandDropStillWorksBehindWallAndLosingCommandInvalidatesCachedVisibility() throws Exception {
    var f = new JingzheExecutionFixture();
    f.vehicle.moduleState[0] = 2;
    f.vehicle.moduleCharge[0] = 1000;
    f.world.sideClear = false;
    JingzheExecutionFixture.Coordinator.commandTarget = f.target;
    JingzheExecutionFixture.Coordinator.commandRevision = 1;
    JingzheExecutionFixture.Coordinator.commandValidUntil = 1000;
    JingzheExecutionFixture.Coordinator.commandLive = true;
    f.tick();
    assertEquals(3, f.vehicle.moduleState[0]);
    assertEquals(1, f.world.clips);
    JingzheExecutionFixture.Coordinator.commandTarget = null;
    JingzheExecutionFixture.Coordinator.commandLive = false;
    f.tick();
    assertEquals(3, f.vehicle.moduleState[0]);
    assertEquals(DnvTurretTargetMemory.Source.AUTONOMOUS, f.decision(0).target().orElseThrow().source());
    assertEquals(0, f.target.hits);
    assertEquals(2, f.world.clips);
  }

  @Test void deferredNewLockDoesNotConsumeAndUnloadedPathDoesNotClip() throws Exception {
    var f = new JingzheExecutionFixture();
    f.vehicle.energy = 1; // Keep other empty scrolls from starting unrelated reloads.
    f.vehicle.moduleState[0] = 2;
    f.vehicle.moduleCharge[0] = 1000;
    f.vehicle.pathDeferred = true;
    f.tick();
    assertTrue(f.decision(0).target().isEmpty());
    assertEquals(0, f.world.clips);
    f.vehicle.pathDeferred = false;
    f.world.loaded = false;
    f.tick();
    assertTrue(f.decision(0).target().isEmpty());
    assertEquals(0, f.world.clips);
    assertEquals(0, f.target.hits + f.vehicle.energyWrites);
  }

  @Test void candidateViewResolvesOncePerCombatUpdateAndReleasesAfterward() throws Exception {
    var f = new JingzheExecutionFixture();
    var method = f.vehicle.getClass().getMethod("candidateView");
    assertSame(method.invoke(f.vehicle), method.invoke(f.vehicle));
    assertEquals(1, f.vehicle.candidateViewReads);

    f.call("tickCombatSystems");
    method.invoke(f.vehicle);
    assertEquals(2, f.vehicle.candidateViewReads);
  }

  @Test void geometryQueueWaitPreservesWindupButInvalidTargetStillClearsIt() throws Exception {
    var f = new JingzheExecutionFixture();
    f.ready(0);
    f.vehicle.pathDeferred = true;
    f.call("tickCombatSystems");
    assertEquals(4, f.vehicle.moduleState[0]);
    assertEquals(12, f.vehicle.moduleTimer[0]);
    assertEquals(1000, f.vehicle.moduleCharge[0]);
    assertEquals(0, f.target.hits);

    f.vehicle.pathDeferred = false;
    f.vehicle.targetAllowed = false;
    f.tick();
    assertEquals(2, f.vehicle.moduleState[0]);
    assertEquals(0, f.target.hits);
  }

  @Test void fiveModulesKeepIndependentMemoryAndShareCommandPriorityAndNativeFallback()
      throws Exception {
    var f = new JingzheExecutionFixture();
    var second = new JingzheExecutionFixture.Target();
    second.level = f.world;
    second.position = new net.minecraft.world.phys.Vec3(20, 3, 4);
    f.world.entities.put(second.id, second);
    f.vehicle.sharedCandidates = java.util.List.of(f.target, second);
    for (int module = 0; module < 2; module++) {
      f.vehicle.moduleState[module] = 2;
      f.vehicle.moduleCharge[module] = 1000;
    }

    f.call("tickCombatSystems");
    f.vehicle.lastLockStartTick = f.vehicle.tickCount - 40;
    f.tick();
    assertEquals(f.target.id, f.decision(0).target().orElseThrow().targetId());
    assertEquals(second.id, f.decision(1).target().orElseThrow().targetId());
    assertEquals(DnvTurretTargetMemory.Source.AUTONOMOUS,
        f.decision(0).target().orElseThrow().source());
    assertEquals(DnvTurretTargetMemory.Source.AUTONOMOUS,
        f.decision(1).target().orElseThrow().source());

    JingzheExecutionFixture.Coordinator.commandTarget = second;
    JingzheExecutionFixture.Coordinator.commandRevision = 9L;
    JingzheExecutionFixture.Coordinator.commandValidUntil = f.world.now + 40L;
    JingzheExecutionFixture.Coordinator.commandExplicit = true;
    JingzheExecutionFixture.Coordinator.commandLive = true;
    f.tick();
    for (int module = 0; module < 2; module++) {
      var target = f.decision(module).target().orElseThrow();
      assertEquals(second.id, target.targetId());
      assertEquals(DnvTurretTargetMemory.Source.EXPLICIT_COMMAND, target.source());
      assertEquals(DnvTurretTargetMemory.COMMAND_PRIORITY, target.priority());
    }
    assertEquals(2, f.vehicle.moduleState[0]);
    f.vehicle.lastLockStartTick = f.vehicle.tickCount - 40;
    f.tick();
    assertEquals(second.id.toString(), f.vehicle.moduleTarget[0]);

    JingzheExecutionFixture.Coordinator.commandTarget = null;
    JingzheExecutionFixture.Coordinator.commandRevision = -1L;
    JingzheExecutionFixture.Coordinator.commandValidUntil = -1L;
    JingzheExecutionFixture.Coordinator.commandExplicit = false;
    JingzheExecutionFixture.Coordinator.commandLive = false;
    f.tick();
    for (int module = 0; module < 2; module++) {
      assertEquals(DnvTurretTargetMemory.Source.AUTONOMOUS,
          f.decision(module).target().orElseThrow().source());
      assertEquals(DnvTurretTargetMemory.AUTONOMOUS_PRIORITY,
          f.decision(module).target().orElseThrow().priority());
    }
  }

  @Test void moduleMemoriesFailClosedForCommandTargetCoreLifecycleAndRestart() throws Exception {
    var f = new JingzheExecutionFixture();
    f.vehicle.moduleState[0] = 2;
    f.vehicle.moduleCharge[0] = 1000;
    f.call("tickCombatSystems");
    assertTrue(f.decision(0).target().isPresent());

    JingzheExecutionFixture.Coordinator.commandTarget = f.target;
    JingzheExecutionFixture.Coordinator.commandRevision = 4L;
    JingzheExecutionFixture.Coordinator.commandValidUntil = f.world.now + 40L;
    JingzheExecutionFixture.Coordinator.commandExplicit = true;
    JingzheExecutionFixture.Coordinator.commandLive = true;
    f.target.pvp = true;
    f.tick();
    assertTrue(f.decision(0).target().isEmpty());
    assertEquals(DnvTurretTargetMemory.DecisionReason.COMMAND_TARGET_INVALID,
        f.decision(0).reason());

    f.target.pvp = false;
    f.vehicle.lastLockStartTick = f.vehicle.tickCount - 40;
    f.tick();
    assertEquals(DnvTurretTargetMemory.Source.EXPLICIT_COMMAND,
        f.decision(0).target().orElseThrow().source());
    f.vehicle.vehicleData.config.seek.max = 1.0D;
    f.tick();
    assertTrue(f.decision(0).target().isEmpty());
    assertEquals(DnvTurretTargetMemory.InvalidationReason.CORE_INACTIVE,
        f.decision(0).invalidatedBy().orElseThrow());

    f.call("clearTransientTargets");
    assertTrue(f.decision(0).target().isEmpty());
    var restarted = new JingzheExecutionFixture();
    assertTrue(restarted.decision(0).target().isEmpty());
  }

  @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
  void eachRealScrollReleasesItsEffectOnceWithoutAnotherEnergyCharge(int module) throws Exception {
    var f = new JingzheExecutionFixture();
    f.ready(module);
    var frame = f.aim(module);
    assertEquals("scroll_" + module, frame.station());
    assertEquals("lightning", frame.weapon());
    assertEquals("RELEASED", f.port(module).tryFire(frame).reason());
    assertEquals(225, f.target.damage);
    assertEquals(1, f.target.hits);
    assertInstanceOf(JingzheExecutionFixture.DirectDamage.class, f.target.lastSource);
    assertEquals(.60F, ((JingzheExecutionFixture.DirectDamage) f.target.lastSource).fraction);
    assertEquals(1, f.vehicle.pendingSecondaryStrikes.size());
    assertEquals(1, f.vehicle.effects);
    assertEquals(5, f.vehicle.moduleState[module]);
    assertEquals(100, f.vehicle.lastReleaseTick);
    assertEquals(0, f.vehicle.energyWrites);
    assertFalse(f.port(module).tryFire(frame).accepted());
    assertFalse(f.port(module).pending());
    assertTrue(f.port(module).inspect(f.target.id).lastReceipt().isEmpty());
  }

  @Test void actualReloadLockWindupAndFadeKeepFivePrepaidChargesAndFortyTickReleaseSpacing() throws Exception {
    var f = new JingzheExecutionFixture();
    f.call("tickCombatSystems");
    assertEquals(550_000, f.vehicle.energy);
    assertEquals(5, f.vehicle.energyWrites);
    assertTrue(Arrays.stream(f.vehicle.moduleTimer).allMatch(t -> t == 1));
    // Keep newly emptied modules from reloading, but preserve operating power.
    f.vehicle.energy = 1;
    for (int tick = 1; tick < 100; tick++) f.tick();
    assertTrue(Arrays.stream(f.vehicle.moduleState).allMatch(s -> s == 2));
    f.tick();
    assertEquals(3, f.vehicle.moduleState[0]);
    for (int tick = 0; tick < 59; tick++) f.tick();
    assertEquals(3, f.vehicle.moduleState[0]);
    f.tick();
    assertEquals(4, f.vehicle.moduleState[0]);
    for (int tick = 0; tick < 11; tick++) f.tick();
    assertEquals(0, f.target.hits);
    f.tick();
    assertEquals(1, f.target.hits);
    int firstRelease = f.vehicle.lastReleaseTick;
    for (int tick = 0; tick < 19; tick++) f.tick();
    assertEquals(5, f.vehicle.moduleState[0]);
    f.tick();
    assertEquals(0, f.vehicle.moduleState[0]);
    for (int tick = 0; tick < 20; tick++) f.tick();
    assertEquals(2, f.target.hits);
    assertEquals(40, f.vehicle.lastReleaseTick - firstRelease);
    assertEquals(5, f.vehicle.energyWrites);
  }

  @ParameterizedTest @ValueSource(strings = {"manual", "power", "inactive", "deployment", "removed", "wreck",
      "target", "targetObject", "pvp", "owner", "provider", "seek", "range", "charge", "reserve", "timer",
      "moduleTarget", "cooldown", "world", "tick", "path", "unloaded", "cancel"})
  void changesAfterAimCannotReleaseOrConsumeThePrepaidModule(String change) throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    var frame = f.aim(0);
    switch (change) {
      case "manual" -> f.vehicle.passenger = new JingzheExecutionFixture.Target();
      case "power" -> f.vehicle.energy = 0;
      case "inactive" -> f.vehicle.active = false;
      case "deployment" -> f.vehicle.deployed = false;
      case "removed" -> f.world.entities.remove(f.vehicle.id);
      case "wreck" -> f.vehicle.wreck = true;
      case "target" -> f.vehicle.targetAllowed = false;
      case "targetObject" -> f.world.entities.put(f.target.id, new JingzheExecutionFixture.Target());
      case "pvp" -> f.target.pvp = true;
      case "owner" -> f.vehicle.owner = new JingzheExecutionFixture.Target();
      case "provider" -> JingzheExecutionFixture.Coordinator.stamp = new Object();
      case "seek" -> f.vehicle.vehicleData.config.seek = new JingzheExecutionFixture.Seek();
      case "range" -> f.vehicle.vehicleData.config.seek.max = 20;
      case "charge" -> f.vehicle.moduleCharge[0] = 999;
      case "reserve" -> f.vehicle.moduleEnergyReserved[0] = true;
      case "timer" -> f.vehicle.moduleTimer[0] = 11;
      case "moduleTarget" -> f.vehicle.moduleTarget[0] = "none";
      case "cooldown" -> f.vehicle.lastReleaseTick = f.vehicle.tickCount;
      case "world" -> f.vehicle.level = new JingzheExecutionFixture.World();
      case "tick" -> f.world.now++;
      case "path" -> f.world.clear = false;
      case "unloaded" -> f.world.loaded = false;
      case "cancel" -> f.call("invalidateExecution");
      default -> fail(change);
    }
    assertFalse(f.port(0).tryFire(frame).accepted(), change);
    assertEquals(0, f.target.hits + f.vehicle.effects + f.vehicle.energyWrites);
    assertTrue(f.vehicle.pendingSecondaryStrikes.isEmpty());
    assertEquals(4, f.vehicle.moduleState[0]);
  }

  @ParameterizedTest @ValueSource(strings = {"manual", "pvp", "config", "module", "deny", "relation", "moved", "allied"})
  void providerAndRelationCallbacksAreRecheckedImmediatelyBeforeRelease(String change) throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    var frame = f.aim(0);
    JingzheExecutionFixture.Coordinator.beforeAuthorization = () -> {
      switch (change) {
        case "manual" -> f.vehicle.passenger = new JingzheExecutionFixture.Target();
        case "pvp" -> f.target.pvp = true;
        case "config" -> f.vehicle.vehicleData.config.seek.max = 50;
        case "module" -> f.vehicle.moduleState[0] = 2;
        case "deny" -> JingzheExecutionFixture.Coordinator.allowed = false;
        case "relation" -> f.vehicle.duringRelation = () -> f.vehicle.passenger = new JingzheExecutionFixture.Target();
        case "moved" -> f.vehicle.duringRelation = () -> f.target.position = new net.minecraft.world.phys.Vec3(100, 0, 0);
        case "allied" -> f.vehicle.duringRelation = () -> f.target.allied = true;
      }
    };
    assertFalse(f.port(0).tryFire(frame).accepted());
    assertEquals(0, f.target.hits + f.vehicle.effects + f.vehicle.energyWrites);
  }

  @Test void damageCallbackFailureCannotReplayAnAlreadyCommittedEffect() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    f.target.duringHurt = () -> { throw new IllegalStateException("damage event failed after the hit"); };
    var frame = f.aim(0);
    assertThrows(IllegalStateException.class, () -> f.port(0).tryFire(frame));
    assertEquals(1, f.target.hits);
    assertEquals(5, f.vehicle.moduleState[0]);
    assertEquals(100, f.vehicle.lastReleaseTick);
    assertFalse(f.port(0).tryFire(frame).accepted());
    f.vehicle.energy = 1;
    f.tick();
    assertEquals(1, f.target.hits);
    assertEquals(0, f.vehicle.energyWrites);
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void invalidationInsideAnyFinalRelationCheckCannotRestoreTheOldFrame(int when) throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    var frame = f.aim(0);
    int[] calls = {0};
    f.vehicle.duringRelation = () -> {
      if (++calls[0] == when) {
        try { f.port(0).invalidate(); } catch (Exception e) { throw new AssertionError(e); }
      }
    };
    assertEquals("STALE_FRAME", f.port(0).tryFire(frame).reason());
    assertEquals(0, f.target.hits + f.vehicle.energyWrites);
  }

  @Test void profileReplacementAndClockRollbackRequireFreshFramesAndRetainRealReleaseCadence() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0); f.ready(1);
    var first = f.aim(0);
    f.vehicle.vehicleData.config.seek.max = 60;
    assertFalse(f.port(0).tryFire(first).accepted());
    var fresh = f.aim(0);
    assertEquals(first.profileRevision() + 1, fresh.profileRevision());
    assertTrue(f.port(0).tryFire(fresh).accepted());
    f.world.now--;
    var second = f.port(1).inspect(f.target.id);
    assertEquals("COOLDOWN", second.reason());
    assertTrue(f.port(1).applyAim(second, new Point(0, -1, 0)).accepted());
    assertEquals("COOLDOWN", f.port(1).tryFire(second).reason());
    f.vehicle.tickCount += 40;
    assertTrue(f.port(1).tryFire(f.aim(1)).accepted());
  }

  @Test void wrongFrameWrongModuleAndWrongDirectionNeverCreateAnAimOrBarrelMovement() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0); f.ready(1);
    var old = f.aim(0);
    var frame = f.port(0).inspect(f.target.id);
    assertFalse(f.port(0).tryFire(old).accepted());
    assertFalse(f.port(1).tryFire(frame).accepted());
    var copy = new DnvTurretExecutionPort.Frame(frame.platformId(), frame.dimension(), frame.profileId(),
        frame.profileRevision(), frame.controlRevision(), frame.tick(), frame.station(), frame.weapon(), frame.targetId(),
        frame.readiness(), frame.reason(), frame.lastReceipt());
    assertFalse(f.port(0).applyAim(copy, new Point(0, -1, 0)).accepted());
    assertEquals("INVALID_EFFECT_DIRECTION", f.port(0).applyAim(frame, new Point(1, 0, 0)).reason());
    assertEquals("NOT_ALIGNED", f.port(0).tryFire(frame).reason());
    assertEquals(0, f.target.hits);
  }

  @Test void synchronousDamageReentryAndCancellationCannotReleaseTwiceOrRestoreTheConsumedCharge() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0); f.ready(1);
    f.target.immune = true;
    f.target.duringHurt = () -> {
      try {
        f.call("invalidateExecution");
        var port = f.port(1);
        var nested = port.inspect(f.target.id);
        assertEquals("RELEASE_IN_PROGRESS", nested.reason());
        assertFalse(port.applyAim(nested, new Point(0, -1, 0)).accepted());
        assertFalse(port.tryFire(nested).accepted());
      } catch (Exception e) { throw new AssertionError(e); }
    };
    assertTrue(f.port(0).tryFire(f.aim(0)).accepted());
    assertEquals(0, f.target.damage);
    assertEquals(1, f.target.hits);
    assertEquals(1, f.vehicle.pendingSecondaryStrikes.size());
    assertEquals(5, f.vehicle.moduleState[0]);
    assertEquals(4, f.vehicle.moduleState[1]);
    f.call("clearTransientTargets");
    assertEquals(1, f.vehicle.pendingSecondaryStrikes.size());
  }

  @Test void powerAndManualHoldFreezeTheOriginalTimersAndInvalidateOnlyTransientPermission() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    var before = f.aim(0);
    f.vehicle.energy = 0;
    for (int i = 0; i < 20; i++) f.tick();
    assertEquals(12, f.vehicle.moduleTimer[0]);
    assertFalse(f.port(0).tryFire(before).accepted());
    f.vehicle.energy = 1;
    f.vehicle.passenger = new JingzheExecutionFixture.Target();
    f.tick();
    assertEquals(0, f.target.hits);
    f.vehicle.passenger = null;
    f.tick();
    assertEquals(1, f.target.hits);
  }

  @Test void actualNbtRecoveryRetainsReservedReloadButNeverRestoresTargetOrOldFrames() throws Exception {
    var f = new JingzheExecutionFixture();
    f.call("startAffordableReloads");
    f.ready(0); f.vehicle.moduleEnergyReserved[0] = false;
    var before = f.aim(0);
    var tag = f.save();
    f.load(tag);
    assertFalse(f.port(0).tryFire(before).accepted());
    assertEquals(2, f.vehicle.moduleState[0]);
    assertEquals(1000, f.vehicle.moduleCharge[0]);
    assertEquals("none", f.vehicle.moduleTarget[0]);
    assertEquals(1, f.vehicle.moduleState[1]);
    assertTrue(f.vehicle.moduleEnergyReserved[1]);
    assertEquals(550_000, f.vehicle.energy);
    tag.getCompound("JingzheModule1").putBoolean("EnergyReserved", false);
    f.load(tag);
    assertEquals(0, f.vehicle.moduleState[1]);
  }

  @Test void actualSecondaryAreaDamagePreservesNoPvpAndDoesNotTreatControlCancellationAsRecall() throws Exception {
    var f = new JingzheExecutionFixture(); f.ready(0);
    assertTrue(f.port(0).tryFire(f.aim(0)).accepted());
    var player = new JingzheExecutionFixture.Target(); player.pvp = true;
    player.level = f.world; player.position = f.target.position;
    f.world.entities.put(player.id, player);
    f.call("invalidateExecution");
    f.vehicle.active = false;
    f.impact();
    assertEquals(0, player.hits);
    assertEquals(2, f.target.hits);
  }

  @Test void effectDefinitionHasFiveStationsAndNoNativeBallisticCapability() {
    var definition = DnvJingzheTurretExecution.DEFINITION;
    assertTrue(definition.projectile().isEmpty());
    assertFalse(definition.remoteManualOptIn());
    assertEquals(5, definition.profile(1, 2, 72).capabilities().maxStations());
    assertTrue(definition.profile(1, 2, 72).capabilities().ballistics().isEmpty());
    assertFalse(definition.owns("vehicle", "Main"));
    assertThrows(java.util.NoSuchElementException.class, definition::projectileType);
  }

  @Test void nativeQueueAdapterRefusesAnEntityEffectDefinition(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
    var queue = new ExactQueuedShotFixture(temp);
    var runtime = queue.loader.loadClass((ExactQueuedShotFixture.GENERATED + "Turret").replace('/', '.'));
    var constructor = runtime.getConstructor(ExactQueuedShotFixture.Vehicle.class,
        com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition.class,
        String.class, String.class, double.class, DnvTurretExecutionPort.Hooks.class);
    var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
        () -> constructor.newInstance(queue.vehicle, DnvJingzheTurretExecution.DEFINITION, "scroll_0", "lightning", 1, null));
    assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
    assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
  }

  @Test void compiledRealModuleConsumerUsesThePortAndAllThreeNativeShootEntrypointsAreInert() throws Exception {
    var deployment = compiled("AbstractJingzheTurretDeployment");
    var tick = deployment.methods.stream().filter(m -> m.name.equals("tickModule")).findFirst().orElseThrow();
    var calls = Arrays.stream(tick.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
    for (String operation : Set.of("inspect", "applyAim", "tryFire")) {
      assertTrue(calls.stream().anyMatch(c -> c.owner.endsWith("DnvTurretExecutionPort") && c.name.equals(operation)));
    }
    assertFalse(calls.stream().anyMatch(c -> c.name.equals("releaseAttack")));
    var contract = compiled("AbstractJingzheTurretContract");
    var methods = contract.methods.stream().filter(m -> m.name.equals("vehicleShoot")).toList();
    assertEquals(3, methods.size());
    methods.forEach(m -> assertFalse(Arrays.stream(m.instructions.toArray()).anyMatch(MethodInsnNode.class::isInstance)));
  }

  private static ClassNode compiled(String name) throws Exception {
    var node = new ClassNode();
    try (var in = DnvJingzheTurretExecutionTest.class.getResourceAsStream("/com/liucodex/doggiesnewvehicle/entity/" + name + ".class")) {
      new ClassReader(in).accept(node, 0);
    }
    return node;
  }
}
