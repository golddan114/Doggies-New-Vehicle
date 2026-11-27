package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MothershipHullTest {
    private static final MothershipRig RIG=MothershipRig.get();
    @Test void majorHullRegionsAndTurretBodiesRemainHittableWhileThinBarrelsAreExcluded() throws Exception {
        assertEquals(15,RIG.hullBoxes.size());
        assertTrue(RIG.hullBoxes.stream().noneMatch(b->b.attachment().startsWith("pitch_assembly_front_")||b.attachment().startsWith("pitch_assembly_rear_")));
        Angles[] poses=new Angles[6];
        for(int scenario=0;scenario<4;scenario++) {
            for(int i=0;i<6;i++)poses[i]=scenario==0?RIG.rest(i):new Angles(RIG.stations.get(i).centerYaw()+scenario*25,-scenario*20);
            double yaw=scenario*63;Vec3 bottom=new Vec3(271,93,-185);
            var shapes=NativeHullFixture.motherPose(bottom,yaw,poses);
            for(var entry:RIG.parts.entrySet()) {
                if(entry.getValue().material()!=0)continue;
                String attachment=entry.getKey().split("/")[0];
                if(attachment.equals("body_static")||attachment.startsWith("pitch_assembly_front_")||attachment.startsWith("pitch_assembly_rear_"))continue;
                float[] stream=entry.getValue().stream();
                for(int offset=0;offset<stream.length;offset+=8) {
                    V p=new V(stream[offset],stream[offset+1],stream[offset+2]);
                    if(!attachment.equals("body_static")) {
                        int station=0;while(!attachment.endsWith(RIG.stations.get(station).id()))station++;
                        var info=RIG.stations.get(station);var pose=poses[station];
                        p=attachment.startsWith("pitch_")?RIG.pitchPoint(station,p,pose):info.yawPivot().add(MothershipRig.yaw(p,pose.yaw()));
                    }
                    p=RIG.modelToWorld(p,new V(bottom.x,bottom.y,bottom.z),yaw);
                    Vec3 point=new Vec3(p.x(),p.y(),p.z());
                    boolean covered=false;
                    for(int i=0;i<shapes.size();i++)if(RIG.hullBoxes.get(i).attachment().equals(attachment)
                            && shapes.get(i).inflate(.001).contains(point)){covered=true;break;}
                    assertTrue(covered,attachment+" uncovered vertex "+offset/8);
                }
            }
            for(V local:new V[]{new V(3.5,1,10),new V(-3.5,1,10),new V(0,0,8),new V(0,-1,-10),
                    new V(0,0,-25),new V(9,-1,-20),new V(-9,-1,-20),new V(0,1.2,17.8)}) {
                V p=RIG.modelToWorld(local.scale(1.5),new V(bottom.x,bottom.y,bottom.z),yaw);
                assertTrue(NativeHullFixture.intersects(shapes,new AABB(p.x()-.01,p.y()-.01,p.z()-.01,p.x()+.01,p.y()+.01,p.z()+.01)),"missing major hull region "+local);
            }
        }
    }
    @Test void bowAndWingVoidsDoNotCollideAndActualSparsDoAtEveryHullHeading() throws Exception {
        for(double yaw:new double[]{0,30,90,135,179}) {
            var shapes=NativeHullFixture.motherPose(Vec3.ZERO,yaw,null);
            // Full rectangular envelope falsely occupied these large spaces alongside the narrow bow.
            for(V local:new V[]{new V(10,0,20),new V(-10,0,20),new V(9,0,0),new V(-9,0,0)}) {
                V p=RIG.modelToWorld(local.scale(1.5),new V(0,0,0),yaw);
                assertFalse(NativeHullFixture.intersects(shapes,new AABB(p.x()-.1,p.y()-.1,p.z()-.1,p.x()+.1,p.y()+.1,p.z()+.1)));
                for(var shape:shapes)assertFalse(shape.clip(new Vec3(p.x(),p.y()-30,p.z()),new Vec3(p.x(),p.y()+30,p.z())));
            }
            for(var shape:shapes)assertTrue(shape.contains(new Vec3(shape.center().x,shape.center().y,shape.center().z)));
        }
    }
    @Test void collisionScanBudgetUsesSolidSectionsInsteadOfTheEntireRotatedNinetyMeterEnvelope()throws Exception {
        boolean reduced=false;
        for(double yaw:new double[]{0,45,90,135}) {
            var boxes=NativeHullFixture.motherPose(new Vec3(.25,84.25,.25),yaw,null);
            AABB broad=boxes.get(0).bounds();long sectionCells=0;
            for(var box:boxes)broad=broad.minmax(box.bounds());
            var queries=NativeHullFixture.scanBounds(boxes);
            for(var query:queries)sectionCells+=cells(query);
            for(var box:boxes){AABB b=box.bounds();assertTrue(queries.stream().anyMatch(q->q.minX<=b.minX&&q.minY<=b.minY&&q.minZ<=b.minZ&&q.maxX>=b.maxX&&q.maxY>=b.maxY&&q.maxZ>=b.maxZ));}
            assertTrue(sectionCells<=cells(broad),"solid section traversal exceeded broad-volume scan at "+yaw);
            reduced|=sectionCells<cells(broad);
        }
        assertTrue(reduced,"query grouping must save visits without dropping any solid section");
    }
    private static long cells(AABB box) {
        return (long)(Math.floor(box.maxX)-Math.floor(box.minX)+3)
                *(long)(Math.floor(box.maxY)-Math.floor(box.minY)+3)
                *(long)(Math.floor(box.maxZ)-Math.floor(box.minZ)+3);
    }
}
