package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvPerformanceArtifactContractTest {
    private static final Path PROJECT = Path.of("").toAbsolutePath();

    @Test
    void bossesOwnIndependentInstancesOfTheSharedAutonomyCore() throws IOException {
        for (String source : new String[] {
                readEntityFamily(
                        "src/boss/java/com/liucodex/noise/entity/NoiseVehicleEntity.java"),
                readEntityFamily(
                        "src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity.java")
        }) {
            assertTrue(source.contains("protected final DnvAutonomyCore autonomyCore"));
            assertTrue(source.contains(
                    "new DnvAutonomyCore(PRESSURE_MIN_RANGE, PRESSURE_MAX_RANGE)"));
            assertTrue(source.contains("autonomyCore.decide("));
            assertTrue(source.contains("new DnvAutonomyCore.PerceptionSnapshot("));
            assertTrue(source.contains("autonomyMayFire("));
            assertTrue(!source.contains("static final DnvAutonomyCore autonomyCore"));
        }
    }

    @Test
    void autonomousCombatEntitiesUseStaggeredLowFrequencyValidation() throws IOException {
        String changfeng = read("src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java");
        String storm = read("src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java");
        String jingzhe = readEntityFamily(
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/JingzheTurretEntity.java");
        for (String source : new String[] {changfeng, storm, jingzhe}) {
            assertTrue(source.contains("targetCandidates.refreshIfDue("));
            assertTrue(source.contains("DnvTargetingBudget.admitGeometryCheck("));
            assertTrue(source.contains("DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL"));
            assertTrue(source.contains("DnvTargetingBudget.RELATION_RECHECK_INTERVAL"));
            assertTrue(source.contains("DnvTargetExclusions.isUnavailableCombatTarget("));
        }
        assertTrue(jingzhe.contains(
                "TARGET_SCAN_INTERVAL = DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL"));
    }

    @Test
    void bossesShareCandidatesAndLeaveMissileFlightToEachProjectile() throws IOException {
        String noise = readEntityFamily(
                "src/boss/java/com/liucodex/noise/entity/NoiseVehicleEntity.java");
        String silent = readEntityFamily(
                "src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity.java");
        for (String source : new String[] {noise, silent}) {
            assertTrue(source.contains("new DnvTargetCandidateCache(TARGET_SCAN_INTERVAL)"));
            assertTrue(source.contains("DnvBossVlsGuidance.arm("));
            assertFalse(source.contains("recoverGuidedMissiles(serverLevel)"));
            assertFalse(source.contains("guidedMissiles"));
        }
        assertTrue(noise.contains("DnvTargetExclusions.isUnavailableLaserTarget("));
        assertTrue(silent.contains("DnvTargetExclusions.isUnavailableCombatTarget("));
        assertTrue(silent.contains("recoverFallingWarheads(serverLevel)"));
        assertTrue(!noise.contains("recoverFallingWarheads(serverLevel)"));
    }

    @Test
    void bossesUseDelayedNoWreckDeathAndZeroEightNineDamageData()
            throws IOException {
        for (String relative : new String[] {
                "src/boss/java/com/liucodex/noise/entity/NoiseVehicleEntity.java",
                "src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity.java"
        }) {
            String source = readEntityFamily(relative);
            assertTrue(source.contains("if (isWreck() || getHealth() <= 0.0F)"));
            assertTrue(source.contains("clearCombatStateForWreck()"));
            assertTrue(source.contains("targetCandidates.clear();"));
            assertTrue(source.contains("tickDestructionSequence()"));
        }
        for (String id : new String[] {
                "50_changfeng_cannon", "storm_turret", "jingzhe_turret",
                "noise", "silent_heaven"
        }) {
            String sourceSet = isBoss(id) ? "boss" : "artillery";
            Path loot = PROJECT.resolve(
                    "src/" + sourceSet + "/resources/data/doggievehicle/sbw/loot/"
                            + id + ".json");
            assertTrue(Files.exists(loot), id);
            assertTrue(readVehicleData(id).contains("\"HasLowHealthWarning\": true"), id);
        }
        assertTrue(!Files.exists(PROJECT.resolve(
                "src/main/resources/data/doggiesnewvehicle/sbw/vehicles/noise.json")));
        assertTrue(!Files.exists(PROJECT.resolve(
                "src/main/resources/data/doggiesnewvehicle/sbw/vehicles/silent_heaven.json")));
    }

    @Test
    void clientLoopSoundDiscoveryRunsOncePerSecond() throws IOException {
        String shared = read(
                "src/main/java/com/liucodex/doggiesnewvehicle/client/"
                        + "DnvVehicleLoopSoundManager.java");
        String noise = read("src/boss/java/com/liucodex/noise/client/NoiseSoundManager.java");
        String silent = read(
                "src/boss/java/com/liucodex/silentheaven/client/SilentHeavenSoundManager.java");
        String jingzhe = read(
                "src/artillery/java/com/liucodex/doggiesnewvehicle/client/JingzheSoundManager.java");
        assertTrue(shared.contains("DISCOVERY_INTERVAL = 20"));
        assertTrue(shared.contains(
                "Math.floorMod(minecraft.player.tickCount"));
        assertTrue(noise.contains("new DnvVehicleLoopSoundManager<>("));
        assertTrue(silent.contains("new DnvVehicleLoopSoundManager<>("));
        assertTrue(jingzhe.contains("DISCOVERY_INTERVAL = 20"));
        assertTrue(jingzhe.contains(
                "Math.floorMod(minecraft.player.tickCount"));
    }

    @Test
    void allFiveAutonomousCombatUnitsUseTheSharedBoundedCandidateCache() throws IOException {
        for (String relative : new String[] {
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java",
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/JingzheTurretEntity.java",
                "src/boss/java/com/liucodex/noise/entity/NoiseVehicleEntity.java",
                "src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity.java"
        }) {
            String source = readEntityFamily(relative);
            assertTrue(source.contains("targetCandidates.refreshIfDue("), relative);
            assertTrue(!source.contains("getAllEntities()"), relative);
        }
        String cache = read("src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache.java");
        assertTrue(cache.contains("MAX_CANDIDATES = 32"));
        assertTrue(cache.contains("DnvTargetingBudget.admitScanStep("));
    }

    @Test
    void intrinsicTurretTieBreaksUsePersistentIdentityInsteadOfRuntimeEntityIds()
            throws IOException {
        for (String source : new String[] {
                read("src/artillery/java/com/liucodex/doggiesnewvehicle/entity/ChangfengCannonEntity.java"),
                read("src/artillery/java/com/liucodex/doggiesnewvehicle/entity/StormTurretEntity.java")
        }) {
            assertTrue(source.contains("DnvTargetCandidateCache::compareStableIds"));
            assertTrue(!source.contains("thenComparingInt(Entity::getId)"));
        }
        String jingzhe = readEntityFamily(
                "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/JingzheTurretEntity.java");
        assertTrue(jingzhe.contains("targetCandidates.candidates(this)"));
        String cache = read("src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache.java");
        assertTrue(cache.contains("thenComparing(RankedId::id, DnvTargetCandidateCache::compareStableIds)"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String readVehicleData(String id) throws IOException {
        return read("src/" + (isBoss(id) ? "boss" : "artillery")
                + "/resources/data/doggievehicle"
                + "/sbw/vehicles/" + id + ".json");
    }

    private static boolean isBoss(String id) {
        return id.equals("noise") || id.equals("silent_heaven");
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
            return read(relative);
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
                    .collect(java.util.stream.Collectors.joining("\n"));
        } catch (java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
