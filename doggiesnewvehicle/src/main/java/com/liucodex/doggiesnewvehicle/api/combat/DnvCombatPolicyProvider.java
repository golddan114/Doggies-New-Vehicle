package com.liucodex.doggiesnewvehicle.api.combat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

/**
 * Stable optional command seam. Providers may designate non-player targets,
 * but cannot expand DNV's ambient autonomous target set or bypass final gates.
 */
public interface DnvCombatPolicyProvider {
    int CONTRACT_VERSION = 2;
    int MAX_SCOPE_ID_LENGTH = 96;

    String providerId();

    int contractVersion();

    Optional<Assignment> assignment(Entity subordinate);

    default LossBehavior lossBehavior(Entity subordinate) {
        return LossBehavior.NATIVE_FALLBACK;
    }

    default FireDecision authorizeFire(Entity subordinate, Assignment assignment) {
        return FireDecision.HOLD;
    }

    default FireDecision authorizeFire(
            Entity subordinate, Assignment assignment, FireAttempt attempt) {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(attempt, "attempt");
        return assignment.appliesTo(attempt)
                ? authorizeFire(subordinate, assignment)
                : FireDecision.DENY;
    }

    default boolean excludesTarget(Entity target) {
        return false;
    }

    record Assignment(
            UUID subordinateId,
            UUID targetId,
            long revision,
            long validUntilTick,
            boolean explicitDesignation,
            Optional<String> stationId,
            Optional<String> weaponGroupId) {
        public Assignment {
            Objects.requireNonNull(subordinateId, "subordinateId");
            Objects.requireNonNull(targetId, "targetId");
            stationId = normalizeOptionalId(stationId, "stationId");
            weaponGroupId = normalizeOptionalId(weaponGroupId, "weaponGroupId");
            if (revision < 0L || validUntilTick < 0L) {
                throw new IllegalArgumentException(
                        "assignment revision and lifetime must not be negative");
            }
        }

        public Assignment(
                UUID subordinateId,
                UUID targetId,
                long revision,
                long validUntilTick,
                boolean explicitDesignation) {
            this(
                    subordinateId,
                    targetId,
                    revision,
                    validUntilTick,
                    explicitDesignation,
                    Optional.empty(),
                    Optional.empty());
        }

        public boolean expired(long gameTime) {
            return gameTime > validUntilTick;
        }

        public boolean appliesTo(FireAttempt attempt) {
            Objects.requireNonNull(attempt, "attempt");
            return attempt.assignmentRevision() == revision
                    && !expired(attempt.attemptTick())
                    && stationId.map(attempt.stationId()::equals).orElse(true)
                    && weaponGroupId.map(attempt.weaponGroupId()::equals).orElse(true);
        }
    }

    record FireAttempt(
            String stationId,
            String weaponGroupId,
            long assignmentRevision,
            long attemptTick) {
        public FireAttempt {
            stationId = requireId(stationId, "stationId");
            weaponGroupId = requireId(weaponGroupId, "weaponGroupId");
            if (assignmentRevision < 0L || attemptTick < 0L) {
                throw new IllegalArgumentException(
                        "fire attempt revision and tick must not be negative");
            }
        }
    }

    enum LossBehavior {
        NATIVE_FALLBACK,
        HOLD_LAST_TARGET,
        DISABLE
    }

    enum FireDecision {
        ALLOW,
        HOLD,
        DENY
    }

    private static Optional<String> normalizeOptionalId(
            Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(id -> requireId(id, label));
    }

    private static String requireId(String value, String label) {
        String checked = Objects.requireNonNull(value, label).trim();
        if (checked.isEmpty() || checked.length() > MAX_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException(label + " must be bounded text");
        }
        return checked;
    }
}
