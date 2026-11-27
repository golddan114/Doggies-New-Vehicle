package com.liucodex.silentheaven.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SilentHeavenAutocannonCadenceTest {
    @Test
    void threeIntervalsProduceExactlyThreeShotsPerSecond() {
        int[] intervals = new int[SilentHeavenAutocannonCadence.SHOTS_PER_CYCLE];
        int index = 0;
        int elapsedTicks = 0;
        for (int shot = 0; shot < intervals.length; shot++) {
            intervals[shot] = SilentHeavenAutocannonCadence.intervalForIndex(index);
            elapsedTicks += intervals[shot];
            index = SilentHeavenAutocannonCadence.nextIndex(index);
        }

        assertArrayEquals(new int[] {7, 7, 6}, intervals);
        assertEquals(SilentHeavenAutocannonCadence.CYCLE_TICKS, elapsedTicks);
        assertEquals(0, index);
    }

    @Test
    void cadenceHandlesPersistedIndicesSafely() {
        assertEquals(2, SilentHeavenAutocannonCadence.normalizeIndex(-1));
        assertEquals(0, SilentHeavenAutocannonCadence.normalizeIndex(3));
    }
}
