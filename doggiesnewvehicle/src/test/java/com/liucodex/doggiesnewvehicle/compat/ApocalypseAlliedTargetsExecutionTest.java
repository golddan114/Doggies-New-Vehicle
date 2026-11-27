package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Runs the compiled relation predicate with live category/team/occupancy ports. */
class ApocalypseAlliedTargetsExecutionTest {
    @Test void explicitEnemiesAndBossesAreEligibleButDifferentTeamsAndNeutralMobsAreNot() throws Exception {
        var policy = predicate(); var source = new Vehicle(); source.team = "dnv_allied";
        var boss = new Boss(); boss.team = "dnv_boss";
        assertTrue(policy.test(source, boss)); assertTrue(policy.test(source, new Monster()));
        var emptyTank = new Vehicle(); emptyTank.team = "other";
        assertFalse(policy.test(source, emptyTank)); assertFalse(policy.test(source, new Living()));
        assertFalse(policy.test(source, new NeutralMonster()));
        boss.team = source.team;
        assertFalse(policy.test(source, boss));
        boss.team = "dnv_boss"; boss.nativeFriendly = true;
        assertFalse(policy.test(source, boss));
    }

    @Test void allPlayerStatesOwnedEnemiesAndNestedPlayerVehiclesAreProtected() throws Exception {
        var policy = predicate(); var source = new Vehicle();
        for (boolean creative : new boolean[] {false, true}) for (boolean spectator : new boolean[] {false, true}) {
            var player = new Player(); player.creative = creative; player.spectator = spectator;
            assertFalse(policy.test(source, player));
            var boss = new Boss(); var intermediate = new Vehicle();
            intermediate.passengers.add(player); boss.passengers.add(intermediate);
            assertFalse(policy.test(source, boss));
        }
        assertFalse(policy.test(source, new PetMonster()));
        var owned = new OwnedBoss(); owned.owner = UUID.randomUUID();
        assertFalse(policy.test(source, owned));
        owned.owner = null;
        assertTrue(policy.test(source, owned));
    }

    @Test void currentDeathWorldTeamAndExclusionOverrideRetainedIdentity() throws Exception {
        var policy = predicate(); var source = new Vehicle(); var enemy = new Monster();
        assertTrue(policy.test(source, enemy));
        enemy.removed = true; assertFalse(policy.test(source, enemy)); enemy.removed = false;
        enemy.health = 0; assertFalse(policy.test(source, enemy)); enemy.health = 20;
        enemy.world = new World(); assertFalse(policy.test(source, enemy)); enemy.world = source.world;
        enemy.excluded = true; assertFalse(policy.test(source, enemy)); enemy.excluded = false;
        source.team = enemy.team = "red"; assertFalse(policy.test(source, enemy));
        var wreck = new Boss(); wreck.wreck = true; assertFalse(policy.test(source, wreck));
        var passenger = new Monster(); passenger.root = source; assertFalse(policy.test(source, passenger));
    }

    static BiPredicate<Body, Body> predicate() throws Exception {
        String original = "com/liucodex/doggiesnewvehicle/armored/ApocalypseAlliedTargets";
        String generated = "com/liucodex/doggiesnewvehicle/compat/executionprobe/AlliedRelations";
        Map<String, String> names = new HashMap<>(); names.put(original, generated);
        Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity", Body.class),
                Map.entry("net/minecraft/world/entity/LivingEntity", Living.class),
                Map.entry("net/minecraft/world/entity/player/Player", Player.class),
                Map.entry("net/minecraft/world/entity/NeutralMob", Neutral.class),
                Map.entry("net/minecraft/world/entity/OwnableEntity", Owned.class),
                Map.entry("net/minecraft/world/entity/monster/Enemy", Hostile.class),
                Map.entry("net/minecraft/world/level/Level", World.class),
                Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Vehicle.class),
                Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity", AutoVehicle.class),
                Map.entry("com/atsuishio/superbwarfare/tools/SeekTool", NativeRelations.class),
                Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetExclusions", Exclusions.class),
                Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime", Runtime.class))
                .forEach((key, value) -> names.put(key, Type.getInternalName(value)));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        try (var in = ApocalypseAlliedTargetsExecutionTest.class.getResourceAsStream("/" + original + ".class")) {
            new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer, new Remapper() {
                @Override public String map(String name) { return names.getOrDefault(name, name); }
            }), 0);
        }
        var loader = new ExactQueuedShotFixture.Loader(Map.of(generated, writer.toByteArray()));
        var method = loader.loadClass(generated.replace('/', '.')).getMethod("eligible", Body.class, Body.class);
        return (a, b) -> {
            try { return (boolean) method.invoke(null, a, b); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        };
    }

    public static class World { }
    private static final World WORLD = new World();
    public static class Body {
        public final UUID id = UUID.randomUUID();
        public World world = WORLD;
        public Body root = this;
        public String team;
        public boolean removed, spectator, excluded, nativeFriendly;
        public final List<Body> passengers = new ArrayList<>();
        public World level() { return world; }
        public Body getRootVehicle() { return root; }
        public boolean isSpectator() { return spectator; }
        public boolean isAlliedTo(Body other) { return team != null && team.equals(other.team); }
        public Stream<Body> getSelfAndPassengers() { return Stream.concat(Stream.of(this), passengers.stream().flatMap(Body::getSelfAndPassengers)); }
    }
    public static class Living extends Body { public float health = 20; }
    public static class Player extends Living { public boolean creative; }
    public interface Hostile { }
    public interface Neutral { }
    public interface Owned { UUID getOwnerUUID(); }
    public static class Monster extends Living implements Hostile { }
    public static class NeutralMonster extends Monster implements Neutral { }
    public static class PetMonster extends Monster implements Owned { public UUID getOwnerUUID() { return id; } }
    public static class Vehicle extends Body { public boolean wreck; public boolean isWreck() { return wreck; } }
    public static class AutoVehicle extends Vehicle { public UUID owner; public Optional<UUID> getOptionalOwnerUUID() { return Optional.ofNullable(owner); } }
    public static class Boss extends Vehicle implements DnvCombatPolicy.BossAutonomy { }
    public static class OwnedBoss extends AutoVehicle implements DnvCombatPolicy.BossAutonomy { }
    public static class NativeRelations { public static final BiPredicate<Body, Body> IS_FRIENDLY = (a, b) -> b.nativeFriendly; }
    public static class Exclusions {
        public static boolean isUnavailableCombatTarget(Body target) {
            return target.removed || target.excluded || target instanceof Living living && living.health <= 0;
        }
    }
    public static class Runtime {
        public static boolean allowsNativeTarget(Body source, Body target, boolean hostile) {
            return DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(source.id, target.id,
                    DnvCombatPolicy.Scope.NATIVE_AUTONOMY, target instanceof Player ? DnvCombatPolicy.TargetKind.PLAYER
                    : DnvCombatPolicy.TargetKind.OTHER_VEHICLE, hostile, false)).allowed();
        }
    }
}
