package com.liucodex.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.liucodex.noise.entity.NoiseAutocannonCadenceTestAccess;
import org.junit.jupiter.api.Test;

class NoiseAutocannonCadenceTest {
    @Test
    void cadenceIsExactlyFiveRoundsPerSecond() {
        assertEquals(5, NoiseAutocannonCadenceTestAccess.shotsPerCycle());
        int elapsed = 0;
        int index = 0;
        for (int shot = 0; shot < 5; shot++) {
            elapsed += NoiseAutocannonCadenceTestAccess.intervalForIndex(index);
            index = NoiseAutocannonCadenceTestAccess.nextIndex(index);
        }
        assertEquals(20, elapsed);
        assertEquals(0, index);
    }
}
