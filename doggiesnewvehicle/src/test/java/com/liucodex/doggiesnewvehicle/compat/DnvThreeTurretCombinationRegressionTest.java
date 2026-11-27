package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyRegistry;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Combined regression for the three intrinsic turret owners; no Minecraft server is started. */
class DnvThreeTurretCombinationRegressionTest {
  private static final Path PROJECT = Path.of("").toAbsolutePath();
  private static final DnvTurretTargetMemory.Position COMMAND_POSITION =
      new DnvTurretTargetMemory.Position(12.0D, 8.0D, 4.0D);
  private static final DnvTurretTargetMemory.Position AUTO_POSITION =
      new DnvTurretTargetMemory.Position(6.0D, 3.0D, 2.0D);

  @TempDir Path temp;

  @Test
  void interleavedLifecycleFailuresRemainScopedToOneTurretMemory() {
    var changfeng = tower(1);
    var storm = tower(2);
    var jingzhe = tower(3);

    assertCommand(changfeng.command(100, 160));
    assertCommand(storm.command(100, 100));
    assertCommand(jingzhe.command(100, 160));

    var cancelled = changfeng.autonomous(101, 1);
    assertAutonomous(cancelled, DnvTurretTargetMemory.InvalidationReason.COMMAND_ENDED);
    var expired = storm.autonomous(101, 1);
    assertAutonomous(expired, DnvTurretTargetMemory.InvalidationReason.TTL_EXPIRED);
    var missing = jingzhe.invalidCommand(101, 1);
    assertTrue(missing.target().isEmpty());
    assertEquals(DnvTurretTargetMemory.DecisionReason.COMMAND_TARGET_INVALID, missing.reason());

    assertCommand(changfeng.command(102, 160));
    assertCommand(storm.command(102, 160));
    assertCommand(jingzhe.command(102, 160));

    var reconfigured = changfeng.autonomous(103, 2);
    assertAutonomous(reconfigured,
        DnvTurretTargetMemory.InvalidationReason.CONFIGURATION_CHANGED);
    assertCommand(storm.command(103, 160));
    assertCommand(jingzhe.command(103, 160));

    var stormCoreLost = storm.input(104, 1, false,
        DnvTurretTargetMemory.CommandMode.NONE, Optional.empty(), Optional.empty());
    assertTrue(stormCoreLost.target().isEmpty());
    assertEquals(DnvTurretTargetMemory.DecisionReason.CORE_INACTIVE, stormCoreLost.reason());
    assertAutonomous(changfeng.autonomous(104, 2), Optional.empty());
    assertCommand(jingzhe.command(104, 160));

    var jingzheRollback = jingzhe.command(103, 160);
    assertTrue(jingzheRollback.target().isEmpty());
    assertEquals(DnvTurretTargetMemory.DecisionReason.CLOCK_ROLLBACK,
        jingzheRollback.reason());
    assertAutonomous(changfeng.autonomous(105, 2), Optional.empty());

    changfeng.memory.invalidate(106,
        DnvTurretTargetMemory.InvalidationReason.ENTITY_INVALIDATED);
    assertTrue(changfeng.memory.current().target().isEmpty());
    assertTrue(new DnvTurretTargetMemory().current().target().isEmpty());
    assertTrue(new DnvTurretTargetMemory().current().target().isEmpty());
    assertTrue(new DnvTurretTargetMemory().current().target().isEmpty());
  }

