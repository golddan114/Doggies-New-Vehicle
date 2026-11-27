package com.liucodex.doggiesnewvehicle.mission;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Platform-neutral, server-owned route value. Ground and surface consumers never interpret an
 * editor-provided Y coordinate; an optional height is valid only after server-side resolution.
 */
public record DnvRoutePlan(
    int schemaVersion,
    String dimension,
    long routeRevision,
    MovementMedium movementMedium,
    List<RoutePoint> points,
    CompletionPolicy completionPolicy,
    BlockedPolicy blockedPolicy,
    AltitudePolicy altitudePolicy) {
  public static final int CURRENT_SCHEMA = 1;
  public static final int MAX_POINTS = 32;
  private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

  public DnvRoutePlan {
    String checkedDimension = Objects.requireNonNull(dimension, "dimension").trim();
    Objects.requireNonNull(movementMedium, "movementMedium");
    points = List.copyOf(Objects.requireNonNull(points, "points"));
    Objects.requireNonNull(completionPolicy, "completionPolicy");
    Objects.requireNonNull(blockedPolicy, "blockedPolicy");
    Objects.requireNonNull(altitudePolicy, "altitudePolicy");
    if (schemaVersion != CURRENT_SCHEMA
        || !DIMENSION.matcher(checkedDimension).matches()
        || routeRevision < 0L
        || points.isEmpty()
        || points.size() > MAX_POINTS) {
      throw new IllegalArgumentException("route plan header is invalid or outside hard bounds");
    }
    if (movementMedium == MovementMedium.AIR
        != (altitudePolicy.reference() != AltitudeReference.NONE)) {
      throw new IllegalArgumentException("only an air route may carry an altitude policy");
    }
    dimension = checkedDimension;
  }

  public record RoutePoint(
      double x,
      double z,
      OptionalDouble serverResolvedHeight,
      WaypointAction action) {
    public RoutePoint {
      serverResolvedHeight = Objects.requireNonNull(
          serverResolvedHeight, "serverResolvedHeight");
      Objects.requireNonNull(action, "action");
      if (!Double.isFinite(x)
          || !Double.isFinite(z)
          || serverResolvedHeight.isPresent()
              && !Double.isFinite(serverResolvedHeight.getAsDouble())) {
        throw new IllegalArgumentException("route point must contain finite server values");
      }
    }
  }

  public record AltitudePolicy(
      AltitudeReference reference,
      double desiredAltitude,
      double minimumTerrainClearance,
      double maximumAltitude) {
    public AltitudePolicy {
      Objects.requireNonNull(reference, "reference");
      if (!Double.isFinite(desiredAltitude)
          || !Double.isFinite(minimumTerrainClearance)
          || !Double.isFinite(maximumAltitude)
          || desiredAltitude < 0.0D
          || minimumTerrainClearance < 0.0D
          || maximumAltitude < 0.0D
          || reference == AltitudeReference.NONE
              && (desiredAltitude != 0.0D
                  || minimumTerrainClearance != 0.0D
                  || maximumAltitude != 0.0D)
          || reference != AltitudeReference.NONE
              && maximumAltitude < Math.max(desiredAltitude, minimumTerrainClearance)) {
        throw new IllegalArgumentException("altitude policy is invalid");
      }
    }

    public static AltitudePolicy none() {
      return new AltitudePolicy(AltitudeReference.NONE, 0.0D, 0.0D, 0.0D);
    }
  }

  public enum CompletionPolicy {
    HOLD,
    LOOP
  }

  public enum MovementMedium {
    GROUND,
    SURFACE,
    AIR
  }

  public enum BlockedPolicy {
    HOLD,
    RECOVERY_REQUIRED
  }

  public enum WaypointAction {
    PASS_THROUGH,
    HOLD
  }

  public enum AltitudeReference {
    NONE,
    ABSOLUTE,
    ABOVE_TERRAIN
  }
}
