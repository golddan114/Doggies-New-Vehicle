package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvProjectilePath;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Target category and fire-time validation only; SBW owns acquisition, timing, input and HUD. */
public final class ApocalypseTargeting {
    private final DnvApocalypseEntity tank;

    public ApocalypseTargeting(DnvApocalypseEntity tank) { this.tank = tank; }

    public Entity locked(Player player, UUID requested) {
        if (!(tank.level() instanceof ServerLevel level) || player != tank.getFirstPassenger() || tank.getWeaponIndex(0) != 2
                || requested == null || !player.isAlive() || player.isSpectator()) return null;
        Entity target = level.getEntity(requested);
        return valid(target, player) ? target : null;
    }

    /** Read-only admission shared by the tank's server orders and client firing feedback. */
    boolean valid(Entity target, Player operator) {
        if (!eligible(tank, operator, target) || !SeekTool.NOT_IN_SMOKE.test(target)) return false;
        var gun = tank.getGunData("Missiles");
        var seek = gun == null ? null : gun.get(GunProp.SEEK_WEAPON_INFO);
        if (seek == null) return false;
        Vec3 origin = tank.lockOrigin(), end = target.getBoundingBox().getCenter(), delta = end.subtract(origin);
        if (delta.lengthSqr() > seek.getSeekRange() * seek.getSeekRange() || delta.lengthSqr() < 1) return false;
        return loadedLockRay(tank, origin, end) && tank.level().clip(new ClipContext(
                origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank)).getType() == HitResult.Type.MISS;
    }

    private static boolean loadedLockRay(Entity source, Vec3 from, Vec3 to) {
        var level = source.level();
        var border = level.getWorldBorder();
        // Build height limits block placement. Aircraft and guided projectiles can fly above it.
        return border.isWithinBounds(BlockPos.containing(from)) && border.isWithinBounds(BlockPos.containing(to))
                && DnvProjectilePath.limit(from, to, level::hasChunk) == 1;
    }

    public static boolean eligible(Entity source, Entity operator, Entity target) {
        if (target == null || source == target || target == operator || !target.isAlive() || target.isRemoved()
                || target.level() != source.level() || target.isSpectator() || target.getRootVehicle() == source.getRootVehicle()) return false;
        if (!(target instanceof VehicleEntity) && !(target instanceof LivingEntity && target instanceof Enemy)) return false;
        if (target instanceof VehicleEntity vehicle && vehicle.isWreck()) return false;
        if (source.isAlliedTo(target) || target.isAlliedTo(source) || operator != null && target.isAlliedTo(operator)
                || SeekTool.IS_FRIENDLY.test(source, target) || operator != null && SeekTool.IS_FRIENDLY.test(operator, target)) return false;
        if (target instanceof VehicleEntity vehicle && operator != null && vehicle.getLastDriverUUID().equals(operator.getUUID().toString())) return false;
        if (target instanceof AutoAimableEntity vehicle && operator != null
                && vehicle.getOptionalOwnerUUID().filter(operator.getUUID()::equals).isPresent()) return false;
        return target.getSelfAndPassengers().noneMatch(passenger -> passenger == operator || passenger.isSpectator()
                || passenger instanceof Player player && player.isCreative()
                || source.isAlliedTo(passenger) || operator != null && operator.isAlliedTo(passenger));
    }

    /** Bound the ray to loaded terrain before invoking native clipping; never request chunks for a lock. */
    public static boolean loadedRay(Entity source, Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 8));
        if (steps > 64) return false;
        for (int i = 0; i <= steps; i++) {
            var pos = net.minecraft.core.BlockPos.containing(from.lerp(to, i / (double) steps));
            if (!source.level().hasChunkAt(pos) || !source.level().getWorldBorder().isWithinBounds(pos)
                    || source.level().isOutsideBuildHeight(pos)) return false;
        }
        return true;
    }
}