  @Test
  void providerRegistryCleanupAndEntityTargetingRemainBoundedAndScoped()
      throws IOException {
    DnvCombatPolicyRegistry registry = new DnvCombatPolicyRegistry(4);
    registry.register(provider("turret.storm"));
    registry.register(provider("turret.changfeng"));
    registry.register(provider("turret.jingzhe"));
    assertEquals(List.of("turret.changfeng", "turret.jingzhe", "turret.storm"),
        registry.snapshot().providers().stream()
            .map(DnvCombatPolicyRegistry.ProviderSnapshot::providerId).toList());
    assertTrue(registry.unregister("turret.storm"));
    assertEquals(List.of("turret.changfeng", "turret.jingzhe"),
        registry.snapshot().providers().stream()
            .map(DnvCombatPolicyRegistry.ProviderSnapshot::providerId).toList());

    String coordinator = read(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator.java");
    assertTrue(coordinator.contains("private static final int MAX_ACTIVE_LEASES = 1_024"));
    assertTrue(coordinator.contains("private static final Map<UUID, ActiveLease> ACTIVE_LEASES"));
    assertTrue(coordinator.contains("previous.provider() == provider"));
    assertTrue(coordinator.contains(
        "remember(subordinate.getUUID(), previous.inactive(true))"));
    assertTrue(coordinator.contains(
        "ACTIVE_LEASES.remove(subordinate.getUUID(), lease)"));
    assertTrue(coordinator.contains("if (previous != null && !providers.contains(previous.provider()))"));
    assertTrue(coordinator.contains("public static void clearRuntimeState()"));

    String main = read(
        "src/main/java/com/liucodex/doggiesnewvehicle/DoggiesNewVehicle.java");
    assertTrue(main.contains("ServerStoppingEvent"));
    assertTrue(main.contains("DnvCombatCoordinator.clearRuntimeState()"));

    String cache = read(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache.java");
    assertTrue(cache.contains("MAX_CANDIDATES = 32"));
    assertTrue(cache.contains("DnvTargetingBudget.isScanDue("));
    assertTrue(cache.contains("DnvTargetCandidateCache::compareStableIds"));
    for (String relative : List.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java",
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java",
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/AbstractJingzheTurretDeployment.java")) {
      String source = read(relative);
      assertTrue(source.contains("targetCandidates.refreshIfDue("), relative);
      assertTrue(source.contains("DnvTurretTargetMemory"), relative);
      assertTrue(source.contains(".invalidate(now, reason)"), relative);
      assertFalse(source.contains("getAllEntities()"), relative);
    }
  }

  @Test
  void friendlyAndNoPvpBoundariesApplyToEveryTurretIdentity() {
    for (int tower = 1; tower <= 3; tower++) {
      UUID source = id(tower);
      for (DnvCombatPolicy.TargetKind kind : List.of(
          DnvCombatPolicy.TargetKind.PLAYER,
          DnvCombatPolicy.TargetKind.PLAYER_OCCUPIED_VEHICLE)) {
        var decision = DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
            source, id(tower + 10), DnvCombatPolicy.Scope.EXTERNAL_ASSIGNMENT,
            kind, true, true));
        assertEquals(DnvCombatPolicy.Outcome.DENY_NO_PVP, decision.outcome());
      }
    }
    assertFalse(DnvCombatPolicyRuntime.executionRelationsAllowed(true, true, true));
    assertFalse(DnvCombatPolicyRuntime.executionRelationsAllowed(true, false, true));
  }

