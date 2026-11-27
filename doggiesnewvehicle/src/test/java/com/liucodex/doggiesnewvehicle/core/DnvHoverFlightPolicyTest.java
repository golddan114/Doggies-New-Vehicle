package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvHoverFlightPolicy.Point;
import org.junit.jupiter.api.Test;

class DnvHoverFlightPolicyTest {
    @Test
    void orbitKeepsItsAnchorAndBuildHeightEnvelope() {
        Point start = DnvHoverFlightPolicy.orbitGoal(100.0D, -40.0D, 0, 72, -64, 320);
        Point quarter = DnvHoverFlightPolicy.orbitGoal(100.0D, -40.0D, 87, 72, -64, 320);
        assertEquals(110.0D, start.x(), 0.001D);
        assertEquals(-40.0D, start.z(), 0.001D);
        assertEquals(10.0D, Math.hypot(quarter.x() - 100.0D, quarter.z() + 40.0D), 0.001D);
        assertTrue(quarter.y() >= 79.0D && quarter.y() <= 81.0D);
        assertEquals(316.0D,
                DnvHoverFlightPolicy.orbitGoal(0, 0, 0, 319, -64, 320).y(), 0.001D);
    }

    @Test
    void controllerAcceleratesAndBrakesWithoutExceedingAxisLimits() {
        Point position = new Point(0.0D, 64.0D, 0.0D);
        Point goal = new Point(100.0D, 300.0D, -100.0D);
        Point velocity = new Point(0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 100; i++) {
            velocity = DnvHoverFlightPolicy.nextMotion(position, velocity, goal);
            assertTrue(Math.abs(velocity.x()) <= 0.16D);
            assertTrue(Math.abs(velocity.y()) <= 0.12D);
            assertTrue(Math.abs(velocity.z()) <= 0.16D);
            position = new Point(position.x() + velocity.x(),
                    position.y() + velocity.y(), position.z() + velocity.z());
        }
        assertTrue(position.x() > 10.0D);
        assertTrue(position.y() > 70.0D);
        assertTrue(position.z() < -10.0D);

        goal = position;
        for (int i = 0; i < 30; i++) {
            velocity = DnvHoverFlightPolicy.nextMotion(position, velocity, goal);
        }
        assertTrue(Math.abs(velocity.x()) < 0.001D);
        assertTrue(Math.abs(velocity.y()) < 0.001D);
    }

    @Test
    void blockedDiagonalSlidesOnOneLegalAxisAndNeverInventsExtraMotion() {
        Point planned = new Point(0.12D, 0.03D, 0.08D);
        Point zSlide = DnvHoverFlightPolicy.chooseUnobstructed(planned,
                motion -> motion.x() == 0.0D);
        assertEquals(new Point(0.0D, 0.03D, 0.08D), zSlide);
        assertEquals(new Point(0.0D, 0.0D, 0.0D),
                DnvHoverFlightPolicy.chooseUnobstructed(planned, motion -> false));
        assertEquals(planned,
                DnvHoverFlightPolicy.chooseUnobstructed(planned, motion -> true));
    }
}
