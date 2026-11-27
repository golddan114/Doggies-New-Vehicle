package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Maps live entities into the pure DNV no-PVP target contract. */
public final class DnvCombatPolicyRuntime {
    public static final double CREW_TARGET_RANGE = 128.0D;
    private DnvCombatPolicyRuntime() {
    }

    public static boolean crewCandidate(VehicleEntity source, Entity target, Optional<UUID> owner) {
        return target != null && !DnvTargetExclusions.isUnavailableCombatTarget(target)
                && target != source && target.level() == source.level()
                && target.getRootVehicle() != source && source.getRootVehicle() != target
                && (target instanceof LivingEntity || target instanceof VehicleEntity)
                && source.distanceToSqr(target) <= CREW_TARGET_RANGE * CREW_TARGET_RANGE
                && owner.filter(target.getUUID()::equals).isEmpty()
                && owner.filter(id -> target.getSelfAndPassengers()
                    .anyMatch(passenger -> id.equals(passenger.getUUID()))).isEmpty()
                && !isPvpProtected(target);
    }

    public static boolean crewHostile(VehicleEntity source, Entity target, Optional<UUID> owner) {
        if (!crewCandidate(source, target, owner) || crewFriendly(source, target, owner)) {
            return false;
        }
        ServerPlayer relationOwner = !(target instanceof Enemy)
                && source.level() instanceof ServerLevel level && owner.isPresent()
                ? level.getServer().getPlayerList().getPlayer(owner.orElseThrow()) : null;
        boolean nativeHostile = target instanceof Enemy && target instanceof LivingEntity living
                ? living.getHealth() > 0.0F
                : relationOwner != null && relationOwner.getTeam() != null
                    && target.getTeam() != null && !target.isAlliedTo(relationOwner);
        return allowsSbwNativeTarget(source, target, relationOwner, nativeHostile);
    }

    /** Re-evaluated on the server tick immediately before a station executes a cached intent. */
    public static boolean crewExecutionAllowed(VehicleEntity source, Entity target, Optional<UUID> owner) {
        if (!crewCandidate(source, target, owner)) return false;
        return executionRelationsAllowed(crewFriendly(source, target, owner),
                crewHostile(source, target, owner), DnvCombatCoordinator.isActiveAssignment(source, target));
    }

    static boolean executionRelationsAllowed(boolean friendly, boolean nativeHostile, boolean assigned) {
        return !friendly && (nativeHostile || assigned);
    }

    private static boolean crewFriendly(VehicleEntity source, Entity target, Optional<UUID> owner) {
        ServerPlayer relationOwner = source.level() instanceof ServerLevel level && owner.isPresent()
                ? level.getServer().getPlayerList().getPlayer(owner.orElseThrow()) : null;
        return target.isAlliedTo(source) || relationOwner != null && target.isAlliedTo(relationOwner)
                || target.getTeam() != null && (SeekTool.IS_FRIENDLY.test(source, target)
                    || relationOwner != null && SeekTool.IS_FRIENDLY.test(relationOwner, target));
    }

    /** A local designation cannot turn a friendly, player or neutral entity into a hostile one. */
    public static boolean crewDesignationAllowed(VehicleEntity source, Entity target, UUID owner) {
        if (!crewHostile(source, target, Optional.of(owner))
                || !(source.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerPlayer relationOwner = level.getServer().getPlayerList().getPlayer(owner);
        boolean friendly = target.isAlliedTo(source) || relationOwner != null
                && (target.isAlliedTo(relationOwner) || target.getTeam() != null
                    && (SeekTool.IS_FRIENDLY.test(relationOwner, target)
                        || SeekTool.IS_FRIENDLY.test(source, target)));
        return designationRelationsAllowed(true, relationOwner != null, friendly);
    }

    static boolean designationRelationsAllowed(boolean nativeHostile, boolean ownerPresent, boolean friendly) {
        return nativeHostile && ownerPresent && !friendly;
    }

    public static boolean isPvpProtected(Entity target) {
        return target != null && (target instanceof Player
                || target.getSelfAndPassengers().anyMatch(Player.class::isInstance));
    }

    public static boolean allowsNativeTarget(
            Entity source,
            Entity target,
            boolean nativeEligible) {
        if (source == null || target == null || DnvCombatCoordinator.isExcluded(target)) {
            return false;
        }
        return DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                source.getUUID(),
                target.getUUID(),
                DnvCombatPolicy.Scope.NATIVE_AUTONOMY,
                kind(target),
                nativeEligible,
                false)).allowed();
    }

