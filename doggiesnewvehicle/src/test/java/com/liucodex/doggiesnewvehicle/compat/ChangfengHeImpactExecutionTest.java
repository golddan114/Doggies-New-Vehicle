package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Exact native afterHitEntity body, with explicit world/entity/effect doubles. Not an in-game test. */
class ChangfengHeImpactExecutionTest {
    private static final String SBW = "com/atsuishio/superbwarfare/";
    private static final String SHELL = SBW + "entity/projectile/CannonShellEntity";

    @Test void configuredHeExplodesAtOrdinaryEnemyWithoutSecondaryPenetration() throws Exception {
        var fixture = new Fixture();
        var shot = fixture.create();
        shot.type = configuredType();
        fixture.hit(shot, new Body());
        assertEquals(1, shot.explosions, "Changfeng must explode on an ordinary enemy, not keep flying as AP");
        assertEquals(Hit.POINT, shot.explosionPoint);
        assertTrue(shot.removed);
        assertEquals(0, ((ServerWorld) shot.world).traces);
    }

    @Test void oldMissingShellTypeReproducesPassThroughButVehicleContactStillExplodes() throws Exception {
        var fixture = new Fixture();
        var ordinary = fixture.create(); // exact native default AP, checked below
        fixture.hit(ordinary, new Body());
        assertEquals(0, ordinary.explosions);
        assertFalse(ordinary.removed);
        assertEquals(1, ((ServerWorld) ordinary.world).traces);
        var vehicle = fixture.create();
        fixture.hit(vehicle, new Vehicle());
        assertEquals(1, vehicle.explosions);
        assertTrue(vehicle.removed);
    }

    @Test void heStillHonoursServerAndOwnerVehicleGuards() throws Exception {
        var fixture = new Fixture();
        var client = fixture.create(); client.type = configuredType(); client.world = new World();
        fixture.hit(client, new Body());
        assertEquals(0, client.explosions); assertFalse(client.removed);
        var own = fixture.create(); own.type = configuredType(); own.owner = new Body();
        own.owner.root = new Vehicle();
        fixture.hit(own, own.owner.root);
        assertEquals(0, own.explosions); assertFalse(own.removed);
    }

    @Test void configuredHeAlsoExplodesOnTerrainWithoutApPenetration() throws Exception {
        var fixture = new ExactApBlockFixture();
        var shot = fixture.create(ExactApBlockFixture.State.material(1.5F, true, ExactApBlockFixture.Sound.OTHER), true);
        shot.type = ExactApBlockFixture.ShellType.valueOf(configuredType().name());
        fixture.hit(shot);
        assertEquals(1, shot.explosions); assertTrue(shot.removed);
        assertEquals(Vec3.ZERO, shot.explosionPoint);
        assertEquals(0, shot.level.destroys, "No AP penetration block-removal loop");
    }

