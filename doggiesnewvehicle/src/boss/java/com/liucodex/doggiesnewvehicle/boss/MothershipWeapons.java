package com.liucodex.doggiesnewvehicle.boss;

import com.atsuishio.superbwarfare.entity.projectile.RpgRocketStandardEntity;
import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.DamageHandler;
import com.liucodex.doggiesnewvehicle.combat.DnvDamageTypes;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.noise.init.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server firing adapter. The mothership entity supplies calibrated muzzles and owns the clocks. */
public final class MothershipWeapons {
    private MothershipWeapons() { }

    /** A station may consider a scent, but only a compatible laser may track it. */
    public static boolean stationTargetAvailable(Entity ship, Entity target) {
        return sameLevel(ship, target) && DnvCombatPolicyRuntime.allowsBossLaserTarget(
                ship, target, target instanceof LivingEntity || target instanceof VehicleEntity);
    }

    public static boolean compatibleWithStation(MothershipArmament.Station station, Entity target) {
        return station.kind == MothershipArmament.Kind.TWIN_LASER || !DnvTargetExclusions.isSporeScent(target);
    }

    public static boolean fireSecondary(MothershipArmament.Station station, int barrel, Entity ship, Entity target,
                                        Vec3 muzzle, Vec3 aimPoint) {
        if (!(ship.level() instanceof ServerLevel level) || station == null || barrel < 0 || barrel >= station.kind.barrels
                || !compatibleWithStation(station, target)
                || !validTarget(ship, target, station.kind == MothershipArmament.Kind.TWIN_LASER)
                || !inStationRange(station,muzzle,aimPoint,0)) return false;
        var kind = station.kind;
        Vec3 end = target.getBoundingBox().getCenter();
        double distance = end.distanceTo(muzzle);
        if (distance < 1.0E-6) return false;
        var trace = MothershipWorld.trace(ship, muzzle, end);
        if (trace.impact() || trace.end().distanceToSqr(end) > 1.0E-6) return false;
        var intercepted = ProjectileUtil.getEntityHitResult(level, ship, muzzle, end, new AABB(muzzle, end).inflate(.3),
                e -> e != ship && e.isPickable() && !e.isSpectator());
        if (intercepted != null && intercepted.getEntity() != target) return false;
        Vec3 direction = aimPoint.subtract(muzzle).normalize();
        if (direction.lengthSqr() < .5) return false;
        double relativeYaw = Math.toDegrees(Math.atan2(-direction.x, direction.z)) - ship.getYRot();
        double elevation = Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        if (!MothershipRig.get().permits(station.ordinal(),new MothershipRig.Angles(relativeYaw,elevation))) return false;
        if (kind == MothershipArmament.Kind.AUTOCANNON) {
            var bullet = new ProjectileEntity(level);
            bullet.setPos(muzzle.x, muzzle.y, muzzle.z);
            bullet.setOwner(ship);
            bullet.setDamage(kind.damage);
            bullet.setExplosionDamage(kind.blastDamage);
            bullet.setExplosionRadius(kind.blastRadius);
            bullet.setHeadShot(1);
            bullet.setLegShot(1);
            bullet.setExplosionDestroy(true);
            bullet.setLife(MothershipAutocannonAim.LIFE);
            bullet.setCustomGravity(MothershipAutocannonAim.GRAVITY);
            com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(bullet, 0.10F);
            com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(bullet, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("ses_autocannon"));
            com.liucodex.doggiesnewvehicle.api.combat.DnvProjectileLifetime.trackKinetic(bullet, bullet.getLife());
            bullet.shoot(direction.x, direction.y, direction.z, MothershipAutocannonAim.SPEED, 0);
            return level.addFreshEntity(bullet);
        }
        if (kind == MothershipArmament.Kind.HE_ROCKET) {
            var rocket = new RpgRocketStandardEntity(com.atsuishio.superbwarfare.init.ModEntities.RPG_ROCKET_STANDARD.get(),
                    muzzle.x, muzzle.y, muzzle.z, level, kind.damage, kind.blastDamage, kind.blastRadius);
            rocket.setOwner(ship);
            rocket.setExplosionDestroy(true);
            rocket.setHeadShot(1);
            rocket.setLegShot(1);
            rocket.setLife(80);
            com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.rocket(rocket);
            com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(rocket, 0.20F);
            com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(rocket, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("ses_he_rocket"));
            rocket.shoot(direction.x, direction.y, direction.z, 2.5F, 0);
            com.liucodex.doggiesnewvehicle.api.combat.DnvProjectileLifetime.track(rocket, rocket.getLife());
            return level.addFreshEntity(rocket);
        }
        var damage = DnvDamageTypes.piercingLaser(level.registryAccess(), ship, ship,
                com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("ses_twin_laser"));
        if (!DnvBossLaserHit.apply(ship, target, damage, kind.damage)) return false;
        var light = ModEntities.LASER_BOLT.get().create(level);
        if (light != null) {
            light.setPos(muzzle.x, muzzle.y, muzzle.z);
            light.setDeltaMovement(direction);
            light.setBeamLength((float) distance);
            light.setLifetime(3);
            level.addFreshEntity(light);
        }
        return true;
    }

