package com.liucodex.noise;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class NoiseProjectArtifactContractTest {
  private static final Path PROJECT = Path.of("").toAbsolutePath();

  @Test
  void independentRegistrationAndCombatContractRemainPresent() throws IOException {
    String entry = read("src/boss/java/com/liucodex/doggiesnewvehicle/boss/DnvBossContent.java");
    String entities = read("src/boss/java/com/liucodex/noise/init/ModEntities.java");
    String items = read("src/boss/java/com/liucodex/noise/init/ModItems.java");
    String controller = read("src/boss/java/com/liucodex/doggiesnewvehicle/item/"
        + "DnvBehaviorControllerItem.java");
    String vehicle = readJavaFamily("src/boss/java/com/liucodex/noise/entity", "NoiseVehicle");
    assertTrue(entry.contains("NoiseContent.register(modBus)"));
    assertTrue(entities.contains("ENTITY_TYPES.register(\"noise\""));
    assertTrue(entities.contains("ENTITY_TYPES.register(\"noise_laser_bolt\""));
    assertTrue(items.contains("\"Noise\"") && items.contains("\"noise\""));
    assertTrue(items.contains("NoiseVehicleEntity::setHoldingPosition"));
    assertTrue(items.contains("NoiseVehicleEntity::commandTeleportTo"));
    assertTrue(controller.contains("prefix + \"ControllerTarget\""));
    assertTrue(vehicle.contains("public static final float MAX_HEALTH = 4_000.0F;"));
    assertTrue(vehicle.contains("public float getMaxHealth()"));
    assertTrue(vehicle.contains("static final int MAIN_MAGAZINE = 6;"));
    assertTrue(vehicle.contains("static final int MAIN_INTERVAL = 20;"));
    assertTrue(vehicle.contains("static final int MAIN_RELOAD = 160;"));
    assertTrue(vehicle.contains("static final float MAIN_DAMAGE = 60.0F;"));
    assertTrue(vehicle.contains("static final int AUTOCANNON_MAGAZINE = 40;"));
    assertTrue(vehicle.contains("target.hurt(createAutocannonDamageSource(serverLevel), 5.0F)"));
    assertTrue(vehicle.contains("no projectile entity"));
    assertTrue(vehicle.contains("static final int MISSILE_MAGAZINE = 3;"));
    assertTrue(vehicle.contains("static final int MISSILE_INTERVAL = 10;"));
    assertTrue(vehicle.contains("static final int MISSILE_RELOAD = 240;"));
    assertTrue(vehicle.contains("static final float SIDE_ROCKET_DAMAGE = 40.0F;"));
    assertTrue(
        vehicle.contains("static final float SIDE_ROCKET_EXPLOSION_DAMAGE = 30.0F;"));
    assertTrue(vehicle.contains("static final int VLS_RELOAD = 200;"));
    assertTrue(vehicle.contains("static final float MISSILE_DAMAGE = 60.0F;"));
    assertTrue(vehicle.contains("static final float MISSILE_EXPLOSION_DAMAGE = 140.0F;"));
    assertTrue(vehicle.contains("tickVlsTube(vlsBattery, true, frame)"));
    assertTrue(vehicle.contains("tickVlsTube(vlsBatteryRight, false, frame)"));
    String compactVehicle = vehicle.replaceAll("\\s+", "");
    assertTrue(
        compactVehicle.contains(
            "publicbooleancommandTeleportTo(ServerLevelrequestedLevel,Vec3destination){returnfalse;}"));
    String presentation = read(
        "src/boss/java/com/liucodex/doggiesnewvehicle/entity/DnvVehiclePresentation.java")
        .replaceAll("\\s+", "");
    assertTrue(presentation.contains("defaultbooleanisPhaseTwo(){returnfalse;}"));
    assertTrue(presentation.contains("defaultbooleanisHaloVisible(){returnfalse;}"));
    assertFalse(compactVehicle.contains("publicbooleanisPhaseTwo()"));
    assertFalse(compactVehicle.contains("publicbooleanisHaloVisible()"));
    assertFalse(vehicle.contains("tickHaloWeapon("));
    assertFalse(vehicle.contains("tickDroneLauncher("));
    assertFalse(vehicle.contains("beginTeleportSequence("));
    assertFalse(vehicle.contains("NoisePendingStrikes"));
    assertFalse(vehicle.contains("NoiseTeleportX"));
    assertTrue(vehicle.contains("protected void tickObstructionRecovery("));
    assertTrue(vehicle.contains(
        "return distance <= VLS_MAX_RANGE"));
    assertTrue(vehicle.contains("new DnvTargetCandidateCache(TARGET_SCAN_INTERVAL)"));
    assertTrue(vehicle.contains("DnvBossVlsGuidance.arm("));
    assertFalse(vehicle.contains("recoverGuidedMissiles(serverLevel)"));
    String autocannonRenderer =
        read("src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvInvisibleEntityRenderer.java");
    String autocannonRound =
        read("src/main/java/com/liucodex/doggiesnewvehicle/entity/"
            + "DnvInvisibleProjectileEntity.java");
    assertTrue(autocannonRenderer.contains("boolean shouldRender("));
    assertTrue(autocannonRenderer.contains("return false;"));
    assertFalse(autocannonRenderer.contains("super.render("));
    assertTrue(autocannonRound.contains("setInvisible(true)"));
  }

  @Test
  void packagedMeshesMatchTheAuditedExports() throws Exception {
    assertHashEqual(
        "src/test/resources/audited-exports/noise/noise.shm",
        "src/boss/resources/assets/noise/meshes/noise.shm");
    assertHashEqual(
        "src/test/resources/audited-exports/noise/drop-rig/noise_drop_rig.shm",
        "src/boss/resources/assets/noise/meshes/noise_drop_rig.shm");
    String report =
        Files.readString(
                PROJECT
                    .resolve(
                        "src/test/resources/audited-exports/noise/drop-rig/noise_drop_rig_export_report.json"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(report.contains("\"part_count\":5"));
    assertTrue(report.contains("\"triangle_total\":308"));
    assertTrue(report.contains("\"bounds_min_mc\":[-4.712681,0.857656,-6.082219]"));
    assertTrue(report.contains("\"bounds_max_mc\":[4.712681,3.454667,6.082219]"));
  }

  @Test
  void dropTimelineStaysVerticalAndHaloGeometryIsNotRendered() throws IOException {
    String vehicle = readJavaFamily("src/boss/java/com/liucodex/noise/entity", "NoiseVehicle");
    String renderer = read("src/boss/java/com/liucodex/noise/client/NoiseDropRigLayer.java");
    String meshLayer = read("src/boss/java/com/liucodex/noise/client/NoiseMeshLayer.java");
    assertTrue(
        vehicle.contains(
            "public static final int ENTRANCE_EFFECT_TICKS = NoiseDropTimeline.TOTAL_TICKS;"));
    assertTrue(vehicle.contains("Component.translatable(\"bossbar.noise.noise\")"));
    String chinese = read("src/boss/resources/assets/noise/lang/zh_cn.json");
    assertTrue(chinese.contains("\"subtitle.noise.noise\": \"（大象尖叫）\""));
    assertTrue(renderer.contains("NoiseDropTimeline.isRigVisible(elapsed)"));
    assertTrue(renderer.contains("NoiseDropTimeline.renderElapsedTicks("));
    assertTrue(meshLayer.contains("NoiseDropTimeline.renderElapsedTicks("));
    assertTrue(
        renderer.contains(
            "poseStack.translate(0.0D, entity.presentedRigOffset(elapsed), 0.0D)"));
    assertTrue(meshLayer.contains("vehicle.presentedVehicleOffset(elapsed)"));
    assertTrue(vehicle.contains("NoiseDropTimeline.rigVerticalOffset(elapsed, getMothershipDropStart())"));
    assertTrue(vehicle.contains("tag.putFloat(\"NoiseMothershipDropStart\", getMothershipDropStart())"));
    assertTrue(vehicle.contains("entityData.define(MOTHERSHIP_DROP_START, -1.0F)"));
    assertTrue(meshLayer.contains("renderNormalVehicle("));
    assertFalse(meshLayer.contains("DnvVehicleTeleportTimeline"));
    assertFalse(meshLayer.contains("DnvVehicleTransitionTimeline"));
    assertFalse(meshLayer.contains("renderTeleportWhiteOverlay("));
    assertFalse(meshLayer.contains("haloNeedleVisible("));
    assertFalse(meshLayer.contains("ENTRANCE_WHITE_TICKS"));
    assertFalse(meshLayer.contains("renderEntranceSilhouette("));
    assertTrue(meshLayer.contains("2.0339D, 0.6084D, -0.8865D, -2.3391D"));
    String baseGroups =
        meshLayer.substring(
            meshLayer.indexOf("private static final String[] BASE_GROUPS"),
            meshLayer.indexOf("private static final String[] GROUPS"));
    assertFalse(baseGroups.contains("halo_ring"));
    assertFalse(baseGroups.contains("halo_needle"));
  }

  @Test
  void weaponSocketsMatchTheAuditedNoiseMarkers() throws IOException {
    String report =
        read("src/test/resources/audited-exports/noise/noise_export_report.json")
            .replaceAll("\\s+", "");
    assertTrue(report.contains("\"main_gun_l_muzzle\":[2.273122,3.574424,2.219845]"));
    assertTrue(report.contains("\"main_gun_r_muzzle\":[-2.273121,3.574424,2.219844]"));
    assertTrue(report.contains("\"autocannon_l_muzzle\":[1.705207,1.901002,1.926614]"));
    assertTrue(report.contains("\"autocannon_r_muzzle\":[-1.70581,1.901002,1.926614]"));
    assertTrue(report.contains("\"missile_pod_l_launch\":[1.349663,4.910265,-0.588821]"));
    assertTrue(report.contains("\"missile_pod_r_launch\":[-1.329664,4.910265,-0.588821]"));
    assertTrue(report.contains("\"vls_l_launch\":[1.410375,4.5525,-2.11119]"));
    assertTrue(report.contains("\"vls_r_launch\":[-1.410375,4.5525,-2.11119]"));

    String vehicle = readJavaFamily("src/boss/java/com/liucodex/noise/entity", "NoiseVehicle");
    assertTrue(vehicle.contains("new Vec3(1.705207D, 1.901002D, 1.926614D)"));
    assertTrue(vehicle.contains("new Vec3(-1.705810D, 1.901002D, 1.926614D)"));
    assertTrue(vehicle.contains("double muzzleX = left ? 2.273122D : -2.273121D;"));
    assertTrue(vehicle.contains("left ? 1.410375D : -1.410375D"));
  }

  @Test
  void localAudioSetContainsTenVoiceSegmentsAndDropEngine() throws IOException {
    Path voices = PROJECT.resolve("src/boss/resources/assets/noise/sounds/voice");
    try (var files = Files.list(voices)) {
      assertTrue(files.filter(path -> path.toString().endsWith(".ogg")).count() == 10);
    }
    assertTrue(
        Files.exists(
            PROJECT.resolve(
                "src/boss/resources/assets/noise/sounds/drop_rig/noise_drop_rig_engine.ogg")));
    String sounds = read("src/boss/resources/assets/noise/sounds.json");
    assertTrue(sounds.contains("superbwarfare:waveforce_tower/waveforce_tower_fire_1"));
    assertTrue(sounds.contains("noise:idle"));
    assertFalse(sounds.contains("doggiesnewvehicle:jingzhe_idle"));
    assertTrue(Files.exists(PROJECT.resolve("src/boss/resources/assets/noise/sounds/idle.ogg")));
    assertTrue(sounds.contains("superbwarfare:vehicle/plz_05/plz_05_engine"));
    assertTrue(sounds.contains("superbwarfare:vehicle/common/track_vehicle_step"));
    assertFalse(sounds.contains("noise_lock_on_charge"));
  }

  @Test
  void clientLoopsStartAfterLandingAndFollowDriveIntensity() throws IOException {
    String manager = read("src/boss/java/com/liucodex/noise/client/NoiseSoundManager.java");
    String shared = read(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvVehicleLoopSoundManager.java");
    String events = read(
        "src/boss/java/com/liucodex/doggiesnewvehicle/boss/client/DnvBossClientGameEvents.java");
    assertTrue(manager.contains("!boss.isEntering()"));
    assertTrue(manager.contains("NoiseVehicleEntity::getDriveIntensity"));
    assertTrue(shared.contains("class IdleLoop"));
    assertTrue(shared.contains("class DriveLoop"));
    assertTrue(shared.contains("DriveLoop.engine("));
    assertTrue(shared.contains("DriveLoop.track("));
    assertTrue(shared.contains("Mth.approach(volume"));
    assertTrue(events.contains("NoiseSoundManager.tick()"));
    assertTrue(events.contains("SilentHeavenSoundManager.tick()"));
    assertTrue(events.contains("value = Dist.CLIENT"));
  }

  @Test
  void mainBeamUsesSbwAdditiveLaserPassAndThreeFullBrightLayers() throws IOException {
    String renderer = read("src/boss/java/com/liucodex/noise/client/NoiseLaserBoltRenderer.java");
    assertTrue(renderer.contains("ModRenderTypes.LASER.apply(WHITE)"));
    assertTrue(renderer.contains("\"superbwarfare\", \"textures/entity/white.png\""));
    assertTrue(renderer.contains("0.22F * pulse"));
    assertTrue(renderer.contains("0.115F * pulse"));
    assertTrue(renderer.contains("0.052F * pulse"));
    assertTrue(renderer.contains("LightTexture.FULL_BRIGHT"));
    assertFalse(renderer.contains("RenderType.entityTranslucent(WHITE)"));
  }

  @Test
  void deathFrameAndVehicleOverlapCannotRestoreHudOrLaunchBoss() throws IOException {
    String vehicle = readJavaFamily("src/boss/java/com/liucodex/noise/entity", "NoiseVehicle");
    assertTrue(vehicle.contains("if (isRemoved() || getHealth() <= 0.0F)"));
    assertTrue(vehicle.contains("void clearBossBar()"));
    assertTrue(vehicle.contains("bossEvent.setVisible(false);"));
    assertTrue(vehicle.contains("public void destroy()"));
    assertTrue(vehicle.contains("DESTRUCTION_SEQUENCE_TICKS = 60"));
    assertTrue(vehicle.contains("TARGET_ACQUIRED_VOICE_COOLDOWN = 240"));
    assertTrue(vehicle.contains("public void preventStacking()"));
    assertTrue(vehicle.contains("public void pushNew(double x, double y, double z)"));
  }

  @Test
  void vehicleDataKeepsYx100StyleArenaSafeDeathExplosion() throws IOException {
    String serverVehicle =
        read("src/boss/resources/data/doggievehicle/sbw/vehicles/noise.json")
            .replaceAll("\\s+", "");
    assertFalse(serverVehicle.contains("\"ID\""));
    assertTrue(serverVehicle.contains("\"MaxHealth\":4000"));
    assertTrue(serverVehicle.contains("\"ParticleType\":\"Giant\""));
    assertTrue(serverVehicle.contains("\"ExplosionDamage\":200"));
    assertTrue(serverVehicle.contains("\"ExplosionRadius\":16"));
    assertTrue(serverVehicle.contains("\"ExplodeBlocks\":false"));
    assertTrue(serverVehicle.contains("\"NoWreck\":true"));
  }

  private static String read(String relative) throws IOException {
    return Files.readString(PROJECT.resolve(relative), StandardCharsets.UTF_8);
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

  private static void assertHashEqual(String exportPath, String packagedPath)
      throws IOException, NoSuchAlgorithmException {
    assertArrayEquals(
        sha256(PROJECT.resolve(exportPath).normalize()), sha256(PROJECT.resolve(packagedPath)));
  }

  private static byte[] sha256(Path path) throws IOException, NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
  }
}