  @Test
  void compiledConsumersKeepIndependentNativeAndEffectOwners() throws Exception {
    var changfeng = new DnvTurretExecutionTest.Fixture(temp);
    var storm = new DnvTurretExecutionTest.Fixture(temp, true);
    var jingzhe = new JingzheExecutionFixture();

    assertEquals("QUEUED", changfeng.port.tryFire(changfeng.aim()).reason());
    changfeng.queue.run();
    var changfengReceipt = changfeng.port.inspect(changfeng.queue.target.id)
        .lastReceipt().orElseThrow();
    assertEquals("WORLD_ACCEPTED", changfengReceipt.reason());
    assertEquals(1, changfeng.queue.level.admissions);
    assertEquals(40_000, changfeng.queue.vehicle.energy);
    assertEquals("superbwarfare:cannon_shell", changfeng.queue.level.entities
        .get(changfengReceipt.admission().orElseThrow().projectileId()).type);

    List<Long> stormShots = new ArrayList<>();
    List<Double> stormMuzzles = new ArrayList<>();
    for (long tick = 100; tick <= 113; tick++) {
      storm.queue.level.now = tick;
      if (storm.port.pending()) continue;
      var frame = storm.aim();
      if (!frame.readiness().cooldownReady()) continue;
      assertEquals("QUEUED", storm.port.tryFire(frame).reason());
      storm.queue.run();
      var receipt = storm.port.inspect(storm.queue.target.id).lastReceipt().orElseThrow();
      assertEquals("WORLD_ACCEPTED", receipt.reason());
      stormShots.add(receipt.evaluatedTick());
      stormMuzzles.add(receipt.admission().orElseThrow().position().z());
    }
    assertEquals(List.of(100L, 103L, 105L, 108L, 110L, 113L), stormShots);
    for (int index = 0; index < stormMuzzles.size(); index++) {
      double expected = 4.0D + (index % 2 == 0 ? 0.306176D : -0.306176D);
      assertEquals(expected, stormMuzzles.get(index), 1.0E-12D);
    }
    assertEquals(60_000 - stormShots.size() * 2500, storm.queue.vehicle.energy);
    assertEquals(stormShots.size(), storm.queue.vehicle.energyWrites);
    assertEquals(0, storm.queue.vehicle.gun.fireIndex.get());
    assertStormResourceContract();

    jingzhe.call("tickCombatSystems");
    assertEquals(550_000, jingzhe.vehicle.energy);
    assertEquals(5, jingzhe.vehicle.energyWrites);
    assertTrue(Arrays.stream(jingzhe.vehicle.moduleTimer).allMatch(value -> value == 1));
    jingzhe.vehicle.energy = 1;
    for (int tick = 1; tick < 100; tick++) jingzhe.tick();
    assertTrue(Arrays.stream(jingzhe.vehicle.moduleState).allMatch(value -> value == 2));
    jingzhe.tick();
    assertEquals(3, jingzhe.vehicle.moduleState[0]);
    for (int tick = 0; tick < 59; tick++) jingzhe.tick();
    jingzhe.tick();
    assertEquals(4, jingzhe.vehicle.moduleState[0]);
    for (int tick = 0; tick < 11; tick++) jingzhe.tick();
    jingzhe.tick();
    assertEquals(1, jingzhe.target.hits);
    assertEquals(1, jingzhe.vehicle.pendingSecondaryStrikes.size());
    assertTrue(jingzhe.vehicle.groundFlameEffects.isEmpty());
    int firstRelease = jingzhe.vehicle.lastReleaseTick;
    for (int tick = 0; tick < 20; tick++) jingzhe.secondaryTick();
    assertEquals(2, jingzhe.target.hits);
    assertTrue(jingzhe.vehicle.pendingSecondaryStrikes.isEmpty());
    assertEquals(1, jingzhe.vehicle.groundFlameEffects.size());
    for (int tick = 0; tick < 39; tick++) jingzhe.tick();
    jingzhe.tick();
    assertEquals(3, jingzhe.target.hits);
    assertEquals(40, jingzhe.vehicle.lastReleaseTick - firstRelease);
    assertEquals(1, jingzhe.vehicle.groundFlameEffects.size());
    assertTrue(DnvJingzheTurretExecution.DEFINITION.projectile().isEmpty());
    assertEquals(5, DnvJingzheTurretExecution.DEFINITION
        .profile(1, 2, 72).capabilities().maxStations());

    assertEquals(1, changfeng.queue.level.admissions);
    assertEquals(40_000, changfeng.queue.vehicle.energy);
    assertEquals(stormShots.size(), storm.queue.level.admissions);
    assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
  }

