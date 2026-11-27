package com.liucodex.doggiesnewvehicle.mission;

import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Deterministic cross-platform route controller. It emits normalized actuator intent only and
 * cannot load chunks, move an entity, fabricate buoyancy, or bypass a platform adapter.
 */
public final class DnvRouteFollower {
  private static final double ARRIVAL_RADIUS = 4.0D;
  private static final double AIR_ARRIVAL_VERTICAL_TOLERANCE = 1.0D;
  private static final double VERTICAL_RESPONSE_BAND = 20.0D;
  private static final int SLOW_AFTER_TICKS = 1;
  private static final int REPLAN_AFTER_TICKS = 10;
  private static final int HOLD_AFTER_TICKS = 20;
  private static final int RECOVERY_AFTER_TICKS = 40;
  private static final int MIN_REPLAN_INTERVAL_TICKS = 20;

  private DnvRoutePlan plan;
  private RouteCapability capabilities;
  private int pointIndex;
  private int blockedTicks;
  private long lastTick = -1L;
  private long lastReplanTick = -1L;

  public RouteUpdate apply(
      DnvRoutePlan proposed, RouteCapability platformCapabilities) {
    Objects.requireNonNull(proposed, "proposed");
    Objects.requireNonNull(platformCapabilities, "platformCapabilities");
    if (!platformCapabilities.movementActuator()) {
      return RouteUpdate.rejected(activeRevision(), Reason.MOVEMENT_UNSUPPORTED);
    }
    if (!platformCapabilities.supports(proposed.movementMedium())) {
      return RouteUpdate.rejected(activeRevision(), Reason.MEDIUM_UNSUPPORTED);
    }
    if (plan != null && proposed.routeRevision() < plan.routeRevision()) {
      return RouteUpdate.rejected(plan.routeRevision(), Reason.STALE_REVISION);
    }
    if (plan != null && proposed.routeRevision() == plan.routeRevision()) {
      return plan.equals(proposed)
          ? new RouteUpdate(true, plan.routeRevision(), Reason.NONE)
          : RouteUpdate.rejected(plan.routeRevision(), Reason.REVISION_COLLISION);
    }
    plan = proposed;
    capabilities = platformCapabilities;
    resetRuntime();
    return new RouteUpdate(true, plan.routeRevision(), Reason.NONE);
  }

  public Intent tick(SensorSnapshot sensor) {
    Objects.requireNonNull(sensor, "sensor");
    if (plan == null || capabilities == null) {
      return Intent.hold(State.HOLD, Reason.NO_ROUTE);
    }
    if (!sensor.finite()) {
      return Intent.hold(State.HOLD, Reason.INVALID_SENSOR);
    }
    if (lastTick > sensor.gameTime()) {
      clear();
      return Intent.hold(State.HOLD, Reason.TIME_REGRESSION);
    }
    lastTick = sensor.gameTime();
    if (sensor.playerOverride()) {
      blockedTicks = 0;
      return Intent.hold(State.PLAYER_OVERRIDE, Reason.PLAYER_OVERRIDE);
    }
    if (pointIndex >= plan.points().size()) {
      if (plan.completionPolicy() == DnvRoutePlan.CompletionPolicy.LOOP) {
        pointIndex = 0;
      } else {
        return Intent.hold(State.COMPLETE, Reason.ROUTE_COMPLETE);
      }
    }
    if (!sensor.routeSegmentLoaded()) {
      return Intent.hold(State.HOLD, Reason.ROUTE_SEGMENT_UNLOADED);
    }

    DnvRoutePlan.RoutePoint point = plan.points().get(pointIndex);
    double distance = horizontalDistance(sensor, point);
    boolean altitudeReached = plan.movementMedium() != DnvRoutePlan.MovementMedium.AIR
        || point.serverResolvedHeight().isEmpty()
        || sensor.y() >= point.serverResolvedHeight().getAsDouble()
            - AIR_ARRIVAL_VERTICAL_TOLERANCE;
    if (distance <= ARRIVAL_RADIUS && altitudeReached) {
      if (point.action() == DnvRoutePlan.WaypointAction.HOLD
          || pointIndex + 1 >= plan.points().size()
              && plan.completionPolicy() == DnvRoutePlan.CompletionPolicy.HOLD) {
        pointIndex++;
        return Intent.hold(State.COMPLETE, Reason.ROUTE_COMPLETE);
      }
      pointIndex = (pointIndex + 1) % plan.points().size();
      point = plan.points().get(pointIndex);
      distance = horizontalDistance(sensor, point);
    }

    return switch (plan.movementMedium()) {
      case GROUND -> ground(sensor, point, distance);
      case SURFACE -> surface(sensor, point, distance);
      case AIR -> air(sensor, point, distance);
    };
  }

