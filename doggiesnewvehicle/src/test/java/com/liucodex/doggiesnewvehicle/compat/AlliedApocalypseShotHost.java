package com.liucodex.doggiesnewvehicle.compat;

import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.*;

/** AI permissions and aim ports; the production autonomous weapons and mixed admission tail run unchanged. */
public class AlliedApocalypseShotHost extends ApocalypseShotHost {
    private Class<?> type;
    private Object weapons;
    public Entity enemy;
    public boolean active = true, ready = true, muzzleClear = true;
    public Boolean laneClear = true;
    public int immediateLaneChecks;
    public int preference;
    public Vec3 priorDirection;
    @Override public Vec3 getShootVec(String name, float partial) {
        return partial == 0 && priorDirection != null ? priorDirection : super.getShootVec(name, partial);
    }
    public void setPreferredAmmo(int value) { preference = value; }
    void initializeAi(ExactQueuedShotFixture fixture) throws Exception {
        passenger = null; enemy = fixture.target;
        enemy.position = new Vec3(40, 3, 4); enemy.velocity = Vec3.ZERO;
        for (var gun : guns.values()) { gun.gravity = 0; gun.ammo.set(4); }
        type = fixture.loader.loadClass((ExactQueuedShotFixture.GENERATED + "AutonomousWeapons").replace('/', '.'));
        weapons = type.getConstructor(AlliedApocalypseShotHost.class).newInstance(this);
    }
    public boolean autonomousReady() { return ready && !removed && level.getEntity(id) == this; }
    public boolean activeEnemy(Entity e) { return active && autonomousReady() && e != null && e == enemy && level.getEntity(e.id) == e && !e.removed; }
    public Vec3 lockOrigin() { return muzzle; }
    public org.joml.Matrix4d getVehicleTransform(float partial) { return new org.joml.Matrix4d(); }
    public boolean muzzleClear(String name, Vec3 point) { return muzzleClear && lineClear; }
    public String getGunName(int seat, int group) { return group == 0 ? main == 0 ? "MainL" : "MainR" : group == 1 ? rocket == 0 ? "RocketL" : "RocketR" : "Missiles"; }
    public void playShootSound3p(Entity source, Data gun, Vec3 at) { if(source != null) throw new AssertionError(); sound++; }
    @Override public boolean admitNativeProjectile(Level level, Entity projectile, Parameters parameters) {
        return (boolean)callAi("admit",new Class<?>[]{Level.class,Entity.class,Parameters.class},level,projectile,parameters);
    }
    @Override public boolean nativeProjectileAccepted(Parameters parameters) {
        return (boolean)callAi("accepted",new Class<?>[]{Parameters.class},parameters);
    }
    boolean shoot(int group) { return (boolean)callAi("fire",new Class<?>[]{int.class,Entity.class},group,enemy); }
    void lock() { callAi("tickLock",new Class<?>[]{Entity.class,boolean.class},enemy,true); }
    Object callAi(String name,Class<?>[] parameters,Object...args) {
        try { return type.getMethod(name,parameters).invoke(weapons,args); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e.getCause()==null?e:e.getCause()); }
    }
    public static class Smoke { public static final Predicate<Entity> NOT_IN_SMOKE = e -> true; }
    public static class FriendlyLane {
        private final AlliedApocalypseShotHost tank;
        public FriendlyLane(AlliedApocalypseShotHost tank) { this.tank=tank; }
        public Boolean inspect(Vec3 from,Vec3 end,boolean immediate) {
            if(immediate)tank.immediateLaneChecks++;
            return tank.laneClear;
        }
        public void clear() { }
    }
    public static class Missile extends Entity { public void markAllied(AlliedApocalypseShotHost source){} }
}
