package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.Assignment;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.FireDecision;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.FireAttempt;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.LossBehavior;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Runtime owner for optional assignments; native DNV behavior remains the default. */
public final class DnvCombatCoordinator {
    private static final int MAX_ACTIVE_LEASES = 1_024;
    private static final Map<UUID, ActiveLease> ACTIVE_LEASES = new LinkedHashMap<>();

    private DnvCombatCoordinator() {
    }

    /** Immutable command observation for the shared turret target-memory decision. */
    public static TargetCommand targetCommand(Entity subordinate) {
        Optional<Entity> target = assignedTarget(subordinate);
        if (subordinate == null) {
            return TargetCommand.none();
        }
        ActiveLease lease = ACTIVE_LEASES.get(subordinate.getUUID());
        if (lease == null) {
            return TargetCommand.none();
        }
        Assignment assignment = lease.assignment();
        LossBehavior loss = lease.active() ? LossBehavior.NATIVE_FALLBACK
                : lease.providerError() ? LossBehavior.DISABLE : lossBehavior(subordinate);
        if (!lease.active() && !lease.providerError()
                && loss == LossBehavior.NATIVE_FALLBACK) {
            ACTIVE_LEASES.remove(subordinate.getUUID(), lease);
            return new TargetCommand(Optional.empty(), Optional.of(assignment.targetId()),
                    assignment.revision(), assignment.validUntilTick(),
                    assignment.explicitDesignation(), false, loss, "ASSIGNMENT_ENDED");
        }
        return new TargetCommand(
                target,
                Optional.of(assignment.targetId()),
                assignment.revision(),
                assignment.validUntilTick(),
                assignment.explicitDesignation(),
                hasLiveLease(subordinate),
                loss,
                target.isPresent() ? "ACTIVE_ASSIGNMENT" : "ASSIGNMENT_TARGET_UNAVAILABLE");
    }

