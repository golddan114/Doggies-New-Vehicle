package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard;
import com.liucodex.doggiesnewvehicle.compat.ExactQueuedShotFixture.*;
import net.minecraft.world.phys.Vec3;

/** World/crew ports only; the production adapter and exact native queue/admission bodies execute. */
public class Project612ShotHost extends Vehicle implements DnvNativeRemoteShotGuard, ApocalypseShotAdmission {
    public final ApocalypseShotHost.Player player = new ApocalypseShotHost.Player();
    public int selected;
    public boolean wreck;
    private Object armament;
    private Class<?> type;
    void initialize(ExactQueuedShotFixture fixture) throws Exception {
        type = fixture.loader.loadClass((ExactQueuedShotFixture.GENERATED + "Project612Armament").replace('/', '.'));
        armament = type.getConstructor(Project612ShotHost.class).newInstance(this);
        passenger = player;
        player.level = level;
    }
    @Override public boolean isWreck() { return wreck; }
    public int getWeaponIndex(int seat) { return selected; }
    public Vec3 firingPivot() { return muzzle; }
    public void submitNativeShot(Entity actor) { vehicleShoot(actor, "Main", null, null); }
    @Override public boolean deferNativeShotEffects() { return true; }
    @Override public Runnable guardNativeShot(Runnable work) { return (Runnable) call("guard", new Class<?>[] {Runnable.class}, work); }
    @Override public void onNativeShotQueueResult(boolean accepted) { call("queueResult", new Class<?>[] {boolean.class}, accepted); }
    @Override public boolean admitNativeProjectile(Level world, Entity projectile, Parameters p) {
        return (boolean) call("admit", new Class<?>[] {Level.class, Entity.class, Parameters.class}, world, projectile, p);
    }
    @Override public boolean nativeProjectileAccepted(Parameters p) { return (boolean) call("accepted", new Class<?>[] {Parameters.class}, p); }
    void fire(Entity actor, String name) { call("fire", new Class<?>[] {Entity.class, String.class}, actor, name); }
    void invalidate() { call("invalidate", new Class<?>[0]); }
    private Object call(String name, Class<?>[] types, Object... values) {
        try { return type.getMethod(name, types).invoke(armament, values); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error.getCause() == null ? error : error.getCause()); }
    }
}
