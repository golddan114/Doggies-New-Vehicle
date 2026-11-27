package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Selection and admission only. SBW GunData owns ammunition, reload progress and projectile damage. */
public final class ApocalypseArmament {
    private final DnvApocalypseEntity tank;
    private final long[] nextTrigger = new long[3];
    private ShootParameters active;
    private ServerPlayer operator;
    private String weapon;
    private boolean attempted;
    private boolean accepted;
    private boolean queued;
    private boolean executing;
    private UUID targetId;

    public ApocalypseArmament(DnvApocalypseEntity tank) { this.tank = tank; }

    public boolean ready(LivingEntity actor) {
        int group = tank.getWeaponIndex(0);
        GunData gun = tank.getGunData(0);
        return !queued && readyNow(actor, group, gun);
    }

    private boolean readyNow(LivingEntity actor, int group, GunData gun) {
        return actor instanceof ServerPlayer && actor == tank.getFirstPassenger()
                && !actor.isSpectator() && actor.isAlive() && !tank.isWreck() && !tank.isRemoved()
                && group >= 0 && group < 3 && tank.level().getGameTime() >= nextTrigger[group]
                && gun != null && gun.get(GunProp.PROJECTILE_AMOUNT) == 1 && gun.canShoot(tank.getAmmoSupplier());
    }

    public void fire(LivingEntity actor, String requestedWeapon, UUID requestedTarget) {
        if (!(tank.level() instanceof ServerLevel) || !ready(actor)) return;
        int group = tank.getWeaponIndex(0);
        String selected = tank.getGunName(0);
        if (requestedWeapon != null && !requestedWeapon.equals(selected)) return;
        Entity target = group == 2 ? tank.targeting().locked((ServerPlayer) actor, requestedTarget) : null;
        if (group == 2 && target == null || !tank.poseClear()) return;
        Vec3 muzzle = tank.getShootPos(selected, 1);
        if (!muzzleClear(selected, muzzle)) return;
        weapon = selected;
        operator = (ServerPlayer) actor;
        targetId = target == null ? null : target.getUUID();
        attempted = false;
        accepted = false;
        queued = true;
        try {
            tank.submitNativeShot(actor, selected, targetId, target == null ? null : target.getBoundingBox().getCenter());
        } catch (RuntimeException failure) {
            clearQueue();
            throw failure;
        }
    }

    /** Wrap the existing SBW queue. Its callback still creates ShootParameters and fires GunData. */
    public Runnable guard(Runnable nativeWork) {
        String selected = weapon;
        ServerPlayer actor = operator;
        int group = tank.getWeaponIndex(0);
        GunData gun = tank.getGunData(selected);
        return () -> {
            if (!queued || executing || weapon == null || !weapon.equals(selected) || operator != actor) return;
            try {
                if (tank.isRemoved() || !selected.equals(tank.getGunName(0)) || !readyNow(actor, group, gun) || !tank.poseClear()
                        || !muzzleClear(selected, tank.getShootPos(selected, 1))
                        || group == 2 && tank.targeting().locked(actor, targetId) == null) return;
                executing = true;
                int fireIndex = gun.fireIndex.get();
                nativeWork.run();
                if (accepted) {
                    tank.afterShoot(gun, tank.getShootVec(selected, 1));
                    tank.playShootSound3p(actor, selected);
                    com.atsuishio.superbwarfare.tools.MinecraftUtil.sendPacketToAll(
                            new com.atsuishio.superbwarfare.network.message.receive.VehicleShootClientMessage(
                                    actor.getUUID(), tank.getUUID(), fireIndex, selected));
                    nextTrigger[group] = tank.level().getGameTime() + ApocalypseMotion.fireInterval(gun.get(GunProp.RPM));
                    if (group == 0) tank.advanceMain();
                    else if (group == 1) tank.advanceRocket();
                }
            } finally { clearQueue(); }
        };
    }

    public void queueResult(boolean enqueued) { if (!enqueued) clearQueue(); }
    private void clearQueue() {
        queued = false;
        executing = false;
        active = null;
        operator = null;
        weapon = null;
        targetId = null;
    }