  private Intent ground(SensorSnapshot sensor, DnvRoutePlan.RoutePoint point, double distance) {
    if (!sensor.localTraversable()) {
      return blocked(sensor, point);
    }
    blockedTicks = Math.max(0, blockedTicks - 2);
    return approach(sensor, point, distance, 0.0D);
  }

  private Intent surface(SensorSnapshot sensor, DnvRoutePlan.RoutePoint point, double distance) {
    if (!sensor.navigableWater() || !sensor.afloat()) {
      blockedTicks = Math.min(RECOVERY_AFTER_TICKS, blockedTicks + 1);
      return Intent.hold(State.HOLD, Reason.NOT_ON_NAVIGABLE_WATER);
    }
    if (!sensor.localTraversable()) {
      return blocked(sensor, point);
    }
    blockedTicks = Math.max(0, blockedTicks - 2);
    return approach(sensor, point, distance, 0.0D);
  }

  private Intent air(SensorSnapshot sensor, DnvRoutePlan.RoutePoint point, double distance) {
    if (sensor.terrainHeight().isEmpty()) {
      return Intent.hold(State.HOLD, Reason.TERRAIN_SAMPLE_UNAVAILABLE);
    }
    if (!sensor.localTraversable()) {
      return blocked(sensor, point);
    }
    DnvRoutePlan.AltitudePolicy altitude = plan.altitudePolicy();
    double terrain = sensor.terrainHeight().getAsDouble();
    double desired = point.serverResolvedHeight().orElseGet(() ->
        altitude.reference() == DnvRoutePlan.AltitudeReference.ABOVE_TERRAIN
            ? terrain + altitude.desiredAltitude()
            : altitude.desiredAltitude());
    desired = Math.max(desired, terrain + altitude.minimumTerrainClearance());
    if (desired > altitude.maximumAltitude()) {
      return Intent.hold(State.HOLD, Reason.ALTITUDE_OUTSIDE_POLICY);
    }
    blockedTicks = Math.max(0, blockedTicks - 2);
    return approach(sensor, point, distance,
        clamp((desired - sensor.y()) / VERTICAL_RESPONSE_BAND, -1.0D, 1.0D));
  }

  private Intent blocked(SensorSnapshot sensor, DnvRoutePlan.RoutePoint point) {
    blockedTicks = Math.min(RECOVERY_AFTER_TICKS, blockedTicks + 1);
    if (blockedTicks >= RECOVERY_AFTER_TICKS
        && plan.blockedPolicy() == DnvRoutePlan.BlockedPolicy.RECOVERY_REQUIRED) {
      return Intent.hold(State.RECOVERY_REQUIRED, Reason.RECOVERY_REQUIRED);
    }
    if (blockedTicks >= HOLD_AFTER_TICKS) {
      return Intent.hold(State.HOLD, Reason.ROUTE_BLOCKED_HOLD);
    }
    if (blockedTicks >= REPLAN_AFTER_TICKS) {
      boolean replan = lastReplanTick < 0L
          || sensor.gameTime() - lastReplanTick >= MIN_REPLAN_INTERVAL_TICKS;
      if (replan) {
        lastReplanTick = sensor.gameTime();
      }
      return new Intent(
          State.LOCAL_REPLAN, Reason.LOCAL_REPLAN, pointIndex, point,
          0.0D, 0.0D, 0.0D, true, replan);
    }
    return new Intent(
        State.SLOW, Reason.LOCAL_OBSTACLE, pointIndex, point,
        blockedTicks >= SLOW_AFTER_TICKS ? 0.15D : 0.3D,
        0.0D, 0.0D, false, false);
  }

  private Intent approach(
      SensorSnapshot sensor,
      DnvRoutePlan.RoutePoint point,
      double distance,
      double climb) {
    double desiredYaw = Math.toDegrees(Math.atan2(
        -(point.x() - sensor.x()), point.z() - sensor.z()));
    double steering = clamp(wrap(desiredYaw - sensor.yawDegrees()) / 45.0D, -1.0D, 1.0D);
    double throttle = Math.abs(steering) > 0.8D
        ? 0.2D
        : clamp(distance / 24.0D, 0.2D, 1.0D);
    return new Intent(
        State.APPROACH, Reason.FOLLOW_ROUTE, pointIndex, point,
        throttle, steering, climb, false, false);
  }

  public void clear() {
    plan = null;
    capabilities = null;
    resetRuntime();
  }

  public Optional<DnvRoutePlan> plan() {
    return Optional.ofNullable(plan);
  }

