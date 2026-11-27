package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvCombatNetworkArtifactContractTest {
    private static final Path PROJECT = Path.of("").toAbsolutePath();

    @Test
    void allFiveAutonomousUnitsPreferAssignmentsAndEnforceFireAuthorization() throws IOException {
        for (String relative : new String[] {
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/JingzheTurretEntity.java",
                "src/boss/java/com/liucodex/noise/entity/NoiseVehicleEntity.java",
                "src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity.java"
        }) {
            String source = readEntityFamily(relative);
            boolean usesSharedTargetMemory = relative.contains("ChangfengCannonEntity")
                    || relative.contains("StormTurretEntity")
                    || relative.contains("JingzheTurretEntity");
            assertTrue(source.contains(usesSharedTargetMemory
                    ? "DnvCombatCoordinator.targetCommand(vehicle)"
                    : "DnvCombatCoordinator.assignedTarget(this)"), relative);
            assertTrue(source.contains(usesSharedTargetMemory
                    ? "DnvCombatCoordinator.fireAuthorized("
                    : "DnvCombatCoordinator.fireAuthorizedBoss("), relative);
        }
    }

    @Test
    void allThreeTurretsRouteCommandAndAutonomousCandidatesThroughSharedTargetMemory()
            throws IOException {
        String memory = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/core/DnvTurretTargetMemory.java");
        for (String relative : new String[] {
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/JingzheTurretEntity.java"
        }) {
            String source = readEntityFamily(relative);
            assertTrue(source.contains("DnvTurretTargetMemory.Candidate.command("), relative);
            assertTrue(source.contains("DnvTurretTargetMemory.Candidate.autonomous("), relative);
            assertTrue(source.contains(".decide("), relative);
            assertTrue(source.contains("targetDecisionSnapshot("), relative);
            assertTrue(source.contains(".targetingContext()"), relative);
            assertTrue(source.contains(".targetCommand()"), relative);
            assertFalse(source.contains("DnvCombatCoordinator.assignedTarget(this)"), relative);
        }
        assertTrue(memory.contains("CONFIGURATION_CHANGED"));
        assertTrue(memory.contains("TTL_EXPIRED"));
        assertTrue(memory.contains("lastVerifiedPosition"));
    }

    @Test
    void currentAutonomousUnitsRejectUnleasedTacticalMapFireEntrypoints() throws IOException {
        for (String relative : new String[] {
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/JingzheTurretEntity.java",
                "src/boss/java/com/liucodex/noise/entity/NoiseVehicleEntity.java",
                "src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity.java"
        }) {
            String family = readEntityFamily(relative)
                    .replace("@Nullable", "")
                    .replaceAll("\\s+", "");
            assertTrue(family.contains(
                    "vehicleShoot(LivingEntityliving,UUIDtargetUuid,Vec3targetPos)"
            ), relative);
            assertTrue(family.contains(
                    "vehicleShoot(LivingEntityliving,StringweaponName,UUIDtargetUuid,Vec3targetPos)"
            ), relative);
            assertTrue(family.contains(
                    "Existingautonomousunitshavenovalidatedremoteweapon-stationlease."
            ), relative);
        }
    }

    @Test
    void dnvMainUsesOnlyTheNeutralProviderContract() throws IOException {
        String main = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/DoggiesNewVehicle.java"
        );
        String provider = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/api/combat/"
                        + "DnvCombatPolicyProvider.java"
        );
        String coordinator = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator.java"
        );
        assertFalse(main.contains("casterhud"));
        assertFalse(provider.contains("casterhud"));
        assertFalse(coordinator.contains("casterhud"));
        assertTrue(provider.contains("explicitDesignation"));
        assertTrue(coordinator.contains("DnvCombatPolicyRuntime.isPvpProtected"));
    }

    @Test
    void independentBossesRecheckVanillaTeamsWithoutOpeningCrewPvp() throws IOException {
        String runtime = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime.java");
        assertTrue(runtime.contains("source.isAlliedTo(passenger) || passenger.isAlliedTo(source)"));
        assertTrue(runtime.contains("DnvCombatPolicy.Scope.BOSS_AUTONOMY"));
        assertTrue(runtime.contains("source instanceof DnvCombatPolicy.BossAutonomy"));
        assertTrue(runtime.contains("DnvCombatPolicy.Scope.NATIVE_AUTONOMY"));
        for (String family : new String[] {"silentheaven", "noise"}) {
            String prefix = family.equals("silentheaven")
                    ? "AbstractSilentHeavenVehicle" : "AbstractNoiseVehicle";
            String root = "src/boss/java/com/liucodex/" + family + "/entity/";
            String lifecycle = read(root + prefix + "Lifecycle.java");
            String weapons = read(root + prefix + "Weapons.java");
            assertTrue(lifecycle.contains("bossRelationsAllow(this, entity)"), family);
            assertTrue(lifecycle.contains("implements DnvCombatPolicy.BossAutonomy"), family);
            assertTrue(lifecycle.contains(family.equals("noise") ? "allowsBossLaserTarget(" : "allowsBossNativeTarget("), family);
            assertFalse(lifecycle.contains("isPvpProtected(entity)"), family);
            assertTrue(lifecycle.contains("return isCheapTargetValid(entity)"
                    + " && targetCandidates.isAuthorized(entity);"), family);
            assertTrue(weapons.contains("isTrackedTargetValid(target)"), family);
            assertTrue(weapons.contains("DnvCombatCoordinator.fireAuthorizedBoss("), family);
        }
        String coordinator = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator.java");
        assertTrue(coordinator.contains("return !hasLiveLease(subordinate);"));
        assertTrue(coordinator.contains("DnvCombatPolicyRuntime.allowsBossNativeTarget("));
    }

    @Test
    void activeProviderErrorsFailClosedInTheCoordinator() throws IOException {
        String coordinator = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator.java"
        );
        assertTrue(coordinator.contains("return LossBehavior.DISABLE;"));
        assertTrue(coordinator.contains("return false;"));
        assertTrue(coordinator.contains(
                "lease.providerError() ? LossBehavior.DISABLE"));
        assertTrue(coordinator.contains("\"ASSIGNMENT_ENDED\""));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(
                PROJECT.resolve(relative),
                StandardCharsets.UTF_8
        );
    }

    private static String readEntityFamily(String relative) throws IOException {
        Path source = PROJECT.resolve(relative);
        String fileName = source.getFileName().toString();
        String fragment = fileName.startsWith("JingzheTurret")
                ? "JingzheTurret"
                : fileName.startsWith("NoiseVehicle")
                        ? "NoiseVehicle"
                        : fileName.startsWith("SilentHeavenVehicle")
                                ? "SilentHeavenVehicle"
                                : null;
        if (fragment == null) {
            return read(relative) + (fileName.equals("ChangfengCannonEntity.java") || fileName.equals("StormTurretEntity.java")
                    ? read("src/artillery/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwTurretExecution.java") : "");
        }
        try (var sources = Files.list(source.getParent())) {
            return sources
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().contains(fragment))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n"))
                    + (fragment.equals("JingzheTurret")
                        ? read("src/artillery/java/com/liucodex/doggiesnewvehicle/compat/DnvJingzheTurretExecution.java") : "");
        } catch (java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
