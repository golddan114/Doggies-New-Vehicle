package com.liucodex.doggiesnewvehicle.boss;

import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Local major-region boxes follow render attachments; distant bow/wing gaps stay empty. */
public final class MothershipHull {
    private MothershipHull() { }
    public static List<OBB> pose(Vec3 bottom,double yaw,Angles[] angles) {
        return pose(bottom,yaw,0,0,angles);
    }
    public static List<OBB> pose(Vec3 bottom,double yaw,double pitch,double roll,Angles[] angles) {
        var rig=MothershipRig.get();var result=new ArrayList<OBB>(rig.hullBoxes.size());
        Quaterniond body=new Quaterniond().rotationY(-Math.toRadians(yaw)).rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
        for(var box:rig.hullBoxes) {
            V point=box.center();Quaterniond rotation=new Quaterniond(body);
            if(!box.attachment().equals("body_static")) {
                int index=-1;
                for(int i=0;i<6;i++)if(box.attachment().endsWith(rig.stations.get(i).id())){index=i;break;}
                if(index<0)throw new IllegalStateException("Unbound collision attachment");
                var station=rig.stations.get(index);var angle=angles==null||angles[index]==null?rig.rest(index):angles[index];
                rotation.rotateY(-Math.toRadians(angle.yaw()));
                if(box.attachment().startsWith("pitch_")) {
                    point=rig.pitchPoint(index,point,angle);rotation.rotateX(-Math.toRadians(angle.elevation()));
                } else point=station.yawPivot().add(MothershipRig.yaw(point,angle.yaw()));
            }
            Vector3d center=body.transform(new Vector3d(point.x(),point.y()+rig.lift,point.z())).add(bottom.x,bottom.y,bottom.z);
            rotation.mul(new Quaterniond(box.qx(),box.qy(),box.qz(),box.qw()));
            var half=box.half();
            result.add(new OBB(center,
                    new Vector3d(half.x(),half.y(),half.z()),rotation,OBB.Part.BODY));
        }
        return List.copyOf(result);
    }
    public static AABB bounds(List<OBB> boxes) {
        AABB result=OBB.getWorldAABB(boxes.get(0));
        for(int i=1;i<boxes.size();i++)result=result.minmax(OBB.getWorldAABB(boxes.get(i)));
        return result;
    }
    public static boolean intersects(List<OBB> boxes,AABB shape) {
        for(OBB box:boxes)if(OBB.isColliding(box,shape))return true;
        return false;
    }
    /** Merge overlapping query regions when that reduces padded block visits. Narrow-phase stays OBB. */
    public static List<AABB> scanBounds(List<OBB> boxes) {
        List<AABB> result=new ArrayList<>();
        for(OBB box:boxes) {
            AABB next=OBB.getWorldAABB(box);long cost=blockVisits(next),bestSaving=-1;int best=-1;
            for(int i=0;i<result.size();i++) {
                AABB existing=result.get(i);
                long saving=blockVisits(existing)+cost-blockVisits(existing.minmax(next));
                if(saving>=0&&saving>bestSaving){bestSaving=saving;best=i;}
            }
            if(best>=0)result.set(best,result.get(best).minmax(next));else result.add(next);
        }
        AABB broad=bounds(boxes);long visits=0;for(AABB query:result)visits+=blockVisits(query);
        return visits<blockVisits(broad)?List.copyOf(result):List.of(broad);
    }
    private static long blockVisits(AABB b) {
        return (long)(Math.floor(b.maxX)-Math.floor(b.minX)+3)
                *(long)(Math.floor(b.maxY)-Math.floor(b.minY)+3)
                *(long)(Math.floor(b.maxZ)-Math.floor(b.minZ)+3);
    }
}
