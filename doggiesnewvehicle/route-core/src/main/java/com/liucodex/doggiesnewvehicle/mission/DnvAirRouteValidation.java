package com.liucodex.doggiesnewvehicle.mission;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure, bounded AIR-route geometry and terrain-corridor validation. */
public final class DnvAirRouteValidation {
  public static final double MAX_SAMPLE_SPACING = 16.0D;
  public static final int MAX_TERRAIN_SAMPLES = 2_048;

  private DnvAirRouteValidation() {}

  /** Geometry accepted here is safe to store as a draft; an executable route still needs 2 points. */
  public static GeometryResult checkGeometry(
      List<DnvRouteWaypoint> waypoints,
      DnvTacticalRouteDrafts.Bounds bounds) {
    Objects.requireNonNull(waypoints, "waypoints");
    Objects.requireNonNull(bounds, "bounds");
    if (waypoints.size() > DnvRouteWaypoint.MAX_WAYPOINTS) {
      return GeometryResult.rejected(-1, Reason.TOO_MANY_WAYPOINTS);
    }
    List<SegmentGeometry> segments = new ArrayList<>();
    double total = 0.0D;
    DnvRouteWaypoint previous = null;
    for (int waypointIndex = 0; waypointIndex < waypoints.size(); waypointIndex++) {
      DnvRouteWaypoint waypoint = waypoints.get(waypointIndex);
      int segmentIndex = Math.max(0, waypointIndex - 1);
      if (waypoint == null || !waypoint.isFinite()) {
        return GeometryResult.rejected(segmentIndex, Reason.NON_FINITE_WAYPOINT);
      }
      if (!bounds.contains(waypoint)) {
        return GeometryResult.rejected(segmentIndex, Reason.OUTSIDE_WORLD_BOUNDS);
      }
      if (previous != null) {
        double distance = distance(previous, waypoint);
        double horizontalDistance = horizontalDistance(previous, waypoint);
        if (distance == 0.0D) {
          return GeometryResult.rejected(waypointIndex - 1, Reason.ZERO_LENGTH_SEGMENT);
        }
        if (distance < DnvTacticalRouteDrafts.MIN_WAYPOINT_SPACING) {
          return GeometryResult.rejected(waypointIndex - 1, Reason.WAYPOINTS_TOO_CLOSE);
        }
        if (distance > DnvTacticalRouteDrafts.MAX_SEGMENT_LENGTH) {
          return GeometryResult.rejected(waypointIndex - 1, Reason.SEGMENT_TOO_LONG);
        }
        total += distance;
        if (total > DnvTacticalRouteDrafts.MAX_TOTAL_LENGTH) {
          return GeometryResult.rejected(waypointIndex - 1, Reason.ROUTE_TOO_LONG);
        }
        segments.add(new SegmentGeometry(
            waypointIndex - 1, previous, waypoint, distance, horizontalDistance));
      }
      previous = waypoint;
    }
    return GeometryResult.accepted(segments, total);
  }

  public static Result validate(
      List<DnvRouteWaypoint> waypoints,
      DnvTacticalRouteDrafts.Bounds bounds,
      FlightEnvelope envelope,
      TerrainSampler sampler) {
    Objects.requireNonNull(envelope, "envelope");
    Objects.requireNonNull(sampler, "sampler");
    GeometryResult geometry = checkGeometry(waypoints, bounds);
    if (!geometry.accepted()) {
      return blockedGeometry(geometry);
    }
    if (geometry.segments().isEmpty()) {
      return new Result(
          Status.BLOCKED, Reason.ROUTE_INCOMPLETE, List.of(), 0,
          MAX_TERRAIN_SAMPLES);
    }

    List<SegmentResult> results = new ArrayList<>();
    int totalSamples = 0;
    boolean budgetExhausted = false;
    for (SegmentGeometry segment : geometry.segments()) {
      if (budgetExhausted) {
        results.add(new SegmentResult(
            segment.index(), Status.UNVERIFIED, Reason.SAMPLE_BUDGET_EXCEEDED,
            new SampleRange(0.0D, 0.0D, 0)));
        continue;
      }
      SegmentAssessment assessment = validateSegment(
          segment, bounds, envelope, sampler, totalSamples);
      totalSamples += assessment.result().sampleRange().sampleCount();
      results.add(assessment.result());
      budgetExhausted = assessment.budgetExhausted();
    }
    return summarize(results, totalSamples);
  }

