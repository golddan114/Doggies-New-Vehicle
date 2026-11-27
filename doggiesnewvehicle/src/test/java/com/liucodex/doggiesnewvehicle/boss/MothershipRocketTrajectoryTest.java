package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs exact RPG tick and FastThrowable movement. World, trails and collision ports are isolated. */
class MothershipRocketTrajectoryTest {
    @Test void fasterRocketWithGravityCorrectionReachesStationaryTargetsWithinItsBlastRadius() throws Exception {
        Loader loader = new Loader();
        Vec3 from = new Vec3(0, 35, 0);
        for (int distance : new int[] {5, 25, 50, 80}) {
            for (int height : new int[] {-40, -15, 0, 30}) {
                Vec3 target = from.add(0, height, distance);
                assertTrue(impact(loader, from, target, Vec3.ZERO, MothershipRocketAim.direction(from, target)),
                        distance + "/" + height);
            }
        }
        assertEquals(Vec3.ZERO, MothershipRocketAim.direction(from, new Vec3(Double.NaN, 0, 0)));
    }
    @Test void machineGunFiresTwiceAsOftenWithoutCatchupOrDuplicateTickBursts() {
        var armament = new MothershipArmament();
        int shots = 0;
        for (int tick = 0; tick < 100; tick++) {
            shots += armament.tryFire(MothershipArmament.Station.FRONT_PORT, tick, (s, b) -> true);
            assertEquals(0, armament.tryFire(MothershipArmament.Station.FRONT_PORT, tick, (s, b) -> true));
        }
        assertEquals(10, shots);
        assertEquals(1, armament.tryFire(MothershipArmament.Station.FRONT_PORT, 2000, (s, b) -> true));
    }
    private static boolean impact(Loader loader, Vec3 from, Vec3 target, Vec3 motion, Vec3 direction) throws Exception {
        Body rocket = loader.rocket(); rocket.position = from; rocket.velocity = direction.scale(2.5);
        AABB bounds = new AABB(-2.45, -2.9, -2.45, 2.45, 2.9, 2.45);
        for (int tick = 0; tick < 79; tick++) {
            Vec3 before = rocket.position.subtract(target);
            rocket.getClass().getMethod("tick").invoke(rocket);
            target = target.add(motion);
            Vec3 after = rocket.position.subtract(target);
            if (bounds.contains(before) || bounds.contains(after) || bounds.clip(before, after).isPresent()) return true;
        }
        return false;
    }
    public static class World { }
    public static class ServerWorld extends World { }
    public static class Body {
        public int tickCount; public double xo, yo, zo;
        public Vec3 position = Vec3.ZERO, velocity = Vec3.ZERO;
        public void tick() {
            tickCount++;
            try { getClass().getMethod("projectileMove", World.class).invoke(this, level()); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }
        public World level() { return new World(); }
        public double getX() { return position.x; } public double getY() { return position.y; } public double getZ() { return position.z; }
        public Vec3 getDeltaMovement() { return velocity; } public void setDeltaMovement(Vec3 v) { velocity = v; }
        public Vec3 position() { return position; } public void setPos(double x, double y, double z) { position = new Vec3(x, y, z); }
        public boolean isInWater() { return false; } public boolean canPassThroughFluid() { return false; }
        public float getCustomGravity() { return .015F; } public void updateRotation() { } public void mediumTrail() { }
        public float underwaterMotionScaleValue = 1;
    }
    private static final class Loader extends ClassLoader {
        private static final String NATIVE = "com/atsuishio/superbwarfare/entity/projectile/";
        private static final String GENERATED = "com/liucodex/doggiesnewvehicle/boss/rocketprobe/";
        private final Map<String, String> methods = new HashMap<>(), fields = new HashMap<>();
        Loader() throws Exception {
            for (String line : Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] pieces = line.split(" ");
                if (pieces[0].equals("MD:")) methods.put(shortName(pieces[1]), shortName(pieces[3]));
                if (pieces[0].equals("FD:")) fields.put(shortName(pieces[1]), shortName(pieces[2]));
            }
        }
        Body rocket() throws Exception { return (Body) loadClass((GENERATED + "RpgRocketStandardEntity").replace('/', '.')).getConstructor().newInstance(); }
        private static String shortName(String value) { return value.substring(value.lastIndexOf('/') + 1); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if (!name.replace('.', '/').startsWith(GENERATED)) throw new ClassNotFoundException(name);
            try (var zip = new ZipFile(SbwTestDependency.path().toFile())) {
                ClassNode node = new ClassNode();
                new ClassReader(zip.getInputStream(zip.getEntry(NATIVE + simple + ".class"))).accept(node, 0);
                node.methods.removeIf(m -> !m.name.equals(simple.startsWith("Fast") ? "projectileMove" : "m_8119_"));
                node.fields.clear(); node.interfaces.clear(); node.innerClasses.clear(); node.visibleAnnotations = null;
                if (simple.startsWith("Fast")) node.superName = Type.getInternalName(Body.class);
                MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
                init.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(init);
                ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(new ClassRemapper(out, new Remapper() {
                    @Override public String map(String value) {
                        if (value.equals(NATIVE + "FastThrowableProjectile") || value.equals(NATIVE + "RpgRocketStandardEntity")) return GENERATED + shortName(value);
                        if (value.equals("net/minecraft/world/level/Level")) return Type.getInternalName(World.class);
                        if (value.equals("net/minecraft/server/level/ServerLevel")) return Type.getInternalName(ServerWorld.class);
                        if (value.equals("net/minecraft/world/entity/projectile/ThrowableItemProjectile")
                                || value.equals("net/minecraft/world/entity/Entity")) return Type.getInternalName(Body.class);
                        return value;
                    }
                    @Override public String mapMethodName(String owner, String value, String desc) { return methods.getOrDefault(value, value); }
                    @Override public String mapFieldName(String owner, String value, String desc) { return fields.getOrDefault(value, value); }
                }));
                byte[] bytes = out.toByteArray(); return defineClass(name, bytes, 0, bytes.length);
            } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
        }
    }
}
