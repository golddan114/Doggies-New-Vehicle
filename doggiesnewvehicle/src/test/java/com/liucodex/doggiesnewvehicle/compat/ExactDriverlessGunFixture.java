package com.liucodex.doggiesnewvehicle.compat;

import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Exact SBW shoot/canShoot/before/after/reloadAmmo bodies; inventory, event and bullet backends are ports. */
final class ExactDriverlessGunFixture {
    private static final String SBW = "com/atsuishio/superbwarfare/";
    private static final String PROBE = "com/liucodex/doggiesnewvehicle/compat/driverlessprobe/";
    final Data data;
    final Vehicle tank = new Vehicle();
    final Parameters parameters;
    final Handler handler;

    ExactDriverlessGunFixture() throws Exception {
        ClassNode item = node("item/gun/GunItem"), vehicleItem = node("item/gun/vehicle/VehicleGunItem");
        item.methods.removeIf(m -> !(Set.of("shoot", "beforeShoot", "afterShoot").contains(m.name)
                && m.desc.equals("(L" + SBW + "data/gun/ShootParameters;)V")));
        vehicleItem.methods.stream().filter(m -> m.name.equals("canShoot")).forEach(item.methods::add);
        ClassNode gunData = node("data/gun/GunData");
        gunData.methods.removeIf(m -> !(m.name.equals("shoot") && m.desc.equals("(L" + SBW + "data/gun/ShootParameters;)V"))
                && !(m.name.equals("reloadAmmo") && m.desc.equals("(Lnet/minecraft/world/entity/Entity;Z)V")));
        ClassNode handlerNode = node("event/GunEventHandler");
        handlerNode.methods.removeIf(m -> !m.name.equals("autoReload"));
        var loader = new ExactQueuedShotFixture.Loader(Map.of(
                PROBE + "Gun", isolate(item, "Gun", Gun.class), PROBE + "Data", isolate(gunData, "Data", Data.class),
                PROBE + "Handler", isolate(handlerNode, "Handler", Handler.class)));
        Gun gun = (Gun) loader.loadClass((PROBE + "Gun").replace('/', '.')).getConstructor().newInstance();
        data = (Data) loader.loadClass((PROBE + "Data").replace('/', '.')).getConstructor().newInstance();
        data.item = gun;
        parameters = new Parameters(data, tank);
        handler = (Handler) loader.loadClass((PROBE + "Handler").replace('/', '.')).getConstructor().newInstance();
        Events.before = () -> { };
    }

