package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Rotations;
import org.junit.jupiter.api.Test;

class DnvTurretPoseSyncTest {
  @Test void interpolatorPreservesSmoothMotionAndShortestYawAcrossWrap() {
    var pose = new DnvTurretPoseInterpolation();
    Rotations first = new Rotations(179.0F, -8.0F, 0.0F);
    assertEquals(179.0F, pose.yaw(0.5F, first));
    var initial = pose.advance(first);
    assertEquals(0.0F, initial.yawDelta());
    assertEquals(0.0F, initial.pitchDelta());

    var motion = pose.advance(new Rotations(-179.0F, -10.0F, 0.0F));
    assertEquals(2.0F, motion.yawDelta(), 0.001F);
    assertEquals(-2.0F, motion.pitchDelta(), 0.001F);
    assertEquals(180.0F, pose.yaw(0.5F, first), 0.001F);
    assertEquals(-9.0F, pose.pitch(0.5F, first), 0.001F);
  }

  @Test void bothAutonomousTurretsPublishAtomicPoseAndUsePoweredLampSemantics() throws Exception {
    for (String entity : new String[] {"ChangfengCannonEntity.java", "StormTurretEntity.java"}) {
      String source = read("entity/" + entity);
      assertTrue(source.contains("EntityDataSerializers.ROTATIONS"), entity);
      assertTrue(source.contains("clientTurretPose.advance(syncedPose)"), entity);
      assertTrue(source.contains("syncAutonomousTurretPose();"), entity);
      assertTrue(source.contains("turretTurnSound(motion.pitchDelta(), motion.yawDelta(), 0.95F)"), entity);
      assertTrue(source.contains("boolean hasOperationalPower() { return getEnergy() > 0; }"), entity);
    }

    for (String renderer : new String[] {"EndfieldSniperTurretRenderer.java", "StormTurretRenderer.java"}) {
      String source = Files.readString(Path.of("src/artillery/java/com/liucodex/doggiesnewvehicle/client", renderer));
      assertTrue(source.contains("entity.isTurretActivated() && entity.hasOperationalPower()"), renderer);
    }
  }

  private static String read(String relative) throws Exception {
    return Files.readString(Path.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle", relative));
  }
}
