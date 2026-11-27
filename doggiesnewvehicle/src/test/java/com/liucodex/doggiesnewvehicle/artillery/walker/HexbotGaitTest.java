package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HexbotGaitTest {
    private static final Point ZERO=new Point(0,0,0);
    private static Point flat(Point p){return new Point(p.x(),0,p.z());}
    @Test void plantedFeetStayInWorldSpaceAsBodyMoves(){
        var gait=new HexbotGait();gait.update(new Point(0,1.1,0),0,ZERO,HexbotGaitTest::flat);
        Point[] initial=new Point[6];for(int i=0;i<6;i++)initial[i]=gait.foot(i).point();
        for(int tick=1;tick<=4;tick++){
            gait.update(new Point(0,1.1,tick*.1),0,new Point(0,0,.1),HexbotGaitTest::flat);
            for(int i=0;i<6;i++){assertFalse(gait.foot(i).moving());assertEquals(initial[i],gait.foot(i).point());}
        }
    }
    @Test void walkingUsesAlternatingSupportAndLiftWithBoundedQueries(){
        var gait=new HexbotGait();int swings=0;boolean lifted=false;
        for(int tick=0;tick<180;tick++){
            gait.update(new Point(0,1.1,tick*.1),0,new Point(0,0,.1),HexbotGaitTest::flat);
            assertEquals(12,gait.queryCount());assertTrue(gait.supportedCount()>=3,"tick "+tick);
            for(int i=0;i<6;i++)if(gait.foot(i).moving()){
                swings++;lifted|=gait.foot(i).point().y()>.01;
                for(int j:new int[]{i^1,i-2,i+2})if(j>=0&&j<6)assertFalse(gait.foot(j).moving(),"Adjacent legs airborne");
            }
        }
        assertTrue(swings>20);assertTrue(lifted);
    }
    @Test void terrainHeightsDrivePitchAndRollWithoutMovingIdleFeet(){
        var gait=new HexbotGait();
        gait.update(new Point(0,1.1,0),0,ZERO,p->new Point(p.x(),.15*p.z()+.1*p.x(),p.z()));
        assertTrue(gait.pitch()>0);assertTrue(gait.roll()<0);assertTrue(Double.isFinite(gait.groundHeight()));
        assertEquals(6,gait.supportedCount());
    }
    @Test void voidDoesNotInventGroundAndTeleportReprobesFeet(){
        var gait=new HexbotGait();gait.update(new Point(0,1.1,0),0,ZERO,p->null);
        assertEquals(0,gait.supportedCount());assertTrue(Double.isNaN(gait.groundHeight()));
        gait.update(new Point(100,1.1,100),0,ZERO,HexbotGaitTest::flat);
        assertEquals(6,gait.supportedCount());
        for(int i=0;i<6;i++)assertTrue(gait.foot(i).point().distance(new Point(100,0,100))<3);
    }
    @Test void unreachableSurfaceIsRejected(){
        var gait=new HexbotGait();gait.update(new Point(0,1.1,0),0,ZERO,p->new Point(p.x()+20,0,p.z()));
        assertTrue(Double.isNaN(gait.groundHeight()));assertEquals(0,gait.supportedCount());
    }
    @Test void everyFootKeepsUpAtVehicleTopSpeedInAllDirections(){
        for(double speed:new double[]{.15,.25})for(int direction=0;direction<8;direction++){
            var gait=new HexbotGait();Point v=new Point(0,0,speed).rotate(direction*Math.PI/4);
            int[] lastStep=new int[6];
            for(int tick=0;tick<400;tick++){
                Point body=new Point(0,1.1,0).add(v.scale(tick));gait.update(body,0,v,HexbotGaitTest::flat);
                for(int i=0;i<6;i++){
                    if(gait.foot(i).moving())lastStep[i]=tick;
                    assertTrue(tick-lastStep[i]<30,"Starved leg "+i+" direction "+direction);
                    assertTrue(gait.foot(i).point().horizontalDistance(body.add(HexbotGeometry.rest(i)))<(speed==.15?.8:1.4),"Foot trails body: "+i);
                }
                assertTrue(gait.supportedCount()>=3);assertEquals(12,gait.queryCount());
            }
        }
    }
    @Test void sustainedTurningCannotStarveMiddleOrOppositeLegs(){
        for(double turn:new double[]{-.07,.07,-Math.toRadians(5),Math.toRadians(5)})for(double speed:new double[]{0,.15,.25}){
            var gait=new HexbotGait();int[] lastStep=new int[6];Point body=new Point(0,1.1,0);
            for(int tick=0;tick<400;tick++){
                double yaw=Math.IEEEremainder(tick*turn,2*Math.PI);
                Point velocity=new Point(0,0,speed).rotate(yaw);body=body.add(velocity);
                gait.update(body,yaw,velocity,HexbotGaitTest::flat);
                for(int i=0;i<6;i++){
                    if(gait.foot(i).moving())lastStep[i]=tick;
                    assertTrue(tick-lastStep[i]<35,"Turning starves leg "+i);
                    assertTrue(gait.foot(i).point().horizontalDistance(body.add(HexbotGeometry.rest(i).rotate(yaw)))<1.6,
                            "Turning trails leg "+i+" speed "+speed+" turn "+turn);
                }
                assertTrue(gait.supportedCount()>=3);assertEquals(12,gait.queryCount());
            }
        }
    }
    @Test void ungroundedDeploymentAndChangedGroundRecoverWithoutTeleport(){
        for(boolean drop:new boolean[]{true,false}){
            var gait=new HexbotGait();
            for(int tick=0;tick<100;tick++){
                double y=drop?Math.max(1.1,4-tick*.15):1.1;
                double floor=!drop&&tick>=20?.1:0;
                gait.update(new Point(0,y,0),0,ZERO,p->new Point(p.x(),floor,p.z()));
            }
            assertEquals(6,gait.supportedCount(),"No permanent airborne wait cycle");
            for(int i=0;i<6;i++)assertEquals(drop?0:.1,gait.foot(i).point().y(),1e-9);
        }
    }
}
