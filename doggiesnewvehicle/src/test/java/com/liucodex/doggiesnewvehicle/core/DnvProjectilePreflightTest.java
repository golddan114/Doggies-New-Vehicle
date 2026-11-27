package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.*;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate.Reason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DnvProjectilePreflightTest {
  private static final DryAirProfile PROFILE = new DryAirProfile(16.4667F, 0.05F, 0.03F, 5, 128, 16);
  private static final Point ORIGIN = new Point(0, 100, 0);

  @Test void pointImpactAllowsOnlyDesignatedSurfaceAndStillChecksFriendlyDanger() {
    Point target = new Point(40, 100, 0);
    var frame = new MuzzleFrame(ORIGIN, new Point(1, 0, 0), 20);
    for (boolean friendly : new boolean[]{false, true}) {
      Probe probe = new Probe() {
        public Segment observe(Point from, Point to, double uncertainty) {
          return new Segment(Reason.BALLISTIC_PATH_BLOCKED, new Point(39.5, 100, 0));
        }
        public Reason danger(Point position, double radius) {
          return friendly ? Reason.BALLISTIC_FRIENDLY_DANGER_AREA : null;
        }
      };
      assertEquals(friendly ? Reason.BALLISTIC_FRIENDLY_DANGER_AREA : null,
          DnvProjectilePreflight.trace(PROFILE, frame, target, 20, probe, true).rejection());
      assertNotNull(DnvProjectilePreflight.trace(PROFILE, frame, target, 20, probe).rejection());
      assertNotNull(DnvProjectilePreflight.trace(PROFILE, frame, new Point(60, 100, 0), 20, probe, true).rejection());
    }
  }

  @Test void compensatedPointAimFollowsDiscreteGravityAtSeveralRanges() {
    for (double distance : new double[]{10, 40, 80, 127}) {
      Point target = ORIGIN.add(new Point(distance, -10, 0));
      Point desired = DnvProjectilePreflight.aimDirection(PROFILE, ORIGIN, target);
      var result = DnvProjectilePreflight.trace(PROFILE, new MuzzleFrame(ORIGIN, desired, 20),
          target, 20, new RecordingProbe());
      assertNull(result.rejection());
      assertTrue(result.end().subtract(target).length() < 0.2, "point aim must compensate native gravity");
    }
  }

  @Test
  void firstTickUsesActualMuzzleAndNoPrematureGravityDrop() {
    var probe = new RecordingProbe();
    var frame = new MuzzleFrame(ORIGIN, new Point(2, 0, 0), 20);
    var observation = DnvProjectilePreflight.trace(PROFILE, frame,
        ORIGIN.add(new Point(PROFILE.velocity(), 0, 0)), 20, probe);
    assertNull(observation.rejection());
    assertEquals(ORIGIN, probe.starts.get(0));
    assertEquals(100, observation.end().y(), 1.0E-12);
    assertEquals(PROFILE.velocity(), observation.end().x(), 1.0E-12);
    assertEquals(1, observation.segments());
  }

  @Test
  void discreteFlightDiffersFromContinuousGravityAndHandlesPartialTick() {
    var observation = trace(ORIGIN, new Point(1, 0, 0), PROFILE.velocity() * 3.5, new RecordingProbe());
    // Three full moves, then half of velocity[3]: drop = g * (0 + 1 + 2 + 1.5).
    assertEquals(100 - PROFILE.gravity() * 4.5, observation.end().y(), 1.0E-10);
    assertNotEquals(100 - 0.5 * PROFILE.gravity() * 3.5 * 3.5, observation.end().y(), 1.0E-4);
    assertEquals(4, observation.segments());
  }

  @Test
  void multipleTurretsKeepIndependentOriginsDirectionsAndGravity() {
    var east = trace(new Point(8, 30, -4), new Point(1, 0, 0), 40, new RecordingProbe());
    var west = trace(new Point(-15, 50, 7), new Point(-1, 0, 0), 40, new RecordingProbe());
    var north = trace(new Point(3, 80, 9), new Point(0, 0, 1), 40, new RecordingProbe());
    var eastAgain = trace(new Point(8, 30, -4), new Point(1, 0, 0), 40, new RecordingProbe());
    assertEquals(48, east.end().x(), 1.0E-10);
    assertEquals(-55, west.end().x(), 1.0E-10);
    assertEquals(49, north.end().z(), 1.0E-10);
    assertEquals(east, eastAgain);
    assertEquals(-4, east.end().z());
    assertEquals(7, west.end().z());
  }

  @Test
  void independentPlatformCanSupplyAnotherVerifiedDryAirProfile() {
    var ownProfile = new DryAirProfile(4, 0.2F, 0, 2, 100, 32);
    var observed = DnvProjectilePreflight.trace(ownProfile,
        new MuzzleFrame(ORIGIN, new Point(0, 0, 1), 20), new Point(0, 100, 80), 20, new RecordingProbe());
    assertNull(observed.rejection());
    assertEquals(80, observed.end().z(), 1.0E-10);
    assertEquals(100 - 190 * (double) ownProfile.gravity(), observed.end().y(), 1.0E-10);
    assertEquals(20, observed.segments());
  }

  @Test
  void staleAndFutureMuzzleFramesNeverReadTheWorld() {
    for (long execution : new long[] {19, 21, 25}) {
      var probe = new RecordingProbe();
      var observation = DnvProjectilePreflight.trace(PROFILE,
          new MuzzleFrame(ORIGIN, new Point(1, 0, 0), 20), new Point(50, 100, 0), execution, probe);
      assertEquals(Reason.BALLISTIC_MUZZLE_STALE, observation.rejection());
      assertTrue(probe.starts.isEmpty());
      assertTrue(probe.dangerCenters.isEmpty());
    }
  }

  @Test
  void everyPathRefusalStopsFurtherQueries() {
    for (Reason reason : List.of(Reason.BALLISTIC_PATH_UNLOADED, Reason.BALLISTIC_MEDIUM_UNSUPPORTED,
        Reason.BALLISTIC_FRIENDLY_CORRIDOR, Reason.BALLISTIC_SCAN_LIMIT)) {
      var probe = new RecordingProbe();
      probe.segmentReason = reason;
      var observation = trace(ORIGIN, new Point(1, 0, 0), 100, probe);
      assertEquals(reason, observation.rejection());
      assertEquals(1, observation.segments());
      assertEquals(1, probe.starts.size());
      assertTrue(probe.dangerCenters.isEmpty());
    }
  }

  @Test
  void terrainImpactChecksItsOwnDangerAreaBeforeReportingObstruction() {
    var probe = new RecordingProbe();
    probe.segmentReason = Reason.BALLISTIC_PATH_BLOCKED;
    probe.impact = new Point(9, 99, 0);
    probe.dangerReason = Reason.BALLISTIC_FRIENDLY_DANGER_AREA;
    var observation = trace(ORIGIN, new Point(1, 0, 0), 100, probe);
    assertEquals(Reason.BALLISTIC_FRIENDLY_DANGER_AREA, observation.rejection());
    assertEquals(List.of(probe.impact), probe.dangerCenters);
    assertTrue(probe.radii.get(0) > 5);
  }

  @Test
  void clearNominalPathStillChecksBothEndAndIntendedTargetAreas() {
    var probe = new RecordingProbe();
    var observation = trace(ORIGIN, new Point(1, 0, 0), 100, probe);
    assertNull(observation.rejection()); // Observation only; it is not a FireGate.Decision.
    assertEquals(2, probe.dangerCenters.size());
    assertEquals(observation.end(), probe.dangerCenters.get(0));
    assertEquals(new Point(100, 100, 0), probe.dangerCenters.get(1));
    assertNotEquals(probe.dangerCenters.get(0), probe.dangerCenters.get(1));
    assertTrue(probe.radii.stream().allMatch(radius -> radius > PROFILE.explosionRadius()));
  }

  @Test
  void friendlyNearEndOrUnloadedDangerAreaRejectsEvenWithClearFlight() {
    for (Reason reason : List.of(Reason.BALLISTIC_FRIENDLY_DANGER_AREA, Reason.BALLISTIC_PATH_UNLOADED)) {
      var probe = new RecordingProbe();
      probe.dangerReason = reason;
      assertEquals(reason, trace(ORIGIN, new Point(1, 0, 0), 100, probe).rejection());
    }
  }

  @Test
  void rangeAndFlightBudgetsRefuseWithoutWorldQueries() {
    var probe = new RecordingProbe();
    assertEquals(Reason.BALLISTIC_SCAN_LIMIT, trace(ORIGIN, new Point(1, 0, 0), 129, probe).rejection());
    assertEquals(Reason.BALLISTIC_SCAN_LIMIT, trace(ORIGIN, new Point(1, 0, 0), 0.01, probe).rejection());
    var slow = new DryAirProfile(1, 0.05F, 0, 0, 128, 4);
    assertEquals(Reason.BALLISTIC_SCAN_LIMIT, DnvProjectilePreflight.trace(slow,
        new MuzzleFrame(ORIGIN, new Point(1, 0, 0), 20), new Point(10, 100, 0), 20, probe).rejection());
    assertTrue(probe.starts.isEmpty());
  }

  @Test
  void dispersionEnvelopeContainsWorstCaseTriangularNoiseOnAllThreeAxes() {
    double ticks = 8;
    double bound = Math.sqrt(3) * PROFILE.velocity() * 0.0172275 * PROFILE.spread() * ticks;
    assertEquals(bound + 0.35, PROFILE.uncertainty(ticks), 1.0E-12);
    assertTrue(PROFILE.uncertainty(8) > PROFILE.uncertainty(4));
  }

  @Test
  void malformedParametersCannotEnterGeometryQueries() {
    assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new MuzzleFrame(ORIGIN, new Point(0, 0, 0), 20));
    assertThrows(IllegalArgumentException.class, () -> new DryAirProfile(Float.POSITIVE_INFINITY, 0, 0, 0, 128, 8));
    assertThrows(IllegalArgumentException.class, () -> new DryAirProfile(16, -1, 0, 0, 128, 8));
    assertThrows(IllegalArgumentException.class, () -> new DryAirProfile(16, 0, 0, 0, 128, 65));
  }

  private static Observation trace(Point origin, Point direction, double distance, RecordingProbe probe) {
    return DnvProjectilePreflight.trace(PROFILE, new MuzzleFrame(origin, direction, 20),
        origin.add(direction.scale(distance / direction.length())), 20, probe);
  }

  private static final class RecordingProbe implements Probe {
    private final List<Point> starts = new ArrayList<>();
    private final List<Point> dangerCenters = new ArrayList<>();
    private final List<Double> radii = new ArrayList<>();
    private Reason segmentReason;
    private Reason dangerReason;
    private Point impact;

    @Override public Segment observe(Point from, Point to, double uncertainty) {
      starts.add(from);
      return new Segment(segmentReason, impact);
    }

    @Override public Reason danger(Point point, double radius) {
      dangerCenters.add(point);
      radii.add(radius);
      return dangerReason;
    }
  }
}
