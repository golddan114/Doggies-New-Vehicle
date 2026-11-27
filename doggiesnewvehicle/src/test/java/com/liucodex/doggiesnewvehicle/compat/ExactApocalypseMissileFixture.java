package com.liucodex.doggiesnewvehicle.compat;

import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Exact native Javelin tick/turn math and compiled launch adapters; world movement and entity data are doubles. */
public final class ExactApocalypseMissileFixture extends ClassLoader {
    private static final String SBW = "com/atsuishio/superbwarfare/";
    private static final String JAV = SBW + "entity/projectile/JavelinMissileEntity";
    private static final String TANK = "com/liucodex/doggiesnewvehicle/armored/DnvApocalypseMissile";
    private static final String VLS = "com/liucodex/doggiesnewvehicle/boss/DnvBossVlsGuidance";
    private static final String FLIGHT = "com/liucodex/doggiesnewvehicle/compat/DnvVlsFlight";
    private static final String LIFETIME = "com/liucodex/doggiesnewvehicle/compat/DnvTurretShells";
    private static final String DEADLINE = "com/liucodex/doggiesnewvehicle/api/combat/DnvProjectileLifetime";
    private static final String POINT = "com/liucodex/doggiesnewvehicle/compat/DnvTargetPoint";
    private static final String TURN = SBW + "entity/projectile/ITrackableProjectile$DefaultImpls";
    private static final String PREFIX = "com/liucodex/doggiesnewvehicle/compat/missileprobe/";
    private final Map<String, String> names = new HashMap<>();
    private final Class<?> type;
    private final Class<?> nativeType, vls, flight;
    final Body missile;
    final Body target = new Body();
    private final boolean legacy;
    private static byte[] adaptedJavelin;

    ExactApocalypseMissileFixture() throws Exception { this(false); }
    ExactApocalypseMissileFixture(boolean boss) throws Exception { this(boss, false); }
    ExactApocalypseMissileFixture(boolean boss, boolean legacy) throws Exception {
        this.legacy = legacy;
        names.put(JAV, PREFIX + "Javelin"); names.put(TANK, PREFIX + "TankMissile");
        names.put(VLS, PREFIX + "BossVls"); names.put(TURN, PREFIX + "NativeTurn");
        names.put(FLIGHT, PREFIX + "Flight"); names.put(POINT, PREFIX + "Point");
        names.put(LIFETIME, PREFIX + "Lifetime");
        names.put(DEADLINE, PREFIX + "Deadline");
        Map.ofEntries(
            Map.entry(SBW + "entity/projectile/MissileProjectile", Body.class),
            Map.entry(SBW + "entity/projectile/FastThrowableProjectile", Body.class),
            Map.entry(SBW + "entity/vehicle/base/VehicleEntity", Vehicle.class),
            Map.entry(SBW + "tools/EntityFindUtil", Find.class), Map.entry(SBW + "tools/VectorTool", Angles.class),
            Map.entry(SBW + "tools/VectorToolKt", Angles.class),
            Map.entry(SBW + "entity/vehicle/utils/VehicleVecUtils", Angles.class),
            Map.entry(SBW + "entity/projectile/ITrackableProjectile", Trackable.class),
            Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseTargeting", Targeting.class),
            Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAlliedTargets", AlliedTargets.class),
            Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity", AlliedTank.class),
            Map.entry("net/minecraft/world/entity/Entity", Body.class),
            Map.entry("net/minecraft/world/entity/projectile/ThrowableItemProjectile", Body.class),
            Map.entry("net/minecraft/world/entity/animal/Pig", Pig.class),
            Map.entry("net/minecraft/world/entity/boss/enderdragon/EnderDragon", Dragon.class),
            Map.entry("net/minecraft/world/level/Level", World.class),
            Map.entry("net/minecraft/server/level/ServerLevel", Server.class),
            Map.entry("net/minecraft/network/syncher/EntityDataAccessor", Key.class),
            Map.entry("net/minecraft/network/syncher/SynchedEntityData", Sync.class),
            Map.entry("net/minecraft/nbt/CompoundTag", Tag.class),
            Map.entry("net/minecraft/world/level/ClipContext", Clip.class),
            Map.entry("net/minecraft/world/level/ClipContext$Block", ClipBlock.class),
            Map.entry("net/minecraft/world/level/ClipContext$Fluid", ClipFluid.class),
            Map.entry("net/minecraft/world/phys/BlockHitResult", Hit.class),
            Map.entry("net/minecraft/world/phys/HitResult$Type", HitKind.class))
            .forEach((key, value) -> names.put(key, Type.getInternalName(value)));
        names.put("net/minecraft/core/particles/ParticleOptions", ParticleOptions.class.getName().replace('.', '/'));
        names.put("net/minecraft/core/particles/SimpleParticleType", Particle.class.getName().replace('.', '/'));
        names.put("net/minecraft/core/particles/ParticleTypes", Particles.class.getName().replace('.', '/'));
        compile(DEADLINE, Set.of("track"));
        compile(LIFETIME, Set.of("managed", "beforeTick", "track", "deadline"));
        nativeType = compile(JAV, Set.of("m_8119_"));
        compile(POINT, Set.of("center"));
        flight=compile(FLIGHT, Set.of("active", "tick", "armEjection", "discardIfTargetLost", "loadedTarget", "beforeNativeTick"));
        Class<?> turn = compile(TURN, Set.of("turn", "turnYaw"));
        vls = compile(VLS, Set.of("arm"));
        type = boss ? nativeType : compile(TANK, Set.of("tick", "turn", "turnYaw", "turnLimit",
                "forceLoadChunk", "getFlareHiddenTicks", "markAllied"));
        missile = (Body) type.getConstructor().newInstance();
        missile.nativeTurn = turn.getMethod("turn", Trackable.class, Vec3.class, float.class);
        missile.nativeTurnYaw = turn.getMethod("turnYaw", Trackable.class, Vec3.class, float.class);
        missile.world.target = target;
        target.position = new Vec3(0, 0, 250);
        target.velocity = Vec3.ZERO;
        missile.targetId = target.id.toString();
    }
    void tick() throws Exception { type.getMethod("tick").invoke(missile); }
    void nativeTick() throws Exception { nativeType.getMethod("m_8119_").invoke(missile); }
    boolean beforeTick() throws Exception { return (boolean)flight.getMethod("beforeNativeTick",nativeType).invoke(null,missile); }
    void arm(double hullTop) throws Exception {
        vls.getMethod("arm", nativeType, Body.class, double.class, float.class)
                .invoke(null, missile, target, hullTop, 2F);
    }
    boolean forceLoad() throws Exception { return (boolean) type.getMethod("forceLoadChunk").invoke(missile); }
    void allied(AlliedTank source) throws Exception {
        missile.world.source = source;
        var mark = type.getDeclaredMethod("markAllied", AlliedTank.class); mark.setAccessible(true); mark.invoke(missile, source);
    }

