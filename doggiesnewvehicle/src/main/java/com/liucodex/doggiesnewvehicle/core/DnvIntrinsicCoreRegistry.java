package com.liucodex.doggiesnewvehicle.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Server profile owner for DNV-built turrets that ship with an intrinsic shared autonomy core. */
public final class DnvIntrinsicCoreRegistry {
  public static final int CURRENT_SCHEMA = 1;
  public static final int MAX_PROFILES = 64;
  private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
  private static final Pattern FACTION = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

  private final Map<String, Profile> profiles = new LinkedHashMap<>();

  public Mutation publish(Profile profile) {
    Objects.requireNonNull(profile, "profile");
    Profile previous = profiles.get(profile.profileId());
    if (previous == null && profiles.size() >= MAX_PROFILES) {
      return new Mutation(false, Reason.CAPACITY_EXCEEDED, -1L);
    }
    if (previous != null && profile.revision() <= previous.revision()) {
      return new Mutation(false, Reason.STALE_REVISION, previous.revision());
    }
    profiles.put(profile.profileId(), profile);
    return new Mutation(true, Reason.NONE, profile.revision());
  }

  public Mutation revoke(String profileId, long expectedRevision) {
    String id = checkedKey(profileId, "profileId");
    Profile profile = profiles.get(id);
    if (profile == null) {
      return new Mutation(false, Reason.PROFILE_MISSING, -1L);
    }
    if (profile.revision() != expectedRevision) {
      return new Mutation(false, Reason.STALE_REVISION, profile.revision());
    }
    profiles.remove(id);
    return new Mutation(true, Reason.NONE, expectedRevision);
  }

  public Inspection inspect(String profileId) {
    String id = checkedKey(profileId, "profileId");
    Profile profile = profiles.get(id);
    if (profile == null) {
      return new Inspection(Optional.empty(), Reason.PROFILE_MISSING);
    }
    return new Inspection(
        Optional.of(profile), profile.enabled() ? Reason.NONE : Reason.PROFILE_DISABLED);
  }

  public Optional<InstalledCore> install(String profileId) {
    Inspection inspection = inspect(profileId);
    if (inspection.reason() != Reason.NONE) {
      return Optional.empty();
    }
    Profile profile = inspection.profile().orElseThrow();
    return Optional.of(new InstalledCore(
        profile, new DnvAutonomyCore(profile.pressureMinRange(), profile.pressureMaxRange())));
  }

  public void clear() {
    profiles.clear();
  }

  private boolean isCurrent(Profile profile) {
    Profile current = profiles.get(profile.profileId());
    return current != null
        && current.enabled()
        && current.revision() == profile.revision()
        && current.equals(profile);
  }

  private static String checkedKey(String value, String field) {
    String checked = Objects.requireNonNull(value, field).trim();
    if (!KEY.matcher(checked).matches()) {
      throw new IllegalArgumentException(field + " must be a namespaced key");
    }
    return checked;
  }

  public record Profile(
      int schemaVersion,
      String profileId,
      String sourceId,
      long revision,
      String factionId,
      boolean enabled,
      double pressureMinRange,
      double pressureMaxRange,
      DnvPlatformCapabilities capabilities,
      Set<DnvStationEngagementCore.TargetingMode> allowedTargetingModes) {
    public Profile {
      profileId = checkedKey(profileId, "profileId");
      sourceId = checkedKey(sourceId, "sourceId");
      String checkedFaction = Objects.requireNonNull(factionId, "factionId").trim();
      Objects.requireNonNull(capabilities, "capabilities");
      allowedTargetingModes = Set.copyOf(Objects.requireNonNull(
          allowedTargetingModes, "allowedTargetingModes"));
      if (schemaVersion != CURRENT_SCHEMA
          || revision < 1L
          || !FACTION.matcher(checkedFaction).matches()
          || !Double.isFinite(pressureMinRange)
          || !Double.isFinite(pressureMaxRange)
          || pressureMinRange < 0.0D
          || pressureMaxRange <= pressureMinRange
          || capabilities.family() != DnvPlatformCapabilities.PlatformFamily.TURRET
          || capabilities.movementActuator()
          || allowedTargetingModes.isEmpty()) {
        throw new IllegalArgumentException("intrinsic core profile is invalid");
      }
      factionId = checkedFaction;
    }
  }

  public record InstalledCore(Profile profile, DnvAutonomyCore core) {
    public InstalledCore {
      Objects.requireNonNull(profile, "profile");
      Objects.requireNonNull(core, "core");
    }

    /** A replaced, disabled, or removed server profile immediately invalidates autonomous fire. */
    public boolean active(DnvIntrinsicCoreRegistry registry) {
      return Objects.requireNonNull(registry, "registry").isCurrent(profile);
    }
  }

  public record Inspection(Optional<Profile> profile, Reason reason) {
    public Inspection {
      profile = Objects.requireNonNull(profile, "profile");
      Objects.requireNonNull(reason, "reason");
    }
  }

  public record Mutation(boolean accepted, Reason reason, long authoritativeRevision) {
    public Mutation {
      Objects.requireNonNull(reason, "reason");
      if (accepted != (reason == Reason.NONE)) {
        throw new IllegalArgumentException("intrinsic registry mutation is inconsistent");
      }
    }
  }

  public enum Reason {
    NONE,
    PROFILE_MISSING,
    PROFILE_DISABLED,
    STALE_REVISION,
    CAPACITY_EXCEEDED
  }
}
