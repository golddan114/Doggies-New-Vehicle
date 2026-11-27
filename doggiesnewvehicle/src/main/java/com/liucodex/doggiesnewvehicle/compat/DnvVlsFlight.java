package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

/** Native movement, collision, damage, warning and decoys remain with SBW; only DNV VLS steering is adapted. */
public final class DnvVlsFlight {
    public static final String ACTIVE = "DnvVlsFlight";
    public static final String OWNED = "DnvVlsOwned";
    private static final String TURNS = "DnvVlsFlightTicks";
    private static final String PENDING = "DnvVlsPending";
    private static final String TARGET = "DnvVlsTarget";
    private static final String CLEARANCE_Y = "DnvVlsClearanceY";
    private static final String LAUNCH_SPEED = "DnvVlsLaunchSpeed";
    private static final String CLIMB = "DnvVlsClimb";
    private static final String NEAR_TICKS = "DnvVlsNearTicks";
    private static final String NEAR_TARGET = "DnvVlsNearTarget";
    private static final String NEAR_TIME = "DnvVlsNearTime";
    private DnvVlsFlight() { }

    /** The missile keeps its own target and hull-clearance state across boss unloads and saves. */
    public static void armEjection(JavelinMissileEntity missile, Entity target, double clearanceY, float launchSpeed) {
        CompoundTag data = missile.getPersistentData();
        data.putBoolean(OWNED, true);
        data.putBoolean(PENDING, true);
        data.putUUID(TARGET, target.getUUID());
        data.putDouble(CLEARANCE_Y, clearanceY);
        data.putFloat(LAUNCH_SPEED, launchSpeed);
        data.putInt(CLIMB, 0);
        data.remove(ACTIVE);
        missile.setAttackMode(false);
        missile.setTargetUuid("none");
        missile.setGuideType(2);
    }

    /** Run before native movement or collision so a lost target cannot cause a damaging impact. */
    public static boolean discardIfTargetLost(JavelinMissileEntity missile) {
        if (!(missile.level() instanceof ServerLevel server) || missile.isRemoved()
                || !missile.getPersistentData().getBoolean(OWNED)) return false;
        if (loadedTarget(missile, server) != null) return false;
        server.sendParticles(ParticleTypes.EXPLOSION, missile.getX(), missile.getY(), missile.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        missile.discard();
        return true;
    }

    /** Native blast only, at the missile position: no artificial direct hit or remote target damage. */
    public static boolean beforeNativeTick(JavelinMissileEntity missile) {
        if (discardIfTargetLost(missile)) return true;
        if (!(missile.level() instanceof ServerLevel server) || missile.isRemoved()
                || !missile.getPersistentData().getBoolean(OWNED)) return false;
        CompoundTag data = missile.getPersistentData();
        Entity target = loadedTarget(missile, server);
        if (target == null || !active(missile) || data.getBoolean(PENDING) || missile.tickCount < 12) {
            data.putInt(NEAR_TICKS, 0); return false;
        }
        if (!data.hasUUID(NEAR_TARGET) || !target.getUUID().equals(data.getUUID(NEAR_TARGET))) {
            data.putUUID(NEAR_TARGET, target.getUUID()); data.putInt(NEAR_TICKS, 0); data.remove(NEAR_TIME);
        }
        var bounds = target.getBoundingBox();
        var position = missile.position();
        var nearest = new net.minecraft.world.phys.Vec3(
            Math.max(bounds.minX, Math.min(bounds.maxX, position.x)),
            Math.max(bounds.minY, Math.min(bounds.maxY, position.y)),
            Math.max(bounds.minZ, Math.min(bounds.maxZ, position.z)));
        long now = server.getGameTime();
        if (position.distanceToSqr(nearest) > 4) { data.putInt(NEAR_TICKS, 0); data.putLong(NEAR_TIME, now); return false; }
        if (data.contains(NEAR_TIME) && data.getLong(NEAR_TIME) == now) return false;
        int near = data.contains(NEAR_TIME) && now - data.getLong(NEAR_TIME) == 1
            ? Math.min(6, Math.max(0, data.getInt(NEAR_TICKS)) + 1) : 1;
        data.putLong(NEAR_TIME, now); data.putInt(NEAR_TICKS, near);
        if (near < 6) return false;
        // At most one local ray per six close ticks; walls and unloaded chunks never become blast permissions.
        data.putInt(NEAR_TICKS, 0);
        if (!server.hasChunksAt(net.minecraft.core.BlockPos.containing(position), net.minecraft.core.BlockPos.containing(nearest))
                || server.clip(new net.minecraft.world.level.ClipContext(position, nearest,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, missile))
                    .getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        missile.causeExplode(position);
        missile.discard();
        return true;
    }

    private static Entity loadedTarget(JavelinMissileEntity missile, ServerLevel server) {
        CompoundTag data = missile.getPersistentData();
        Entity target;
        if (data.getBoolean(PENDING)) {
            if (!data.hasUUID(TARGET)) return null;
            UUID id = data.getUUID(TARGET);
            target = server.getEntity(id);
        } else {
            target = EntityFindUtil.findEntity(server, missile.getTargetUUID());
            if (target != null && server.getEntity(target.getUUID()) != target) return null;
        }
        return target != null && target.isAlive() ? target : null;
    }

    public static boolean active(JavelinMissileEntity missile) {
        CompoundTag data = missile.getPersistentData();
        return (data.getBoolean(ACTIVE) || data.getBoolean(PENDING)) && !missile.isDistracted();
    }

    public static void tick(JavelinMissileEntity missile) {
        if (!(missile.level() instanceof ServerLevel server) || missile.isRemoved()
                || !missile.getPersistentData().getBoolean(OWNED)) return;
        Entity target = loadedTarget(missile, server);
        if (target == null) {
            discardIfTargetLost(missile);
            return;
        }
        CompoundTag data = missile.getPersistentData();
        if (data.getBoolean(PENDING)) {
            int climb = data.getInt(CLIMB);
            if (climb < 3 || missile.getY() < data.getDouble(CLEARANCE_Y)) {
                data.putInt(CLIMB, climb + 1);
                missile.setTargetUuid("none");
                missile.setGuideType(2);
                missile.setDeltaMovement(0.0D, data.getFloat(LAUNCH_SPEED), 0.0D);
                missile.setYRot(0.0F);
                missile.setXRot(-90.0F);
                return;
            }
            data.remove(PENDING);
            data.putBoolean(ACTIVE, true);
            missile.setTargetUuid(target.getStringUUID());
            missile.setTargetVec(target.getBoundingBox().getCenter());
            missile.setGuideType(0);
            return;
        }
        if (!active(missile) || missile.tickCount <= 3) return;
        int turning = Math.min(40, Math.max(0, Math.min(40, missile.getPersistentData().getInt(TURNS))) + 1);
        missile.getPersistentData().putInt(TURNS, turning);
        var plan = DnvMissileIntercept.plan(missile.position(), missile.getDeltaMovement(), missile.getLookAngle(),
                DnvTargetPoint.center(target, 1), target.getDeltaMovement(), turning);
        missile.turn(plan.direction(), plan.turnDegrees());
        missile.setDeltaMovement(missile.getLookAngle().scale(plan.speed()));
    }
}