    /** Shared boresight for the visible station and the accepted projectile command. */
    public static Vec3 aimPoint(MothershipArmament.Station station, Entity target, Vec3 muzzle) {
        Vec3 center = target.getBoundingBox().getCenter();
        if (station.kind == MothershipArmament.Kind.AUTOCANNON)
            return MothershipAutocannonAim.point(muzzle, center, target.getDeltaMovement());
        if (station.kind != MothershipArmament.Kind.HE_ROCKET) return center;
        Vec3 direction = MothershipRocketAim.direction(muzzle, center);
        return muzzle.add(direction.scale(center.distanceTo(muzzle)));
    }

    /** Horizontal weapon envelope is measured from the firing muzzle, including compensated aim. */
    public static boolean inStationRange(MothershipArmament.Station station,Vec3 muzzle,Vec3 aimPoint,double grace) {
        if(station==null || !finite(muzzle) || !finite(aimPoint) || !Double.isFinite(grace) || grace<0)return false;
        Vec3 relative=aimPoint.subtract(muzzle);
        return relative.horizontalDistance()<=station.kind.range+grace
                && Math.abs(relative.y)<=MothershipTactics.MAX_TARGET_HEIGHT_DIFFERENCE+grace*.5;
    }

    /** One crash pulse over loaded entities. No block explosion/fire API is invoked on either SBW version. */
    public static void crashImpact(Entity ship) {
        if (!(ship.level() instanceof ServerLevel level)) return;
        AABB hull=ship.getBoundingBox();
        Vec3 point=new Vec3(hull.getCenter().x,hull.minY+.75,hull.getCenter().z);
        double radius=40;
        var source=level.damageSources().explosion(ship,ship);
        for(Entity target:level.getEntities(ship,new AABB(point,point).inflate(radius),
                e->e.isAlive()&&!e.isSpectator()
                        &&!com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime.isProtectedPlayerTarget(e))) {
            Vec3 direction=target.getBoundingBox().getCenter().subtract(point);
            double distance=direction.length();
            if(distance>=radius)continue;
            double strength=1-distance/radius;
            com.atsuishio.superbwarfare.tools.DamageHandler.doDamage(target,source,(float)(1200*strength));
            Vec3 push=direction.normalize().scale(1.8*strength).add(0,.3*strength,0);
            target.push(push.x,push.y,push.z);
        }
        // Reuse Kirov-scale SBW sound/shake/particle delivery, without a second damage or block explosion.
        com.atsuishio.superbwarfare.tools.ParticleTool.spawnExplosionParticles(
                com.atsuishio.superbwarfare.tools.ParticleTool.particleTypeForRadius(42),level,point.add(0,2,0));
    }

    /** One cosmetic hull-local burst per second while descending; never destroys the ship or terrain. */
    public static void fallingExplosion(com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity ship,int age) {
        if(!(ship.level() instanceof ServerLevel level))return;
        var boxes=ship.getOBBs();
        if(boxes.isEmpty())return;
        var box=com.atsuishio.superbwarfare.tools.OBB.getWorldAABB(boxes.get(Math.floorMod(age/20,boxes.size())));
        Vec3 point=box.getCenter().add(0,box.getYsize()*.35,0);
        com.atsuishio.superbwarfare.tools.ParticleTool.spawnExplosionParticles(
                com.atsuishio.superbwarfare.tools.ParticleTool.ParticleType.MEDIUM,level,point);
    }

