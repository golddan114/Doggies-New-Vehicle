package com.liucodex.doggiesnewvehicle.api.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvCombatPolicyContractTest {
    private static final UUID SOURCE = UUID.fromString(
            "00000000-0000-0000-0000-000000000101");
    private static final UUID TARGET = UUID.fromString(
            "00000000-0000-0000-0000-000000000202");

    @Test
    void noPvpBoundaryCannotBeOverriddenByAnExternalAssignment() {
        for (DnvCombatPolicy.TargetKind kind : new DnvCombatPolicy.TargetKind[] {
                DnvCombatPolicy.TargetKind.PLAYER,
                DnvCombatPolicy.TargetKind.PLAYER_OCCUPIED_VEHICLE
        }) {
            assertEquals(
                    DnvCombatPolicy.Outcome.DENY_NO_PVP,
                    DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                            SOURCE,
                            TARGET,
                            DnvCombatPolicy.Scope.EXTERNAL_ASSIGNMENT,
                            kind,
                            false,
                            true)).outcome());
        }
    }

    @Test
    void onlyOwnedBossAutonomyMayAdmitAnEligiblePlayerTarget() {
        assertEquals(0, DnvCombatPolicy.Scope.NATIVE_AUTONOMY.ordinal());
        assertEquals(1, DnvCombatPolicy.Scope.EXTERNAL_ASSIGNMENT.ordinal());
        for (DnvCombatPolicy.TargetKind kind : new DnvCombatPolicy.TargetKind[] {
                DnvCombatPolicy.TargetKind.PLAYER,
                DnvCombatPolicy.TargetKind.PLAYER_OCCUPIED_VEHICLE
        }) {
            assertEquals(DnvCombatPolicy.Outcome.DENY_NO_PVP,
                    DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                            SOURCE, TARGET, DnvCombatPolicy.Scope.NATIVE_AUTONOMY,
                            kind, true, false)).outcome());
            assertEquals(DnvCombatPolicy.Outcome.ALLOW_NATIVE,
                    DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                            SOURCE, TARGET, DnvCombatPolicy.Scope.BOSS_AUTONOMY,
                            kind, true, false)).outcome());
            assertEquals(DnvCombatPolicy.Outcome.DENY_NOT_NATIVE_TARGET,
                    DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                            SOURCE, TARGET, DnvCombatPolicy.Scope.BOSS_AUTONOMY,
                            kind, false, true)).outcome());
        }
    }

    @Test
    void providerCannotExpandAmbientAutonomousTargetEligibility() {
        assertEquals(
                DnvCombatPolicy.Outcome.DENY_NOT_NATIVE_TARGET,
                DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                        SOURCE,
                        TARGET,
                        DnvCombatPolicy.Scope.NATIVE_AUTONOMY,
                        DnvCombatPolicy.TargetKind.OTHER_VEHICLE,
                        false,
                        true)).outcome());
    }

    @Test
    void explicitNonPvpAssignmentMayEnterTheMechanicalSafetyChain() {
        assertEquals(
                DnvCombatPolicy.Outcome.ALLOW_EXTERNAL_NON_PVP,
                DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                        SOURCE,
                        TARGET,
                        DnvCombatPolicy.Scope.EXTERNAL_ASSIGNMENT,
                        DnvCombatPolicy.TargetKind.OTHER_VEHICLE,
                        false,
                        true)).outcome());
        assertEquals(
                DnvCombatPolicy.Outcome.DENY_NOT_DESIGNATED,
                DnvCombatPolicy.evaluate(new DnvCombatPolicy.TargetRequest(
                        SOURCE,
                        TARGET,
                        DnvCombatPolicy.Scope.EXTERNAL_ASSIGNMENT,
                        DnvCombatPolicy.TargetKind.OTHER_VEHICLE,
                        false,
                        false)).outcome());
    }

    @Test
    void providerRegistryIsVersionedBoundedAndDeterministic() {
        DnvCombatPolicyRegistry registry = new DnvCombatPolicyRegistry(2);
        registry.register(provider("zulu"));
        registry.register(provider("alpha"));

        assertEquals(
                java.util.List.of("alpha", "zulu"),
                registry.snapshot().providers().stream()
                        .map(DnvCombatPolicyRegistry.ProviderSnapshot::providerId)
                        .toList());
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(provider("alpha")));
        assertThrows(IllegalStateException.class,
                () -> registry.register(provider("third")));
        assertEquals(true, registry.unregister("alpha"));
        assertEquals(false, registry.contains("alpha"));
        assertEquals(3L, registry.snapshot().revision());
        registry.register(provider("third"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new DnvCombatPolicyProvider() {
                    @Override
                    public String providerId() {
                        return "future";
                    }

                    @Override
                    public int contractVersion() {
                        return DnvCombatPolicyProvider.CONTRACT_VERSION + 1;
                    }

                    @Override
                    public Optional<Assignment> assignment(
                            net.minecraft.world.entity.Entity subordinate) {
                        return Optional.empty();
                    }
                }));
    }

    @Test
    void scopedAssignmentOnlyAppliesToTheNamedStationAndWeaponGroup() {
        DnvCombatPolicyProvider.Assignment scoped = new DnvCombatPolicyProvider.Assignment(
                SOURCE,
                TARGET,
                7L,
                100L,
                true,
                Optional.of("seat_1"),
                Optional.of("cannon_40"));

        assertTrue(scoped.appliesTo(new DnvCombatPolicyProvider.FireAttempt(
                "seat_1", "cannon_40", 7L, 80L)));
        assertFalse(scoped.appliesTo(new DnvCombatPolicyProvider.FireAttempt(
                "seat_0", "cannon_40", 7L, 80L)));
        assertFalse(scoped.appliesTo(new DnvCombatPolicyProvider.FireAttempt(
                "seat_1", "cannon_105", 7L, 80L)));
        assertFalse(scoped.appliesTo(new DnvCombatPolicyProvider.FireAttempt(
                "seat_1", "cannon_40", 6L, 80L)));
        assertFalse(scoped.appliesTo(new DnvCombatPolicyProvider.FireAttempt(
                "seat_1", "cannon_40", 7L, 101L)));
    }

    @Test
    void legacyVehicleWideAssignmentRemainsCompatibleWithContextualAttempts() {
        DnvCombatPolicyProvider.Assignment assignment = new DnvCombatPolicyProvider.Assignment(
                SOURCE, TARGET, 3L, 50L, true);
        assertTrue(assignment.appliesTo(new DnvCombatPolicyProvider.FireAttempt(
                "seat_9", "any_weapon", 3L, 50L)));
    }

    private static DnvCombatPolicyProvider provider(String id) {
        return new DnvCombatPolicyProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public int contractVersion() {
                return CONTRACT_VERSION;
            }

            @Override
            public Optional<Assignment> assignment(
                    net.minecraft.world.entity.Entity subordinate) {
                return Optional.empty();
            }
        };
    }
}
