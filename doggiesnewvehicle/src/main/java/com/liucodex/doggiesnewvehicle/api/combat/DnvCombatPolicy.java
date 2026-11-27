package com.liucodex.doggiesnewvehicle.api.combat;

import java.util.Objects;
import java.util.UUID;

/** Pure target-scope gate shared by native autonomy and optional command providers. */
public final class DnvCombatPolicy {
    private DnvCombatPolicy() {
    }

    /** Explicit native combat capability; only DNV-owned bosses opt in. */
    public interface BossAutonomy {
    }

    public static Decision evaluate(TargetRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.scope() != Scope.BOSS_AUTONOMY
                && (request.targetKind() == TargetKind.PLAYER
                    || request.targetKind() == TargetKind.PLAYER_OCCUPIED_VEHICLE)) {
            return new Decision(Outcome.DENY_NO_PVP, "dnv_no_pvp");
        }
        if (request.scope() == Scope.NATIVE_AUTONOMY
                || request.scope() == Scope.BOSS_AUTONOMY) {
            return request.nativeEligible()
                    ? new Decision(Outcome.ALLOW_NATIVE, "native_target")
                    : new Decision(Outcome.DENY_NOT_NATIVE_TARGET, "not_native_target");
        }
        if (!request.explicitDesignation()) {
            return new Decision(Outcome.DENY_NOT_DESIGNATED, "not_designated");
        }
        return new Decision(Outcome.ALLOW_EXTERNAL_NON_PVP, "external_non_pvp");
    }

    public record TargetRequest(
            UUID sourceId,
            UUID targetId,
            Scope scope,
            TargetKind targetKind,
            boolean nativeEligible,
            boolean explicitDesignation) {
        public TargetRequest {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(targetKind, "targetKind");
        }
    }

    public record Decision(Outcome outcome, String reason) {
        public Decision {
            Objects.requireNonNull(outcome, "outcome");
            reason = Objects.requireNonNullElse(reason, "").trim();
        }

        public boolean allowed() {
            return outcome == Outcome.ALLOW_NATIVE
                    || outcome == Outcome.ALLOW_EXTERNAL_NON_PVP;
        }
    }

    public enum Scope {
        NATIVE_AUTONOMY,
        EXTERNAL_ASSIGNMENT,
        BOSS_AUTONOMY
    }

    public enum TargetKind {
        PLAYER,
        PLAYER_OCCUPIED_VEHICLE,
        HOSTILE_LIVING,
        OTHER_LIVING,
        OTHER_VEHICLE,
        OTHER
    }

    public enum Outcome {
        ALLOW_NATIVE,
        ALLOW_EXTERNAL_NON_PVP,
        DENY_NO_PVP,
        DENY_NOT_NATIVE_TARGET,
        DENY_NOT_DESIGNATED
    }
}
