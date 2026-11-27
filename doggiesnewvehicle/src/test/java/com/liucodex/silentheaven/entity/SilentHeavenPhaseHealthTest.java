package com.liucodex.silentheaven.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SilentHeavenPhaseHealthTest {
    @Test void fullFirstLifeEndsAtZeroWithoutCarryingDamageIntoTheSecond() {
        assertEquals(4_000,SilentHeavenPhaseHealth.capacity(0));
        assertEquals(3_000,SilentHeavenPhaseHealth.capacity(1));
        assertEquals(3_000,SilentHeavenPhaseHealth.capacity(2));
        assertEquals(3_999,SilentHeavenPhaseHealth.beforeTransitionDamage(4_000));
        assertEquals(.5F,SilentHeavenPhaseHealth.beforeTransitionDamage(1));
        assertEquals(0,SilentHeavenPhaseHealth.barProgress(1,0,80,1));
        assertEquals(0,SilentHeavenPhaseHealth.barProgress(1,79,80,1));
        assertEquals(.5,SilentHeavenPhaseHealth.barProgress(1,200,80,1_500));
        assertEquals(1,SilentHeavenPhaseHealth.barProgress(2,370,80,3_000));
        assertEquals(.5,SilentHeavenPhaseHealth.barProgress(2,370,80,1_500));
        assertEquals(1,SilentHeavenPhaseHealth.barProgress(2,370,80,4_000));
    }
}