  public static Result configurationUnavailable(
      List<DnvRouteWaypoint> waypoints,
      DnvTacticalRouteDrafts.Bounds bounds) {
    GeometryResult geometry = checkGeometry(waypoints, bounds);
    if (!geometry.accepted()) {
      return blockedGeometry(geometry);
    }
    if (geometry.segments().isEmpty()) {
      return new Result(
          Status.BLOCKED, Reason.ROUTE_INCOMPLETE, List.of(), 0,
          MAX_TERRAIN_SAMPLES);
    }
    List<SegmentResult> results = geometry.segments().stream()
        .map(segment -> new SegmentResult(
            segment.index(), Status.UNVERIFIED, Reason.CONFIGURATION_UNAVAILABLE,
            new SampleRange(0.0D, 0.0D, 0)))
        .toList();
    return new Result(
        Status.UNVERIFIED, Reason.CONFIGURATION_UNAVAILABLE, results, 0,
        MAX_TERRAIN_SAMPLES);
  }

  private static SegmentAssessment validateSegment(
      SegmentGeometry segment,
      DnvTacticalRouteDrafts.Bounds bounds,
      FlightEnvelope envelope,
      TerrainSampler sampler,
      int samplesAlreadyUsed) {
    if (segment.start().y() > envelope.maximumAltitude()
        || segment.end().y() > envelope.maximumAltitude()) {
      return new SegmentAssessment(new SegmentResult(
          segment.index(), Status.BLOCKED, Reason.OUTSIDE_AIR_ENVELOPE,
          new SampleRange(0.0D, 0.0D, 0)), false);
    }
    int steps = Math.max(
        1, (int) Math.ceil(segment.horizontalDistance() / MAX_SAMPLE_SPACING));
    int lanes = segment.horizontalDistance() > 0.0D
            && envelope.corridorHalfWidth() > 0.0D
        ? 3
        : 1;
    double lateralX = segment.horizontalDistance() == 0.0D
        ? 0.0D
        : -(segment.end().z() - segment.start().z()) / segment.horizontalDistance();
    double lateralZ = segment.horizontalDistance() == 0.0D
        ? 0.0D
        : (segment.end().x() - segment.start().x()) / segment.horizontalDistance();
    Status status = Status.EXECUTABLE;
    Reason reason = Reason.NONE;
    int samples = 0;
    double lastFraction = 0.0D;
    boolean budgetExhausted = false;
    outer:
    for (int step = 0; step <= steps; step++) {
      double fraction = (double) step / (double) steps;
      double centerX = interpolate(segment.start().x(), segment.end().x(), fraction);
      double centerZ = interpolate(segment.start().z(), segment.end().z(), fraction);
      for (int lane = 0; lane < lanes; lane++) {
        if (samplesAlreadyUsed + samples >= MAX_TERRAIN_SAMPLES) {
          status = Status.UNVERIFIED;
          reason = Reason.SAMPLE_BUDGET_EXCEEDED;
          budgetExhausted = true;
          break outer;
        }
        double laneOffset = lanes == 1
            ? 0.0D
            : (lane - 1) * envelope.corridorHalfWidth();
        double sampleX = centerX + lateralX * laneOffset;
        double sampleZ = centerZ + lateralZ * laneOffset;
        samples++;
        lastFraction = fraction;
        if (sampleX < bounds.minX() || sampleX > bounds.maxX()
            || sampleZ < bounds.minZ() || sampleZ > bounds.maxZ()) {
          status = Status.BLOCKED;
          reason = Reason.OUTSIDE_WORLD_BOUNDS;
          break outer;
        }
        TerrainSample terrain = Objects.requireNonNull(
            sampler.sample(sampleX, sampleZ), "terrain sample");
        if (!terrain.loaded()) {
          if (status != Status.BLOCKED) {
            status = Status.UNVERIFIED;
            reason = Reason.CHUNK_UNLOADED;
          }
          continue;
        }
        if (!Double.isFinite(terrain.surfaceY())) {
          if (status != Status.BLOCKED) {
            status = Status.UNVERIFIED;
            reason = Reason.TERRAIN_DATA_UNAVAILABLE;
          }
          continue;
        }
        double requestedAltitude = Math.max(
            envelope.minimumAltitude(),
            Math.max(
                envelope.preferredAltitude(),
                interpolate(segment.start().y(), segment.end().y(), fraction)));
        double requiredAltitude = Math.max(
            requestedAltitude,
            terrain.surfaceY() + envelope.minimumTerrainClearance());
        if (requiredAltitude > envelope.maximumAltitude()) {
          status = Status.BLOCKED;
          reason = Reason.TERRAIN_CLEARANCE_EXCEEDS_ENVELOPE;
          break outer;
        }
      }
    }
    return new SegmentAssessment(
        new SegmentResult(
            segment.index(), status, reason,
            new SampleRange(0.0D, lastFraction, samples)),
        budgetExhausted);
  }