    @Test void exactNativeLaunchConsumesShellTypeAndDefaultsToAp() throws Exception {
        ClassNode gun = read(SBW + "item/gun/GunItem");
        MethodNode launch = gun.methods.stream().filter(m -> m.name.equals("shootBullet$lambda$19")).findFirst().orElseThrow();
        var instructions = Arrays.asList(launch.instructions.toArray());
        assertTrue(instructions.stream().anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("SHELL_TYPE")));
        var he = instructions.stream().filter(i -> i instanceof FieldInsnNode f && f.owner.equals(SHELL + "$Type") && f.name.equals("HE")).findFirst().orElseThrow();
        AbstractInsnNode next = he.getNext(); while (next.getOpcode() < 0) next = next.getNext();
        assertTrue(next instanceof MethodInsnNode m && m.owner.equals(SHELL) && m.name.equals("setType"));
        MethodNode constructor = read(SHELL).methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        var set = Arrays.stream(constructor.instructions.toArray()).filter(i -> i instanceof FieldInsnNode f && f.name.equals("type") && f.getOpcode() == Opcodes.PUTFIELD).findFirst().orElseThrow();
        AbstractInsnNode previous = set.getPrevious(); while (previous.getOpcode() < 0) previous = previous.getPrevious();
        assertTrue(previous instanceof FieldInsnNode f && f.name.equals("AP"));
        assertEquals(ShellType.HE, configuredType());
    }

    private static ShellType configuredType() throws Exception {
        try (var in = ChangfengHeImpactExecutionTest.class.getResourceAsStream("/data/doggievehicle/sbw/vehicles/50_changfeng_cannon.json")) {
            JsonObject main = JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("Weapons").getAsJsonObject("Main");
            assertEquals("superbwarfare:cannon_shell", main.get("Projectile").getAsString());
            return ShellType.valueOf(main.has("ShellType") ? main.get("ShellType").getAsString() : "AP");
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (var zip = new ZipFile(SbwTestDependency.path().toFile())) {
            var result = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry(name + ".class")).readAllBytes()).accept(result, 0);
            return result;
        }
    }

    static final class Fixture extends ClassLoader {
        final Class<?> generated;
        Fixture() throws Exception {
            super(Fixture.class.getClassLoader());
            var node = new ClassNode(); node.version = Opcodes.V17; node.access = Opcodes.ACC_PUBLIC;
            node.name = "com/liucodex/doggiesnewvehicle/compat/heprobe/Cannon";
            node.superName = Type.getInternalName(Shot.class);
            var init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
            init.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(init);
            for (var method : read(SHELL).methods) if (method.name.equals("afterHitEntity") || method.name.startsWith("afterHitEntity$")) node.methods.add(method);
            var names = new HashMap<String,String>(); names.put(SHELL, node.name);
            names.put(SHELL + "$Type", Type.getInternalName(ShellType.class));
            names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Body.class));
            names.put("net/minecraft/world/entity/LivingEntity", Type.getInternalName(Living.class));
            names.put(SBW + "entity/vehicle/base/VehicleEntity", Type.getInternalName(Vehicle.class));
            names.put("net/minecraft/world/phys/EntityHitResult", Type.getInternalName(Hit.class));
            names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
            names.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(ServerWorld.class));
            names.put("net/minecraft/world/phys/AABB", Type.getInternalName(Box.class));
            names.put(SBW + "tools/TraceTool", Type.getInternalName(Trace.class));
            names.put(SBW + "tools/TraceTool$RayTraceResultEntity", Type.getInternalName(TraceResult.class));
            names.put(SBW + "init/ModDamageTypes", Type.getInternalName(Damage.class));
            names.put(SBW + "tools/DamageHandlerKt", Type.getInternalName(Damage.class));
            names.put("net/minecraft/core/RegistryAccess", "java/lang/Object");
            names.put("net/minecraft/world/damagesource/DamageSource", "java/lang/Object");
            var out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(out, new Remapper() {
                @Override public String map(String value) { return names.getOrDefault(value, value); }
                @Override public String mapMethodName(String owner, String name, String descriptor) {
                    return owner.equals("net/minecraft/world/phys/Vec3") && name.equals("m_82490_") ? "scale" : name;
                }
            }));
            byte[] bytes = out.toByteArray(); generated = defineClass(node.name.replace('/','.'), bytes, 0, bytes.length);
        }
        Shot create() throws Exception { return (Shot) generated.getConstructor().newInstance(); }
        void hit(Shot shot, Body target) throws Exception { generated.getMethod("afterHitEntity", Hit.class).invoke(shot, new Hit(target)); }
    }
    public enum ShellType { AP, HE, WP }
    public static class World { public Object m_9598_() { return this; } }
    public static class ServerWorld extends World { int traces; }
    public static class Body { public Body root = this; public Body m_20202_() { return root; } public Box m_20191_() { return new Box(); } }
    public static class Living extends Body { public int f_19802_; }
    public static class Vehicle extends Body {}
    public static class Box { public Vec3 m_82399_() { return Vec3.ZERO; } }
    public static class Hit {
        static final Vec3 POINT = new Vec3(7, 12, 4); final Body target;
        Hit(Body target) { this.target = target; }
        public Body m_82443_() { return target; } public Vec3 m_82450_() { return POINT; }
    }
    public static class Shot extends Body {
        public ShellType type = ShellType.AP; public World world = new ServerWorld(); public Body owner;
        public boolean removed; public int explosions; public Vec3 explosionPoint, velocity = new Vec3(15,0,0); public float damage = 190;
        public World m_9236_() { return world; } public Body m_19749_() { return owner; }
        public Vec3 m_20184_() { return velocity; } public void m_20256_(Vec3 value) { velocity = value; }
        public float getDamageValue() { return damage; } public void setDamage(float value) { damage = value; }
        public void causeExplode(Vec3 point) { explosions++; explosionPoint = point; } public void m_146870_() { removed = true; }
        public void causeWPEffect(Vec3 point, Body body) { fail("HE must not use WP"); }
    }
    public static class Trace {
        public static List<TraceResult> getEntitiesAlongVector(World world, Vec3 start, Vec3 velocity, Predicate<Body> filter) {
            ((ServerWorld) world).traces++; return List.of();
        }
    }
    public static class TraceResult { public Body getEntity() { return null; } }
    public static class Damage {
        public static Object causeProjectileHitDamage(Object registry, Body projectile, Body owner) { throw new AssertionError("Unexpected secondary damage"); }
        public static boolean forceHurt(Body target, Object source, float amount) { throw new AssertionError("Unexpected secondary damage"); }
    }
}
