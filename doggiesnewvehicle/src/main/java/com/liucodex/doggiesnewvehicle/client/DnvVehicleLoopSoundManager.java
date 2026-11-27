package com.liucodex.doggiesnewvehicle.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** Shared lifecycle for DNV machinery, engine and track loop layers. */
public final class DnvVehicleLoopSoundManager<T extends Entity> {
  public static final int DISCOVERY_INTERVAL = 20;
  private final double discoveryRange;
  private final double retentionRangeSqr;
  private final boolean fixedPitch;

  private final Class<T> entityType;
  private final int discoveryPhase;
  private final SoundEvent idleSound;
  private final SoundEvent engineSound;
  private final SoundEvent trackSound;
  private final Predicate<T> operational;
  private final ToDoubleFunction<T> driveIntensity;
  private final Map<Integer, IdleLoop<T>> idleLoops = new HashMap<>();
  private final Map<Integer, DriveLoop<T>> engineLoops = new HashMap<>();
  private final Map<Integer, DriveLoop<T>> trackLoops = new HashMap<>();

  public DnvVehicleLoopSoundManager(
      Class<T> entityType,
      int discoveryPhase,
      SoundEvent idleSound,
      SoundEvent engineSound,
      SoundEvent trackSound,
      Predicate<T> operational,
      ToDoubleFunction<T> driveIntensity) {
    this(entityType, discoveryPhase, idleSound, engineSound, trackSound, operational, driveIntensity, 27, 30);
  }

  /** Each movement layer is optional; machinery idle always remains available. */
  public DnvVehicleLoopSoundManager(Class<T> entityType, int discoveryPhase, SoundEvent idleSound,
      SoundEvent engineSound, SoundEvent trackSound, Predicate<T> operational,
      ToDoubleFunction<T> driveIntensity, double discoveryRange, double retentionRange) {
    this(entityType, discoveryPhase, idleSound, engineSound, trackSound, operational, driveIntensity,
        discoveryRange, retentionRange, false);
  }

  /** Selected finished samples retain their authored pitch; movement still controls engine gain. */
  public DnvVehicleLoopSoundManager(Class<T> entityType, int discoveryPhase, SoundEvent idleSound,
      SoundEvent engineSound, SoundEvent trackSound, Predicate<T> operational,
      ToDoubleFunction<T> driveIntensity, double discoveryRange, double retentionRange, boolean fixedPitch) {
    if (!Double.isFinite(discoveryRange) || discoveryRange <= 0 || !Double.isFinite(retentionRange)
        || retentionRange < discoveryRange || retentionRange > 256) throw new IllegalArgumentException("Sound range");
    this.discoveryRange = discoveryRange;
    this.retentionRangeSqr = retentionRange * retentionRange;
    this.fixedPitch = fixedPitch;
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.discoveryPhase = Math.floorMod(discoveryPhase, DISCOVERY_INTERVAL);
    this.idleSound = Objects.requireNonNull(idleSound, "idleSound");
    this.engineSound = engineSound;
    this.trackSound = trackSound;
    this.operational = Objects.requireNonNull(operational, "operational");
    this.driveIntensity = Objects.requireNonNull(driveIntensity, "driveIntensity");
  }

