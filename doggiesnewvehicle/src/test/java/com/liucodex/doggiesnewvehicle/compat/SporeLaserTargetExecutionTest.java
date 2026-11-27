package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Executes the production identity, availability and boss admission chain with entity ports. */
class SporeLaserTargetExecutionTest {
    @Test void exactLivingScentIsAvailableOnlyToLasersAndNeverSurvivesRemovalOrProtection() throws Exception {
        var f = new Fixture(); var boss = new Boss(); var scent = f.scent();
        assertTrue(f.unavailable(scent, false)); assertFalse(f.unavailable(scent, true));
        assertFalse(f.allowed(boss, scent, false)); assertTrue(f.allowed(boss, scent, true));
        scent.excluded = true; assertFalse(f.allowed(boss, scent, true)); scent.excluded = false;
        scent.removed = true; assertFalse(f.allowed(boss, scent, true)); scent.removed = false;
        scent.health = 0; assertFalse(f.allowed(boss, scent, true)); scent.health = 10;
        boss.team = scent.team = "same"; assertFalse(f.allowed(boss, scent, true)); scent.team = "other";
        assertTrue(f.allowed(boss, scent, true));
        var passenger = new Player(); passenger.creative = true; scent.passengers.add(passenger);
        assertFalse(f.allowed(boss, scent, true)); passenger.creative = false;
        assertTrue(f.allowed(boss, scent, true));
    }

    @Test void ordinarySporeMobsWrongClassesAndCustomIdsKeepNormalCombatRules() throws Exception {
        var f = new Fixture(); var boss = new Boss(); var ordinary = new Living();
        ordinary.kind = new Kind("spore:scent");
        assertFalse(f.unavailable(ordinary, false)); assertTrue(f.allowed(boss, ordinary, false));
        var renamed = f.scent(); renamed.kind = new Kind("spore:other");
        assertFalse(f.unavailable(renamed, false)); assertTrue(f.allowed(boss, renamed, false));
        var vehicle = new Vehicle(); assertTrue(f.allowed(boss, vehicle, true));
        vehicle.wreck = true; assertFalse(f.allowed(boss, vehicle, true));
        assertTrue(f.unavailable(null, true)); assertFalse(f.allowed(boss, null, true));
        var proxy = new Living(); proxy.kind = new Kind("doggievehicle:combat_proxy");
        assertTrue(f.unavailable(proxy, true));
    }

    static final class Fixture {
        private final Class<?> exclusions, policy, scentType;
        Fixture() throws Exception {
            String base = "com/liucodex/doggiesnewvehicle/compat/";
            String exclusionsName = base + "DnvTargetExclusions", policyName = base + "DnvCombatPolicyRuntime";
            String scent = "com/Harbinger/Spore/Sentities/Utility/ScentEntity";
            Map<String,String> names = new HashMap<>();
            names.put(exclusionsName, exclusionsName + "SporeProbe"); names.put(policyName, policyName + "SporeProbe");
            for (var entry : Map.ofEntries(
                    Map.entry("net/minecraft/world/entity/Entity", Actor.class),
                    Map.entry("net/minecraft/world/entity/LivingEntity", Living.class),
                    Map.entry("net/minecraft/world/entity/player/Player", Player.class),
                    Map.entry("net/minecraft/world/entity/monster/Enemy", Enemy.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Vehicle.class),
                    Map.entry("net/minecraft/world/entity/EntityType", Kind.class),
                    Map.entry("net/minecraftforge/registries/ForgeRegistries", Catalog.class),
                    Map.entry("net/minecraftforge/registries/IForgeRegistry", Registry.class),
                    Map.entry(base + "DnvCombatCoordinator", Coordinator.class)).entrySet()) {
                names.put(entry.getKey(), Type.getInternalName(entry.getValue()));
            }
            Map<String,byte[]> definitions = new HashMap<>();
            for (String source : List.of(exclusionsName, policyName)) {
                ClassNode node = new ClassNode();
                try (var input = getClass().getResourceAsStream("/" + source + ".class")) { new ClassReader(input).accept(node, 0); }
                if (source.equals(policyName)) {
                    Set<String> keep = Set.of("allowsBossNativeTarget", "allowsBossLaserTarget", "allowsBossTarget",
                            "bossRelationsAllow", "isProtectedPlayerTarget", "isPvpProtected", "kind");
                    node.methods.removeIf(m -> !keep.contains(m.name) && keep.stream().noneMatch(k -> m.name.startsWith("lambda$" + k + "$")));
                    node.fields.clear();
                }
                node.innerClasses.clear(); node.nestHostClass = null; node.nestMembers = null;
                ClassWriter out = new ClassWriter(0); node.accept(new ClassRemapper(out, new SimpleRemapper(names)));
                definitions.put(names.get(source), out.toByteArray());
            }
            ClassWriter out = new ClassWriter(0); out.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, scent, null, Type.getInternalName(Living.class), null);
            var init = out.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode(); init.visitVarInsn(Opcodes.ALOAD,0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL,Type.getInternalName(Living.class),"<init>","()V",false);
            init.visitInsn(Opcodes.RETURN); init.visitMaxs(1,1); init.visitEnd(); out.visitEnd(); definitions.put(scent,out.toByteArray());
            var loader = new ExactQueuedShotFixture.Loader(definitions);
            exclusions = loader.loadClass(names.get(exclusionsName).replace('/','.'));
            policy = loader.loadClass(names.get(policyName).replace('/','.')); scentType = loader.loadClass(scent.replace('/','.'));
        }
        Living scent() throws Exception { var actor = (Living)scentType.getConstructor().newInstance(); actor.kind = new Kind("spore:scent"); return actor; }
        boolean unavailable(Actor target, boolean laser) throws Exception { return (boolean)exclusions.getMethod(laser ? "isUnavailableLaserTarget" : "isUnavailableCombatTarget",Actor.class).invoke(null,target); }
        boolean allowed(Actor source, Actor target, boolean laser) throws Exception { return (boolean)policy.getMethod(laser ? "allowsBossLaserTarget" : "allowsBossNativeTarget",Actor.class,Actor.class,boolean.class).invoke(null,source,target,true); }
    }
    public static class Actor {
        public Kind kind = new Kind("minecraft:zombie"); public boolean removed,excluded,spectator; public String team;
        public List<Actor> passengers = new ArrayList<>(); private final UUID id = UUID.randomUUID();
        public Kind getType(){return kind;} public boolean isRemoved(){return removed;} public boolean isAlive(){return !removed;}
        public boolean isSpectator(){return spectator;} public UUID getUUID(){return id;}
        public Stream<Actor> getSelfAndPassengers(){return Stream.concat(Stream.of(this),passengers.stream().flatMap(Actor::getSelfAndPassengers));}
        public boolean isAlliedTo(Actor other){return team!=null&&team.equals(other.team);}
    }
    public static class Living extends Actor {public float health=10;public float getHealth(){return health;}}
    public static class Player extends Living {public boolean creative;public boolean isCreative(){return creative;}}
    public static class Vehicle extends Actor {public boolean wreck;public boolean isWreck(){return wreck;}public float getHealth(){return 100;}}
    public static class Boss extends Vehicle implements DnvCombatPolicy.BossAutonomy { }
    public interface Enemy { }
    public record Kind(String id) { }
    public interface Registry {default ResourceLocation getKey(Object type){return new ResourceLocation(((Kind)type).id());}}
    public static class Catalog {public static final Registry ENTITY_TYPES = new Registry(){};}
    public static class Coordinator {public static boolean isExcluded(Actor actor){return actor.excluded;}}
}
