package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Active targeting only. Projectile, explosion and contact damage retain native semantics. */
public final class ApocalypseAlliedTargets {
    private ApocalypseAlliedTargets() { }

    public static boolean eligible(Entity source, Entity target) {
        if (source == null || target == null || source == target || source.level() != target.level()
                || source.getRootVehicle() == target.getRootVehicle()
                || DnvTargetExclusions.isUnavailableCombatTarget(target)
                || target instanceof VehicleEntity vehicle && vehicle.isWreck()) return false;
        if (target.getSelfAndPassengers().anyMatch(member -> protectedEntity(source, member))) return false;
        boolean hostile = target instanceof DnvCombatPolicy.BossAutonomy
                || target instanceof LivingEntity && target instanceof Enemy && !(target instanceof NeutralMob);
        return DnvCombatPolicyRuntime.allowsNativeTarget(source, target, hostile);
    }

    private static boolean protectedEntity(Entity source, Entity target) {
        return target instanceof Player || target.isSpectator()
                || source.isAlliedTo(target) || target.isAlliedTo(source) || SeekTool.IS_FRIENDLY.test(source, target)
                || target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                || target instanceof AutoAimableEntity owned && owned.getOptionalOwnerUUID().isPresent();
    }
}
