package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;
import java.util.Set;

/** Composable server profile; the family is diagnostic and never substitutes for capabilities. */
public record DnvPlatformCapabilities(
    PlatformFamily family,
    Set<MovementMedium> movementMedia,
    Set<SensorCapability> sensors,
    Set<BallisticCapability> ballistics,
    boolean movementActuator,
    int maxStations,
    int maxWeaponsPerStation,
    int scanIntervalTicks,
    int decisionIntervalTicks,
    int ballisticIntervalTicks,
    int routeReplanIntervalTicks) {
  public static final int MAX_STATIONS = 32;
  public static final int MAX_WEAPONS_PER_STATION = 8;
  public static final int MAX_FAST_INTERVAL_TICKS = 200;
  public static final int MAX_SLOW_INTERVAL_TICKS = 1_200;

  public DnvPlatformCapabilities {
    family = Objects.requireNonNull(family, "family");
    movementMedia = Set.copyOf(Objects.requireNonNull(movementMedia, "movementMedia"));
    sensors = Set.copyOf(Objects.requireNonNull(sensors, "sensors"));
    ballistics = Set.copyOf(Objects.requireNonNull(ballistics, "ballistics"));
    if (movementActuator && movementMedia.isEmpty()) {
      throw new IllegalArgumentException("movement actuator needs a movement medium");
    }
    if (!movementActuator && !movementMedia.isEmpty()) {
      throw new IllegalArgumentException("fixed platform cannot declare movement media");
    }
    if (maxStations < 0
        || maxStations > MAX_STATIONS
        || maxWeaponsPerStation < 1
        || maxWeaponsPerStation > MAX_WEAPONS_PER_STATION) {
      throw new IllegalArgumentException("station budget out of range");
    }
    if (scanIntervalTicks < 20
        || scanIntervalTicks > MAX_SLOW_INTERVAL_TICKS
        || decisionIntervalTicks < 10
        || decisionIntervalTicks > MAX_FAST_INTERVAL_TICKS
        || ballisticIntervalTicks < 10
        || ballisticIntervalTicks > MAX_FAST_INTERVAL_TICKS
        || routeReplanIntervalTicks < 20
        || routeReplanIntervalTicks > MAX_SLOW_INTERVAL_TICKS) {
      throw new IllegalArgumentException("autonomy intervals are below server safety limits");
    }
  }

  public boolean supports(MovementMedium medium) {
    return movementMedia.contains(medium);
  }

  public boolean supports(SensorCapability sensor) {
    return sensors.contains(sensor);
  }

  public boolean supports(BallisticCapability ballistic) {
    return ballistics.contains(ballistic);
  }

  public static DnvPlatformCapabilities defaults(PlatformFamily family) {
    return switch (family) {
      case TURRET -> new DnvPlatformCapabilities(
          family, Set.of(), Set.of(SensorCapability.LOCAL_ENTITIES),
          Set.of(BallisticCapability.DIRECT, BallisticCapability.ARC),
          false, 8, 8, 20, 10, 10, 40);
      case GROUND -> new DnvPlatformCapabilities(
          family, Set.of(MovementMedium.GROUND),
          Set.of(SensorCapability.LOCAL_ENTITIES, SensorCapability.TERRAIN_FORWARD),
          Set.of(BallisticCapability.DIRECT, BallisticCapability.ARC),
          true, 16, 8, 20, 10, 10, 40);
      case SURFACE -> new DnvPlatformCapabilities(
          family, Set.of(MovementMedium.SURFACE),
          Set.of(SensorCapability.LOCAL_ENTITIES, SensorCapability.WATER_SURFACE),
          Set.of(BallisticCapability.DIRECT, BallisticCapability.ARC),
          true, 16, 8, 20, 10, 10, 40);
      case AIR -> new DnvPlatformCapabilities(
          family, Set.of(MovementMedium.AIR),
          Set.of(SensorCapability.LOCAL_ENTITIES, SensorCapability.ALTITUDE,
              SensorCapability.TERRAIN_FORWARD),
          Set.of(BallisticCapability.DIRECT),
          true, 16, 8, 20, 10, 10, 40);
    };
  }

  public enum PlatformFamily {
    TURRET,
    GROUND,
    SURFACE,
    AIR
  }

  public enum MovementMedium {
    GROUND,
    SURFACE,
    AIR
  }

  public enum SensorCapability {
    LOCAL_ENTITIES,
    ALTITUDE,
    TERRAIN_FORWARD,
    WATER_SURFACE
  }

  public enum BallisticCapability {
    DIRECT,
    ARC
  }
}
