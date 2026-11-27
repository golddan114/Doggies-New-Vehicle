package com.liucodex.doggiesnewvehicle.core;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Internal executable identity. SBW resources remain the owner of mechanical and weapon values. */
public record DnvTurretDefinition(
    String entityType, String profileId, Map<String, Set<String>> stationWeapons,
    boolean remoteManualOptIn, Optional<Projectile> projectile) {
  public DnvTurretDefinition {
    entityType = key(entityType);
    profileId = key(profileId);
    projectile = Objects.requireNonNull(projectile);
    stationWeapons = Objects.requireNonNull(stationWeapons).entrySet().stream().collect(
        Collectors.toUnmodifiableMap(entry -> name(entry.getKey()), entry -> {
          Set<String> weapons = Set.copyOf(entry.getValue());
          if (weapons.isEmpty() || weapons.size() > DnvPlatformCapabilities.MAX_WEAPONS_PER_STATION) {
            throw new IllegalArgumentException("invalid turret weapon count");
          }
          weapons.forEach(DnvTurretDefinition::name);
          return weapons;
        }));
    if (stationWeapons.isEmpty() || stationWeapons.size() > DnvPlatformCapabilities.MAX_STATIONS) {
      throw new IllegalArgumentException("invalid turret station count");
    }
  }

  public DnvTurretDefinition(String entityType, String profileId, Map<String, Set<String>> stationWeapons,
      boolean remoteManualOptIn, String projectileType, DnvPlatformCapabilities.BallisticCapability ballistic) {
    this(entityType, profileId, stationWeapons, remoteManualOptIn,
        Optional.of(new Projectile(projectileType, ballistic)));
  }

  /** Entity-owned effects do not claim a native projectile or a ballistic solver capability. */
  public static DnvTurretDefinition entityEffect(String entityType, String profileId,
      Map<String, Set<String>> stationWeapons) {
    return new DnvTurretDefinition(entityType, profileId, stationWeapons, false, Optional.empty());
  }

  public String projectileType() { return projectile.orElseThrow().type(); }
  public DnvPlatformCapabilities.BallisticCapability ballistic() { return projectile.orElseThrow().ballistic(); }

  public record Projectile(String type, DnvPlatformCapabilities.BallisticCapability ballistic) {
    public Projectile {
      type = key(type);
      Objects.requireNonNull(ballistic);
    }
  }

  public boolean owns(String station, String weapon) {
    return stationWeapons.getOrDefault(station, Set.of()).contains(weapon);
  }

  public DnvIntrinsicCoreRegistry.Profile profile(long revision, double minRange, double maxRange) {
    var capabilities = new DnvPlatformCapabilities(
        DnvPlatformCapabilities.PlatformFamily.TURRET, Set.of(),
        Set.of(DnvPlatformCapabilities.SensorCapability.LOCAL_ENTITIES),
        projectile.map(value -> Set.of(value.ballistic())).orElse(Set.of()), false,
        stationWeapons.size(), stationWeapons.values().stream().mapToInt(Set::size).max().orElseThrow(),
        20, 10, 10, 40);
    return new DnvIntrinsicCoreRegistry.Profile(1, profileId, entityType, revision, "native", true,
        minRange, maxRange, capabilities, Set.of(DnvStationEngagementCore.TargetingMode.INDEPENDENT));
  }

  private static String key(String value) {
    if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
      throw new IllegalArgumentException("invalid turret key");
    }
    return value;
  }

  private static String name(String value) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw new IllegalArgumentException("invalid station/weapon name");
    }
    return value;
  }
}
