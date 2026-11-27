package com.liucodex.doggiesnewvehicle.artillery.silo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NuclearCollisionTest {
    @Test void deeperExcavationClearsTenLayersAndLeavesTwoBlocksBelowTheModel(){
        var anchor=new net.minecraft.core.BlockPos(-17,64,31);
        var cleared=new java.util.HashSet<net.minecraft.core.BlockPos>();
        for(int cursor=0;cursor<NuclearExcavation.TOTAL;cursor++){
            var position=NuclearExcavation.position(anchor,cursor);
            if(NuclearExcavation.clears(anchor,position))cleared.add(position);
        }
        assertTrue(cleared.contains(anchor.below(10)));assertTrue(cleared.contains(anchor.offset(4,-10,4)));
        assertFalse(cleared.contains(anchor.below(11)));assertFalse(cleared.contains(anchor.offset(5,-10,0)));
        assertFalse(cleared.contains(anchor));
        assertEquals(10,NuclearDeploymentGeometry.WELL_DEPTH);
        double floor=NuclearExcavation.shaft(anchor).minY-anchor.getY();
        for(var box:NuclearCollision.silo(1,0,Vec3.ZERO))assertTrue(box.minY-floor>1.9);
    }
    @Test void authoredWellRemainsHollowAndFourFacingsFitTheExcavationEnvelope(){
        for(int yaw:new int[]{0,90,180,270})for(int frame:new int[]{1,181,661,781,1081,1381,1681,1903,2264,2384,2664,2944}){
            var boxes=NuclearCollision.silo(frame,yaw,Vec3.ZERO);assertTrue(boxes.size()<=768);
            assertFalse(boxes.stream().anyMatch(b->b.intersects(new AABB(-.1,-4.1,-.1,.1,-3.9,.1))),"Deep shaft filled at "+frame);
            for(var box:boxes){
                assertTrue(box.minX>=-8.5-.02&&box.maxX<=8.5+.02&&box.minZ>=-8.5-.02&&box.maxZ<=8.5+.02);
                assertTrue(box.minY>=-8-.02&&box.maxY<=11+.02,"Deployment height excludes geometry: "+box);
                // The authored ground plate seats 0.052 blocks into the supporting ground; deep geometry must fit the shaft.
                if(box.maxY<-.1)assertTrue(box.minX>=-4.5-.02&&box.maxX<=4.5+.02&&box.minZ>=-4.5-.02&&box.maxZ<=4.5+.02,"Underground shaft excludes geometry");
            }
        }
    }
    @Test void sideAndBoosterGrazingCannotPassThroughAnObstacleMissedByTheAxis(){
        var body=NuclearCollision.missile(0,0,Vec3.ZERO);var outer=body.stream().max(Comparator.comparingDouble(b->b.maxX)).orElseThrow();
        var obstacle=new AABB(outer.maxX-.02,outer.getCenter().y-.01,outer.maxZ+5,outer.maxX+.02,outer.getCenter().y+.01,outer.maxZ+5.05);
        var motion=new Vec3(0,0,10);assertTrue(obstacle.clip(Vec3.ZERO,motion).isEmpty());
        double hit=NuclearCollision.sweep(body,motion,obstacle);assertTrue(hit>=0&&hit<=.501);
        var contact=NuclearCollision.contact(body,motion,obstacle,hit);assertEquals(obstacle.minZ,contact.z,1e-5);
        assertEquals(0,NuclearCollision.sweep(body,motion,outer));
        assertEquals(Double.POSITIVE_INFINITY,NuclearCollision.sweep(body,motion,obstacle.move(100,0,0)));
    }
    @Test void sweepDetectsHighSpeedThinWallsWithoutTickSamplingAndKeepsFarCoordinatePrecision(){
        var body=List.of(new AABB(-1,-1,-1,1,1,1));
        assertEquals(.45,NuclearCollision.sweep(body,new Vec3(20,0,0),new AABB(10,-.1,-.1,10.001,.1,.1)),1e-9);
        var origin=new Vec3(199999.123456,65,-199999.654321);var local=NuclearCollision.missile(90,-45,Vec3.ZERO);
        var far=NuclearCollision.missile(90,-45,origin);
        for(int i=0;i<local.size();i++){assertEquals(local.get(i).minX+origin.x,far.get(i).minX,1e-9);assertEquals(local.get(i).maxZ+origin.z,far.get(i).maxZ,1e-9);}
    }
}
