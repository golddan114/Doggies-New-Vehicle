package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.core.MothershipAnnihilatorDefense;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MothershipAnnihilatorDefenseTest {
    @Test void eightWorstCaseTenPulseVolleysLeaveHullAlive() {
        float health=8000;
        for(int volley=0;volley<8;volley++) {
            for(int pulse=0;pulse<10;pulse++) {
                float applied=0;
                for(int ray=0;ray<3;ray++) {
                    float direct=MothershipAnnihilatorDefense.withinTick(applied,
                            MothershipAnnihilatorDefense.laser(1000));
                    applied+=direct;
                }
                // Each ray can trigger an entity and a block-end blast in the upstream path.
                for(int blast=0;blast<6;blast++)applied+=MothershipAnnihilatorDefense.withinTick(applied,72);
                assertEquals(80,applied);
                health-=applied;
            }
        }
        assertEquals(1600,health);
        assertEquals(0,MothershipAnnihilatorDefense.withinTick(80,72));
        assertEquals(0,MothershipAnnihilatorDefense.laser(Float.NaN));
    }
}
