package com.liucodex.silentheaven.client;

import com.liucodex.doggiesnewvehicle.client.DnvVehicleLoopSoundManager;
import com.liucodex.silentheaven.entity.SilentHeavenVehicleEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Binds Silent Heaven sounds and transition rules to the shared DNV loop owner. */
public final class SilentHeavenSoundManager {
  private static final float LOOP_RANGE = 25.0F;
  private static final DnvVehicleLoopSoundManager<SilentHeavenVehicleEntity> LOOPS =
      new DnvVehicleLoopSoundManager<>(
          SilentHeavenVehicleEntity.class,
          9,
          sound("idle_loop"),
          sound("engine_loop"),
          sound("track_loop"),
          SilentHeavenSoundManager::shouldOperate,
          boss -> boss.isTransitioning() ? 0.0D : boss.getDriveIntensity());

  private SilentHeavenSoundManager() {}

  public static void tick() {
    LOOPS.tick();
  }

  private static boolean shouldOperate(SilentHeavenVehicleEntity boss) {
    return !boss.isRemoved() && boss.isAlive() && !boss.isWreck();
  }

  private static SoundEvent sound(String path) {
    return SoundEvent.createFixedRangeEvent(
        ResourceLocation.fromNamespaceAndPath("silent_heaven", path), LOOP_RANGE);
  }
}