  private static void assertStormResourceContract() throws IOException {
    var root = JsonParser.parseString(read(
        "src/artillery/resources/data/doggievehicle/sbw/vehicles/storm_turret.json"))
        .getAsJsonObject();
    var seek = root.getAsJsonObject("SeekInfo");
    var gun = root.getAsJsonObject("Weapons").getAsJsonObject("Main");
    assertEquals(150, seek.get("SeekEnergyCost").getAsInt());
    assertEquals(2500, gun.get("AmmoCostPerShoot").getAsInt());
    assertEquals(480, gun.get("RPM").getAsInt());
    assertEquals(2, gun.getAsJsonObject("ShootPos")
        .getAsJsonArray("Positions").size());
    String stormSource = read(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java");
    assertTrue(stormSource.contains("REQUIRED_ALIGNED_TICKS = 2"));
  }

  private static TowerMemory tower(int suffix) {
    return new TowerMemory(id(suffix), id(suffix + 10));
  }

  private static UUID id(int suffix) {
    return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
  }

  private static void assertCommand(DnvTurretTargetMemory.Decision decision) {
    var target = decision.target().orElseThrow();
    assertEquals(DnvTurretTargetMemory.Source.EXPLICIT_COMMAND, target.source());
    assertEquals(DnvTurretTargetMemory.COMMAND_PRIORITY, target.priority());
  }

  private static void assertAutonomous(
      DnvTurretTargetMemory.Decision decision,
      DnvTurretTargetMemory.InvalidationReason invalidation) {
    assertAutonomous(decision, Optional.of(invalidation));
  }

  private static void assertAutonomous(
      DnvTurretTargetMemory.Decision decision,
      Optional<DnvTurretTargetMemory.InvalidationReason> invalidation) {
    var target = decision.target().orElseThrow();
    assertEquals(DnvTurretTargetMemory.Source.AUTONOMOUS, target.source());
    assertEquals(DnvTurretTargetMemory.AUTONOMOUS_PRIORITY, target.priority());
    assertEquals(invalidation, decision.invalidatedBy());
  }

  private static DnvCombatPolicyProvider provider(String providerId) {
    return new DnvCombatPolicyProvider() {
      @Override public String providerId() {
        return providerId;
      }

      @Override public int contractVersion() {
        return CONTRACT_VERSION;
      }

      @Override public Optional<Assignment> assignment(
          net.minecraft.world.entity.Entity subordinate) {
        return Optional.empty();
      }
    };
  }

  private static String read(String relative) throws IOException {
    return Files.readString(PROJECT.resolve(relative), StandardCharsets.UTF_8);
  }

  private static final class TowerMemory {
    private final DnvTurretTargetMemory memory = new DnvTurretTargetMemory();
    private final UUID commandTarget;
    private final UUID autonomousTarget;

    private TowerMemory(UUID commandTarget, UUID autonomousTarget) {
      this.commandTarget = commandTarget;
      this.autonomousTarget = autonomousTarget;
    }

    private DnvTurretTargetMemory.Decision command(long now, long expires) {
      return input(now, 1, true, DnvTurretTargetMemory.CommandMode.ACTIVE,
          Optional.of(DnvTurretTargetMemory.Candidate.command(
              commandTarget, true, 7, now, expires, COMMAND_POSITION)),
          Optional.of(autoCandidate(now, expires)));
    }

    private DnvTurretTargetMemory.Decision autonomous(long now, long revision) {
      return input(now, revision, true, DnvTurretTargetMemory.CommandMode.NONE,
          Optional.empty(), Optional.of(autoCandidate(now, now + 20)));
    }

    private DnvTurretTargetMemory.Decision invalidCommand(long now, long revision) {
      return input(now, revision, true, DnvTurretTargetMemory.CommandMode.ACTIVE,
          Optional.empty(), Optional.of(autoCandidate(now, now + 20)));
    }

    private DnvTurretTargetMemory.Decision input(
        long now,
        long configurationRevision,
        boolean active,
        DnvTurretTargetMemory.CommandMode mode,
        Optional<DnvTurretTargetMemory.Candidate> command,
        Optional<DnvTurretTargetMemory.Candidate> autonomous) {
      return memory.decide(new DnvTurretTargetMemory.Input(
          now, configurationRevision, active, mode, command, autonomous));
    }

    private DnvTurretTargetMemory.Candidate autoCandidate(long now, long expires) {
      return DnvTurretTargetMemory.Candidate.autonomous(
          autonomousTarget, 3, now, expires, AUTO_POSITION);
    }
  }
}
