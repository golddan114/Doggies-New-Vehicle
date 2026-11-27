package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvM1RemoteCameraTest {
    @Test void mountedCameraStopsBeforeTheWallAndNeverMovesBehindTheSeat() {
        var origin = new Vec3(10, 3, -20);
        for (float yaw : new float[]{-179, -90, 0, 90, 179}) {
            Vec3 direction = Vec3.directionFromRotation(-15, yaw).normalize();
            Vec3 desired = origin.add(direction.scale(4));
            Vec3 wall = origin.add(direction.scale(2));
            var stopped = DnvM1RemoteCrewView.stopBeforeWall(origin, desired, wall);
            assertEquals(.15, stopped.distanceTo(wall), 1e-6);
            assertEquals(1.85, stopped.distanceTo(origin), 1e-6);
            assertEquals(origin, DnvM1RemoteCrewView.stopBeforeWall(origin, desired, origin));
        }
    }

    @Test void bothThirdPersonDirectionsStayAtTheTurretAndTranslateWithItAcrossEveryAimAngle() {
        for (float yaw : new float[]{-180,-90,0,90,180}) for (float pitch : new float[]{-80,0,50}) {
            var anchor = new Vec3(120,75,-360);
            var back = DnvM1RemoteCrewView.thirdPersonPosition(anchor,yaw,pitch,7);
            var front = DnvM1RemoteCrewView.thirdPersonPosition(anchor,yaw+180,-pitch,7);
            assertEquals(7,back.distanceTo(anchor),1e-6); assertEquals(7,front.distanceTo(anchor),1e-6);
            assertTrue(back.add(front).scale(.5).distanceTo(anchor)<.003);
            var movement = new Vec3(-450,12,70);
            var moved = DnvM1RemoteCrewView.thirdPersonPosition(anchor.add(movement),yaw,pitch,7);
            assertTrue(moved.subtract(back).distanceTo(movement)<1e-6);
        }
    }
}
