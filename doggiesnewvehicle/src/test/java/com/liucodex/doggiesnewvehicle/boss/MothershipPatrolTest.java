package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import static com.liucodex.doggiesnewvehicle.boss.MothershipEntityExecutionFixture.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MothershipPatrolTest {
    @Test void tenThousandIdleLegsStayLocalWithRequestedHorizontalAndVerticalSteps() {
        var random=new java.util.Random(6326);V origin=new V(0,99,0),current=origin;double height=35;
        for(int i=0;i<10000;i++) {
            var leg=MothershipPatrol.choose(current,origin,height,random.nextDouble(),random.nextDouble(),random.nextDouble());
            double distance=leg.destination().sub(current).length(),change=Math.abs(leg.clearance()-height);
            assertTrue(distance>=10-1e-8&&distance<=15+1e-8);
            assertTrue(change>=5-1e-8&&change<=10+1e-8);
            assertTrue(leg.destination().sub(origin).length()<=30+1e-8);
            assertTrue(leg.clearance()>=30&&leg.clearance()<=40);
            current=leg.destination();height=leg.clearance();
        }
    }
    @Test void distantPursuitReturnsInShortLegsAndNeverTeleportsHome() {
        var leg=MothershipPatrol.choose(new V(100,99,0),new V(0,99,0),35,0,1,1);
        assertEquals(85,leg.destination().x(),1e-8);assertEquals(40,leg.clearance());
    }
    @Test void actualIdleFlightPausesMovesLocallyAndCombatImmediatelyCancelsTheLeg()throws Exception {
        Base ship=create();Vec3 origin=ship.position;double distance=0;
        for(int tick=0;tick<900;tick++) {
            ship.tickCount=tick;Vec3 previous=ship.position;execute(ship,"moveShip",null,tick);
            if(tick<100)assertEquals(origin,ship.position);
            assertTrue(ship.position.distanceTo(previous)<=.05000001);
            assertTrue(ship.getY()-ship.world.ground>=29.75&&ship.getY()-ship.world.ground<=40.25);
            distance+=ship.position.distanceTo(previous);
        }
        assertTrue(distance>12);assertTrue(ship.horizontal(origin)>5);
        ship.idleDestination=origin.add(12,0,0);ship.nextNavigation=1000;
        Actor target=new Actor();target.world=ship.world;target.position=origin.add(0,0,120);
        execute(ship,"moveShip",target,900);assertNull(ship.idleDestination);assertEquals(35,ship.idleClearance);
    }
    @Test void unloadedObstructedAndExpiredLegsStopAndReloadHasNoCatchup()throws Exception {
        Base ship=create();ship.nextIdle=1;execute(ship,"moveShip",null,1);assertNotNull(ship.idleDestination);
        Vec3 stopped=ship.position;ship.world.loaded=false;
        for(int i=2;i<100;i++)execute(ship,"moveShip",null,i);
        assertEquals(stopped,ship.position);ship.world.loaded=true;ship.world.obstructed=true;
        for(int i=100;i<300;i++)execute(ship,"moveShip",null,i);
        assertEquals(stopped,ship.position);
        execute(ship,"moveShip",null,1300);assertNull(ship.idleDestination);assertTrue(ship.nextIdle>1300);
        Base restored=create();restore(restored,save(ship,1300),50000);assertNull(restored.idleDestination);
        execute(restored,"moveShip",null,50000);assertEquals(new Vec3(0,99,0),restored.position);assertTrue(restored.nextIdle>50000);
    }
    @Test void actualFlightHonorsGroundRelativeAndWorldCeilingsWithoutRaisingTheDatum()throws Exception {
        Base ship=create();ship.position=new Vec3(0,191.9,0);ship.world.ground=200;
        Actor target=new Actor();target.world=ship.world;target.position=new Vec3(0,210,128);
        for(int tick=0;tick<250;tick++){ship.tickCount=tick;execute(ship,"moveShip",target,tick);assertTrue(ship.getY()<=192.05);}
        assertEquals(64,ship.encounterGround);
        ship.world.ceiling=150;ship.world.ground=120;ship.position=new Vec3(0,125,0);ship.nextNavigation=0;
        double max=150-ship.rig.dimensions.y()-.25-4;
        for(int tick=300;tick<550;tick++){ship.tickCount=tick;execute(ship,"moveShip",target,tick);assertTrue(ship.getY()<=max+.25);}
    }
}
