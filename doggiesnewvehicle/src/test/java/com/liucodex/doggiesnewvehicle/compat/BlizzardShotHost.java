package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.*;
import com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;

/** Explicit seat/world ports for compiled Blizzard admission inside the exact native shot queue. */
public class BlizzardShotHost extends Vehicle implements DnvNativeRemoteShotGuard, ApocalypseShotAdmission {
    public final ApocalypseShotHost.Player player = new ApocalypseShotHost.Player();
    public int selected = 1, port, begins;
    public boolean powered = true, targetValid = true;
    public final UUID targetId = UUID.randomUUID();
    private Object armament;
    private Class<?> type;
    public Missile current; public int tvBegins;
    public static class Missile extends Entity {
        public Entity owner; public UUID target;
        public void setOwner(Entity actor) { owner=actor; }
        public void bind(BlizzardShotHost tank, UUID nonce) { target=nonce; tank.current=this; }
    }
    public static class Loaded {
        public static boolean loaded(Entity owner, Vec3 from, Vec3 to) { return ((Vehicle) owner).lineClear; }
    }
    void initialize(ExactQueuedShotFixture fixture) throws Exception {
        passenger=player; player.level=level;
        gun.projectileType=gun.configuredProjectile="doggievehicle:blizzard_missile";
        gun.shootPos.muzzles=2;
        type=fixture.loader.loadClass((ExactQueuedShotFixture.GENERATED+"BlizzardArmament").replace('/','.'));
        armament=type.getConstructor(BlizzardShotHost.class).newInstance(this);
    }
    public boolean driverReady() { return powered && passenger==player && !removed; }
    public int getWeaponIndex(int seat) { return selected; }
    public Matrix4d groupTransform(String group,float partial) { return new Matrix4d(); }
    @Override public Vec3 getShootPos(String name,float partial) { return muzzle.add(0,0,port*.4); }
    public void submitMissile(Entity actor, UUID id) { vehicleShoot(actor,"ClearingMissile",id,null); }
    public Entity lockedTarget(ApocalypseShotHost.Player actor, UUID id) { return targetValid && actor == player && targetId.equals(id) ? player : null; }
    public void beginTv(ApocalypseShotHost.Player operator, Missile missile) { tvBegins++; }
    public void advanceMissilePort() { begins++; port=1-port; }
    public void fire(Entity actor,String name) { fire(actor,name,targetId); }
    public void fire(Entity actor,String name,UUID target) { invoke("fire",new Class<?>[]{Entity.class,String.class,UUID.class},actor,name,target); }
    @Override public Runnable guardNativeShot(Runnable work) { return (Runnable)invoke("guard",new Class<?>[]{Runnable.class},work); }
    @Override public void onNativeShotQueueResult(boolean enqueued) { invoke("queueResult",new Class<?>[]{boolean.class},enqueued); }
    @Override public boolean admitNativeProjectile(Level world,Entity missile,Parameters parameters) { return (boolean)invoke("admit",new Class<?>[]{Level.class,Entity.class,Parameters.class},world,missile,parameters); }
    @Override public boolean nativeProjectileAccepted(Parameters parameters) { return (boolean)invoke("accepted",new Class<?>[]{Parameters.class},parameters); }
    private Object invoke(String method,Class<?>[] signature,Object...args) {
        try { return type.getMethod(method,signature).invoke(armament,args); }
        catch(ReflectiveOperationException error) {throw new AssertionError(error.getCause()==null?error:error.getCause());}
    }
}
