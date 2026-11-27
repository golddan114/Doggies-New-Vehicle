package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.tools.SeekTool;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** AI-only synchronous admission. SBW alone creates projectiles and writes ammo/reload/heat. */
final class ApocalypseAutonomousWeapons {
    private static final double MISSILE_YAW_DOT = Math.cos(Math.toRadians(3));
    private final DnvAlliedApocalypseEntity tank;
    private final ApocalypseFriendlyLane friendlyLane;
    private final long[] nextTrigger = new long[3];
    private int nextGroup;
    private long lastAttempt = Long.MIN_VALUE;
    private UUID missileTarget;
    private int missileLockTicks;
    private ShootParameters active;
    private String weapon;
    private Entity target;
    private int group;
    private long shotTick;
    private boolean attempted, accepted;
    private int preferredAmmo;
    private long ammoCheckedAt = Long.MIN_VALUE;
    private boolean mainBackupAvailable;

    ApocalypseAutonomousWeapons(DnvAlliedApocalypseEntity tank) {
        this.tank = tank; friendlyLane = new ApocalypseFriendlyLane(tank);
    }

    Boolean mainLaneClear(Entity enemy) {
        if (!inRange(0, enemy)) return Boolean.TRUE;
        return friendlyLane.inspect(tank.getShootPos(tank.getGunName(0, 0), 1), aimPoint(0, enemy), false);
    }

    private boolean directLaneClear(int selection, Entity enemy) {
        if (selection == 2) return true; // VLS retains its separate launch and guidance rules.
        String name = tank.getGunName(0, selection);
        Vec3 from = tank.getShootPos(name, 1);
        Vec3 end = from.add(tank.getShootVec(name, 1).normalize().scale(from.distanceTo(aimPoint(selection, enemy))));
        return Boolean.TRUE.equals(friendlyLane.inspect(from, end, true));
    }

    int select(Entity enemy) {
        int tracking = -1;
        for (int i = 0; i < 3; i++) {
            int candidate = (nextGroup + i) % 3;
            if (!inRange(candidate, enemy) || !reachable(candidate, enemy)) continue;
            if (ready(candidate)) {
                if (aligned(candidate, enemy)) return candidate;
                if (tracking < 0) tracking = candidate;
            }
        }
        if (tracking >= 0) return tracking;
        // Keep tracking a reachable group during reload without an impossible cannon starving VLS.
        for (int i = 0; i < 3; i++) {
            int candidate = (nextGroup + i) % 3;
            if (inRange(candidate, enemy) && reachable(candidate, enemy)) return candidate;
        }
        return 2;
    }

    private org.joml.Vector3d localAim(int selection, Entity enemy) {
        String name = tank.getGunName(0, selection);
        Vec3 delta = aimPoint(selection, enemy).subtract(tank.firingPivot(name));
        return tank.getVehicleTransform(1).invert().transformDirection(new org.joml.Vector3d(delta.x, delta.y, delta.z));
    }

    boolean reachable(int selection, Entity enemy) {
        if (enemy == null) return false;
        if (selection == 2) return true; // Fixed launch tubes steer after clearing the hull.
        var local = localAim(selection, enemy);
        float yaw = (float) Math.toDegrees(Math.atan2(local.x, local.z));
        float elevation = (float) Math.toDegrees(Math.atan2(local.y, Math.hypot(local.x, local.z)));
        return Float.isFinite(elevation) && elevation >= ApocalypseMotion.floor(yaw, selection == 1)
                && elevation <= (selection == 1 ? 10 : 25);
    }

    double combatDistance(Entity enemy) {
        // Two degrees of upper-arc margin prevent stop/approach oscillation at the mechanical limit.
        double rise = localAim(0, enemy).y;
        double range = rise >= 0 ? rise / Math.tan(Math.toRadians(23)) : -rise / Math.tan(Math.toRadians(4));
        return Double.isFinite(range) ? Math.max(24, Math.min(160, range)) : 24;
    }

    boolean hasSupply() {
        refreshAmmoPreference();
        if (mainBackupAvailable) return true;
        for (String name : new String[] {"MainL", "MainR", "RocketL", "RocketR", "Missiles"}) {
            var gun = tank.getGunData(name);
            if (gun != null && (gun.ammo.get() > 0 || gun.reloading() || gun.hasBackupAmmo(tank))) return true;
        }
        return false;
    }

    void preferAmmo(boolean armored) {
        int wanted = armored ? 1 : 0;
        if (preferredAmmo != wanted) { preferredAmmo = wanted; ammoCheckedAt = Long.MIN_VALUE; }
        refreshAmmoPreference();
    }