  public void tick() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.level == null || minecraft.player == null) {
      stopAll(minecraft);
      return;
    }
    removeStopped(idleLoops);
    removeStopped(engineLoops);
    removeStopped(trackLoops);
    if (Math.floorMod(minecraft.player.tickCount, DISCOVERY_INTERVAL) != discoveryPhase) {
      return;
    }
    for (T entity : minecraft.level.getEntitiesOfClass(
        entityType,
        minecraft.player.getBoundingBox().inflate(discoveryRange))) {
      if (!operational.test(entity)) continue;
      idleLoops.computeIfAbsent(entity.getId(), ignored -> {
        IdleLoop<T> sound = new IdleLoop<>(idleSound, entity, operational, retentionRangeSqr, fixedPitch);
        minecraft.getSoundManager().play(sound);
        return sound;
      });
      if (engineSound != null) engineLoops.computeIfAbsent(entity.getId(), ignored -> {
        DriveLoop<T> sound = DriveLoop.engine(
            engineSound, entity, operational, driveIntensity, retentionRangeSqr, fixedPitch);
        minecraft.getSoundManager().play(sound);
        return sound;
      });
      if (trackSound != null) trackLoops.computeIfAbsent(entity.getId(), ignored -> {
        DriveLoop<T> sound = DriveLoop.track(
            trackSound, entity, operational, driveIntensity, retentionRangeSqr, fixedPitch);
        minecraft.getSoundManager().play(sound);
        return sound;
      });
    }
  }

  private static void removeStopped(
      Map<Integer, ? extends AbstractTickableSoundInstance> loops) {
    loops.entrySet().removeIf(entry -> entry.getValue().isStopped());
  }

  private void stopAll(Minecraft minecraft) {
    idleLoops.values().forEach(minecraft.getSoundManager()::stop);
    engineLoops.values().forEach(minecraft.getSoundManager()::stop);
    trackLoops.values().forEach(minecraft.getSoundManager()::stop);
    idleLoops.clear();
    engineLoops.clear();
    trackLoops.clear();
  }

  private static boolean outOfRange(Entity entity, Minecraft minecraft, double retentionRangeSqr) {
    return minecraft.player == null
        || entity.level() != minecraft.level || entity.isRemoved()
        || entity.distanceToSqr(minecraft.player) > retentionRangeSqr;
  }

  private static final class IdleLoop<T extends Entity>
      extends AbstractTickableSoundInstance {
    private final T entity;
    private final Predicate<T> operational;
    private final double retentionRangeSqr;

    private IdleLoop(SoundEvent sound, T entity, Predicate<T> operational, double retentionRangeSqr, boolean fixedPitch) {
      super(sound, SoundSource.HOSTILE, RandomSource.create());
      this.entity = entity;
      this.operational = operational;
      this.retentionRangeSqr = retentionRangeSqr;
      looping = true;
      delay = 0;
      volume = 1.05F;
      pitch = fixedPitch ? 1F : 0.82F;
      attenuation = SoundInstance.Attenuation.LINEAR;
      updatePosition();
    }

    @Override
    public void tick() {
      Minecraft minecraft = Minecraft.getInstance();
      if (!operational.test(entity) || outOfRange(entity, minecraft, retentionRangeSqr)) {
        stop();
        return;
      }
      updatePosition();
    }

    private void updatePosition() {
      x = entity.getX();
      y = entity.getY() + 1.6D;
      z = entity.getZ();
    }
  }

  private static final class DriveLoop<T extends Entity>
      extends AbstractTickableSoundInstance {
    private final T entity;
    private final Predicate<T> operational;
    private final ToDoubleFunction<T> driveIntensity;
    private final boolean engine;
    private final boolean fixedPitch;
    private final double retentionRangeSqr;

    private DriveLoop(
        SoundEvent sound,
        T entity,
        Predicate<T> operational,
        ToDoubleFunction<T> driveIntensity,
        boolean engine, double retentionRangeSqr, boolean fixedPitch) {
      super(sound, SoundSource.AMBIENT, RandomSource.create());
      this.entity = entity;
      this.operational = operational;
      this.driveIntensity = driveIntensity;
      this.engine = engine;
      this.fixedPitch = fixedPitch;
      this.retentionRangeSqr = retentionRangeSqr;
      looping = true;
      delay = 0;
      volume = 0.0F;
      pitch = fixedPitch ? 1F : engine ? 0.70F : 0.78F;
      attenuation = SoundInstance.Attenuation.LINEAR;
      updatePosition();
    }

    private static <T extends Entity> DriveLoop<T> engine(
        SoundEvent sound,
        T entity,
        Predicate<T> operational,
        ToDoubleFunction<T> driveIntensity, double retentionRangeSqr, boolean fixedPitch) {
      return new DriveLoop<>(sound, entity, operational, driveIntensity, true, retentionRangeSqr, fixedPitch);
    }

    private static <T extends Entity> DriveLoop<T> track(
        SoundEvent sound,
        T entity,
        Predicate<T> operational,
        ToDoubleFunction<T> driveIntensity, double retentionRangeSqr, boolean fixedPitch) {
      return new DriveLoop<>(sound, entity, operational, driveIntensity, false, retentionRangeSqr, fixedPitch);
    }

    @Override public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
      Minecraft minecraft = Minecraft.getInstance();
      if (!operational.test(entity) || outOfRange(entity, minecraft, retentionRangeSqr)) {
        stop();
        return;
      }
      float intensity = Mth.clamp((float) driveIntensity.applyAsDouble(entity), 0.0F, 1.0F);
      float targetVolume = engine
          ? intensity < 0.02F ? 0.0F : 0.34F + 0.66F * intensity
          : 0.86F * intensity;
      float targetPitch = fixedPitch ? 1F : engine
          ? 0.70F + 0.16F * intensity
          : 0.78F + 0.14F * intensity;
      volume = Mth.approach(volume, targetVolume, 0.045F);
      pitch = Mth.approach(pitch, targetPitch, 0.02F);
      updatePosition();
    }

    private void updatePosition() {
      x = entity.getX();
      y = entity.getY() + 0.8D;
      z = entity.getZ();
    }
  }
}
