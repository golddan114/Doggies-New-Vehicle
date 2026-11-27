package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Per-AI target and guard-area owner. Only UUIDs and value observations survive a tick. */
final class ApocalypseAutonomy {
    static final double GUARD_RANGE = 160, HOME_LEASH = 64;
    private final DnvAlliedApocalypseEntity tank;
    private final ApocalypseAutonomousWeapons weapons;
    private final ApocalypseAutonomousNavigation navigation;
    private final DnvTargetCandidateCache candidates = new DnvTargetCandidateCache(20);
    private Vec3 home, lastSeenPoint;
    private String dimension;
    private UUID targetId;
    private DnvSightObservation sight;
    private long lastTick = Long.MIN_VALUE, lastSeen;
    private long idleUntil;
    private int idleStep;
    private double idleYaw, idleGoal;
    private int candidateCursor;
    private boolean observed, returning;
    private double maneuverLeash = HOME_LEASH;

    ApocalypseAutonomy(DnvAlliedApocalypseEntity tank, ApocalypseAutonomousWeapons weapons) {
        this.tank = tank; this.weapons = weapons;
        navigation = new ApocalypseAutonomousNavigation(tank);
    }

    void beforeNativeTick() {
        if (!(tank.level() instanceof ServerLevel)) return;
        long now = tank.level().getGameTime();
        if (!tank.autonomousReady()) { clear(); return; }
        if (now == lastTick) { navigation.stop(); observed = false; return; }
        if (now < lastTick) clear();
        lastTick = now;
        String currentDimension = tank.level().dimension().location().toString();
        if (home == null || !currentDimension.equals(dimension)) {
            clear(); home = tank.position(); dimension = currentDimension; lastTick = now;
        }
        observed = false;
        if (tank.getMaxEnergy() > 0 && tank.getEnergy() <= 0) { clearTarget(); navigation.stop(); return; }
        double homeDistance = horizontalSquared(tank.position(), home);
        if (!weapons.hasSupply()) returning = true;
        if (returning) {
            clearTarget();
            if (homeDistance <= 9) returning = false;
            navigation.move(home, false, false); return;
        }
        Entity enemy = resolve();
        if (!legal(enemy)) { clearTarget(); enemy = null; }
        if (enemy != null) {
            observed = Boolean.TRUE.equals(observe(enemy));
            if (observed) { lastSeenPoint = enemy.position(); lastSeen = now; }
            else if (now - lastSeen > 40) { clearTarget(); enemy = null; }
        }
        if (enemy == null && weapons.hasSupply()) enemy = acquire(now);
        double minimumDistance = enemy != null && observed ? weapons.combatDistance(enemy) : 24;
        // High targets may need more than the ordinary 64-block guard footprint to enter the main gun arc.
        // Only a currently visible target grants this bounded retreat space; target acquisition stays home-bound.
        maneuverLeash = Math.min(GUARD_RANGE, HOME_LEASH + Math.max(0, minimumDistance - 24));
        if (homeDistance >= (maneuverLeash - 2) * (maneuverLeash - 2)) {
            returning = true; clearTarget(); navigation.move(home, false, false); return;
        }
        if (enemy != null) {
            weapons.preferAmmo(enemy instanceof VehicleEntity || enemy instanceof DnvCombatPolicy.BossAutonomy);
            Boolean laneClear = observed ? weapons.mainLaneClear(enemy) : Boolean.TRUE;
            Vec3 away = tank.position().subtract(lastSeenPoint);
            Vec3 fromHome = tank.position().subtract(home);
            // Keep the target and VLS at the guard boundary instead of retreat -> return -> retreat loops.
            boolean outwardRetreat = away.horizontalDistance() < minimumDistance
                    && homeDistance >= (maneuverLeash - 4) * (maneuverLeash - 4)
                    && away.x * fromHome.x + away.z * fromHome.z > 0;
            if (outwardRetreat || laneClear == null) navigation.stop();
            else navigation.move(lastSeenPoint, true, !observed || !laneClear, minimumDistance);
        } else guardIdle();
    }

    private void guardIdle() {
        if (horizontalSquared(tank.position(), home) > 28 * 28) {
            navigation.move(home, false, false); return;
        }
        navigation.stop();
    }