    /** One sound for an accepted station burst, including the two-barrel laser volley. */
    public static void playStationSound(MothershipArmament.Station station, Entity ship, Vec3 muzzle) {
        if (!(ship.level() instanceof ServerLevel level) || station == null || !finite(muzzle)) return;
        var sound = switch (station.kind) {
            case AUTOCANNON -> MothershipSounds.AUTOCANNON_FIRE;
            case HE_ROCKET -> MothershipSounds.ROCKET_FIRE;
            case TWIN_LASER -> MothershipSounds.LASER_FIRE;
        };
        float volume=switch(station.kind) {
            case AUTOCANNON -> MothershipSounds.AUTOCANNON_VOLUME;
            case HE_ROCKET -> MothershipSounds.ROCKET_VOLUME;
            case TWIN_LASER -> MothershipSounds.LASER_VOLUME;
        };
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, sound.get(), SoundSource.HOSTILE, volume, 1F);
    }

    /** SBW's visual/audio explosion at each footfall; server damage remains owned by the ship. */
    public static void playDoomsdayImpact(Entity ship, Vec3 impact, MothershipDoomsday.Hit hit) {
        if (!(ship.level() instanceof ServerLevel level) || !finite(impact)
                || hit == null || !MothershipWorld.loaded(level, new AABB(impact, impact))) return;
        // Kirov's reference radius is 42. The final visual uses 70% of that size;
        // hit.blastRadius remains 16 and alone governs gameplay damage/terrain.
        double visualRadius = hit.ordinal() == 4 ? MothershipDoomsday.FINAL_VISUAL_RADIUS : hit.blastRadius();
        com.atsuishio.superbwarfare.tools.ParticleTool.spawnExplosionParticles(
                com.atsuishio.superbwarfare.tools.ParticleTool.particleTypeForRadius((float)visualRadius),level,impact);
    }

    /** Caller supplies an exposed, ray-hit target or an eligible target inside the final blast sphere. */
    public static boolean doomsdayDamage(Entity ship, Entity target, MothershipDoomsday.Hit hit, boolean blast) {
        if (!(ship.level() instanceof ServerLevel level) || target == null || target == ship || target.level() != level
                || !target.isAlive() || target.isSpectator()
                || (blast ? DnvTargetExclusions.isUnavailableCombatTarget(target) : DnvTargetExclusions.isUnavailableLaserTarget(target))
                || com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime.isProtectedPlayerTarget(target)
                || !blast && !validTarget(ship,target,true)) return false;
        if (!blast && DnvTargetExclusions.isSporeScent(target)) {
            return DnvBossLaserHit.apply(ship, target, DnvDamageTypes.piercingLaser(level.registryAccess(), ship, ship), hit.directDamageAgainst(0));
        }
        // Fold the percentage into the existing hit: no second hurt call, query or explosion.
        float amount = hit.blastDamage();
        if (!blast) {
            double maximumHealth = target instanceof VehicleEntity vehicle ? vehicle.getMaxHealth()
                    : target instanceof LivingEntity living ? living.getMaxHealth() : 0;
            amount = hit.directDamageAgainst(maximumHealth);
        }
        if (amount <= 0) return false;
        return DamageHandler.doDamage(target, blast ? DnvDamageTypes.doomsdayBlast(level.registryAccess(), ship, ship)
                : DnvDamageTypes.piercingLaser(level.registryAccess(), ship, ship), amount);
    }

    private static boolean validTarget(Entity ship, Entity target) {
        return validTarget(ship, target, false);
    }

    private static boolean validTarget(Entity ship, Entity target, boolean laser) {
        return sameLevel(ship, target) && target.isAlive()
                && !ship.isAlliedTo(target)
                && (laser ? DnvCombatCoordinator.fireAuthorizedBossLaser(ship, target)
                          : DnvCombatCoordinator.fireAuthorizedBoss(ship, target));
    }

    private static boolean sameLevel(Entity ship, Entity target) {
        return target != null && target != ship && target.level() == ship.level();
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

}
