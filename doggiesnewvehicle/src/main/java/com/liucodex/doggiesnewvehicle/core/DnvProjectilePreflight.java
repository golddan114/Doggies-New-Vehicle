package com.liucodex.doggiesnewvehicle.core;

import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate.Reason;
import java.util.Objects;

/** Stateless trajectory observations shared by vehicle stations; this is never a fire permit. */
public final class DnvProjectilePreflight {
  private static final double TRIANGULAR_SPREAD_SCALE = 0.0172275D;

  private DnvProjectilePreflight() {}

  /** One immutable muzzle sample. Each station/weapon supplies its own frame. */
  public record MuzzleFrame(Point position, Point direction, long observedTick) {
    public MuzzleFrame {
      Objects.requireNonNull(position, "position");
      Objects.requireNonNull(direction, "direction");
      if (direction.length() < 1.0E-8D || observedTick < 0) {
        throw new IllegalArgumentException("invalid muzzle frame");
      }
    }
  }

  /** Only adapters proving move-then-gravity and bounded triangular launch spread may use this model. */
  public record DryAirProfile(
      float velocity, float gravity, float spread, double explosionRadius,
      double maximumRange, int maximumFlightTicks) {
    public DryAirProfile {
      if (!Float.isFinite(velocity) || velocity <= 0 || velocity > 128
          || !Float.isFinite(gravity) || gravity < 0 || gravity > 4
          || !Float.isFinite(spread) || spread < 0 || spread > 1
          || !Double.isFinite(explosionRadius) || explosionRadius < 0 || explosionRadius > 64
          || !Double.isFinite(maximumRange) || maximumRange <= 0 || maximumRange > 512
          || maximumFlightTicks < 1 || maximumFlightTicks > 64) {
        throw new IllegalArgumentException("unsupported trajectory bounds");
      }
    }

    public double uncertainty(double ticks) {
      return 0.35D + Math.sqrt(3.0D) * velocity * TRIANGULAR_SPREAD_SCALE * spread * ticks;
    }
  }

  public record Point(double x, double y, double z) {
    public Point {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
          || Math.abs(x) > 6.0E7D || Math.abs(y) > 6.0E7D || Math.abs(z) > 6.0E7D) {
        throw new IllegalArgumentException("invalid trajectory coordinate");
      }
    }

    public Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
    public Point subtract(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
    public Point scale(double value) { return new Point(x * value, y * value, z * value); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
  }

  public interface Probe {
    Segment observe(Point from, Point to, double uncertainty);
    Reason danger(Point point, double radius);
  }

  public record Segment(Reason rejection, Point impact) {}

  /** Null rejection means only that the bounded observation found no hazard. */
  public record Observation(Reason rejection, int segments, Point end) {}

  public static Observation trace(
      DryAirProfile profile, MuzzleFrame muzzle, Point target, long executionTick, Probe probe) {
    return trace(profile, muzzle, target, executionTick, probe, false);
  }

  public static Observation trace(
      DryAirProfile profile, MuzzleFrame muzzle, Point target, long executionTick, Probe probe,
      boolean allowTargetImpact) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(muzzle, "muzzle");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(probe, "probe");
    if (executionTick != muzzle.observedTick()) {
      return new Observation(Reason.BALLISTIC_MUZZLE_STALE, 0, muzzle.position());
    }
    double distance = target.subtract(muzzle.position()).length();
    double flight = distance / profile.velocity();
    if (distance < 0.1D || distance > profile.maximumRange() || flight > profile.maximumFlightTicks()) {
      return new Observation(Reason.BALLISTIC_SCAN_LIMIT, 0, muzzle.position());
    }
    Point velocity = muzzle.direction().scale(profile.velocity() / muzzle.direction().length());
    Point from = muzzle.position();
    double elapsed = 0.0D;
    int segments = 0;
    while (elapsed < flight && segments < profile.maximumFlightTicks()) {
      double duration = Math.min(1.0D, flight - elapsed);
      Point to = from.add(velocity.scale(duration));
      elapsed += duration;
      segments++;
      double uncertainty = profile.uncertainty(elapsed);
      Segment observation = Objects.requireNonNull(probe.observe(from, to, uncertainty), "segment");
      if (observation.impact() != null) {
        Reason danger = probe.danger(observation.impact(), profile.explosionRadius() + uncertainty);
        if (danger != null) return new Observation(danger, segments, observation.impact());
        // A point may designate the surface of its own block, never an intervening obstacle.
        if (allowTargetImpact && observation.rejection() == Reason.BALLISTIC_PATH_BLOCKED
            && observation.impact().subtract(target).length() <= 0.9D) {
          return new Observation(probe.danger(target, profile.explosionRadius() + uncertainty),
              segments, observation.impact());
        }
      }
      if (observation.rejection() != null) {
        return new Observation(observation.rejection(), segments, to);
      }
      from = to;
      // Explicit discrete integration, including a partial final segment. Never a continuous parabola.
      velocity = velocity.add(new Point(0, -profile.gravity(), 0));
    }
    double radius = profile.explosionRadius() + profile.uncertainty(elapsed);
    Reason danger = probe.danger(from, radius);
    if (danger == null) danger = probe.danger(target, radius);
    // These end/target regions do not claim a proved stopping point after penetration or a miss.
    return new Observation(danger, segments, from);
  }

  /** Fixed-speed, move-then-gravity compensation for the verified dry-air profile. */
  public static Point aimDirection(DryAirProfile profile, Point muzzle, Point target) {
    Point delta = target.subtract(muzzle);
    Point desired = delta;
    for (int iteration = 0; iteration < 8; iteration++) {
      double flight = desired.length() / profile.velocity();
      double whole = Math.floor(flight);
      double drop = profile.gravity() * (whole * (whole - 1.0D) / 2.0D + whole * (flight - whole));
      desired = delta.add(new Point(0, drop, 0));
    }
    return desired;
  }
}
