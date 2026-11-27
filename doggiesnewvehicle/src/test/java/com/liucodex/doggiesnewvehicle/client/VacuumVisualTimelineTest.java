package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VacuumVisualTimelineTest {
    @Test void dogAppearsAtSixSecondsStaysVisibleForTwoThenTheExplosionExpands() {
        assertEquals(0,VacuumVisualTimeline.dogAlpha(119.99F));assertEquals(1,VacuumVisualTimeline.dogAlpha(120));
        assertEquals(0,VacuumVisualTimeline.core(120));assertEquals(8,VacuumVisualTimeline.dogWidth(124));
        for(int age=120;age<160;age++){
            assertEquals(1,VacuumVisualTimeline.dogAlpha(age));assertEquals(0,VacuumVisualTimeline.shockAlpha(age));
            assertEquals(0,VacuumVisualTimeline.shockRadius(age));
        }
        assertEquals(1,VacuumVisualTimeline.shockAlpha(160));
        float previous=1,radius=0;
        for(int age=160;age<=222;age++){
            float alpha=VacuumVisualTimeline.dogAlpha(age),next=VacuumVisualTimeline.shockRadius(age);
            assertTrue(alpha<=previous&&alpha>=0);assertTrue(next>=radius&&next<=48);previous=alpha;radius=next;
        }
        assertEquals(0,previous);assertEquals(0,VacuumVisualTimeline.shockAlpha(190));
    }
    @Test void twelveCurvedStreamsConvergeOnTheCoreWithFiniteBoundedGeometry() {
        for(int age=0;age<120;age++)for(int arm=0;arm<VacuumVisualTimeline.ARMS;arm++){
            double previous=Double.POSITIVE_INFINITY;
            for(int segment=0;segment<=VacuumVisualTimeline.SEGMENTS;segment++){
                var p=VacuumVisualTimeline.flow(arm,segment/(float)VacuumVisualTimeline.SEGMENTS,age);
                double radius=Math.hypot(p.x(),p.z());assertTrue(Double.isFinite(radius+p.y()+p.width()));
                assertTrue(radius<=previous+1e-5&&radius<28&&p.width()>0&&p.width()<.18);previous=radius;
            }
            assertEquals(1.8,previous,1e-5);
        }
        assertEquals(0,VacuumVisualTimeline.pullAlpha(0));assertTrue(VacuumVisualTimeline.pullAlpha(60)>.99);
        assertEquals(0,VacuumVisualTimeline.pullAlpha(120));assertEquals(0,VacuumVisualTimeline.revealGlow(160));
    }
    @Test void terminalFireballAndLargerShockRemainVisualAndEndWithinTheExistingLifetime() {
        for(int age=0;age<160;age++){
            assertEquals(0,VacuumVisualTimeline.blastRadius(age));assertEquals(0,VacuumVisualTimeline.blastAlpha(age));
        }
        assertEquals(18,VacuumVisualTimeline.blastRadius(168));assertTrue(VacuumVisualTimeline.blastAlpha(168)>.8);
        assertEquals(48,VacuumVisualTimeline.shockRadius(186));
        for(int age=160;age<=222;age++){
            assertTrue(VacuumVisualTimeline.blastRadius(age)>=2&&VacuumVisualTimeline.blastRadius(age)<=18);
            assertTrue(VacuumVisualTimeline.blastAlpha(age)>=0&&VacuumVisualTimeline.blastAlpha(age)<=1);
        }
        assertEquals(0,VacuumVisualTimeline.blastAlpha(186));
        assertEquals(16,com.liucodex.doggiesnewvehicle.artillery.silo.VacuumPayloadProgress.BLAST_RADIUS);
        assertEquals(325,com.liucodex.doggiesnewvehicle.artillery.silo.VacuumPayloadProgress.BLAST_DAMAGE);
    }
}