    public boolean admit(ServerLevel level, Entity projectile, ShootParameters parameters) {
        if (!executing || attempted || level != tank.level() || level.getEntity(tank.getUUID()) != tank
                || tank.isRemoved() || tank.isWreck() || operator != tank.getFirstPassenger() || !operator.isAlive()
                || operator.isSpectator() || tank.getGunData(0) != parameters.data || !tank.poseClear()
                || parameters.shooter != operator || parameters.ammoSupplier != tank.getAmmoSupplier()
                || !java.util.Objects.equals(targetId, parameters.targetEntityUUID)
                || parameters.data.get(GunProp.PROJECTILE_AMOUNT) != 1 || !parameters.data.canShoot(tank.getAmmoSupplier())) return false;
        if (tank.getWeaponIndex(0) == 2 && tank.targeting().locked(operator, parameters.targetEntityUUID) == null) return false;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString();
        if (!type.equals(parameters.data.get(GunProp.PROJECTILE).getItemId()) || projectile.isRemoved()
                || projectile.position().distanceToSqr(parameters.shootPosition) > 1.0E-10
                || tank.getShootPos(weapon, 1).distanceToSqr(parameters.shootPosition) > 1.0E-10
                || tank.getShootVec(weapon, 1).distanceToSqr(parameters.shootDirection) > 1.0E-10
                || !muzzleClear(weapon, parameters.shootPosition)) return false;
        attempted = true; // Claim before Forge/world callbacks can reenter this host.
        active = parameters;
        decorate(weapon, projectile, parameters);
        accepted = level.addFreshEntity(projectile);
        return accepted;
    }

    static void decorate(String weapon, Entity projectile, ShootParameters parameters) {
        String profile = weapon.startsWith("Main")
                ? (parameters.data.selectedAmmoType.get() == 1 ? "apocalypse_ap" : "apocalypse_he")
                : weapon.startsWith("Rocket") ? "apocalypse_rocket" : weapon.equals("Missiles") ? "apocalypse_missile" : null;
        if (profile != null) com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(projectile,
                com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction(profile));
        if (weapon.startsWith("Rocket")) {
            com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.rocket(projectile);
            com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(projectile, .20F);
        }
        else if (weapon.startsWith("Main") && parameters.data.get(GunProp.EXPLOSION_DAMAGE) > 0)
            com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.cannon(projectile);
        if (weapon.startsWith("Main") && parameters.data.selectedAmmoType.get() == 1)
            com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(projectile, .60F);
        if (weapon.equals("Missiles"))
            com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(projectile, .70F);
    }

    public boolean accepted(ShootParameters parameters) { return active == parameters && attempted && accepted; }

    public boolean requestAmmo(GunData gun, int index) {
        if (gun != tank.getGunData("MainL") && gun != tank.getGunData("MainR")) return false;
        if (index < 0 || index > 1 || tank.level().isClientSide) return true;
        // Native EditMessage emits next/previous; with two types every request toggles the queued choice.
        tank.setPreferredAmmo(1 - tank.preferredAmmo());
        prepareReloads();
        if (tank.getFirstPassenger() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.doggievehicle.apocalypse_ammo",
                    tank.preferredAmmo() == 0 ? "HE" : "AP"), true);
        }
        return true;
    }

    public void prepareReloads() {
        if (tank.level().isClientSide) return;
        for (String name : new String[] {"MainL", "MainR"}) {
            GunData gun = tank.getGunData(name);
            if (gun == null || gun.ammo.get() > 0 || gun.reloading() || gun.selectedAmmoType.get() == tank.preferredAmmo()) continue;
            tank.modifyGunData(name, data -> {
                data.selectedAmmoType.set(tank.preferredAmmo());
                data.nbtVersion.invalidateStructural();
                data.cachedBackupAmmo = -1;
            });
        }
    }

    boolean muzzleClear(String name, Vec3 muzzle) {
        Vec3 pivot = tank.firingPivot(name);
        return ApocalypseTargeting.loadedRay(tank, pivot, muzzle)
                && tank.level().clip(new ClipContext(pivot, muzzle, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, tank)).getType() == HitResult.Type.MISS;
    }

    public void save(CompoundTag tag) {
        long now = tank.level().getGameTime();
        for (int i = 0; i < nextTrigger.length; i++) tag.putInt("Trigger" + i, (int) Math.max(0, Math.min(1200, nextTrigger[i] - now)));
    }

    public void load(CompoundTag tag) {
        long now = tank.level().getGameTime();
        for (int i = 0; i < nextTrigger.length; i++) nextTrigger[i] = now + Math.max(0, Math.min(1200, tag.getInt("Trigger" + i)));
        clearQueue();
    }
}
