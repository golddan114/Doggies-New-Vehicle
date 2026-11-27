package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HexbotIkTest {
    @Test void allSixLegsReachFlatSlopeAndRaisedTargetsWithoutStretching(){
        Point origin=new Point(0,1.1,0);
        for(int i=0;i<6;i++)for(double yaw:new double[]{0,.7,Math.PI})for(double height:new double[]{-.2,0,.5}){
            Point root=origin.add(HexbotGeometry.attachment(i).rotate(yaw));
            Point target=origin.add(HexbotGeometry.rest(i).rotate(yaw)).add(new Point(0,height,0));
            double[] lengths=HexbotGeometry.lengths(i);
            Point[] nodes=HexbotIk.solve(root,target,lengths,HexbotGeometry.rest(i).rotate(yaw));
            assertEquals(root,nodes[0]);assertTrue(nodes[4].distance(target)<.006,"IK end error "+nodes[4].distance(target));
            for(int j=0;j<4;j++)assertEquals(lengths[j],nodes[j].distance(nodes[j+1]),1e-8);
        }
    }
    @Test void distantTargetClampsAtTotalLengthAndInvalidInputFails(){
        double[] lengths=HexbotGeometry.lengths(0);Point root=new Point(0,0,0);
        Point[] nodes=HexbotIk.solve(root,new Point(100,0,0),lengths,new Point(1,0,0));
        assertEquals(HexbotGeometry.reach(0),nodes[4].length(),1e-8);
        assertThrows(IllegalArgumentException.class,()->HexbotIk.solve(root,new Point(Double.NaN,0,0),lengths,root));
    }
    @Test void frameInterpolatesAcrossYawWrapAndResetsLargeTeleports(){
        Point[] feet=new Point[6];for(int i=0;i<6;i++)feet[i]=HexbotGeometry.rest(i);
        var first=new HexbotFrame(new Point(0,0,0),719,0,0,feet);
        var next=new HexbotFrame(new Point(0,0,0),1,0,0,feet);
        assertEquals(720,first.lerp(next,.5).yaw(),.001);
        feet[0]=new Point(4,4,4);assertNotEquals(feet[0],first.foot(0));
        Point[] far=new Point[6];for(int i=0;i<6;i++)far[i]=HexbotGeometry.rest(i).add(new Point(100,0,0));
        var teleport=new HexbotFrame(new Point(100,0,0),0,0,0,far);
        assertSame(teleport,first.lerp(teleport,.2));
    }
}