    private static ClassNode node(String path) throws Exception {
        try (var zip = new ZipFile(SbwTestDependency.path().toFile())) {
            ClassNode n = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry(SBW + path + ".class")).readAllBytes()).accept(n, ClassReader.SKIP_FRAMES);
            return n;
        }
    }

    private static byte[] isolate(ClassNode n, String name, Class<?> parent) {
        n.name = PROBE + name; n.superName = Type.getInternalName(parent);
        n.access = Opcodes.ACC_PUBLIC; n.fields.clear(); n.innerClasses.clear(); n.nestMembers = null;
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, n.superName, "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN)); n.methods.add(init);
        Map<String, String> names = new HashMap<>();
        Map.ofEntries(Map.entry("data/gun/GunData", Data.class), Map.entry("item/gun/GunItem", Gun.class),
                Map.entry("item/gun/vehicle/VehicleGunItem", Gun.class), Map.entry("data/gun/GunProp", Prop.class),
                Map.entry("data/gun/ShootParameters", Parameters.class), Map.entry("data/gun/FireModeInfo", ModeInfo.class),
                Map.entry("data/gun/FireMode", Mode.class), Map.entry("data/gun/ShootPos", Positions.class),
                Map.entry("data/gun/value/IntValue", IntValue.class), Map.entry("data/gun/value/DoubleValue", DoubleValue.class),
                Map.entry("data/gun/value/BooleanValue", BoolValue.class), Map.entry("data/gun/subdata/Bolt", Bolt.class),
                Map.entry("data/gun/subdata/Reload", Reload.class), Map.entry("data/gun/value/ReloadState", ReloadState.class),
                Map.entry("data/gun/NbtVersion", NbtVersion.class), Map.entry("entity/vehicle/base/VehicleEntity", Vehicle.class),
                Map.entry("tools/MinecraftUtil", Events.class), Map.entry("api/event/ShootEvent$Pre", Pre.class),
                Map.entry("event/GunEventHandler", Handler.class),
                Map.entry("api/event/ShootEvent$Post", Post.class))
                .forEach((key, value) -> names.put(SBW + key, Type.getInternalName(value)));
        names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Body.class));
        names.put("net/minecraft/world/entity/LivingEntity", Type.getInternalName(Living.class));
        names.put("net/minecraft/world/entity/player/Player", Type.getInternalName(Player.class));
        names.put("net/minecraft/server/level/ServerPlayer", Type.getInternalName(Player.class));
        names.put("net/minecraft/world/item/ItemStack", Type.getInternalName(Stack.class));
        names.put("net/minecraftforge/eventbus/api/Event", Type.getInternalName(Event.class));
        names.put("kotlin/jvm/internal/Intrinsics", Type.getInternalName(Intrinsics.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        n.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String original) { return names.getOrDefault(original, original); }
        }));
        return writer.toByteArray();
    }

    public static class Body { }
    public static class Intrinsics {
        public static void checkNotNullParameter(Object value, String name) { Objects.requireNonNull(value, name); }
        public static void checkNotNull(Object value) { Objects.requireNonNull(value); }
    }
    public static class Living extends Body { }
    public static class Player extends Living { }
    public static class Vehicle extends Body {
        public int reserve;
        public int getAmmo(Data data) { return data.ammo.get(); }
    }
    public static class Parameters {
        public final Data data;
        public final net.minecraft.server.level.ServerLevel level = null;
        public final Body shooter, ammoSupplier;
        public final boolean zoom = true;
        Parameters(Data data, Vehicle tank) { this.data = data; shooter = ammoSupplier = tank; }
    }
    public enum Prop { PROJECTILE_AMOUNT, HEAT_PER_SHOOT, AMMO_COST_PER_SHOOT, BOLT_ACTION_TIME, MAGAZINE,
        BURST_AMOUNT, SHOOT_ANIMATION_TIME, DURABILITY_PER_SHOOT, RECOIL, SHOOT_POS, AUTO_RELOAD }
    public enum Mode { AUTO, BURST }
    public static class ModeInfo { public Mode mode = Mode.AUTO; }
    public static class Positions {
        public ArrayList<Vec3> getPositions() { return new ArrayList<>(Collections.nCopies(4, Vec3.ZERO)); }
        public boolean getBoundUpWithAmmoAmount() { return false; }
    }
    public static class IntValue {
        int value;
        public int get() { return value; }
        public void set(int v) { value = v; }
        public void reset() { value = 0; }
    }
    public static class DoubleValue {
        double value;
        public double get() { return value; }
        public void set(double v) { value = v; }
    }
    public static class BoolValue {
        boolean value;
        public Boolean get() { return value; }
        public void set(boolean v) { value = v; }
    }
    public static class Bolt { public BoolValue needed = new BoolValue(); }
    public enum ReloadState { NOT_RELOADING, EMPTY_RELOADING }
    public static class Reload {
        ReloadState state = ReloadState.NOT_RELOADING;
        public void setState(ReloadState v) { state = v; }
    }
    public static class NbtVersion { public void invalidateStructural() { } }
    public static class Stack { }
    public static class Event { }
    public static class Pre extends Event { public Pre(Parameters p) { } }
    public static class Post extends Event { public Post(Parameters p) { } }
    public static class Events {
        static Runnable before;
        public static boolean postEvent(Event event) { if (event instanceof Pre) before.run(); return false; }
    }
    public static class Handler {
        public int reloadRequests;
        public void tryStartReload(Body source, Data data, boolean manual) { if (manual) throw new AssertionError();reloadRequests++; }
        public void autoReload(Body source, Data data, boolean active) { throw new AssertionError("exact native autoReload required"); }
    }
    public static class Data {
        public Gun item;
        public IntValue ammo = new IntValue(), burstAmount = new IntValue(), shootAnimationTimer = new IntValue(),
                shootTimer = new IntValue(), fireIndex = new IntValue(), virtualAmmo = new IntValue();
        public DoubleValue heat = new DoubleValue();
        public BoolValue overHeat = new BoolValue(), hideBulletChain = new BoolValue(), closeStrike = new BoolValue();
        public Bolt bolt = new Bolt();
        public Reload reload = new Reload();
        public NbtVersion nbtVersion = new NbtVersion();
        public int saves, withdrawals;
        public Object get(Prop p) {
            return switch (p) {
                case PROJECTILE_AMOUNT, AMMO_COST_PER_SHOOT -> 1;
                case HEAT_PER_SHOOT -> 2.0;
                case BOLT_ACTION_TIME, DURABILITY_PER_SHOOT -> 0;
                case RECOIL -> 0.0;
                case MAGAZINE -> 4;
                case BURST_AMOUNT -> 3;
                case SHOOT_ANIMATION_TIME -> 14;
                case SHOOT_POS -> new Positions();
                case AUTO_RELOAD -> true;
            };
        }
        public boolean reloading() { return reload.state != ReloadState.NOT_RELOADING; }
        public boolean charging() { return false; }
        public boolean canShoot(Body source) { return item.canShoot(this, source); }
        public boolean hasEnoughAmmoToShoot(Body source) { return ammo.get() > 0; }
        public int currentAvailableShots(Body source) { return ammo.get(); }
        public boolean useBackpackAmmo() { return false; }
        public int countBackupAmmo(Body source) { return virtualAmmo.get() + ((Vehicle) source).reserve; }
        public void consumeBackupAmmo(Body source, int amount) {
            int virtual = Math.min(virtualAmmo.get(), amount);virtualAmmo.set(virtualAmmo.get() - virtual);
            ((Vehicle) source).reserve -= amount - virtual; withdrawals += amount;
        }
        public Stack stack() { return new Stack(); }
        public void clearTempModifications() { }
        public void save() { saves++; }
        public static ModeInfo selectedFireModeInfo$default(Data data, List<?> ignored, int mask, Object marker) { return new ModeInfo(); }
        public void shoot(Parameters p) { throw new AssertionError("exact GunData.shoot required"); }
        public void reloadAmmo(Body source, boolean extra) { throw new AssertionError("exact GunData.reloadAmmo required"); }
    }
    public static class Gun {
        public boolean admit = true;
        public int bullets, sounds;
        public boolean shootBullet(Parameters p) { bullets++; return admit; }
        public void shoot(Parameters p) { throw new AssertionError("exact GunItem.shoot required"); }
        public boolean canShoot(Data data, Body source) { throw new AssertionError("exact VehicleGunItem.canShoot required"); }
        public void beforeShoot(Parameters p) { throw new AssertionError("exact beforeShoot required"); }
        public void afterShoot(Parameters p) { throw new AssertionError("exact afterShoot required"); }
        public int hideBulletChainBelowShots() { return -1; }
        public int getMaxDamage(Stack stack) { return 0; }
        public boolean enableShootTimer() { return true; }
        // VehicleGunItem.playFireSounds is intentionally empty; vehicle outer callback owns sound.
        public void playFireSounds(Data data, Body source, boolean zoom) { sounds++; }
    }
}
