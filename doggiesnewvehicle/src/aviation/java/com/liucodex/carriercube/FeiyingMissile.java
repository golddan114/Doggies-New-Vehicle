package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.entity.projectile.Agm65Entity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.world.phys.EntityResult;
import com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Feiying's entity-locked game projectile. The native launcher owns release, ammo and damage. */
public final class FeiyingMissile extends Agm65Entity {
    private static final int RELEASE_TICKS = 8;
    private static final double FLIGHT_STEP = 7;

    public FeiyingMissile(EntityType<? extends Agm65Entity> type, Level level) {
        super(type, level);
    }

    @Override public void tick() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            super.tick();
            return;
        }
        Entity target = EntityFindUtil.findEntity(server, getTargetUUID());
        Entity owner = getOwner();
        if (!CubePrototype.validMissileTarget(owner, target) || target.level() != server
                || target instanceof Player player && owner instanceof Player shooter && !shooter.canHarmPlayer(player)) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            discard();
            return;
        }
        if (tickCount <= getNoHitTicks()) {
            super.tick();
            return;
        }
        Vec3 start = position();
        Vec3 step = getDeltaMovement();
        if (tickCount > RELEASE_TICKS) {
            step = targetPoint(target).subtract(start);
            if (step.lengthSqr() > FLIGHT_STEP * FLIGHT_STEP) step = step.normalize().scale(FLIGHT_STEP);
            setDeltaMovement(step);
            setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
            setXRot((float) -Math.toDegrees(Math.atan2(step.y, step.horizontalDistance())));
        }
        Vec3 end = start.add(step);
        // SBW skips the entire collision sweep outside build height. Preserve first contact
        // with terrain or a nearer entity even when this segment re-enters the buildable world.
        int y = (int)getY();
        if (y < level().getMinBuildHeight() || y > level().getMaxBuildHeight()) {
            var block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double nearest = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : start.distanceToSqr(block.getLocation());
            EntityResult first = null;
            for (var hit : findEntitiesOnPath(start, end)) {
                if (hit.getEntity() instanceof Player player && getOwner() instanceof Player shooter && !shooter.canHarmPlayer(player)) continue;
                double distance = start.distanceToSqr(hit.getHitPos());
                if (distance < nearest) {
                    nearest = distance;
                    first = hit;
                }
            }
            if (first != null) {
                onHit(new ExtendedEntityRayTraceResult(first));
                if (isRemoved()) return;
            } else if (block.getType() != HitResult.Type.MISS) {
                onHit(block);
                if (isRemoved()) return;
            }
        }
        // A moving non-vehicle's native damage box is shifted backwards. Only supplement
        // a miss of that box when the actual segment intersects the target's current body.
        boolean movingBox = !(target instanceof VehicleEntity) && getHitResult(target, start, end) == null;
        super.tick();
        if (isRemoved() || !movingBox || !target.isAlive()) return;
        var box = target.getBoundingBox();
        Vec3 point = box.contains(start) ? start : box.clip(start, end).orElse(null);
        if (point != null && level().clip(new ClipContext(start, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
            onHit(new ExtendedEntityRayTraceResult(new EntityResult(target, point, false, false)));
        }
    }

    private static Vec3 targetPoint(Entity target) {
        if (target instanceof VehicleEntity vehicle && !vehicle.enableAABB()) {
            for (var part : vehicle.getOBBs()) {
                if (part.part == OBB.Part.BODY) return OBB.vector3dToVec3(part.center);
            }
        }
        return target.getBoundingBox().getCenter();
    }

    @Override public com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance getModelInstance() {
        var model = com.atsuishio.superbwarfare.resource.model.ProjectileModelReloadListener.INSTANCE.getModel(
                ResourceLocation.fromNamespaceAndPath("superbwarfare", "models/bedrock/projectile/agm_65.geo.json"));
        return model == null ? null : model.createInstance();
    }
}
