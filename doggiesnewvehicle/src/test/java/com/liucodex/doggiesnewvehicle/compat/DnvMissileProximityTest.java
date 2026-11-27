package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvMissileProximityTest {
    @Test void sustainedClosePursuitExplodesAtTheMissileOnceAfterSixTicks() throws Exception {
        for (boolean boss : new boolean[]{false,true}) {
            var f=armed(boss);
            for (int i=0;i<5;i++) {
                f.missile.world.now++;
                f.target.position=new Vec3((i%2)*.15,0,0);
                assertFalse(f.beforeTick());
                assertFalse(f.beforeTick(),"reentry within the same tick cannot advance the fuze");
            }
            f.missile.world.now++;
            assertTrue(f.beforeTick());
            assertTrue(f.missile.removed);assertEquals(1,f.missile.explosions);
            assertEquals(f.missile.position,f.missile.explosionPoint);
            f.beforeTick();assertEquals(1,f.missile.explosions);
        }
    }
    @Test void escapeOrTeleportResetsTheCloseTimerAndLostTargetDiscardsWithoutBlast() throws Exception {
        var f=armed(false);
        for(int i=0;i<4;i++){f.missile.world.now++;assertFalse(f.beforeTick());}
        f.target.position=new Vec3(40,0,0);f.missile.world.now++;assertFalse(f.beforeTick());
        f.target.position=Vec3.ZERO;
        for(int i=0;i<5;i++){f.missile.world.now++;assertFalse(f.beforeTick());}
        f.target.removed=true;f.missile.world.now++;assertTrue(f.beforeTick());
        assertEquals(0,f.missile.explosions);assertEquals(1,f.missile.world.cosmeticExplosions);
    }
    @Test void wallsUnloadedTerrainDecoysAndLaunchClearanceNeverGrantNearBlast() throws Exception {
        for(String condition:new String[]{"wall","chunk","decoy","launch","young"}) {
            var f=armed(true);
            switch(condition) {
                case "wall" -> f.missile.world.blocked=true;
                case "chunk" -> f.missile.world.loaded=false;
                case "decoy" -> f.missile.distracted=true;
                case "launch" -> { f.arm(6); }
                case "young" -> f.missile.f_19797_=3;
            }
            for(int i=0;i<16;i++){f.missile.world.now++;assertFalse(f.beforeTick(),condition);}
            assertEquals(0,f.missile.explosions,condition);
        }
    }
    private static ExactApocalypseMissileFixture armed(boolean boss)throws Exception {
        var f=new ExactApocalypseMissileFixture(boss);
        f.missile.f_19797_=20;f.missile.position=new Vec3(1.7,.5,0);
        f.target.position=Vec3.ZERO;
        f.missile.data.putBoolean(DnvVlsFlight.OWNED,true);
        f.missile.data.putBoolean(DnvVlsFlight.ACTIVE,true);
        return f;
    }
}
