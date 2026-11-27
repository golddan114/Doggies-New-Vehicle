package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvLegacyPointOrderExitTest {
  private static final Path SOURCE = Path.of("src/main/java/com/liucodex/doggiesnewvehicle/compat");

  @Test void legacyOrdersHaveNoReceiptOrQueueShell() throws Exception {
    String services = Files.readString(SOURCE.resolve("DnvEntityDesignationServices.java"), StandardCharsets.UTF_8);
    assertFalse(services.contains("session.completePoint"));
    assertFalse(services.contains("session.salvoFeedback"));
    assertFalse(services.contains("DnvEntityDesignationSession.FailureDisposition"));
    assertFalse(services.contains("DnvSbwPointFire"));
  }

  @Test void nativeIndicatorParametersAreConsumedOnlyByM1NotRetiredAc130PointFire() throws Exception {
    assertFalse(Files.exists(SOURCE.resolve("DnvSbwPointFire.java")));
    Path m1 = Path.of("src/artillery/java/com/liucodex/doggiesnewvehicle/compat/DnvM1IndicatorOrders.java");
    assertTrue(Files.readString(m1, StandardCharsets.UTF_8).contains("FiringParametersItemKt"));
    for (Path root : new Path[] {Path.of("src/main/java"), Path.of("src/artillery/java")}) {
      try (var files = Files.walk(root)) {
        for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
          String source = Files.readString(file, StandardCharsets.UTF_8);
          assertFalse(source.contains("DnvSbwPointFire"), file.toString());
          if (!file.equals(m1)) assertFalse(source.contains("FiringParametersItemKt"), file.toString());
        }
      }
    }
  }

  @Test void sharedStationGeometryStillRejectsAnUnsafeCorridor() {
    var geometry = new com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore.StationGeometryProfile(
        "test:side", 1, 0, 0, 0, -90,
        java.util.List.of(new com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore.YawArc(-60, 60)),
        new com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore.PitchArc(-60, 60), java.util.List.of(), false);
    assertTrue(geometry.containsDirection(1,0,0, 0));
    assertTrue(geometry.containsDirection(0,0,1, 90));
    assertFalse(geometry.containsDirection(-1,0,0, 0));
    assertFalse(geometry.containsDirection(1,4,0, 0));
    assertFalse(geometry.containsDirection(Double.POSITIVE_INFINITY,0,0, 0));
    assertFalse(geometry.containsDirection(0,0,0, 0));
  }

  @Test void sharedPhysicalAlignmentTracksMuzzleMotion() {
    var east = new Vec3(1, 0, 0);
    var slightlyOff = new Vec3(1, 0.01, 0);
    assertTrue(DnvSbwCrewAutonomy.isAligned(east, slightlyOff));
    assertFalse(DnvSbwWeaponScheduler.isPointAligned(east, slightlyOff));
    assertTrue(DnvSbwWeaponScheduler.isPointAligned(east, new Vec3(1, 0.001, 0)));
    var profile = new com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.DryAirProfile(16.4667F, .05F, .03F, 5, 128, 16);
    var target = new com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point(80, 100, 0);
    var before = com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.aimDirection(profile,
        new com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point(0, 100, 0), target);
    var after = com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.aimDirection(profile,
        new com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point(0, 100, 1), target);
    assertFalse(DnvSbwWeaponScheduler.isPointAligned(new Vec3(before.x(), before.y(), before.z()),
        new Vec3(after.x(), after.y(), after.z())));
  }
}
