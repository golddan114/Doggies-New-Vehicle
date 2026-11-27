package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.world.phys.Vec3;
import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.*;

/** Five native gun handles and world/seat ports for executing the compiled tank's adapter. */
public class ApocalypseShotHost extends Vehicle implements DnvNativeRemoteShotGuard, ApocalypseShotAdmission {
    public static class Player extends Entity {}
    public static class Network {
        public static final List<Feedback> feedback = new ArrayList<>();
        public static void sendPacketToAll(Object packet) { if (packet instanceof Feedback accepted) feedback.add(accepted); }
    }
    public record Feedback(UUID shooter, UUID vehicle, int index, String weapon) { }
    public static class BlastEffects {
        public static void rocket(Entity projectile){projectile.blastStyle=1;}
        public static void cannon(Entity projectile){projectile.blastStyle=2;}
    }
    public static class RocketWarhead {
        public static void mark(Entity projectile,float fraction){projectile.armorFraction=fraction;projectile.armorMarked=fraction>0;}
    }
    public enum Block { COLLIDER }
    public enum Fluid { NONE }
    public enum HitType { MISS, BLOCK }
    public record Hit(HitType type) { public HitType getType() { return type; } }
    public record Clip(Vec3 start, Vec3 end, Block block, Fluid fluid, Entity owner) {}
    public static class Targeting {
        final ApocalypseShotHost owner;
        Targeting(ApocalypseShotHost owner) { this.owner = owner; }
        public Entity locked(Player player, UUID id) { return id == null ? null : owner.level.getEntity(id); }
        public static boolean loadedRay(Entity owner, Vec3 from, Vec3 to) { return ((Vehicle) owner).lineClear; }
    }
    public final Map<String, Data> guns = new LinkedHashMap<>();
    public int selected, main, rocket;
    public boolean clearPose = true;
    public final Player player = new Player();
    private final Targeting targeting = new Targeting(this);
    private Object armament;
    private Class<?> armamentClass;

    void initialize(ExactQueuedShotFixture fixture) throws Exception {
        for (String name : List.of("MainL", "MainR", "RocketL", "RocketR", "Missiles")) {
            Data data = new Data(); data.item = gun.item;
            data.shootPos.muzzles = name.startsWith("Main") ? 1 : 4;
            guns.put(name, data);
        }
        passenger = player;
        player.level = level;
        armamentClass = fixture.loader.loadClass((ExactQueuedShotFixture.GENERATED + "ApocalypseArmament").replace('/', '.'));
        armament = armamentClass.getConstructor(ApocalypseShotHost.class).newInstance(this);
    }
    public boolean poseClear() { return clearPose; }
    public int getWeaponIndex(int seat) { return selected; }
    public String getGunName(int seat) {
        return selected == 0 ? main == 0 ? "MainL" : "MainR" : selected == 1 ? rocket == 0 ? "RocketL" : "RocketR" : "Missiles";
    }
    public Data getGunData(int seat) { return guns.get(getGunName(seat)); }
    @Override public Data getGunData(String name) { return guns.get(name); }
    @Override public void modifyGunData(String name, Consumer<Data> consumer) { consumer.accept(guns.get(name)); }
    public void advanceMain() { main = 1 - main; }
    public void advanceRocket() { rocket = 1 - rocket; }
    public Targeting targeting() { return targeting; }
    public Vec3 firingPivot(String name) { return muzzle; }
    public void submitNativeShot(Entity actor, String name, UUID target, Vec3 point) { vehicleShoot(actor, name, target, point); }
    @Override public Runnable guardNativeShot(Runnable work) { return (Runnable) call("guard", new Class<?>[] {Runnable.class}, work); }
    @Override public void onNativeShotQueueResult(boolean accepted) { call("queueResult", new Class<?>[] {boolean.class}, accepted); }
    @Override public boolean admitNativeProjectile(Level world, Entity projectile, Parameters parameters) {
        return (boolean) call("admit", new Class<?>[] {Level.class, Entity.class, Parameters.class}, world, projectile, parameters);
    }
    @Override public boolean nativeProjectileAccepted(Parameters parameters) {
        return (boolean) call("accepted", new Class<?>[] {Parameters.class}, parameters);
    }
    void fire(Entity actor, String name, UUID target) {
        call("fire", new Class<?>[] {Entity.class, String.class, UUID.class}, actor, name, target);
    }
    private Object call(String name, Class<?>[] types, Object... values) {
        try { return armamentClass.getMethod(name, types).invoke(armament, values); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure.getCause() == null ? failure : failure.getCause()); }
    }
}