  private static Result summarize(List<SegmentResult> segments, int samples) {
    SegmentResult blocked = segments.stream()
        .filter(result -> result.status() == Status.BLOCKED)
        .findFirst()
        .orElse(null);
    if (blocked != null) {
      return new Result(
          Status.BLOCKED, blocked.reason(), segments, samples,
          MAX_TERRAIN_SAMPLES);
    }
    SegmentResult unverified = segments.stream()
        .filter(result -> result.status() == Status.UNVERIFIED)
        .findFirst()
        .orElse(null);
    return unverified == null
        ? new Result(
            Status.EXECUTABLE, Reason.NONE, segments, samples,
            MAX_TERRAIN_SAMPLES)
        : new Result(
            Status.UNVERIFIED, unverified.reason(), segments, samples,
            MAX_TERRAIN_SAMPLES);
  }

  private static Result blockedGeometry(GeometryResult geometry) {
    List<SegmentResult> segments = geometry.failureSegmentIndex() < 0
        ? List.of()
        : List.of(new SegmentResult(
            geometry.failureSegmentIndex(), Status.BLOCKED, geometry.reason(),
            new SampleRange(0.0D, 0.0D, 0)));
    return new Result(
        Status.BLOCKED, geometry.reason(), segments, 0,
        MAX_TERRAIN_SAMPLES);
  }

  private static double distance(
      DnvRouteWaypoint first,
      DnvRouteWaypoint second) {
    double x = second.x() - first.x();
    double y = second.y() - first.y();
    double z = second.z() - first.z();
    return Math.sqrt(x * x + y * y + z * z);
  }

  private static double horizontalDistance(
      DnvRouteWaypoint first,
      DnvRouteWaypoint second) {
    return Math.hypot(second.x() - first.x(), second.z() - first.z());
  }

  private static double interpolate(double start, double end, double fraction) {
    return start + (end - start) * fraction;
  }

  @FunctionalInterface
  public interface TerrainSampler {
    TerrainSample sample(double x, double z);
  }

  public record TerrainSample(boolean loaded, double surfaceY) {
    public static TerrainSample unloaded() {
      return new TerrainSample(false, 0.0D);
    }

    public static TerrainSample loaded(double surfaceY) {
      return new TerrainSample(true, surfaceY);
    }
  }

  public record FlightEnvelope(
      double minimumAltitude,
      double preferredAltitude,
      double maximumAltitude,
      double minimumTerrainClearance,
      double corridorHalfWidth) {
    public FlightEnvelope {
      if (!Double.isFinite(minimumAltitude)
          || !Double.isFinite(preferredAltitude)
          || !Double.isFinite(maximumAltitude)
          || !Double.isFinite(minimumTerrainClearance)
          || !Double.isFinite(corridorHalfWidth)
          || minimumAltitude > preferredAltitude
          || preferredAltitude > maximumAltitude
          || minimumTerrainClearance < 0.0D
          || corridorHalfWidth < 0.0D
          || corridorHalfWidth > 128.0D) {
        throw new IllegalArgumentException("AIR route envelope is invalid");
      }
    }
  }

