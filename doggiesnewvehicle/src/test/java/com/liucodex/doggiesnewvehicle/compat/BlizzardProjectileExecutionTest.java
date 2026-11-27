package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the actual compiled bolt and homing tick; explicit world ports expose collision and hurt. */
class BlizzardProjectileExecutionTest {
    @Test void tinyCollisionBoxDoesNotHideTheRocketAcrossItsWholeFlightRange() throws Exception {
        var f=fixture(true);var visible=f.type.getMethod("shouldRenderAtSqrDistance",double.class);
        for(double distance:new double[]{0,16,32,48,64,127,240,255})assertTrue((boolean)visible.invoke(f.shot,distance*distance));
        for(double distance:new double[]{256*256,-1,Double.NaN,Double.POSITIVE_INFINITY})assertFalse((boolean)visible.invoke(f.shot,distance));
    }
    @Test void missileSpawnPreservesWhiteModelFlightOrientationBeforeTheFirstClientTick() throws Exception {
        var server = fixture(true); var client = fixture(true); client.world.isClientSide = true;
        server.shot.velocity = new Vec3(.35, -.14, .46); server.shot.yaw = -37; server.shot.pitch = 14;
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            server.type.getMethod("writeSpawnData", net.minecraft.network.FriendlyByteBuf.class).invoke(server.shot, buffer);
            client.type.getMethod("readSpawnData", net.minecraft.network.FriendlyByteBuf.class).invoke(client.shot, buffer);
            assertEquals(server.shot.velocity, client.shot.velocity);
            assertEquals(server.shot.yaw, client.shot.yaw); assertEquals(client.shot.yaw, client.shot.yRotO);
            assertEquals(server.shot.pitch, client.shot.pitch); assertEquals(client.shot.pitch, client.shot.xRotO);
            client.tick(); assertEquals(new Vec3(0, 1, 0).add(server.shot.velocity), client.shot.position);
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void diagonalSpawnAndInterleavedVanillaMotionCannotBendADetachedPulse() throws Exception {
        var server = fixture(false);
        Vec3 velocity = new Vec3(7, 2, 3).normalize().scale(8);
        server.shot.velocity = velocity;
        var client = fixture(false); client.world.isClientSide = true;
        var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(1, velocity);
        client.shot.velocity = new Vec3(packet.getXa() / 8000d, packet.getYa() / 8000d, packet.getZa() / 8000d);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            server.type.getMethod("writeSpawnData", net.minecraft.network.FriendlyByteBuf.class).invoke(server.shot, buffer);
            client.type.getMethod("readSpawnData", net.minecraft.network.FriendlyByteBuf.class).invoke(client.shot, buffer);
            for (int tick = 1; tick <= 5; tick++) {
                client.shot.lerpMotion(packet.getXa() / 8000d, packet.getYa() / 8000d, packet.getZa() / 8000d);
                client.tick();
                Vec3 expected = new Vec3(0, 1, 0).add(velocity.scale(tick));
                assertEquals(0, expected.distanceTo(client.shot.position), 1e-9);
                assertEquals(0, velocity.distanceTo(client.shot.velocity), 1e-9);
            }
        } finally { buffer.release(); }
    }
    @Test void boltMovesEightBlocksPerTickDetachesFromTurretAndHurtsOnceWithoutBlast() throws Exception {
        var f = fixture(false); f.world.target.position = new Vec3(0, 1, 12); f.world.trace = true;
        f.tick(); assertEquals(new Vec3(0, 1, 8), f.shot.position); assertEquals(0, f.world.target.hurts);
        assertEquals(1, f.shot.syncs); assertEquals(new Vec3(0, 0, 8), f.shot.syncedVelocity);
        f.world.tank.yaw = 90;
        f.tick(); assertEquals(new Vec3(0, 1, 12), f.shot.position);
        assertEquals(1, f.world.target.hurts); assertEquals(10, f.world.target.damage); assertTrue(f.shot.removed);
        assertEquals(1, f.shot.syncs, "removed pulse must not dispatch stale motion");
    }
    @Test void wallsAndUnloadedTerrainStopBoltAndRangeNeverExceedsTwoHundredFiftySix() throws Exception {
        var f = fixture(false); f.world.wall = 4; f.world.trace = true;
        f.tick(); assertEquals(new Vec3(0, 1, 4), f.shot.position); assertTrue(f.shot.removed); assertEquals(0, f.world.target.hurts);
        f = fixture(false); f.world.loaded = false; f.tick();
        assertEquals(new Vec3(0, 1, 0), f.shot.position); assertTrue(f.shot.removed);
        f = fixture(false); for (int i = 0; i < 32; i++) f.tick();
        assertEquals(new Vec3(0, 1, 256), f.shot.position); assertTrue(f.shot.removed);
        f = fixture(false); f.type.getMethod("readAdditionalSaveData", CompoundTag.class).invoke(f.shot, new CompoundTag());
        f.tick(); assertTrue(f.shot.removed); assertEquals(new Vec3(0, 1, 0), f.shot.position);
    }
    @Test void rocketKeepsLaunchRayDespiteTargetLossAndStopsAtUnloadedFlight() throws Exception {
        var f = fixture(true); f.world.target.position = new Vec3(-20, 1, 40);
        f.tick(); assertEquals(0, f.shot.yaw, 1e-12); assertEquals(0, f.shot.velocity.x, 1e-12);
        assertEquals(1, f.shot.syncs); assertEquals(f.shot.velocity, f.shot.syncedVelocity);
        assertSame(f.world.tank, f.shot.owner.vehicle);
        Vec3 pilot = f.shot.owner.position;
        for (int reason = 0; reason < 4; reason++) {
            f = fixture(true);
            if (reason == 0) f.world.target.alive = false;
            if (reason == 1) f.world.target.allied = true;
            if (reason == 2) f.world.loaded = false;
            if (reason == 3) f.world.target = null;
            f.tick(); assertEquals(reason == 2, f.shot.removed); assertEquals(0, f.world.impacts);
            assertEquals(pilot, f.shot.owner.position);
        }
    }
    @Test void boundaryStepKeepsAnEarlierHitButDoesNotHitAcrossTheGap() throws Exception {
        var inside = fixture(false);
        inside.world.activeFraction = .5;
        inside.world.trace = true;
        inside.world.target.position = new Vec3(0, 1, 3);
        inside.tick();
        assertEquals(1, inside.world.target.hurts);
        assertEquals(new Vec3(0, 1, 3), inside.shot.position);
        assertTrue(inside.shot.removed);

        var outside = fixture(false);
        outside.world.activeFraction = .5;
        outside.world.trace = true;
        outside.world.target.position = new Vec3(0, 1, 5);
        outside.tick();
        assertEquals(0, outside.world.target.hurts);
        assertEquals(new Vec3(0, 1, 4), outside.shot.position);
        assertTrue(outside.shot.removed);
        assertEquals(0, outside.shot.syncs);
    }
    @Test void nativeVanillaVelocityPacketClampsFastComponentsAndClientPulseMustNotSendMotion() throws Exception {
        var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(1, new Vec3(8, 2, 1));
        assertEquals(31200, packet.getXa(), "vanilla component clamp is 3.9, so native fast sync must remain active");
        var f = fixture(false); f.world.isClientSide = true;
        f.tick(); assertEquals(new Vec3(0, 1, 8), f.shot.position); assertEquals(0, f.shot.syncs);
    }
    @Test void savedHomingProgressRetainsTargetAndStopsAtRemainingDistanceWithoutReplay() throws Exception {
        var f = fixture(true); f.type.getField("travelled").setDouble(f.shot, 239.9);
        var tag = new CompoundTag(); f.type.getMethod("addAdditionalSaveData", CompoundTag.class).invoke(f.shot, tag);
        f.type.getMethod("readAdditionalSaveData", CompoundTag.class).invoke(f.shot, tag);
        f.tick(); assertEquals(.1, f.shot.position.distanceTo(new Vec3(0, 1, 0)), 1e-9); assertTrue(f.shot.removed);
        assertEquals(0, f.world.impacts);
    }
    record Fixture(Shot shot, World world, Class<?> type) {
        void tick() throws Exception { type.getMethod("tick").invoke(shot); }
    }
    private Fixture fixture(boolean homing) throws Exception {
        String root = "com/liucodex/doggiesnewvehicle/armored/";
        String source = root + (homing ? "DnvBlizzardMissile" : "DnvBlizzardColdBolt");
        String target = "com/liucodex/doggiesnewvehicle/compat/projectileprobe/" + (homing ? "Homing" : "Bolt");
        Map<String, String> names = new HashMap<>(); names.put(source, target);
        // Registry context is unused by this three-double payload; the NeoForge method keeps the same byte buffer port.
        names.put("net/minecraft/network/RegistryFriendlyByteBuf", "net/minecraft/network/FriendlyByteBuf");
        names.put("com/atsuishio/superbwarfare/entity/projectile/JavelinMissileEntity", Type.getInternalName(Shot.class));
        Map.ofEntries(Map.entry("world/entity/Entity", Entity.class), Map.entry("world/level/Level", World.class),
                Map.entry("server/level/ServerLevel", World.class), Map.entry("world/level/ClipContext", Clip.class),
                Map.entry("world/phys/HitResult", Hit.class), Map.entry("world/phys/BlockHitResult", Hit.class),
                Map.entry("core/registries/Registries", BlizzardGameplayExecutionTest.Keys.class),
                Map.entry("resources/ResourceKey", BlizzardGameplayExecutionTest.Key.class),
                Map.entry("core/RegistryAccess", BlizzardGameplayExecutionTest.Registry.class),
                Map.entry("core/Registry", BlizzardGameplayExecutionTest.Registry.class),
                Map.entry("core/Holder", BlizzardGameplayExecutionTest.Holder.class),
                Map.entry("core/Holder$Reference", BlizzardGameplayExecutionTest.Holder.class),
                Map.entry("world/damagesource/DamageSource", Cold.class))
                .forEach((key, value) -> names.put("net/minecraft/" + key, Type.getInternalName(value)));
        names.put(root + "DnvBlizzardEntity", Type.getInternalName(Tank.class));
        names.put(root + "ApocalypseTargeting", Type.getInternalName(Targeting.class));
        names.put(root + "BlizzardLoadedPath", Type.getInternalName(Loaded.class));
        names.put(root + "BlizzardTrace", Type.getInternalName(Trace.class));
        names.put(root + "BlizzardTrace$Hit", Type.getInternalName(Trace.Hit.class));
        names.put(root + "BlizzardColdDamage", Type.getInternalName(BlizzardGameplayExecutionTest.Damage.class));
        ClassNode node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/" + source + ".class")) { new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES); }
        node.methods.removeIf(m -> !Set.of("tick", "source", "addAdditionalSaveData", "readAdditionalSaveData", "writeSpawnData", "readSpawnData", "lerpMotion", "shouldRenderAtSqrDistance").contains(m.name) && !m.name.startsWith("lambda$tick$"));
        node.interfaces.clear();
        node.innerClasses.clear();
        node.methods.forEach(m -> m.access = Opcodes.ACC_PUBLIC | (m.access & Opcodes.ACC_STATIC));
        node.fields.forEach(f -> f.access = Opcodes.ACC_PUBLIC | (f.access & Opcodes.ACC_STATIC));
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new Remapper() { @Override public String map(String key) { return names.getOrDefault(key, key); } }));
        Class<?> kind = new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray())).loadClass(target.replace('/', '.'));
        var shot = (Shot) kind.getConstructor().newInstance(); var world = new World(); shot.world = world;
        shot.owner = new Entity(); shot.owner.vehicle = world.tank; shot.owner.world = world;
        shot.position = new Vec3(0, 1, 0); shot.velocity = new Vec3(0, 0, homing ? 1 : 8);
        if (homing) {
            kind.getField("vehicleId").set(shot, world.tank.id);
        }
        return new Fixture(shot, world, kind);
    }
    public static class Entity {
        public UUID id = UUID.randomUUID(); public World world; public Entity vehicle; public Vec3 position = new Vec3(6, 40, 9);
        public boolean alive = true, allied; public int hurts; public float damage;
        public UUID getUUID() { return id; } public World level() { return world; }
        public boolean isAlive() { return alive; } public boolean isPickable() { return true; } public boolean isSpectator() { return false; }
        public Entity getRootVehicle() { return vehicle == null ? this : vehicle; }
        public AABB getBoundingBox() { return new AABB(position.subtract(.1, .1, .1), position.add(.1, .1, .1)); }
        public boolean hurt(Cold source, float value) { hurts++; damage += value; return true; }
    }
    public static class Tank extends Entity { public float yaw; public boolean ownsTv(Object missile) { return false; } public boolean isWreck() { return false; } }
    public static class Shot extends Entity {
        public Entity owner; public boolean removed; public Vec3 velocity, syncedVelocity; public float yaw, pitch, xRotO, yRotO; public int tickCount, syncs;
        public Entity getOwner() { return owner; } public Vec3 position() { return position; }
        public Vec3 getDeltaMovement() { return velocity; } public void setDeltaMovement(Vec3 value) { velocity = value; }
        public void setPos(Vec3 value) { position = value; } public void baseTick() { tickCount++; }
        public float getYRot() { return yaw; } public float getXRot() { return pitch; }
        public void setYRot(float value) { yaw = value; } public void setXRot(float value) { pitch = value; }
        public void discard() { removed = true; } public void impact() { world.impacts++; discard(); }
        public void releaseControl() { }
        public void syncMotion() { syncs++; syncedVelocity = velocity; }
        public void writeSpawnData(net.minecraft.network.FriendlyByteBuf buffer) { }
        public void readSpawnData(net.minecraft.network.FriendlyByteBuf buffer) { }
        public void lerpMotion(double x, double y, double z) { velocity = new Vec3(x, y, z); }
        public void m_7380_(CompoundTag tag) { } public void m_7378_(CompoundTag tag) { }
    }
    public static class World {
        public boolean isClientSide, loaded = true, trace; public double wall = Double.POSITIVE_INFINITY; public int impacts;
        public double activeFraction = 1;
        public Tank tank = new Tank(); public Entity target = new Entity();
        World() { tank.world = this; target.world = this; target.position = new Vec3(0, 1, 12); }
        public Entity getEntity(UUID id) { return tank.id.equals(id) ? tank : target != null && target.id.equals(id) ? target : null; }
        public Hit clip(Clip clip) { return new Hit(clip.from.z < wall && clip.to.z >= wall, new Vec3(0, 1, wall)); }
        public BlizzardGameplayExecutionTest.Registry registryAccess() { return new BlizzardGameplayExecutionTest.Registry() { }; }
    }
    public static class Cold { public Cold(BlizzardGameplayExecutionTest.Holder holder, Entity direct, Entity owner) { } }
    public record Clip(Vec3 from, Vec3 to, net.minecraft.world.level.ClipContext.Block block, net.minecraft.world.level.ClipContext.Fluid fluid, Entity owner) { }
    public record Hit(boolean blocked, Vec3 location) {
        public net.minecraft.world.phys.HitResult.Type getType() { return blocked ? net.minecraft.world.phys.HitResult.Type.BLOCK : net.minecraft.world.phys.HitResult.Type.MISS; }
        public Vec3 getLocation() { return location; }
    }
    public static class Targeting {
        public static boolean eligible(Entity source, Entity operator, Entity target) { return target != null && target.alive && !target.allied; }
    }
    public static class Loaded {
        public static Vec3 clipStep(Entity owner, Vec3 from, Vec3 to) {
            if (!owner.world.loaded) return null;
            return owner.world.activeFraction == 1 ? to : from.lerp(to, owner.world.activeFraction);
        }
    }
    public static class Trace {
        public record Hit(Entity entity, Vec3 position) { }
        public static Hit nearest(World world, Vec3 from, Vec3 to, java.util.function.Predicate<Entity> filter) {
            var target = world.target;
            return world.trace && target != null && filter.test(target) && from.z <= target.position.z && to.z >= target.position.z ? new Hit(target, target.position) : null;
        }
    }
}