    void afterNativeTick() {
        if (!tank.autonomousReady()) { clear(); return; }
        Entity enemy = resolve();
        if (!activeEnemy(enemy)) {
            weapons.clear();
            if (targetId == null && !returning && lastTick == tank.level().getGameTime()
                    && (tank.getMaxEnergy() <= 0 || tank.getEnergy() > 0)) {
                if (lastTick >= idleUntil) {
                    if (Math.abs(idleGoal - idleYaw) <= .28) {
                        idleYaw = idleGoal;
                        int phase = Math.floorMod(tank.getUUID().hashCode() + ++idleStep * 31, 57);
                        idleGoal = phase - 28;
                        idleUntil = lastTick + 50 + Math.floorMod(phase * 13, 91);
                    } else idleYaw += Math.copySign(.28, idleGoal - idleYaw);
                }
                double angle = Math.toRadians(tank.getYRot() + idleYaw);
                tank.turretAutoAimFromVector(new Vec3(-Math.sin(angle), .05, Math.cos(angle)));
            }
            return;
        }
        int selection = weapons.select(enemy);
        String name = selection == 2 ? "MainL" : tank.getGunName(0, selection);
        tank.turretAutoAimFromVector(weapons.aimPoint(selection, enemy).subtract(tank.firingPivot(name)));
        weapons.tickLock(enemy, true);
        weapons.fire(selection, enemy);
    }

    boolean activeEnemy(Entity enemy) {
        return tank.autonomousReady() && observed && !returning && targetId != null && enemy != null
                && targetId.equals(enemy.getUUID()) && lastTick == tank.level().getGameTime()
                && tank.level() instanceof ServerLevel server && server.getEntity(targetId) == enemy && legal(enemy)
                && horizontalSquared(tank.position(), home) <= maneuverLeash * maneuverLeash;
    }

    private Entity resolve() {
        return targetId == null || !(tank.level() instanceof ServerLevel server) ? null : server.getEntity(targetId);
    }

    private boolean legal(Entity enemy) {
        return home != null && ApocalypseAlliedTargets.eligible(tank, enemy)
                && horizontalSquared(enemy.position(), home) <= GUARD_RANGE * GUARD_RANGE
                && Math.abs(enemy.getY() - home.y) <= GUARD_RANGE;
    }

    private Entity acquire(long now) {
        if (candidates.refreshIfDue(tank, GUARD_RANGE + HOME_LEASH, this::legal)) candidateCursor = 0;
        var options = candidates.candidates(tank).stream().filter(this::legal)
                .sorted(Comparator.<Entity>comparingInt(e -> e instanceof DnvCombatPolicy.BossAutonomy ? 0 : 1)
                        .thenComparingDouble(tank::distanceToSqr)
                        .thenComparing(Entity::getUUID, DnvTargetCandidateCache::compareStableIds)).toList();
        if (options.isEmpty()) return null;
        Entity next = options.get(Math.floorMod(candidateCursor, options.size()));
        Boolean visible = observe(next);
        if (visible == null) return null; // Keep the cursor on budget deferral.
        candidateCursor = (candidateCursor + 1) % options.size();
        if (!visible) return null;
        targetId = next.getUUID(); observed = true; lastSeenPoint = next.position(); lastSeen = now;
        return next;
    }

    private Boolean observe(Entity enemy) {
        Vec3 origin = tank.lockOrigin();
        if (!SeekTool.NOT_IN_SMOKE.test(enemy)) { sight = null; return false; }
        if (sight != null && sight.reusable(enemy.getUUID(), origin, enemy.getBoundingBox(), enemy.getEyeY(), 0, tank.tickCount))
            return sight.clear();
        if (!DnvTargetingBudget.admitGeometryCheck((ServerLevel) tank.level(), tank.getUUID())) return null;
        Vec3 end = enemy.getBoundingBox().getCenter();
        boolean clear = ApocalypseTargeting.loadedRay(tank, origin, end)
                && tank.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank))
                    .getType() == HitResult.Type.MISS;
        sight = new DnvSightObservation(enemy.getUUID(), origin, enemy.getBoundingBox(), enemy.getEyeY(), 0,
                tank.tickCount, tank.tickCount + 5, clear);
        return clear;
    }

    private static double horizontalSquared(Vec3 a, Vec3 b) {
        return b == null ? Double.POSITIVE_INFINITY : (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z);
    }

    private void clearTarget() { targetId = null; lastSeenPoint = null; sight = null; observed = false; weapons.clear(); }
    void clear() {
        clearTarget(); candidates.clear(); navigation.clear(); returning = false; maneuverLeash = HOME_LEASH; lastTick = Long.MIN_VALUE;
        idleYaw = idleGoal = 0; idleStep = 0; idleUntil = 0;
        if (tank.level() instanceof ServerLevel server) DnvTargetingBudget.releaseGeometryOwner(server, tank.getUUID());
    }

    void save(CompoundTag tag) {
        if (home == null) return;
        tag.putString("HomeDimension", dimension);
        tag.putDouble("HomeX", home.x); tag.putDouble("HomeY", home.y); tag.putDouble("HomeZ", home.z);
    }
    void load(CompoundTag tag) {
        clear(); home = null; dimension = null;
        if (!tag.contains("HomeDimension")) return;
        double x = tag.getDouble("HomeX"), y = tag.getDouble("HomeY"), z = tag.getDouble("HomeZ");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000) return;
        home = new Vec3(x, y, z); dimension = tag.getString("HomeDimension");
    }
}
