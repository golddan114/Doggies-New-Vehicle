package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MothershipAutocannonAimTest {
    @Test void compensatedShotCrossesSmallGroundTargetWhereDirectBoresightHitsShort() {
        for(double range:new double[]{12,35,64,96}) {
            Vec3 muzzle=new Vec3(0,35,0),target=new Vec3(range,.25,0);
            Vec3 aim=MothershipAutocannonAim.point(muzzle,target,Vec3.ZERO);
            assertEquals(.25,crossing(muzzle,aim,range,Vec3.ZERO).y,1E-5,"range "+range);
            assertTrue(crossing(muzzle,target,range,Vec3.ZERO).y<0,"Old direct aim crosses below ground");
        }
    }
    @Test void horizontalMovingTargetGetsBoundedLeadWithoutAssumingItsJumpSpeedPersists() {
        Vec3 muzzle=new Vec3(0,35,0),target=new Vec3(85,.5,0),motion=new Vec3(.25,.4,.12);
        Vec3 aim=MothershipAutocannonAim.point(muzzle,target,motion);
        Vec3 miss=crossing(muzzle,aim,target.x,new Vec3(motion.x,0,motion.z)).subtract(target);
        assertTrue(miss.length()<1E-4,miss.toString());
        assertEquals(aim,MothershipAutocannonAim.point(muzzle,target,new Vec3(.25,-.4,.12)));
    }
    private static Vec3 crossing(Vec3 start,Vec3 aim,double targetX,Vec3 motion) {
        Vec3 point=start,velocity=aim.subtract(start).normalize().scale(6);
        for(int tick=0;tick<40;tick++) {
            Vec3 next=point.add(velocity);
            if(next.x>=targetX+motion.x*(tick+1)) {
                double fraction=(targetX+motion.x*tick-point.x)/(velocity.x-motion.x);
                return point.add(velocity.scale(fraction)).subtract(motion.scale(tick+fraction));
            }
            point=next;velocity=velocity.add(0,-.05F,0);
        }
        throw new AssertionError("No interception inside native lifetime");
    }
}