    public static Optional<Entity> assignedTarget(Entity subordinate) {
        if (subordinate == null || !(subordinate.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        long gameTime = level.getGameTime();
        ActiveLease previous = ACTIVE_LEASES.get(subordinate.getUUID());
        java.util.List<DnvCombatPolicyProvider> providers = providers();
        for (DnvCombatPolicyProvider provider : providers) {
            Optional<Assignment> candidate;
            try {
                candidate = provider.assignment(subordinate);
            } catch (LinkageError | RuntimeException exception) {
                if (previous != null && previous.provider() == provider
                        && !previous.assignment().expired(gameTime)) {
                    remember(subordinate.getUUID(), previous.inactive(true));
                    return Optional.empty();
                }
                continue;
            }
            if (candidate.isEmpty()) {
                if (previous != null && previous.provider() == provider) {
                    if (previous.assignment().expired(gameTime)) {
                        ACTIVE_LEASES.remove(subordinate.getUUID());
                    } else {
                        remember(subordinate.getUUID(), previous.inactive(false));
                    }
                }
                continue;
            }
            Assignment assignment = candidate.orElseThrow();
            if (!subordinate.getUUID().equals(assignment.subordinateId())
                    || assignment.expired(gameTime)) {
                ACTIVE_LEASES.remove(subordinate.getUUID());
                continue;
            }
            Entity target = level.getEntity(assignment.targetId());
            if (!DnvCombatPolicyRuntime.allowsExternalAssignment(
                    subordinate, target, assignment.explicitDesignation())) {
                ACTIVE_LEASES.remove(subordinate.getUUID());
                return Optional.empty();
            }
            remember(subordinate.getUUID(), new ActiveLease(provider, assignment, true, false));
            return Optional.of(target);
        }
        if (previous != null && !providers.contains(previous.provider())) {
            ACTIVE_LEASES.remove(subordinate.getUUID());
        }
        return Optional.empty();
    }

    public static LossBehavior lossBehavior(Entity subordinate) {
        if (subordinate == null) {
            return LossBehavior.NATIVE_FALLBACK;
        }
        ActiveLease lease = ACTIVE_LEASES.get(subordinate.getUUID());
        if (lease == null) {
            return LossBehavior.NATIVE_FALLBACK;
        }
        long gameTime = subordinate.level() instanceof ServerLevel level
                ? level.getGameTime()
                : Long.MAX_VALUE;
        if (lease.assignment().expired(gameTime) || !providers().contains(lease.provider())) {
            ACTIVE_LEASES.remove(subordinate.getUUID());
            return LossBehavior.NATIVE_FALLBACK;
        }
        try {
            return lease.provider().lossBehavior(subordinate);
        } catch (LinkageError | RuntimeException exception) {
            return LossBehavior.DISABLE;
        }
    }

    /** Includes a temporarily lost assignment whose provider still owns the fire lease. */
    static boolean hasLiveLease(Entity subordinate) {
        ActiveLease lease = ACTIVE_LEASES.get(subordinate.getUUID());
        return lease != null && providers().contains(lease.provider())
                && !lease.assignment().expired(subordinate.level().getGameTime());
    }

    /** An in-flight attempt cannot silently fall back to native permission after a provider changes. */
    static Object executionStamp(Entity subordinate) {
        return java.util.Arrays.asList(providers(), ACTIVE_LEASES.get(subordinate.getUUID()));
    }

    public static boolean fireAuthorized(Entity subordinate, Entity target) {
        long gameTime = subordinate != null && subordinate.level() instanceof ServerLevel level
                ? level.getGameTime()
                : Long.MAX_VALUE;
        return fireAuthorized(subordinate, target, "vehicle", "default", gameTime);
    }

    /** Only the two DNV-owned autonomous bosses use this native player-fire boundary. */
    public static boolean fireAuthorizedBoss(Entity subordinate, Entity target) {
        return fireAuthorizedBoss(subordinate, target, false);
    }

    public static boolean fireAuthorizedBossLaser(Entity subordinate, Entity target) {
        return fireAuthorizedBoss(subordinate, target, true);
    }

    private static boolean fireAuthorizedBoss(Entity subordinate, Entity target, boolean laser) {
        boolean nativeEligible = target instanceof net.minecraft.world.entity.LivingEntity
                || target instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
        if (!(laser ? DnvCombatPolicyRuntime.allowsBossLaserTarget(subordinate, target, nativeEligible)
                    : DnvCombatPolicyRuntime.allowsBossNativeTarget(subordinate, target, nativeEligible))) {
            return false;
        }
        if (DnvCombatPolicyRuntime.isPvpProtected(target)) {
            // An optional provider may still own the unit. Its external-assignment
            // no-PVP rule cannot be bypassed by a native boss shot.
            return !hasLiveLease(subordinate);
        }
        return fireAuthorized(subordinate, target);
    }

    public static boolean fireAuthorized(
            Entity subordinate,
            Entity target,
            String stationId,
            String weaponGroupId,
            long attemptTick) {
        if (subordinate == null || target == null
                || DnvCombatPolicyRuntime.isPvpProtected(target)) {
            return false;
        }
        ActiveLease lease = ACTIVE_LEASES.get(subordinate.getUUID());
        if (lease == null) {
            return true;
        }
        long gameTime = subordinate.level() instanceof ServerLevel level
                ? level.getGameTime()
                : Long.MAX_VALUE;
        if (attemptTick != gameTime) {
            return false;
        }
        if (!lease.active()
                || !providers().contains(lease.provider())
                || lease.assignment().expired(gameTime)
                || !lease.assignment().targetId().equals(target.getUUID())) {
            return false;
        }
        try {
            FireAttempt attempt = new FireAttempt(
                    stationId,
                    weaponGroupId,
                    lease.assignment().revision(),
                    attemptTick);
            return lease.provider().authorizeFire(
                            subordinate, lease.assignment(), attempt)
                    == FireDecision.ALLOW;
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    /**
     * Returns whether the target is the still-valid explicit assignment already accepted for this
     * subordinate. Ambient scans must never call this to broaden their native candidate set.
     */
    public static boolean isActiveAssignment(Entity subordinate, Entity target) {
        if (subordinate == null || target == null
                || !(subordinate.level() instanceof ServerLevel level)) {
            return false;
        }
        ActiveLease lease = ACTIVE_LEASES.get(subordinate.getUUID());
        if (lease == null
                || !lease.active()
                || !providers().contains(lease.provider())
                || lease.assignment().expired(level.getGameTime())
                || !lease.assignment().targetId().equals(target.getUUID())) {
            return false;
        }
        return DnvCombatPolicyRuntime.allowsExternalAssignment(
                subordinate, target, lease.assignment().explicitDesignation());
    }

    public static boolean isExcluded(Entity target) {
        if (target == null) {
            return true;
        }
        for (DnvCombatPolicyProvider provider : providers()) {
            try {
                if (provider.excludesTarget(target)) {
                    return true;
                }
            } catch (LinkageError | RuntimeException exception) {
                // Optional exclusions cannot disable native DNV after provider loss.
            }
        }
        return false;
    }

    public static void clearRuntimeState() {
        ACTIVE_LEASES.clear();
    }

    private static java.util.List<DnvCombatPolicyProvider> providers() {
        return DnvCombatPolicyRegistry.global().providers();
    }

    private static void remember(UUID subordinateId, ActiveLease lease) {
        while (ACTIVE_LEASES.size() >= MAX_ACTIVE_LEASES
                && !ACTIVE_LEASES.containsKey(subordinateId)) {
            ACTIVE_LEASES.remove(ACTIVE_LEASES.keySet().iterator().next());
        }
        ACTIVE_LEASES.put(subordinateId, lease);
    }

    private record ActiveLease(
            DnvCombatPolicyProvider provider,
            Assignment assignment,
            boolean active,
            boolean providerError) {
        private ActiveLease inactive(boolean error) {
            return active || providerError != error
                    ? new ActiveLease(provider, assignment, false, error) : this;
        }
    }

    public record TargetCommand(
            Optional<Entity> target,
            Optional<UUID> targetId,
            long revision,
            long validUntilTick,
            boolean explicitDesignation,
            boolean liveLease,
            LossBehavior lossBehavior,
            String reason) {
        public TargetCommand {
            target = java.util.Objects.requireNonNull(target, "target");
            targetId = java.util.Objects.requireNonNull(targetId, "targetId");
            java.util.Objects.requireNonNull(lossBehavior, "lossBehavior");
            java.util.Objects.requireNonNull(reason, "reason");
            if (target.isPresent() && targetId.isEmpty()
                    || revision < -1L || validUntilTick < -1L) {
                throw new IllegalArgumentException("invalid target command snapshot");
            }
        }

        private static TargetCommand none() {
            return new TargetCommand(Optional.empty(), Optional.empty(), -1L, -1L, false,
                    false, LossBehavior.NATIVE_FALLBACK, "NO_ACTIVE_ASSIGNMENT");
        }
    }
}
