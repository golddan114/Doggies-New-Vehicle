package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.init.ModMobEffects;
import com.atsuishio.superbwarfare.init.ModSounds;
import com.atsuishio.superbwarfare.tools.DamageHandler;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Low-speed contact for this heavy chassis; native SBW still owns faster impacts and the damage type. */
public final class ApocalypseCrushing {
    public static final double MIN_SPEED = .025;
    public static final int HIT_INTERVAL = 10;
    private final Map<UUID, Long> nextHit = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;

    public void tick(DnvApocalypseEntity tank) {
        if (tank.level().isClientSide || tank.isRemoved() || tank.isWreck() || tank.getHealth() <= 0
                || !tank.canCrushEntities() || !tank.onGround()) return;
        long now = tank.level().getGameTime();
        if (now == lastTick) return;
        if (now < lastTick) nextHit.clear();
        lastTick = now;
        nextHit.values().removeIf(until -> until <= now);
        Vec3 motion = tank.getDeltaMovement().multiply(1, 0, 1);
        if (motion.lengthSqr() < MIN_SPEED * MIN_SPEED) return;
        var transform = tank.getVehicleTransform(1);
        var plow = ApocalypseRig.INSTANCE.boxes.stream().filter(box -> box.name().equals("plow")).findFirst().orElseThrow();
        Vec3 center = ApocalypseRig.transformPoint(transform, plow.center());
        OBB blade = new OBB(new Vector3d(center.x, center.y, center.z),
                new Vector3d(plow.half().x, plow.half().y, plow.half().z),
                transform.getNormalizedRotation(new Quaterniond()), OBB.Part.BODY);
        Vec3 forward = ApocalypseRig.transformDirection(transform, new Vec3(0, 0, 1));
        Entity driver = tank.getFirstPassenger();
        var source = ModDamageTypes.causeVehicleStrikeDamage(tank.level().registryAccess(), tank, driver == null ? tank : driver);
        for (Entity target : tank.level().getEntities(tank, tank.getBoundingBox().expandTowards(motion),
                entity -> eligible(tank, driver, entity))) {
            if (nextHit.containsKey(target.getUUID()) || nextHit.size() >= 128) continue;
            Vec3 relative = motion.subtract(target.getDeltaMovement().multiply(1, 0, 1));
            if (relative.dot(target.getBoundingBox().getCenter().subtract(tank.position())) <= 0) continue;
            // Native target-vs-OBB checks retain vehicle gaps. Only hull/tracks can crush, never overhead barrels.
            if (!tank.isInObb(target, motion)) continue;
            var contact = target.getBoundingBox().move(motion.scale(-1));
            boolean body = tank.getOBBs().stream().anyMatch(box ->
                    (box.part == OBB.Part.BODY || box.part == OBB.Part.WHEEL_LEFT || box.part == OBB.Part.WHEEL_RIGHT)
                            && OBB.isColliding(box, contact));
            if (!body) continue;
            boolean frontBlade = motion.dot(forward) > MIN_SPEED && OBB.isColliding(blade, contact);
            double ratio = target instanceof VehicleEntity vehicle
                    ? tank.getMass() / Math.max(.01, vehicle.getMass())
                    : tank.getBoundingBox().getSize() / Math.max(.01, target.getBoundingBox().getSize());
            float amount = damage(relative.horizontalDistance(), ratio, frontBlade);
            if (amount <= 0) continue;
            nextHit.put(target.getUUID(), now + HIT_INTERVAL);
            if (DamageHandler.doDamage(target, source, amount)) {
                Vec3 push = motion.normalize().scale(Math.min(.25, motion.length() * .8));
                target.push(push.x, .02, push.z);
                tank.level().playSound(null, tank, ModSounds.VEHICLE_STRIKE.get(), tank.getSoundSource(), .7F, frontBlade ? .75F : 1F);
            }
        }
    }

    static float damage(double speed, double ratio, boolean blade) {
        if (!Double.isFinite(speed) || !Double.isFinite(ratio) || speed <= MIN_SPEED) return 0;
        double excess = Math.min(.3, speed) - MIN_SPEED;
        return (float) (Math.max(.25, Math.min(4, ratio)) * 80 * excess * excess * (blade ? 1.5 : 1));
    }

    private static boolean eligible(DnvApocalypseEntity tank, Entity driver, Entity target) {
        if (!target.isAlive() || target.isSpectator() || target.getVehicle() != null || target == driver
                || target == tank || tank.isAlliedTo(target) || DnvCombatPolicyRuntime.isProtectedPlayerTarget(target)) return false;
        if (!(target instanceof LivingEntity || target instanceof VehicleEntity || target instanceof Boat || target instanceof Minecart)) return false;
        if (target instanceof LivingEntity living && living.hasEffect(ModMobEffects.STRIKE_PROTECTION.get())) return false;
        return driver == null || target.getSelfAndPassengers().noneMatch(entity -> driver.isAlliedTo(entity)
                || driver instanceof Player player && entity instanceof Player other && !player.canHarmPlayer(other));
    }
}
