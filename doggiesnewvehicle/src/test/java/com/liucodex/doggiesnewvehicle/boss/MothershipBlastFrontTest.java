package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MothershipBlastFrontTest {
    @Test void shellsExpandOutwardVisitEveryCellOnceAndResumeWithoutReplaying() {
        var front = new MothershipBlastFront(); front.begin();
        var cells = new HashSet<MothershipBlastFront.Cell>(); int previous = 0;
        while (front.active()) {
            var cell = front.next();
            int radius = Math.max(Math.abs(cell.x()), Math.max(Math.abs(cell.y()), Math.abs(cell.z())));
            assertTrue(radius >= previous && radius <= 16); previous = radius;
            assertTrue(cells.add(cell), "Duplicate terrain cell");
            if (front.cursor() % 197 == 0) {
                var restored = new MothershipBlastFront(); restored.restore(front.cursor()); front = restored;
            }
        }
        assertEquals(33 * 33 * 33, cells.size());
        assertEquals(MothershipDoomsday.DEFAULT_BLAST_RADIUS, MothershipBlastFront.RADIUS);
    }

    @Test void missingInvalidAndFinishedCursorsCannotRestartDestruction() {
        var front = new MothershipBlastFront(); assertFalse(front.active());
        for (int value : new int[]{-1, Integer.MIN_VALUE, MothershipBlastFront.TOTAL, Integer.MAX_VALUE}) {
            front.restore(value); assertFalse(front.active()); assertThrows(IllegalStateException.class, front::next);
        }
    }

    @Test void eachPulseUsesOnlyItsOwnRadiusAndRestoresWithinThatRadius() {
        for(int radius:new int[]{4,8,12,16}) {
            var front=new MothershipBlastFront(radius);front.begin();
            int visited=0;
            while(front.active()) {
                var cell=front.next();visited++;
                assertTrue(Math.abs(cell.x())<=radius&&Math.abs(cell.y())<=radius&&Math.abs(cell.z())<=radius);
            }
            assertEquals((2*radius+1)*(2*radius+1)*(2*radius+1),visited);
            var restored=new MothershipBlastFront(radius);restored.restore(visited);
            assertFalse(restored.active());
        }
    }
}
