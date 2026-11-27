package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.energy.M1ServoEnergy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the compiled M1 aiming admission/debit with explicit native pose/energy ports. */
class DnvM1ServoExecutionTest {
    @Test void actualM1AimRejectsLowPowerAndPaysChangedPoseOncePerServerTick() throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity";
        String target = "com/liucodex/doggiesnewvehicle/compat/gameplayprobe/M1Servo";
        var node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/" + source + ".class")) {
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
        }
        node.interfaces.clear(); node.innerClasses.clear();
        node.methods.removeIf(method -> !method.name.equals("aimWithServo"));
        node.fields.removeIf(field -> !field.name.equals("servoEnergy") && !field.name.equals("nativeStateInvalid"));
        node.fields.forEach(field -> field.access = Opcodes.ACC_PUBLIC);
        node.methods.forEach(method -> method.access = Opcodes.ACC_PUBLIC);
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(M1ServoEnergy.class)));
        ctor.instructions.add(new InsnNode(Opcodes.DUP));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, Type.getInternalName(M1ServoEnergy.class), "<init>", "()V", false));
        ctor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, source, "servoEnergy", Type.getDescriptor(M1ServoEnergy.class)));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(ctor);
        var names = Map.of(source, target, node.superName, Type.getInternalName(Pose.class),
                "net/minecraft/world/level/Level", Type.getInternalName(World.class));
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
        }));
        var loader = new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray()));
        var type = loader.loadClass(target.replace('/', '.'));
        var tank = (Pose) type.getConstructor().newInstance();
        var aim = type.getMethod("aimWithServo", Runnable.class);
        tank.energy = 7; aim.invoke(tank, (Runnable) () -> tank.yaw++);
        assertEquals(0, tank.yaw); assertEquals(0, tank.writes);
        tank.energy = 8; aim.invoke(tank, (Runnable) () -> { });
        assertEquals(8, tank.energy); assertEquals(0, tank.writes);
        aim.invoke(tank, (Runnable) () -> tank.yaw++);
        assertEquals(0, tank.energy); assertEquals(1, tank.writes);
        aim.invoke(tank, (Runnable) () -> tank.pitch++);
        assertEquals(1, tank.writes); assertEquals(1, tank.pitch);
        tank.world.tick++; aim.invoke(tank, (Runnable) () -> tank.yaw++);
        assertEquals(1, tank.yaw); assertEquals(1, tank.writes);
        tank.world.tick++; tank.energy=0; tank.mounted=true; tank.active=false;
        aim.invoke(tank,(Runnable)()->tank.yaw++);
        assertEquals(2,tank.yaw,"Unpowered, inactive mounted gun must still aim");
        assertEquals(1,tank.writes,"Mounted gun must never debit FE");
        tank.mounted=false; tank.energy=20;
        aim.invoke(tank,(Runnable)()->tank.yaw++);
        assertEquals(2,tank.yaw,"Inactive remote servo must not move");
        tank.active=true; tank.energy = 20; tank.destroyed = true;
        aim.invoke(tank, (Runnable) () -> tank.yaw++);
        assertEquals(20, tank.energy); assertEquals(2, tank.yaw);
    }
    @Test void actualTickPublishesManualAimToBothRemoteOperatorAndObserverWithoutClientDebit() throws Exception {
        var f = new TickFixture();
        var server = f.tank(false); server.occupied = true; server.energy = 80;
        var operator = f.tank(true); operator.occupied = true;
        var observer = f.tank(true);
        server.tick();
        assertEquals(.8F, server.yaw, 1e-5); assertEquals(72, server.energy);
        for (var client : List.of(operator, observer)) {
            client.entityData.copy(server.entityData); client.tick();
            assertEquals(server.yaw, client.yaw, 1e-5, "Rendered turret must follow server yaw");
            assertEquals(server.pitch, client.pitch, 1e-5, "Rendered barrel must follow server pitch");
            assertEquals(0, client.writes, "Client may not debit native FE");
        }
        server.world.tick++; server.tick();
        operator.entityData.copy(server.entityData); operator.tick();
        assertEquals(server.yaw, operator.yaw, 1e-5);
        assertEquals(1, operator.turnSounds, "Only an observed change emits client turning audio");
    }

    @Test void actualTickRetainsLastPoseWhenPowerRunsOutOrTheTurretBecomesAWreck() throws Exception {
        var f = new TickFixture(); var server = f.tank(false); var client = f.tank(true);
        server.occupied = true; server.energy = 8;
        server.tick(); client.entityData.copy(server.entityData); client.tick();
        float yaw = server.yaw, pitch = server.pitch;
        assertEquals(yaw, client.yaw, 1e-5); assertEquals(0, server.energy);
        server.world.tick++; server.tick(); client.entityData.copy(server.entityData); client.tick();
        assertEquals(yaw, server.yaw); assertEquals(pitch, client.pitch); assertEquals(1, server.writes);
        server.energy = 80; server.destroyed = true; server.world.tick++; server.tick();
        client.entityData.copy(server.entityData); client.tick();
        assertEquals(yaw, client.yaw); assertEquals(80, server.energy); assertEquals(1, server.writes);
        assertEquals(0, client.writes + client.turnSounds);
    }

    @Test void actualTickPublishesIndicatorMotionAndLateTrackingStartsAtTheExistingPose() throws Exception {
        var f = new TickFixture(); var server = f.tank(false); server.energy = 80;
        f.type.getField("indicatorAim").set(server, new Aim());
        server.tick();
        assertEquals(.8F, server.yaw, 1e-5, "Native indicator aiming occurs after the superclass tick");
        var observer = f.tank(true); observer.entityData.copy(server.entityData); observer.tick();
        assertEquals(server.yaw, observer.yaw, 1e-5);
        assertEquals(server.yaw, observer.getTurretYaw(0), 1e-5, "First observation must not sweep from zero");
        assertEquals(1, server.writes); assertEquals(0, observer.writes + observer.turnSounds);
    }

    @Test void clientNativeTransformsInterpolateTheShortArcAcrossAWrapAndRemainStillWithoutNewPose() throws Exception {
        var f = new TickFixture(); var server = f.tank(false); var client = f.tank(true);
        server.yaw = 359; server.pitch = -30; server.tick();
        client.entityData.copy(server.entityData); client.tick();
        server.yaw = 360.5F; server.pitch = -31; server.world.tick++; server.tick();
        client.entityData.copy(server.entityData); client.tick();
        assertEquals(359.75F, client.getTurretYaw(.5F), 1e-5);
        assertEquals(-30.5F, client.getTurretPitch(.5F), 1e-5);
        client.tick();
        assertEquals(.5F, client.getTurretYaw(.5F), 1e-5);
        assertEquals(1, client.turnSounds); assertEquals(0, client.writes);
    }

    /** Only world/crew/native aiming are ports; the complete production tick and pose consumers execute. */
    private static final class TickFixture {
        final Class<?> type;
        TickFixture() throws Exception {
            String source = "com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity";
            String target = "com/liucodex/doggiesnewvehicle/compat/gameplayprobe/M1Tick";
            var node = new ClassNode();
            try (var in = getClass().getResourceAsStream("/" + source + ".class")) {
                new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
            }
            node.interfaces.clear(); node.innerClasses.clear();
            var retained = Set.of("tick", "tickIndicatorAim", "synchronizeTurretPose", "adjustTurretAngle",
                    "aimWithServo", "getTurretYaw", "getTurretPitch");
            node.methods.removeIf(m -> !retained.contains(m.name) && !m.name.startsWith("lambda$adjustTurretAngle$")
                    && !m.name.startsWith("lambda$tick$") && !m.name.startsWith("lambda$tickIndicatorAim$"));
            node.fields.removeIf(field -> !Set.of("servoEnergy", "nativeStateInvalid", "indicatorAim", "indicatorPending",
                    "indicatorSubmitting", "indicatorRequested", "SYNCED_TURRET_POSE", "clientTurretPose").contains(field.name));
            node.fields.forEach(field -> field.access = Opcodes.ACC_PUBLIC | (field.access & Opcodes.ACC_STATIC));
            var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
            for (var field : node.fields) {
                if (!Set.of("servoEnergy", "clientTurretPose").contains(field.name)) continue;
                String owner = Type.getType(field.desc).getInternalName();
                ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                ctor.instructions.add(new TypeInsnNode(Opcodes.NEW, owner)); ctor.instructions.add(new InsnNode(Opcodes.DUP));
                ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false));
                ctor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, source, field.name, field.desc));
            }
            ctor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(ctor);
            var names = new HashMap<String, String>(); names.put(source, target);
            names.put(node.superName, Type.getInternalName(TickPose.class));
            for (var entry : Map.ofEntries(
                    Map.entry("net/minecraft/world/level/Level", World.class),
                    Map.entry("net/minecraft/server/level/ServerLevel", ServerWorld.class),
                    Map.entry("net/minecraft/server/MinecraftServer", ServerWorld.class),
                    Map.entry("net/minecraft/server/players/PlayerList", ServerWorld.class),
                    Map.entry("net/minecraft/server/level/ServerPlayer", Passenger.class),
                    Map.entry("net/minecraft/world/entity/LivingEntity", Passenger.class),
                    Map.entry("net/minecraft/network/syncher/SynchedEntityData", Data.class),
                    Map.entry("net/minecraft/network/syncher/EntityDataAccessor", Key.class),
                    Map.entry("com/atsuishio/superbwarfare/data/gun/GunData", Gun.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/api/combat/DnvNativeCrewTarget", TickPose.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvM1RemoteCrew", Crew.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/entity/DnvM1IndicatorAim", Aim.class)).entrySet())
                names.put(entry.getKey(), Type.getInternalName(entry.getValue()));
            var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer, new Remapper() {
                @Override public String map(String name) { return names.getOrDefault(name, name); }
            }));
            var loader = new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray()));
            type = loader.loadClass(target.replace('/', '.'));
            for (var field : type.getFields()) if (field.getType() == Key.class) field.set(null, new Key());
        }
        TickPose tank(boolean client) throws Exception {
            var tank = (TickPose) type.getConstructor().newInstance();
            tank.world = client ? new World() : new ServerWorld(); tank.world.isClientSide = client;
            return tank;
        }
    }
    public static final class Key { }
    public static final class Data {
        final Map<Key, Object> values = new HashMap<>();
        public Object get(Key key) { return values.getOrDefault(key, new Rotations(0, 0, 0)); }
        public void set(Key key, Object value) { values.put(key, value); }
        void copy(Data source) { values.clear(); values.putAll(source.values); }
    }
    public static class Passenger { }
    public static class Gun { }
    public static class Aim {
        public UUID actor() { return new UUID(0, 1); }
        public Vec3 launch() { return new Vec3(1, 1, 1); }
        public BlockPos target() { return BlockPos.ZERO; }
    }
    public static class Crew { public static boolean valid(TickPose tank) { return !tank.destroyed; } }
    public static class ServerWorld extends World {
        public ServerWorld getServer() { return this; } public ServerWorld getPlayerList() { return this; }
        public Passenger getPlayer(UUID id) { return new Passenger(); }
    }
    public static class TickPose extends Pose {
        public final Data entityData = new Data(); public boolean occupied; public int turnSounds;
        public float oldYaw, oldPitch;
        public Data getEntityData() { return entityData; }
        public void tick() { oldYaw = yaw; oldPitch = pitch; if (occupied) adjustTurretAngle(); }
        public void adjustTurretAngle() { yaw += .8F; pitch -= .6F; }
        public void turretAutoAimFromVector(Vec3 value) { yaw += .8F; pitch -= .6F; }
        public float getTurretYaw(float partial) { return oldYaw + (yaw - oldYaw) * partial; }
        public float getTurretPitch(float partial) { return oldPitch + (pitch - oldPitch) * partial; }
        public void setTurretYRot(float value) { yaw = value; } public void setTurretXRot(float value) { pitch = value; }
        public void setTurretYRotO(float value) { oldYaw = value; } public void setTurretXRotO(float value) { oldPitch = value; }
        public void turretTurnSound(float pitch, float yaw, float speed) { if (Math.abs(pitch) + Math.abs(yaw) > .01F) turnSounds++; }
        public boolean hasRemoteCrew() { return occupied; } public void endRemoteCrew() { occupied = false; }
        public List<Passenger> getPassengers() { return occupied ? List.of(new Passenger()) : List.of(); }
        public boolean indicatorValid(Aim aim, Passenger player) { return true; }
        public boolean indicatorAligned(Aim aim) { return false; }
        public Gun getGunData(String name) { return new Gun(); }
        public void vehicleShoot(Passenger player, String weapon, UUID target, Vec3 point) { }
    }
    public static class World { public long tick; public boolean isClientSide; public long getGameTime() { return tick; } }
    public static class Pose {
        public boolean mounted, active=true;
        public boolean mountedCrew() { return mounted; }
        public boolean getActive() { return active; }
        public World world = new World(); public int energy, writes; public float yaw, pitch; public boolean destroyed;
        public World level() { return world; } public int getEnergy() { return energy; }
        public void consumeEnergy(int amount) { energy -= amount; writes++; }
        public boolean isDestroyed() { return destroyed; }
        public float getTurretYRot() { return yaw; } public float getTurretXRot() { return pitch; }
    }
}