  public record GeometryResult(
      boolean accepted,
      List<SegmentGeometry> segments,
      double totalLength,
      int failureSegmentIndex,
      Reason reason) {
    public GeometryResult {
      segments = List.copyOf(segments);
      Objects.requireNonNull(reason, "reason");
      if (!Double.isFinite(totalLength) || totalLength < 0.0D
          || accepted != (reason == Reason.NONE)
          || accepted && failureSegmentIndex != -1
          || !accepted && failureSegmentIndex < -1) {
        throw new IllegalArgumentException("route geometry result is inconsistent");
      }
    }

    private static GeometryResult accepted(
        List<SegmentGeometry> segments, double totalLength) {
      return new GeometryResult(true, segments, totalLength, -1, Reason.NONE);
    }

    private static GeometryResult rejected(int segmentIndex, Reason reason) {
      return new GeometryResult(false, List.of(), 0.0D, segmentIndex, reason);
    }
  }

  public record SegmentGeometry(
      int index,
      DnvRouteWaypoint start,
      DnvRouteWaypoint end,
      double distance,
      double horizontalDistance) {
    public SegmentGeometry {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      if (index < 0 || !Double.isFinite(distance) || distance <= 0.0D
          || !Double.isFinite(horizontalDistance) || horizontalDistance < 0.0D) {
        throw new IllegalArgumentException("route segment geometry is invalid");
      }
    }
  }

  public record Result(
      Status status,
      Reason reason,
      List<SegmentResult> segments,
      int sampleCount,
      int sampleBudget) {
    public Result {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(reason, "reason");
      segments = List.copyOf(segments);
      if (status == Status.EXECUTABLE != (reason == Reason.NONE)
          || segments.size() > DnvRouteWaypoint.MAX_WAYPOINTS - 1
          || sampleCount < 0 || sampleBudget < 1 || sampleCount > sampleBudget) {
        throw new IllegalArgumentException("AIR route result is inconsistent");
      }
      for (int index = 0; index < segments.size(); index++) {
        if (index > 0
            && segments.get(index - 1).segmentIndex()
                >= segments.get(index).segmentIndex()) {
          throw new IllegalArgumentException("AIR route segment results are not ordered");
        }
      }
    }

    public int firstProblemSegmentIndex() {
      return segments.stream()
          .filter(segment -> segment.status() == status)
          .mapToInt(SegmentResult::segmentIndex)
          .findFirst()
          .orElse(-1);
    }
  }

  public record SegmentResult(
      int segmentIndex,
      Status status,
      Reason reason,
      SampleRange sampleRange) {
    public SegmentResult {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(sampleRange, "sampleRange");
      if (segmentIndex < 0
          || status == Status.EXECUTABLE != (reason == Reason.NONE)) {
        throw new IllegalArgumentException("AIR route segment result is inconsistent");
      }
    }
  }

  public record SampleRange(
      double startFraction, double endFraction, int sampleCount) {
    public SampleRange {
      if (!Double.isFinite(startFraction) || !Double.isFinite(endFraction)
          || startFraction < 0.0D || endFraction < startFraction
          || endFraction > 1.0D || sampleCount < 0) {
        throw new IllegalArgumentException("AIR route sample range is invalid");
      }
    }
  }

  public enum Status {
    EXECUTABLE,
    BLOCKED,
    UNVERIFIED
  }

  public enum Reason {
    NONE,
    ROUTE_INCOMPLETE,
    TOO_MANY_WAYPOINTS,
    NON_FINITE_WAYPOINT,
    OUTSIDE_WORLD_BOUNDS,
    ZERO_LENGTH_SEGMENT,
    WAYPOINTS_TOO_CLOSE,
    SEGMENT_TOO_LONG,
    ROUTE_TOO_LONG,
    OUTSIDE_AIR_ENVELOPE,
    CONFIGURATION_UNAVAILABLE,
    CHUNK_UNLOADED,
    TERRAIN_DATA_UNAVAILABLE,
    TERRAIN_CLEARANCE_EXCEEDS_ENVELOPE,
    SAMPLE_BUDGET_EXCEEDED
  }

  private record SegmentAssessment(
      SegmentResult result, boolean budgetExhausted) {}
}
