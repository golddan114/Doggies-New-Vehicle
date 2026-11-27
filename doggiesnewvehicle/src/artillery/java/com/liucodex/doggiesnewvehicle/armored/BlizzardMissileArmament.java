package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Admits one native GunData shot; ammo and the 400-tick reload are never shadowed by custom counters. */
final class BlizzardMissileArmament {
    private final DnvBlizzardEntity tank;
    private ServerPlayer operator;
    private boolean queued, executing, attempted, accepted;
    private ShootParameters active;
    private DnvBlizzardMissile missile;
    private UUID targetId;
    BlizzardMissileArmament(DnvBlizzardEntity tank) { this.tank = tank; }
    boolean ready(LivingEntity actor) { return !queued && readyNow(actor); }
    private boolean readyNow(LivingEntity actor) {
        GunData gun = tank.getGunData("ClearingMissile");
        return actor instanceof ServerPlayer && actor == tank.getFirstPassenger() && tank.driverReady()
                && tank.getWeaponIndex(0) == 1 && gun != null && gun.get(GunProp.PROJECTILE_AMOUNT) == 1
                && gun.canShoot(tank.getAmmoSupplier());
    }
    void fire(LivingEntity actor, String requestedWeapon, UUID requestedTarget) {
        if (!(tank.level() instanceof ServerLevel) || !ready(actor)
                || requestedWeapon != null && !requestedWeapon.equals("ClearingMissile") || !clear()) return;
        // TV launch needs a clear tube, not an entity lock. Guidance starts only after native acceptance.
        targetId = null;
        operator = (ServerPlayer) actor; queued = true; attempted = false; accepted = false;
        try { tank.submitMissile(actor, targetId); } catch (RuntimeException error) { clearQueue(); throw error; }
    }
    Runnable guard(Runnable work) {
        ServerPlayer player = operator;
        return () -> {
            if (!queued || executing || operator != player) return;
            try {
                if (!readyNow(player) || !clear() || tank.isRemoved()) return;
                int fireIndex = tank.getGunData("ClearingMissile").fireIndex.get();
                executing = true; work.run();
                if (accepted) {
                    var gun = tank.getGunData("ClearingMissile");
                    tank.afterShoot(gun, tank.getShootVec("ClearingMissile", 1));
                    tank.playShootSound3p(player, "ClearingMissile");
                    com.atsuishio.superbwarfare.tools.MinecraftUtil.sendPacketToAll(
                            new com.atsuishio.superbwarfare.network.message.receive.VehicleShootClientMessage(
                                    player.getUUID(), tank.getUUID(), fireIndex, "ClearingMissile"));
                }
                if (accepted && missile != null && !missile.isRemoved()) {
                    tank.advanceMissilePort(); tank.beginTv(player, missile);
                }
            } finally { clearQueue(); }
        };
    }
    boolean admit(ServerLevel level, Entity entity, ShootParameters p) {
        if (!executing || attempted || !(entity instanceof DnvBlizzardMissile guided)
                || level != tank.level() || level.getEntity(tank.getUUID()) != tank || !readyNow(operator)
                || p.shooter != operator || p.data != tank.getGunData("ClearingMissile")
                || p.ammoSupplier != tank.getAmmoSupplier() || !java.util.Objects.equals(p.targetEntityUUID, targetId)
                || !clear()
                || entity.position().distanceToSqr(p.shootPosition) > 1e-10
                || tank.getShootPos("ClearingMissile", 1).distanceToSqr(p.shootPosition) > 1e-10
                || tank.getShootVec("ClearingMissile", 1).distanceToSqr(p.shootDirection) > 1e-10) return false;
        attempted = true; active = p; missile = guided;
        guided.setOwner(operator);
        guided.bind(tank, UUID.randomUUID());
        accepted = level.addFreshEntity(guided);
        return accepted;
    }
    boolean accepted(ShootParameters p) { return p == active && attempted && accepted; }
    void queueResult(boolean enqueued) { if (!enqueued) clearQueue(); }
    private void clearQueue() { queued = executing = false; active = null; operator = null; missile = null; targetId = null; }
    boolean clear() {
        Vec3 pivot = BlizzardRig.point(tank.groupTransform("Turret", 1), BlizzardRig.INSTANCE.yaw);
        Vec3 muzzle = tank.getShootPos("ClearingMissile", 1);
        Vec3 beyond = muzzle.add(tank.getShootVec("ClearingMissile", 1).scale(.3));
        return BlizzardLoadedPath.loaded(tank, pivot, beyond) && tank.level().clip(new ClipContext(pivot, beyond,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank)).getType() == HitResult.Type.MISS;
    }
}
