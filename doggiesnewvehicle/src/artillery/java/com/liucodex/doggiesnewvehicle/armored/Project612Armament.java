package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One guarded native AP shot. GunData alone owns ammunition, reload timing and persistence. */
public final class Project612Armament {
    private final DnvProject612Entity tank;
    private ServerPlayer operator;
    private GunData selected;
    private boolean queued, executing, attempted, accepted;
    private ShootParameters active;

    public Project612Armament(DnvProject612Entity tank) { this.tank = tank; }
    public boolean ready(LivingEntity actor) { return !queued && readyNow(actor); }
    private boolean readyNow(LivingEntity actor) {
        GunData gun = tank.getGunData("Main");
        return actor instanceof ServerPlayer && actor == tank.getFirstPassenger() && actor.isAlive() && !actor.isSpectator()
                && !tank.isRemoved() && !tank.isWreck() && tank.getWeaponIndex(0) == 0
                && gun != null && gun.get(GunProp.PROJECTILE_AMOUNT) == 1 && gun.canShoot(tank.getAmmoSupplier());
    }
    public void fire(LivingEntity actor, String requested) {
        if (!(tank.level() instanceof ServerLevel) || !ready(actor) || requested != null && !requested.equals("Main") || !clear()) return;
        operator = (ServerPlayer) actor;
        selected = tank.getGunData("Main");
        queued = true;
        attempted = accepted = false;
        try { tank.submitNativeShot(actor); } catch (RuntimeException error) { clearQueue(); throw error; }
    }
    public Runnable guard(Runnable work) {
        ServerPlayer player = operator;
        GunData gun = selected;
        return () -> {
            if (!queued || executing || operator != player || selected != gun) return;
            try {
                if (!readyNow(player) || tank.getGunData("Main") != gun || !clear()) return;
                executing = true;
                int fireIndex = gun.fireIndex.get();
                work.run();
                if (accepted) {
                    tank.afterShoot(gun, tank.getShootVec("Main", 1));
                    tank.playShootSound3p(player, "Main");
                    com.atsuishio.superbwarfare.tools.MinecraftUtil.sendPacketToAll(
                            new com.atsuishio.superbwarfare.network.message.receive.VehicleShootClientMessage(
                                    player.getUUID(), tank.getUUID(), fireIndex, "Main"));
                }
            } finally { clearQueue(); }
        };
    }
    public boolean admit(ServerLevel level, Entity projectile, ShootParameters p) {
        if (!executing || attempted || level != tank.level() || level.getEntity(tank.getUUID()) != tank
                || !readyNow(operator) || selected != tank.getGunData("Main") || p.data != selected
                || p.shooter != operator || p.ammoSupplier != tank.getAmmoSupplier() || p.targetEntityUUID != null
                || !(projectile instanceof com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity)
                || !"superbwarfare:cannon_shell".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString())
                || projectile.isRemoved() || projectile.level() != level || !clear()
                || projectile.position().distanceToSqr(p.shootPosition) > 1e-10
                || tank.getShootPos("Main", 1).distanceToSqr(p.shootPosition) > 1e-10
                || tank.getShootVec("Main", 1).distanceToSqr(p.shootDirection) > 1e-10) return false;
        attempted = true;
        active = p;
        com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(projectile, .90F);
        com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(projectile,
                com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction("project612_ap"));
        accepted = level.addFreshEntity(projectile);
        return accepted;
    }
    public boolean accepted(ShootParameters p) { return p == active && attempted && accepted; }
    public void queueResult(boolean enqueued) { if (!enqueued) clearQueue(); }
    public void invalidate() { if (executing) queued = false; else clearQueue(); }
    private void clearQueue() { queued = executing = attempted = accepted = false; active = null; operator = null; selected = null; }
    boolean clear() {
        Vec3 from = tank.firingPivot();
        Vec3 to = tank.getShootPos("Main", 1).add(tank.getShootVec("Main", 1).scale(.3));
        return BlizzardLoadedPath.loaded(tank, from, to) && tank.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank)).getType() == HitResult.Type.MISS;
    }
}