    private void refreshAmmoPreference() {
        long now = tank.level().getGameTime();
        if (ammoCheckedAt != Long.MIN_VALUE && now >= ammoCheckedAt && now - ammoCheckedAt < 20) return;
        ammoCheckedAt = now;
        var gun = tank.getGunData("MainL");
        if (gun == null) { mainBackupAvailable = false; return; }
        var consumers = gun.get(GunProp.AMMO_CONSUMER);
        int chosen = preferredAmmo;
        // Read both native consumers without switching loaded ammunition or consuming inventory.
        boolean preferred = consumers.size() > chosen && (gun.virtualAmmo.get() > 0 || consumers.get(chosen).count(gun, tank) > 0);
        boolean alternate = !preferred && consumers.size() > 1 - chosen && consumers.get(1 - chosen).count(gun, tank) > 0;
        if (alternate) chosen = 1 - chosen;
        mainBackupAvailable = preferred || alternate;
        tank.setPreferredAmmo(chosen);
    }

    Vec3 aimPoint(int selection, Entity enemy) {
        Vec3 point = enemy.getBoundingBox().getCenter();
        if (selection == 2) return point;
        String name = tank.getGunName(0, selection);
        var gun = tank.getGunData(name);
        if (gun == null) return point;
        return lead(tank.getShootPos(name, 1), point, enemy.getDeltaMovement(), gun.get(GunProp.VELOCITY), gun.get(GunProp.GRAVITY));
    }

    static Vec3 lead(Vec3 muzzle, Vec3 point, Vec3 velocity, double speed, double gravity) {
        if (!Double.isFinite(speed) || speed <= 0 || !Double.isFinite(gravity)) return point;
        double time = Math.min(40, muzzle.distanceTo(point) / speed);
        Vec3 motion = velocity.lengthSqr() > 4 ? velocity.normalize().scale(2) : velocity;
        return point.add(motion.scale(time)).add(0, .5 * Math.max(0, gravity) * time * time, 0);
    }

    void tickLock(Entity enemy, boolean observedVisible) {
        boolean valid = observedVisible && enemy != null && tank.activeEnemy(enemy) && inRange(2, enemy)
                && missileTurretAligned(enemy);
        if (!valid) { missileTarget = null; missileLockTicks = 0; return; }
        if (!enemy.getUUID().equals(missileTarget)) { missileTarget = enemy.getUUID(); missileLockTicks = 0; }
        missileLockTicks = Math.min(20, missileLockTicks + 1);
    }

    boolean fire(int selection, Entity enemy) {
        long now = tank.level().getGameTime();
        if (active != null || lastAttempt == now || !tank.activeEnemy(enemy) || !ready(selection)
                || !inRange(selection, enemy) || !reachable(selection, enemy)
                || !tank.poseClear() || !aligned(selection, enemy) || !visible(enemy)) return false;
        String name = tank.getGunName(0, selection);
        Vec3 muzzle = tank.getShootPos(name, 1);
        if (!tank.muzzleClear(name, muzzle) || !clearLine(muzzle, enemy.getBoundingBox().getCenter())
                || !directLaneClear(selection, enemy)) return false;
        lastAttempt = now;
        weapon = name; target = enemy; group = selection; shotTick = now;
        attempted = accepted = false;
        try {
            tank.modifyGunData(name, data -> {
                active = new ShootParameters(tank, tank, (ServerLevel) tank.level(), muzzle, tank.getShootVec(name, 1), data,
                        data.get(GunProp.SPREAD), true, enemy.getUUID(), enemy.getBoundingBox().getCenter());
                data.shoot(active);
            });
            return accepted;
        } finally {
            try { if (accepted) {
                GunData gun = active.data;
                nextTrigger[group] = now + ApocalypseMotion.fireInterval(gun.get(GunProp.RPM));
                if (group == 0) tank.advanceMain();
                else if (group == 1) tank.advanceRocket();
                nextGroup = (group + 1) % 3;
                tank.afterShoot(gun, active.shootDirection);
                tank.playShootSound3p(null, gun, active.shootPosition);
            } } finally { active = null; target = null; weapon = null; }
        }
    }

