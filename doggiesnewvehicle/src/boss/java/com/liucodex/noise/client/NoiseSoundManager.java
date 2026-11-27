package com.liucodex.noise.client;

import com.liucodex.doggiesnewvehicle.client.DnvVehicleLoopSoundManager;
import com.liucodex.noise.entity.NoiseVehicleEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Binds Noise-specific sounds and landing rules to the shared DNV loop owner. */
public final class NoiseSoundManager {
  private static final float LOOP_RANGE = 25.0F;
  private static final DnvVehicleLoopSoundManager<NoiseVehicleEntity> LOOPS =
      new DnvVehicleLoopSoundManager<>(
          NoiseVehicleEntity.class,
          3,
          sound("idle_loop"),
          sound("engine_loop"),
          sound("track_loop"),
          NoiseSoundManager::shouldOperate,
          NoiseVehicleEntity::getDriveIntensity);

  private NoiseSoundManager() {}

  public static void tick() {
    LOOPS.tick();
  }

  private static boolean shouldOperate(NoiseVehicleEntity boss) {
    return !boss.isRemoved()
        && boss.isAlive()
        && !boss.isWreck()
        && !boss.isEntering();
  }

  private static SoundEvent sound(String path) {
    return SoundEvent.createFixedRangeEvent(
        ResourceLocation.fromNamespaceAndPath("noise", path), LOOP_RANGE);
  }
}