    private Class<?> compile(String original, Set<String> methods) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = original.equals(JAV) && !legacy ? new java.io.ByteArrayInputStream(adaptedJavelin())
                : getParent().getResourceAsStream(original.equals(TANK) && legacy
                ? "fixtures/field-feedback/ApocalypseMissile-before.class" : original + ".class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node, ClassReader.SKIP_FRAMES);
        }
        node.methods.removeIf(m -> !methods.contains(m.name) && !m.name.contains("dnv$"));
        if (original.equals(LIFETIME)) node.methods.removeIf(m -> m.name.equals("track")
                && m.desc.contains("/ProjectileEntity;"));
        node.interfaces.clear(); node.innerClasses.clear(); node.nestMembers = null; node.visibleAnnotations = null;
        if (original.equals(JAV)) node.fields.clear();
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) {
                String body = Type.getInternalName(Body.class);
                if ((a.startsWith(PREFIX) || a.equals(body)) && (b.startsWith(PREFIX) || b.equals(body))) return body;
                return super.getCommonSuperClass(a, b);
            }
        };
        node.accept(new ClassRemapper(out, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                if (name.equals("tickCount")) return "f_19797_";
                if (owner.equals("net/minecraft/world/phys/Vec3")) return switch (name) {
                    case "f_82479_" -> "x"; case "f_82480_" -> "y"; case "f_82481_" -> "z"; default -> name;
                };
                return name;
            }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                if (name.equals("m_20185_")) return "getX";
                if (name.equals("m_20186_")) return "getY";
                if (name.equals("m_20189_")) return "getZ";
                if (name.equals("m_20197_")) return "getPassengers";
                if (name.equals("m_20270_")) return "distanceTo";
                if (name.equals("m_146908_")) return "getYRot";
                if (name.equals("m_146909_")) return "getXRot";
                if (owner.equals("net/minecraft/util/Mth")) return switch (name) {
                    case "m_14136_" -> "atan2"; case "m_14177_" -> "wrapDegrees"; default -> name;
                };
                if (owner.equals("net/minecraft/world/phys/Vec3")) return switch (name) {
                    case "m_82505_" -> "vectorTo"; case "m_165925_" -> "horizontalDistanceSqr";
                    case "m_165924_" -> "horizontalDistance"; case "m_82549_" -> "add"; case "m_82520_" -> "add";
                    case "m_82541_" -> "normalize"; case "m_82490_" -> "scale"; case "m_82542_" -> "multiply"; default -> name;
                };
                return name;
            }
        }));
        byte[] bytes = out.toByteArray();
        return defineClass(names.get(original).replace('/', '.'), bytes, 0, bytes.length);
    }

    private static synchronized byte[] adaptedJavelin() throws Exception {
        if (adaptedJavelin == null) {
            var probe = new DnvMuzzleMixinApplicationTest();
            probe.temp = java.nio.file.Files.createTempDirectory("dnv-vls-native-");
            try (var loader = probe.loader()) {
                adaptedJavelin = DnvMuzzleMixinApplicationTest.transform(loader,
                        "entity/projectile/JavelinMissileEntity", "DnvVlsFlightMixin", "SERVER", false)[1];
            }
        }
        return adaptedJavelin;
    }

    public static class Key {}
    public static class Sync {
        public int age;
        public Object get(Key key) { return age; }
        public void set(Key key, Object value) { age = (Integer) value; }
    }
    public static class Tag {
        private final Map<String, Object> values = new HashMap<>();
        public int getInt(String name) { return values.get(name) instanceof Number value ? value.intValue() : 0; }
        public void putInt(String name, int value) { values.put(name, value); }
        public long getLong(String name) { return values.get(name) instanceof Number value ? value.longValue() : 0; }
        public void putLong(String name, long value) { values.put(name, value); }
        public boolean contains(String name) { return values.containsKey(name); }
        public double getDouble(String name) { return values.get(name) instanceof Number value ? value.doubleValue() : 0; }
        public void putDouble(String name, double value) { values.put(name, value); }
        public float getFloat(String name) { return values.get(name) instanceof Number value ? value.floatValue() : 0; }
        public void putFloat(String name, float value) { values.put(name, value); }
        public boolean hasUUID(String name) { return values.get(name) instanceof UUID; }
        public UUID getUUID(String name) { return (UUID) values.get(name); }
        public void putUUID(String name, UUID value) { values.put(name, value); }
        public boolean getBoolean(String name) { return values.get(name) instanceof Number value && value.intValue() != 0; }
        public void putBoolean(String name, boolean value) { values.put(name, value ? 1 : 0); }
        public void remove(String name) { values.remove(name); }
    }
    public interface ParticleOptions {}
    public static class Particle implements ParticleOptions {}
    public static class Particles { public static final Particle EXPLOSION = new Particle(); }
    public static class World {
        public Body target;
        public boolean isClientSide;
        public long getGameTime() { return 0; }
    }
    public static class Server extends World {
        public AlliedTank source;
        public long now;
        public boolean loaded=true, blocked;
        public long getGameTime() { return now; }
        public boolean hasChunksAt(net.minecraft.core.BlockPos from,net.minecraft.core.BlockPos to) { return loaded; }
        public Hit clip(Clip context) { return new Hit(blocked ? HitKind.BLOCK : HitKind.MISS); }
        public int cosmeticExplosions;
        public Body getEntity(UUID id) {
            return source != null && source.id.equals(id) ? source : target != null && target.id.equals(id) ? target : null;
        }
        public int sendParticles(ParticleOptions particle, double x, double y, double z, int count,
                double dx, double dy, double dz, double speed) {
            if (particle == Particles.EXPLOSION) cosmeticExplosions += count;
            return count;
        }
    }
    public record Clip(Vec3 from,Vec3 to,ClipBlock block,ClipFluid fluid,Body entity) { }
    public enum ClipBlock { COLLIDER }
    public enum ClipFluid { NONE }
    public enum HitKind { MISS, BLOCK }
    public record Hit(HitKind kind) { public HitKind getType() { return kind; } }
    public static class Find {
        public static Body findEntity(World world, String id) {
            return world.target != null && world.target.id.toString().equals(id) ? world.target : null;
        }
    }
    public static class Targeting {
        public static boolean eligible(Body source, Body owner, Body target) { return target != null && target.eligible; }
    }
    public static class AlliedTank extends Vehicle {
        public boolean ready = true;
        public boolean autonomousReady() { return ready && !removed; }
    }
    public static class AlliedTargets {
        public static boolean eligible(Body source, Body target) { return target != null && target.eligible; }
    }
    public static class Angles {
        public static double calculateAngle(Vec3 a, Vec3 b) { return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.normalize().dot(b.normalize()))))); }
        public static double angleTo(Vec3 a, Vec3 b) { return calculateAngle(a, b); }
    }
    public static class Vehicle extends Body {}
    public static class Pig extends Body {}
    public static class Dragon extends Body {}
    public interface Trackable {}
    public static class Body implements Trackable {
        public final UUID id = UUID.randomUUID();
        public final Sync entityData = new Sync();
        public final Tag data = new Tag();
        public java.lang.reflect.Method nativeTurn, nativeTurnYaw;
        public Server world = new Server();
        public Vec3 position = Vec3.ZERO, look = new Vec3(0, 1, 1).normalize(), velocity = look.scale(3);
        public String targetId = "none";
        public boolean eligible = true, top, distracted, removed;
        public double xo, yo, zo;
        public int f_19797_, turns;
        public int explosions;
        public Vec3 explosionPoint;
        public void causeExplode(Vec3 point) { explosions++; explosionPoint=point; }
        public float lastTurnSpeed;
        public float yaw, pitch = -45;
        public int guideType;
        public Vec3 targetPosition;
        public UUID getUUID() { return id; }
        public String getStringUUID() { return id.toString(); }
        public double getY() { return position.y; }
        public double getX() { return position.x; }
        public double getZ() { return position.z; }
        public Vec3 getDeltaMovement() { return velocity; }
        public void setDeltaMovement(Vec3 value) { velocity = value; }
        public boolean isDistracted() { return distracted; }
        public int getLife() { return 400; }
        public int getSyncedTick() { return f_19797_; }
        public boolean isRemoved() { return removed; }
        public boolean isAlive() { return !removed; }
        public Tag getPersistentData() { return data; }
        public Body getRootVehicle() { return this; }
        public Body getOwner() { return null; }
        public String getTargetUUID() { return targetId; }
        public void setTargetUUID(String value) { targetId = value; }
        public void setTargetUuid(String value) { targetId = value; }
        public void setGuideType(int value) { guideType = value; }
        public void setTargetVec(Vec3 value) { targetPosition = value; }
        public void setAttackMode(boolean value) { top = value; }
        public void discard() { removed = true; }
        public void setDeltaMovement(double x, double y, double z) { velocity = new Vec3(x, y, z); }
        public void setYRot(float value) { yaw = value; updateLook(); }
        public void setXRot(float value) { pitch = value; updateLook(); }
        public float getYRot() { return yaw; }
        public float getXRot() { return pitch; }
        public void m_146922_(float value) { setYRot(value); }
        public void m_146926_(float value) { setXRot(value); }
        private void updateLook() {
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            look = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        }
        public World level() { return world; }
        public World m_9236_() { return world; }
        public Vec3 position() { return position; }
        public Vec3 getLookAngle() { return look; }
        public AABB getBoundingBox() { return new AABB(position.x - .5, position.y, position.z - .5, position.x + .5, position.y + 1, position.z + .5); }
        public Vec3 m_20182_() { return position; }
        public Vec3 m_146892_() { return position; }
        public Vec3 m_20154_() { return look; }
        public Vec3 m_20184_() { return velocity; }
        public void m_20256_(Vec3 value) { velocity = value; }
        public float m_20206_() { return 1; }
        public float distanceTo(Body other) { return (float) position.distanceTo(other.position); }
        public List<Body> getPassengers() { return List.of(); }
        public int getGuideType() { return guideType; }
        public Vec3 getTargetPos() { return targetPosition; }
        public boolean isTop() { return top; }
        public void setTop(boolean value) { top = value; }
        public void mediumTrail() {}
        public void m_8119_() { f_19797_++; world.now++; position = position.add(velocity); }
        public void turn(Vec3 direction, float speed) { applyTurn(nativeTurn, direction, speed); }
        public void turnYaw(Vec3 direction, float speed) { applyTurn(nativeTurnYaw, direction, speed); }
        private void applyTurn(java.lang.reflect.Method method, Vec3 direction, float speed) {
            turns++; lastTurnSpeed = speed;
            try { method.invoke(null, this, direction, speed); }
            catch (Exception e) { throw new AssertionError(e); }
        }
        public void m_7380_(Tag tag) {}
        public void m_7378_(Tag tag) {}
    }
}