  private void resetRuntime() {
    pointIndex = 0;
    blockedTicks = 0;
    lastTick = -1L;
    lastReplanTick = -1L;
  }

  private long activeRevision() {
    return plan == null ? -1L : plan.routeRevision();
  }

  private static double horizontalDistance(
      SensorSnapshot sensor, DnvRoutePlan.RoutePoint point) {
    return Math.hypot(point.x() - sensor.x(), point.z() - sensor.z());
  }

  private static double wrap(double degrees) {
    double wrapped = degrees % 360.0D;
    return wrapped >= 180.0D ? wrapped - 360.0D
        : wrapped < -180.0D ? wrapped + 360.0D : wrapped;
  }

  private static double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  public record SensorSnapshot(
      long gameTime,
      double x,
      double y,
      double z,
      double yawDegrees,
      double speed,
      boolean routeSegmentLoaded,
      boolean localTraversable,
      boolean navigableWater,
      boolean afloat,
      OptionalDouble terrainHeight,
      double forwardClearance,
      boolean playerOverride) {
    public SensorSnapshot {
      terrainHeight = Objects.requireNonNull(terrainHeight, "terrainHeight");
      if (gameTime < 0L) {
        throw new IllegalArgumentException("game time must not be negative");
      }
    }

    private boolean finite() {
      return Double.isFinite(x)
          && Double.isFinite(y)
          && Double.isFinite(z)
          && Double.isFinite(yawDegrees)
          && Double.isFinite(speed)
          && speed >= 0.0D
          && Double.isFinite(forwardClearance)
          && forwardClearance >= 0.0D
          && (terrainHeight.isEmpty() || Double.isFinite(terrainHeight.getAsDouble()));
    }
  }

  public record Intent(
      State state,
      Reason reason,
      int pointIndex,
      DnvRoutePlan.RoutePoint point,
      double throttle,
      double steering,
      double climbCommand,
      boolean hardHold,
      boolean replanRequested) {
    public Intent {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(reason, "reason");
      if (!Double.isFinite(throttle)
          || !Double.isFinite(steering)
          || !Double.isFinite(climbCommand)
          || throttle < 0.0D
          || throttle > 1.0D
          || Math.abs(steering) > 1.0D
          || Math.abs(climbCommand) > 1.0D) {
        throw new IllegalArgumentException("route actuator intent must be normalized");
      }
    }

    private static Intent hold(State state, Reason reason) {
      return new Intent(state, reason, -1, null, 0.0D, 0.0D, 0.0D, true, false);
    }

    public Optional<DnvRoutePlan.RoutePoint> targetPoint() {
      return Optional.ofNullable(point);
    }
  }

  public record RouteUpdate(boolean accepted, long activeRevision, Reason reason) {
    public RouteUpdate {
      Objects.requireNonNull(reason, "reason");
      if (accepted != (reason == Reason.NONE)) {
        throw new IllegalArgumentException("route update acceptance is inconsistent");
      }
    }

    private static RouteUpdate rejected(long revision, Reason reason) {
      return new RouteUpdate(false, revision, reason);
    }
  }

  public record RouteCapability(
      boolean movementActuator,
      Set<DnvRoutePlan.MovementMedium> movementMedia) {
    public RouteCapability {
      movementMedia = Set.copyOf(Objects.requireNonNull(movementMedia, "movementMedia"));
      if (movementActuator == movementMedia.isEmpty()) {
        throw new IllegalArgumentException("route capability movement contract is inconsistent");
      }
    }

    public boolean supports(DnvRoutePlan.MovementMedium medium) {
      return movementMedia.contains(Objects.requireNonNull(medium, "medium"));
    }
  }

  public enum State {
    HOLD,
    PLAYER_OVERRIDE,
    SLOW,
    LOCAL_REPLAN,
    APPROACH,
    COMPLETE,
    RECOVERY_REQUIRED
  }

  public enum Reason {
    NONE,
    NO_ROUTE,
    MOVEMENT_UNSUPPORTED,
    MEDIUM_UNSUPPORTED,
    STALE_REVISION,
    REVISION_COLLISION,
    INVALID_SENSOR,
    TIME_REGRESSION,
    PLAYER_OVERRIDE,
    ROUTE_SEGMENT_UNLOADED,
    LOCAL_OBSTACLE,
    LOCAL_REPLAN,
    ROUTE_BLOCKED_HOLD,
    RECOVERY_REQUIRED,
    NOT_ON_NAVIGABLE_WATER,
    TERRAIN_SAMPLE_UNAVAILABLE,
    ALTITUDE_OUTSIDE_POLICY,
    FOLLOW_ROUTE,
    ROUTE_COMPLETE
  }
}