    /** Rechecked for retained targets and immediately before firing, including player mode changes. */
    public static boolean bossRelationsAllow(Entity source, Entity target) {
        if (source == null || target == null || source == target || isProtectedPlayerTarget(target)) {
            return false;
        }
        return target.getSelfAndPassengers().noneMatch(passenger ->
                source.isAlliedTo(passenger) || passenger.isAlliedTo(source));
    }

    /** Player mode protection also applies to blast damage, without changing explosion team semantics. */
    public static boolean isProtectedPlayerTarget(Entity target) {
        return target != null && target.getSelfAndPassengers().anyMatch(passenger ->
                passenger.isSpectator() || passenger instanceof Player player && player.isCreative());
    }

    public static boolean allowsBossNativeTarget(
            Entity source, Entity target, boolean nativeEligible) {
        return allowsBossTarget(source, target, nativeEligible, false);
    }

    public static boolean allowsBossLaserTarget(Entity source, Entity target, boolean nativeEligible) {
        return allowsBossTarget(source, target, nativeEligible, true);
    }

    private static boolean allowsBossTarget(Entity source, Entity target, boolean nativeEligible, boolean laser) {
        if (!(source instanceof DnvCombatPolicy.BossAutonomy)
                || !bossRelationsAllow(source, target)
                || (laser ? DnvTargetExclusions.isUnavailableLaserTarget(target)
                          : DnvTargetExclusions.isUnavailableCombatTarget(target))) {
            return false;
        }
        return DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                source.getUUID(), target.getUUID(),
                DnvCombatPolicy.Scope.BOSS_AUTONOMY,
                kind(target), nativeEligible, false)).allowed();
    }

    /**
     * Adapts SBW's own vehicle hostility resolver into DNV's native target scope. The DNV
     * no-PVP boundary is evaluated before the upstream resolver and again by the pure policy.
     */
    public static boolean allowsSbwNativeTarget(
            VehicleEntity source,
            Entity target,
            Entity relationOwner,
            boolean intrinsicHostile) {
        if (source == null
                || target == null
                || DnvCombatCoordinator.isExcluded(target)
                || isPvpProtected(target)) {
            return false;
        }
        boolean sbwHostile = relationOwner != null
                && target.getTeam() != null
                && !SeekTool.IS_FRIENDLY.test(relationOwner, target)
                && !SeekTool.IS_FRIENDLY.test(source, target)
                && !target.isAlliedTo(relationOwner);
        return allowsNativeTarget(
                source,
                target,
                nativeHostilityAllowed(false, sbwHostile, intrinsicHostile));
    }

    static boolean nativeHostilityAllowed(
            boolean pvpProtected,
            boolean sbwHostile,
            boolean intrinsicHostile) {
        return !pvpProtected && (sbwHostile || intrinsicHostile);
    }

    public static boolean allowsExternalAssignment(
            Entity source,
            Entity target,
            boolean explicitDesignation) {
        if (source == null || target == null || DnvCombatCoordinator.isExcluded(target)) {
            return false;
        }
        return DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                source.getUUID(),
                target.getUUID(),
                DnvCombatPolicy.Scope.EXTERNAL_ASSIGNMENT,
                kind(target),
                false,
                explicitDesignation)).allowed();
    }

    /** Keeps an accepted explicit assignment alive without letting providers expand native scans. */
    public static boolean allowsNativeOrActiveAssignment(
            Entity source,
            Entity target,
            boolean nativeEligible) {
        return DnvCombatCoordinator.isActiveAssignment(source, target)
                || allowsNativeTarget(source, target, nativeEligible);
    }

    public static boolean isNativeHostileLiving(Entity source, Entity target) {
        boolean hostile = target instanceof LivingEntity living
                && living instanceof Enemy
                && living.getHealth() > 0.0F;
        return allowsNativeTarget(source, target, hostile);
    }

    private static DnvCombatPolicy.TargetKind kind(Entity target) {
        if (target instanceof Player) {
            return DnvCombatPolicy.TargetKind.PLAYER;
        }
        if (isPvpProtected(target)) {
            return DnvCombatPolicy.TargetKind.PLAYER_OCCUPIED_VEHICLE;
        }
        if (target instanceof LivingEntity living && living instanceof Enemy) {
            return DnvCombatPolicy.TargetKind.HOSTILE_LIVING;
        }
        if (target instanceof LivingEntity) {
            return DnvCombatPolicy.TargetKind.OTHER_LIVING;
        }
        if (target instanceof VehicleEntity) {
            return DnvCombatPolicy.TargetKind.OTHER_VEHICLE;
        }
        return DnvCombatPolicy.TargetKind.OTHER;
    }
}
