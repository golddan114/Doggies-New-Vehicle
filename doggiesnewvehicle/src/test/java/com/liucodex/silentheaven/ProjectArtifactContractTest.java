package com.liucodex.silentheaven;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class ProjectArtifactContractTest {
  private static final Path PROJECT = Path.of("").toAbsolutePath();

  @Test
  void bothPlatformsDeclareTheSameCurrentDnvRelease() throws IOException {
    String build = read(PROJECT.resolve("build.gradle"));
    String metadata = read(PROJECT.resolve("src/main/resources/META-INF/mods.toml"));
    String version = releaseVersion(build);
    assertEquals(version, releaseVersion(read(PROJECT.resolve("build-neoforge.gradle"))));
    assertTrue(build.contains("superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar"));
    assertTrue(metadata.contains("version=\"" + version + "\""));
    assertTrue(read(PROJECT.resolve("src/neoforge1211/main/resources/META-INF/neoforge.mods.toml"))
        .contains("version=\"" + version + "\""));
    assertTrue(metadata.contains("modId=\"doggiesnewvehicle\""));
    assertTrue(metadata.contains("versionRange=\"[0.8.9.2]\""));
    assertFalse(build.contains("geckolib-forge"));
    assertFalse(build.contains("com.eliotlash.mclib"));
    assertFalse(metadata.contains("modId=\"geckolib\""));
    assertTrue(Files.exists(PROJECT.resolve(
        "libs/superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar")));
    assertFalse(Files.exists(PROJECT.resolve("libs/superbwarfare-0.8.9-final.jar")));
    assertFalse(metadata.contains("modId=\"silent_heaven\""));
  }

  @Test
  void turretRenderersUseThePostGeckoSuperbWarfareContract() throws IOException {
    Path client = PROJECT.resolve(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/client");
    String shell = read(client.resolve("DnvTurretRenderer.java"));
    assertTrue(shell.contains("extends EntityRenderer<T>"));
    assertTrue(shell.contains("applyVehicleAxis(entity, entityYaw, partialTick, poseStack)"));
    assertTrue(shell.contains("entity.getBoundingBoxForCulling().inflate(5.0D)"));
    assertFalse(shell.contains("GeoAnimatable"));

    for (String renderer : new String[] {
        "JingzheTurretRenderer.java",
        "StormTurretRenderer.java",
        "EndfieldSniperTurretRenderer.java"
    }) {
      String source = read(client.resolve(renderer));
      assertTrue(source.contains("extends DnvTurretRenderer<"), renderer);
      assertFalse(source.contains("VehicleRenderer<"), renderer);
      assertFalse(source.contains("VehicleMeshRenderLayer"), renderer);
    }
    assertFalse(Files.exists(client.resolve("VehicleMeshRenderLayer.java")));
  }

  @Test
  void turretFallbackTargetingUsesTheCurrentSbwTeamResolver() throws IOException {
    Path entities = PROJECT.resolve(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity");
    for (String entity : new String[] {
        "JingzheTurretEntity.java",
        "StormTurretEntity.java",
        "ChangfengCannonEntity.java"
    }) {
      String source = entity.equals("JingzheTurretEntity.java")
          ? readJavaFamily(entities.toString(), "AbstractJingzheTurret")
          : read(entities.resolve(entity));
      assertTrue(source.contains("TowerAI.TeamResolver.isHostile(this, target)"), entity);
      assertFalse(source.contains("basicEnemyFilter(target)"), entity);
    }
  }

  @Test
  void packagedMeshMatchesAuditedTrackUpgrade() throws IOException, NoSuchAlgorithmException {
    Path exported =
        PROJECT.resolve(
            "src/test/resources/audited-exports/silent-heaven/silent_heaven.shm");
    Path packaged =
        PROJECT.resolve("src/boss/resources/assets/silent_heaven/meshes/silent_heaven.shm");
    assertArrayEquals(sha256(exported), sha256(packaged));

    String report =
        read(
            PROJECT
                .resolve(
                    "src/test/resources/audited-exports/silent-heaven/silent_heaven_export_report.json"));
    String compact = report.replaceAll("\\s+", "");
    assertTrue(report.contains("231_SilentHeaven_Rigged_v11_Runtime_UV.blend"));
    assertTrue(compact.contains("\"mesh_object_count\":91"));
    assertTrue(compact.contains("\"part_count\":147"));
    assertTrue(compact.contains("\"triangle_total\":5370"));
    assertTrue(compact.contains("\"road_wheel_l_0/body\":28"));
    assertTrue(compact.contains("\"road_wheel_r_3/body\":28"));
    assertTrue(compact.contains("\"shoe_count_per_side\":58"));
    assertTrue(compact.contains("\"main_gun_l_muzzle\":[2.273122,3.574424,2.219845]"));
    assertTrue(compact.contains("\"main_gun_r_muzzle\":[-2.273121,3.574424,2.219844]"));
    assertTrue(compact.contains("\"autocannon_l_muzzle\":[1.705815,1.709482,1.887713]"));
    assertTrue(compact.contains("\"autocannon_r_muzzle\":[-1.705815,1.709482,1.887713]"));
    assertTrue(compact.contains("\"missile_pod_l_launch\":[1.349663,4.910265,-0.588821]"));
    assertTrue(compact.contains("\"missile_pod_r_launch\":[-1.329664,4.910265,-0.588821]"));
    assertTrue(compact.contains("\"vls_l_launch\":[1.410375,4.5525,-2.11119]"));
    assertTrue(compact.contains("\"vls_r_launch\":[-1.410375,4.5525,-2.11119]"));
  }

  @Test
  void criticalWeaponAndPhaseContractsRemainPresent() throws IOException {
    String entity =
        readJavaFamily("src/boss/java/com/liucodex/silentheaven/entity", "SilentHeavenVehicle");
    String compactEntity = entity.replaceAll("\\s+", "");
    String phaseHealth = read(PROJECT.resolve("src/boss/java/com/liucodex/silentheaven/entity/SilentHeavenPhaseHealth.java"));
    assertTrue(entity.contains("public static final float MAX_HEALTH = SilentHeavenPhaseHealth.FIRST_CAPACITY;"));
    assertTrue(phaseHealth.contains("FIRST_CAPACITY = 4_000.0F;"));
    assertTrue(phaseHealth.contains("SECOND_CAPACITY = 3_000.0F;"));
    assertTrue(entity.contains("public float getMaxHealth()"));
    assertTrue(entity.contains("static final float MISSILE_DAMAGE = 160.0F;"));
    assertTrue(entity.contains("static final float MISSILE_EXPLOSION_DAMAGE = 160.0F;"));
    assertTrue(entity.contains("static final float PHASE_THRESHOLD = 0.0F;"));
    assertTrue(entity.contains("static final float PHASE_TWO_START_HEALTH = SilentHeavenPhaseHealth.SECOND_CAPACITY;"));
    assertTrue(entity.contains("static final int TRANSITION_TICKS = 370;"));
    assertTrue(entity.contains("static final int TRANSITION_HALO_START_TICK = 330;"));
    assertTrue(entity.contains("static final double TARGET_RANGE = 80.0D;"));
    assertTrue(entity.contains("static final double MAIN_MAX_RANGE = 60.0D;"));
    assertTrue(entity.contains("fireMainGun(true, target)"));
    assertTrue(entity.contains("fireMainGun(false, target)"));
    assertTrue(entity.contains("Vec3 muzzle = mainGunMuzzle(left);"));
    assertTrue(
        compactEntity.contains(
            "CannonShellEntityshell=com.atsuishio.superbwarfare.init.ModEntities.CANNON_SHELL"));
    assertTrue(
        entity.contains(
            "shell.moveTo(muzzle.x, muzzle.y, muzzle.z, getCoreYaw(), getMainGunPitch())"));
    assertTrue(entity.contains("double muzzleX = left ? 2.273122D : -2.273121D;"));
    assertTrue(entity.contains("static final double AUTOCANNON_RANGE = 40.0D;"));
    assertTrue(entity.contains("static final int AUTOCANNON_MAGAZINE = 15;"));
    assertTrue(entity.contains("static final int AUTOCANNON_RELOAD = 100;"));
    assertTrue(entity.contains("static final double AUTOCANNON_MUZZLE_CLEARANCE = 0.22D;"));
    assertTrue(entity.contains("static final float AUTOCANNON_MAX_PITCH = 60.0F;"));
    assertTrue(entity.contains("static final float AUTOCANNON_MAX_LOCAL_YAW = 82.0F;"));
    assertTrue(entity.contains("target.hurt(createAutocannonDamageSource(serverLevel), 10.0F)"));
    assertTrue(entity.replaceAll("\\s+", " ").contains("no projectile // entity is created here"));
    assertTrue(entity.contains("spawnAutocannonMuzzleEffect(serverLevel, muzzle, direction);"));
    assertTrue(entity.contains("new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.06F), 0.72F)"));
    assertTrue(entity.contains("reservedTargetId"));
    assertTrue(entity.contains("nextFireInterval()"));
    assertTrue(entity.contains("ROAD_WHEEL_ANGLE_L"));
    assertTrue(entity.contains("ROAD_WHEEL_ANGLE_R"));
    assertTrue(entity.contains("static final double MISSILE_MAX_RANGE = 40.0D;"));
    assertTrue(entity.contains("static final double VLS_MAX_RANGE = 72.0D;"));
    assertTrue(entity.contains("static final int VLS_LOCK_TICKS = 20;"));
    assertTrue(entity.contains("DnvBossVlsGuidance.arm("));
    assertTrue(compactEntity.contains("JavelinMissileEntitymissile=newJavelinMissileEntity("));
    assertTrue(compactEntity.contains("getPhase()==PHASE_TWO&&distance<=VLS_MAX_RANGE"));
    assertTrue(entity.contains("vlsBattery.arm(VLS_MAGAZINE);"));
    assertTrue(entity.contains("playVoice(ModSounds.VOICE_RESETTING.get())"));
    assertTrue(entity.contains("playVoice(ModSounds.VOICE_HELLO.get())"));
    assertTrue(entity.contains("static final int MAX_ACTIVE_DRONES = 2;"));
    assertFalse(entity.contains("missile.setAttackMode(true);"));
    assertTrue(entity.contains("classifyDamage(source)"));
    assertTrue(entity.contains("OUT_OF_COMBAT_HEAL_DELAY = 1_200"));
    assertTrue(entity.contains("TELEPORT_COOLDOWN_TICKS = 1_800"));
    assertTrue(entity.contains("teleportCooldownTicks = TELEPORT_COOLDOWN_TICKS"));
    assertTrue(entity.contains("setTeleportTicks(DnvVehicleTeleportTimeline.TOTAL_TICKS)"));
    assertTrue(entity.contains("next == DnvVehicleTeleportTimeline.DISAPPEAR_REMAINING_TICKS"));
    assertTrue(entity.contains("next == DnvVehicleTeleportTimeline.TELEPORT_REMAINING_TICKS"));
    assertTrue(entity.contains("teleportDestination = destination;"));
    assertTrue(entity.contains("tickTeleportSequence()"));
    assertTrue(entity.contains("lockForTeleport()"));
    assertTrue(entity.contains("performTeleportJump()"));
    assertTrue(entity.contains("SoundEvents.ENDERMAN_TELEPORT"));
    assertTrue(entity.contains("SilentHeavenHaloTarget"));
    assertTrue(entity.contains("stationSocketToWorld("));
    assertTrue(entity.contains("stationAxisToWorld("));
    assertTrue(entity.contains("IDLE_CORE_TURN_SPEED = 0.28F"));
    assertTrue(entity.contains("IDLE_CORE_MAX_OFFSET = 28.0F"));
    assertTrue(entity.contains("tickIdleCoreScan()"));
    assertTrue(entity.contains("alignCoreToHull()"));
    assertTrue(entity.contains("bossbar.silent_heaven.guardian"));
    assertTrue(entity.contains("bossbar.silent_heaven.unknown"));
    assertTrue(entity.contains("bossbar.silent_heaven.silent_heaven"));
    assertTrue(entity.contains("playPhaseTwoPresentation()"));
    assertTrue(entity.contains("PHASE_TWO_HULL_TURN_MULTIPLIER = 1.50F"));
    assertTrue(entity.contains("if (holdPosition || target == null || isTransitioning())"));
    assertTrue(entity.contains("commandTeleportTo("));
    assertTrue(entity.contains("entityData.define(ENTRANCE_TICKS, ENTRANCE_EFFECT_TICKS)"));
    assertTrue(entity.contains("if (isEntering() || isTransitioning()) return false;"));
    assertTrue(entity.contains("lockForEntrance()"));
    assertTrue(entity.contains("lockForTransition()"));
    assertTrue(entity.contains("if (isEntering()) {"));
    assertTrue(entity.contains("updateBossBar();"));
    assertTrue(entity.contains("new DnvTargetCandidateCache(TARGET_SCAN_INTERVAL)"));
    assertFalse(entity.contains("recoverGuidedMissiles(serverLevel)"));
    assertTrue(entity.contains("recoverFallingWarheads(serverLevel)"));
  }

  @Test
  void obsoleteMonsterPrototypeIsRemoved() throws IOException {
    Path javaRoot = PROJECT.resolve("src/boss/java/com/liucodex/silentheaven");
    assertFalse(Files.exists(javaRoot.resolve("entity/SilentHeavenEntity.java")));
    assertFalse(Files.exists(javaRoot.resolve("client/SilentHeavenModel.java")));
    assertFalse(Files.exists(javaRoot.resolve("client/SilentHeavenRenderer.java")));
    assertFalse(
        Files.exists(
            PROJECT.resolve(
                "src/boss/resources/assets/silent_heaven/models/item/silent_heaven_spawn_egg.json")));

    String entities = read(javaRoot.resolve("init/ModEntities.java"));
    String items = read(javaRoot.resolve("init/ModItems.java"));
    String clientEvents = read(PROJECT.resolve(
        "src/boss/java/com/liucodex/doggiesnewvehicle/boss/client/DnvBossClientModEvents.java"));
    String soundManager = read(javaRoot.resolve("client/SilentHeavenSoundManager.java"));
    assertFalse(entities.contains("SilentHeavenEntity"));
    assertTrue(entities.contains("ENTITY_TYPES.register(\"silent_heaven\""));
    assertFalse(entities.contains("silent_heaven_vehicle_test"));
    assertFalse(items.contains("silent_heaven_spawn_egg"));
    assertFalse(clientEvents.contains("SilentHeavenRenderer"));
    assertFalse(soundManager.contains("SilentHeavenEntity"));
    assertTrue(entities.contains("AUTOCANNON_ROUND"));
    assertTrue(clientEvents.contains("DnvInvisibleEntityRenderer::new"));

    String autocannonRenderer =
        read(PROJECT.resolve("src/main/java/com/liucodex/doggiesnewvehicle/"
            + "client/DnvInvisibleEntityRenderer.java"));
    assertTrue(autocannonRenderer.contains("Deliberately empty"));
    assertTrue(autocannonRenderer.contains("boolean shouldRender("));
    assertTrue(autocannonRenderer.contains("return false;"));
    assertFalse(autocannonRenderer.contains("super.render("));

    String autocannonRound =
        read(PROJECT.resolve("src/main/java/com/liucodex/doggiesnewvehicle/"
            + "entity/DnvInvisibleProjectileEntity.java"));
    assertTrue(autocannonRound.contains("setInvisible(true)"));
  }

  @Test
  void deathFrameAndVehicleOverlapCannotRestoreHudOrLaunchBoss() throws IOException {
    String entity =
        readJavaFamily("src/boss/java/com/liucodex/silentheaven/entity", "SilentHeavenVehicle");
    assertTrue(entity.contains("if (isRemoved() || getHealth() <= 0.0F)"));
    assertTrue(entity.contains("void clearBossBar()"));
    assertTrue(entity.contains("bossEvent.setVisible(false);"));
    assertTrue(entity.contains("public void destroy()"));
    assertTrue(entity.contains("DESTRUCTION_SEQUENCE_TICKS = 60"));
    assertTrue(entity.contains("TARGET_ACQUIRED_VOICE_COOLDOWN = 240"));
    assertTrue(entity.contains("public void preventStacking()"));
    assertTrue(entity.contains("public void pushNew(double x, double y, double z)"));
  }

  @Test
  void formalDnvBuildOwnsBossAndPublishesDeployerInExistingTab() throws IOException {
    String mergedBuild = read(PROJECT.resolve("build.gradle"));
    String mergedMetadata = read(PROJECT.resolve("src/main/resources/META-INF/mods.toml"));
    String creativeTab =
        read(
            PROJECT.resolve(
                "src/main/java/com/liucodex/doggiesnewvehicle/init/ModCreativeTabs.java"));
    String bossContent = read(PROJECT.resolve(
        "src/boss/java/com/liucodex/doggiesnewvehicle/boss/DnvBossContent.java"));

    assertTrue(mergedMetadata.contains("version=\"" + releaseVersion(mergedBuild) + "\""));
    assertFalse(mergedBuild.contains("../../silent-heaven-forge-1.20.1"));
    assertTrue(mergedMetadata.contains("modId=\"doggiesnewvehicle\""));
    assertFalse(mergedMetadata.contains("modId=\"silent_heaven\""));
    assertTrue(countOccurrences(mergedMetadata, "[[mods]]") == 1);
    assertTrue(creativeTab.contains("bossItems.accept(category, output)"));
    assertTrue(bossContent.contains("SILENT_HEAVEN_DEPLOYER.get()"));
    assertTrue(bossContent.contains("SILENT_HEAVEN_BEHAVIOR_CONTROLLER.get()"));
  }

  @Test
  void formalRegistrationAndDedicatedServerIsolationRemainIntact() throws IOException {
    Path silentJava = PROJECT.resolve("src/boss/java/com/liucodex/silentheaven");
    Path content = silentJava.resolve("SilentHeavenContent.java");
    Path oldEntrypoint = silentJava.resolve("SilentHeavenMod.java");
    String dnvEntrypoint =
        read(
            PROJECT.resolve("src/main/java/com/liucodex/doggiesnewvehicle/DoggiesNewVehicle.java"));
    String entities = read(silentJava.resolve("init/ModEntities.java"));
    String items = read(silentJava.resolve("init/ModItems.java"));
    String clientEvents = read(PROJECT.resolve(
        "src/boss/java/com/liucodex/doggiesnewvehicle/boss/client/DnvBossClientModEvents.java"));

    assertTrue(Files.exists(content));
    assertFalse(Files.exists(oldEntrypoint));
    assertTrue(read(content).contains("public static void register(IEventBus modBus)"));
    assertTrue(read(PROJECT.resolve("src/boss/java/com/liucodex/doggiesnewvehicle/boss/DnvBossContent.java"))
        .contains("SilentHeavenContent.register(modBus)"));
    assertTrue(dnvEntrypoint.contains("registerOptionalBoss(modBus)"));
    assertTrue(entities.contains("ENTITY_TYPES.register(\"silent_heaven\""));
    assertTrue(entities.contains("doggievehicle:silent_heaven"));
    assertTrue(items.contains("ITEMS.register(\"silent_heaven_deployer\""));
    assertTrue(clientEvents.contains("value = Dist.CLIENT"));
    assertTrue(clientEvents.contains("modid = DoggiesNewVehicle.MOD_ID"));

    try (var paths = Files.walk(silentJava)) {
      for (Path source :
          paths
              .filter(path -> path.toString().endsWith(".java"))
              .filter(path -> !path.toString().replace('\\', '/').contains("/client/"))
              .toList()) {
        String java = read(source);
        assertFalse(java.contains("net.minecraft.client"), source.toString());
        assertFalse(java.contains("Minecraft.getInstance()"), source.toString());
      }
    }
  }

  @Test
  void controllerAndPresentationResourcesRemainComplete() throws IOException {
    String controller =
        read(
            PROJECT.resolve(
                "src/boss/java/com/liucodex/doggiesnewvehicle/item/"
                    + "DnvBehaviorControllerItem.java"));
    String controllerBindings =
        read(PROJECT.resolve(
            "src/boss/java/com/liucodex/silentheaven/init/ModItems.java"));
    String deployer =
        read(
            PROJECT.resolve(
                "src/boss/java/com/liucodex/silentheaven/init/ModItems.java"));
    String chinese =
        read(PROJECT.resolve("src/boss/resources/assets/silent_heaven/lang/zh_cn.json"));
    String meshLayer =
        read(
            PROJECT.resolve(
                "src/boss/java/com/liucodex/silentheaven/client/SilentHeavenMeshLayer.java"));

    assertTrue(controller.contains("prefix + \"ControllerTarget\""));
    assertTrue(controller.contains("setHoldingPosition.accept(vehicle, hold)"));
    assertTrue(controller.contains("teleportCommand.execute("));
    assertTrue(controller.contains("teleportCooldownTicks.applyAsInt(vehicle)"));
    assertTrue(controllerBindings.contains("\"SilentHeaven\""));
    assertTrue(controllerBindings.contains("\"silent_heaven\""));
    assertTrue(controllerBindings.contains(
        "SilentHeavenVehicleEntity::commandTeleportTo"));
    assertTrue(chinese.contains("message.silent_heaven.controller.teleport_cooldown"));
    assertTrue(deployer.contains("new DnvVehicleDeployerItem<>("));
    assertTrue(deployer.contains("vehicle.alignCoreToHull()"));
    assertTrue(chinese.contains("\"bossbar.silent_heaven.guardian\": \"守护者\""));
    assertTrue(chinese.contains("\"bossbar.silent_heaven.unknown\": \"？？\""));
    assertTrue(chinese.contains("\"bossbar.silent_heaven.silent_heaven\": \"死寂天堂\""));
    assertTrue(chinese.contains("\"subtitle.silent_heaven.guardian\": \"前文明的造物\""));
    assertTrue(chinese.contains("\"subtitle.silent_heaven.silent_heaven\": \"？？？？\""));
    assertTrue(
        Files.exists(
            PROJECT.resolve(
                "src/boss/resources/assets/doggiesnewvehicle/models/item/"
                    + "silent_heaven_behavior_controller.json")));
    assertTrue(meshLayer.contains("if (entranceTicks > ENTRANCE_WHITE_TICKS)"));
    assertTrue(meshLayer.contains("renderEntranceSilhouette("));
    assertTrue(meshLayer.contains("RenderType.entityTranslucent(WHITE_TEXTURE)"));
    assertTrue(meshLayer.contains("|| group.equals(\"halo_ring\")"));
    assertTrue(meshLayer.contains("|| group.startsWith(\"halo_needle_\")"));
    assertTrue(meshLayer.contains("if (haloRotationActive(visual, partialTick))"));
    assertTrue(meshLayer.contains("DnvVehicleTeleportTimeline.isHidden(teleportTicks)"));
    assertTrue(meshLayer.contains("renderTeleportWhiteOverlay("));
    assertTrue(meshLayer.contains("group.startsWith(\"road_wheel_\")"));
    assertTrue(meshLayer.contains("getRoadWheelAngleL()"));
    assertTrue(meshLayer.contains("2.0339D, 0.6084D, -0.8865D, -2.3391D"));
  }

  @Test
  void vehicleDataAndTwentyFiveBlockSoundWrappersRemainConsistent() throws IOException {
    Path serverVehicle =
        PROJECT.resolve("src/boss/resources/data/doggievehicle/sbw/vehicles/silent_heaven.json");
    String vehicle = read(serverVehicle).replaceAll("\\s+", "");
    assertFalse(vehicle.contains("\"ID\""));
    assertTrue(vehicle.contains("\"MaxHealth\":4000"));
    assertTrue(vehicle.contains("\"ParticleType\":\"Giant\""));
    assertTrue(vehicle.contains("\"ExplosionDamage\":200"));
    assertTrue(vehicle.contains("\"ExplosionRadius\":16"));
    assertTrue(vehicle.contains("\"ExplodeBlocks\":false"));
    assertTrue(vehicle.contains("\"NoWreck\":true"));
    assertTrue(!vehicle.contains("\"All-15\""));

    String sounds = read(PROJECT.resolve("src/boss/resources/assets/silent_heaven/sounds.json"));
    assertTrue(sounds.contains("superbwarfare:gun/m_2_hb/m_2_hb_fire_3p"));
    assertTrue(sounds.contains("superbwarfare:vehicle/plz_05/plz_05_engine"));
    assertTrue(sounds.contains("superbwarfare:vehicle/common/track_vehicle_step"));
    assertTrue(sounds.split("\\\"attenuation_distance\\\": 25", -1).length - 1 == 4);
    assertTrue(sounds.split("\\\"attenuation_distance\\\": 48", -1).length - 1 == 19);
    Path voiceDirectory = PROJECT.resolve("src/boss/resources/assets/silent_heaven/sounds/voice");
    try (var voices = Files.list(voiceDirectory)) {
      assertTrue(
          voices.filter(path -> path.getFileName().toString().endsWith(".ogg")).count() == 19);
    }
  }

  private static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static String readJavaFamily(String relativeDirectory, String nameFragment)
      throws IOException {
    try (var sources = Files.list(PROJECT.resolve(relativeDirectory))) {
      return sources
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> path.getFileName().toString().contains(nameFragment))
          .sorted()
          .map(
              path -> {
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

  private static byte[] sha256(Path path) throws IOException, NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
  }

  private static int countOccurrences(String text, String needle) {
    return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
  }

  private static String releaseVersion(String build) {
    var matcher = java.util.regex.Pattern.compile("(?m)^version = '([0-9]+(?:\\.[0-9]+){1,2}(?:hotfix)?)'\\s*$")
        .matcher(build);
    assertTrue(matcher.find(), "Missing formal release version");
    String version = matcher.group(1);
    assertFalse(matcher.find(), "Multiple release version owners in one build");
    return version;
  }
}
