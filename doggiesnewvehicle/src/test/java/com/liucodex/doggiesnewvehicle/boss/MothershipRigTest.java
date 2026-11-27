package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MothershipRigTest {
    private static final MothershipRig RIG=MothershipRig.get();
    @Test void installedAssetIsTheNinetyBlockRevisionWithThirteenAttachmentsAndSparseEmission() {
        assertEquals(6,RIG.stations.size());assertEquals(10,RIG.engines.size());assertEquals(2,RIG.drops.size());
        assertEquals(14,RIG.parts.size());
        int base=RIG.parts.values().stream().filter(p->p.material()==0).mapToInt(p->p.stream().length/24).sum();
        int emission=RIG.parts.values().stream().filter(p->p.material()==1).mapToInt(p->p.stream().length/24).sum();
        assertEquals(6326,base);assertEquals(269,emission);assertEquals(90,RIG.dimensions.z(),.00001);
        assertEquals(10.058949,RIG.lift,1e-5);
    }
    @Test void coordinateRoundTripsAtLargeWorldCoordinatesAndEveryHullHeading() {
        V bottom=new V(29_000_000,103,-29_000_000);
        for(double heading=-180;heading<180;heading+=17)for(int i=0;i<6;i++) {
            V muzzle=RIG.muzzle(i,0,RIG.rest(i));
            V roundtrip=RIG.worldToModel(RIG.modelToWorld(muzzle,bottom,heading),bottom,heading);
            assertTrue(muzzle.sub(roundtrip).length()<1e-7);
        }
    }
    @Test void sideMountedGunsHaveOppositeMechanicalCentersAndCannotShootThroughTheirStops() {
        assertEquals(-90,RIG.stations.get(0).centerYaw());assertEquals(90,RIG.stations.get(1).centerYaw());
        assertTrue(RIG.direction(RIG.rest(0)).x()>.999);assertTrue(RIG.direction(RIG.rest(1)).x()<-.999);
        assertTrue(RIG.permits(0,new Angles(-180,-40)));assertFalse(RIG.permits(0,new Angles(40,-40)));
        assertFalse(RIG.permits(2,new Angles(-90,0)));assertTrue(RIG.permits(2,new Angles(-90,-5)));
        assertFalse(RIG.permits(4,new Angles(151,-30)));assertFalse(RIG.permits(4,new Angles(0,1)));
        assertFalse(RIG.permits(0,new Angles(Double.NaN,-30)));
    }
    @Test void poseChangesMoveOnlyThatAttachmentAndSampledHullIntersectionsActuallyBlock() {
        Angles[] poses=new Angles[6];for(int i=0;i<6;i++)poses[i]=RIG.rest(i);
        V unchanged=RIG.muzzle(1,0,poses[1]);int blocked=0,clear=0;
        for(int i=0;i<6;i++) {
            var station=RIG.stations.get(i);
            for(double yaw:new double[]{station.yawMin(),station.yawMin()/2,0,station.yawMax()/2,station.yawMax()})
                for(double elevation:new double[]{station.minElevation(),(station.minElevation()+station.maxElevation())/2,station.maxElevation()}) {
                    poses[i]=new Angles(station.centerYaw()+yaw,elevation);
                    for(int barrel=0;barrel<station.muzzles().size();barrel++) {
                        V start=RIG.muzzle(i,barrel,poses[i]),direction=RIG.direction(poses[i]);
                        if(RIG.obstructed(start.add(direction.scale(.05)),start.add(direction.scale(50)),poses))blocked++;else clear++;
                    }
                }
            poses[i]=RIG.rest(i);
        }
        assertEquals(150,blocked+clear);assertTrue(blocked>0,"Negative hull rays must not disappear");assertTrue(clear>100);
        assertEquals(unchanged,RIG.muzzle(1,0,poses[1]));
        assertTrue(RIG.obstructed(new V(0,10,0),new V(0,-10,0),poses));
        assertFalse(RIG.obstructed(new V(20,10,0),new V(20,-10,0),poses));
    }
    @Test void aimPointsDownAtTheGroundAndNotThroughTheHullBySignError() {
        V ground=new V(20,-RIG.lift-20,0);Angles aim=RIG.aim(0,ground);
        assertTrue(aim.elevation()<0);assertTrue(aim.yaw()<0);
        V from=RIG.muzzle(0,0,aim),direction=RIG.direction(aim);
        V expected=ground.sub(from);assertTrue(direction.dot(expected)/expected.length()>.999);
    }
    @Test void mainMuzzleCanReachForwardGroundTargetsWithoutClippingItsOwnHousing() {
        Angles[] poses=new Angles[6];for(int i=0;i<6;i++)poses[i]=RIG.rest(i);
        for(double distance:new double[]{35,40,45,50}) {
            V end=new V(0,-20-RIG.lift,distance),delta=end.sub(RIG.doomsday);
            assertFalse(RIG.obstructed(RIG.doomsday.add(delta.scale(.05/delta.length())),end,poses),
                    "The calibrated main muzzle must clear the hull at distance "+distance);
        }
    }
    @Test void sharedDecoderRejectsNonfiniteVerticesTruncationAndUnexpectedBytes() throws Exception {
        byte[] good=Files.readAllBytes(Path.of("src/boss/resources/assets/doggiesnewvehicle/meshes/mothership.shm"));
        byte[] bad=good.clone();ByteBuffer buffer=ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN);
        int name=Short.toUnsignedInt(buffer.getShort(12));buffer.putFloat(14+name+8,Float.NaN);
        assertThrows(IllegalArgumentException.class,()->DnvMeshCodec.parse(bad));
        assertThrows(IllegalArgumentException.class,()->DnvMeshCodec.parse(java.util.Arrays.copyOf(good,good.length-1)));
        assertThrows(IllegalArgumentException.class,()->DnvMeshCodec.parse(java.util.Arrays.copyOf(good,good.length+1)));
    }
    @Test void navigationStopsAtClosedRoutesAndRetainsADetourSide() {
        var navigation=new MothershipNavigation();V desired=new V(0,0,1);
        final int[] probes={0};
        assertEquals(new V(0,0,0),navigation.choose(0,desired,v->{probes[0]++;return false;}));assertTrue(probes[0]<=5);
        V chosen=navigation.choose(10,desired,v->v.x()<-.1);assertTrue(chosen.x()<0);
        V stable=navigation.choose(20,desired,v->true);assertTrue(stable.x()<0);
        assertEquals(desired,navigation.choose(71,desired,v->true));
    }
    @Test void rotatedEnvelopeIncludesBowAndBothSternWings() {
        double[] side=MothershipNavigation.halfExtents(12,30,90);assertEquals(15.25,side[0],1e-8);assertEquals(6.25,side[1],1e-8);
        for(double yaw=-180;yaw<=180;yaw+=.4) {
            double[] half=MothershipNavigation.halfExtents(12,30,yaw);
            for(double x:new double[]{-6,6})for(double z:new double[]{-15,15}) {
                V corner=MothershipRig.yaw(new V(x,0,z),yaw);assertTrue(Math.abs(corner.x())<half[0]);assertTrue(Math.abs(corner.z())<half[1]);
            }
        }
    }
    @Test void rendererMatrixOrderMatchesServerMuzzleTransformsAtBothHullAndTurretExtremes() {
        for(double hull:new double[]{-180,-90,0,90,179})for(int index=0;index<6;index++) {
            var station=RIG.stations.get(index);
            for(double yaw:new double[]{station.centerYaw()+station.yawMin(),station.centerYaw(),station.centerYaw()+station.yawMax()}) {
                var angles=new Angles(yaw,station.minElevation());
                var matrix=new org.joml.Matrix4d().rotateY(Math.toRadians(-hull)).translate(0,RIG.lift,0)
                        .translate(station.yawPivot().x(),station.yawPivot().y(),station.yawPivot().z()).rotateY(Math.toRadians(-yaw))
                        .translate(station.pitchOffset().x(),station.pitchOffset().y(),station.pitchOffset().z()).rotateX(Math.toRadians(-angles.elevation()));
                for(int barrel=0;barrel<station.muzzles().size();barrel++) {
                    var socket=station.muzzles().get(barrel);var rendered=matrix.transformPosition(new org.joml.Vector3d(socket.x(),socket.y(),socket.z()));
                    V server=RIG.modelToWorld(RIG.muzzle(index,barrel,angles),new V(0,0,0),hull);
                    assertEquals(server.x(),rendered.x,1e-8);assertEquals(server.y(),rendered.y,1e-8);assertEquals(server.z(),rendered.z,1e-8);
                }
            }
        }
    }
}