    boolean admit(ServerLevel level, Entity projectile, ShootParameters p) {
        if (p != active || attempted || level != tank.level() || level.getGameTime() != shotTick
                || !tank.activeEnemy(target) || !ready(group) || !inRange(group, target) || !reachable(group, target)
                || !tank.poseClear() || !aligned(group, target) || !visible(target)
                || tank.getGunData(weapon) != p.data || !weapon.equals(tank.getGunName(0, group))
                || p.shooter != tank || p.ammoSupplier != tank || !target.getUUID().equals(p.targetEntityUUID)
                || projectile.level() != level || projectile.isRemoved()
                || !BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString().equals(p.data.get(GunProp.PROJECTILE).getItemId())
                || projectile.position().distanceToSqr(p.shootPosition) > 1E-10
                || tank.getShootPos(weapon, 1).distanceToSqr(p.shootPosition) > 1E-10
                || tank.getShootVec(weapon, 1).distanceToSqr(p.shootDirection) > 1E-10
                || !tank.muzzleClear(weapon, p.shootPosition) || !clearLine(p.shootPosition, target.getBoundingBox().getCenter())
                || !directLaneClear(group, target)) return false;
        attempted = true;
        ApocalypseArmament.decorate(weapon, projectile, p);
        if (projectile instanceof DnvApocalypseMissile missile) missile.markAllied(tank);
        accepted = level.addFreshEntity(projectile);
        return accepted;
    }

    boolean accepted(ShootParameters p) { return active == p && attempted && accepted; }

    private boolean ready(int selection) {
        if (selection < 0 || selection > 2 || !tank.autonomousReady() || tank.level().getGameTime() < nextTrigger[selection]) return false;
        var gun = tank.getGunData(tank.getGunName(0, selection));
        return gun != null && gun.get(GunProp.PROJECTILE_AMOUNT) == 1 && gun.get(GunProp.SHOOT_DELAY_TIME) == 0 && gun.canShoot(tank);
    }

    private boolean inRange(int selection, Entity enemy) {
        if (enemy == null) return false;
        double distance = tank.lockOrigin().distanceToSqr(enemy.getBoundingBox().getCenter());
        double max = selection == 1 ? 96 : selection == 2 ? 256 : 192;
        return distance >= (selection == 2 ? 14 * 14 : 10 * 10) && distance <= max * max;
    }

    private boolean aligned(int selection, Entity enemy) {
        if (selection == 2) return missileLockTicks >= 20 && enemy.getUUID().equals(missileTarget)
                && missileTurretAligned(enemy);
        String name = tank.getGunName(0, selection);
        Vec3 wanted = aimPoint(selection, enemy).subtract(tank.getShootPos(name, 1)).normalize();
        double tolerance = Math.cos(Math.toRadians(selection == 0 ? 2 : 4));
        return wanted.dot(tank.getShootVec(name, 0).normalize()) >= tolerance
                && wanted.dot(tank.getShootVec(name, 1).normalize()) >= tolerance;
    }

    private boolean missileTurretAligned(Entity enemy) {
        Vec3 delta = enemy.getBoundingBox().getCenter().subtract(tank.firingPivot("MainL"));
        return horizontalAlignment(tank.getShootVec("MainL", 0), delta) >= MISSILE_YAW_DOT
                && horizontalAlignment(tank.getShootVec("MainL", 1), delta) >= MISSILE_YAW_DOT;
    }

    private static double horizontalAlignment(Vec3 direction, Vec3 delta) {
        double lengths = direction.horizontalDistance() * delta.horizontalDistance();
        return lengths < 1E-6 ? 0 : (direction.x * delta.x + direction.z * delta.z) / lengths;
    }

    private boolean visible(Entity enemy) {
        return SeekTool.NOT_IN_SMOKE.test(enemy) && clearLine(tank.lockOrigin(), enemy.getBoundingBox().getCenter());
    }

    private boolean clearLine(Vec3 origin, Vec3 end) {
        return ApocalypseTargeting.loadedRay(tank, origin, end)
                && tank.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank))
                    .getType() == HitResult.Type.MISS;
    }

    void clear() { missileTarget = null; missileLockTicks = 0; friendlyLane.clear(); }
    void save(CompoundTag tag) {
        long now = tank.level().getGameTime(); tag.putInt("NextGroup", nextGroup);
        for (int i = 0; i < 3; i++) tag.putInt("AiTrigger" + i, (int) Math.max(0, Math.min(1200, nextTrigger[i] - now)));
    }
    void load(CompoundTag tag) {
        nextGroup = Math.floorMod(tag.getInt("NextGroup"), 3);
        long now = tank.level().getGameTime();
        for (int i = 0; i < 3; i++) nextTrigger[i] = now + Math.max(0, Math.min(1200, tag.getInt("AiTrigger" + i)));
        lastAttempt = Long.MIN_VALUE; active = null; target = null; weapon = null; clear();
        ammoCheckedAt = Long.MIN_VALUE; mainBackupAvailable = false; preferredAmmo = 0;
    }
}
