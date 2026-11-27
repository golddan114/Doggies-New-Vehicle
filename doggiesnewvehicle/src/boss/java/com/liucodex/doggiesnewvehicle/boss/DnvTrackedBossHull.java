package com.liucodex.doggiesnewvehicle.boss;

import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.data.vehicle.subdata.OBBInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Local major-region boxes projected through the existing read-only weapon poses. */
public final class DnvTrackedBossHull {
    public record Box(String group,Vector3d center,Vector3d half) { }
    public record Model(List<Box> boxes,Map<String,Double> pivots) { }
    private static final class Models {
        static final Model NOISE=load("noise"), HEAVEN=load("silent_heaven");
    }
    private final Model model;
    private final boolean heaven;
    private final List<OBB> boxes;
    private final OBBInfo movementInfo;
    private final OBB movement;
    public DnvTrackedBossHull(boolean heaven) {
        this.heaven=heaven;model=heaven?Models.HEAVEN:Models.NOISE;
        boxes=model.boxes.stream().map(box->new OBB(new Vector3d(),new Vector3d(box.half),new Quaterniond(),
                box.group.equals("chassis")?OBB.Part.BODY:OBB.Part.TURRET)).toList();
        AABB chassis=null;
        for(Box box:model.boxes)if(box.group.equals("chassis")) {
            AABB bounds=new AABB(box.center.x-box.half.x,box.center.y-box.half.y,box.center.z-box.half.z,
                    box.center.x+box.half.x,box.center.y+box.half.y,box.center.z+box.half.z);
            chassis=chassis==null?bounds:chassis.minmax(bounds);
        }
        if(chassis==null)throw new IllegalStateException("Missing tracked chassis");
        // Native vMove preserves the entity anchor; vanilla AABB movement re-centers it on gun overhang.
        // The physics-only chassis is deliberately absent from the precise damage/picking list.
        movementInfo=new OBBInfo();movementInfo.setPart(OBB.Part.COLLISION);
        movementInfo.setPosition(new Vec3((chassis.minX+chassis.maxX)/2,chassis.maxY/2,(chassis.minZ+chassis.maxZ)/2));
        movementInfo.setSize(new Vec3(chassis.getXsize()/2,chassis.maxY/2,chassis.getZsize()/2));
        movement=new OBB(new Vector3d(),new Vector3d(chassis.getXsize()/2,chassis.maxY/2,chassis.getZsize()/2),new Quaterniond(),OBB.Part.COLLISION);
    }
    public List<OBB> boxes(){return boxes;}
    public OBB movement(){return movement;}
    public OBBInfo movementInfo(){return movementInfo;}
    public static Model model(boolean heaven){return heaven?Models.HEAVEN:Models.NOISE;}
    public void update(Entity entity,DnvVehiclePresentation state) {
        update(entity.getX(),entity.getY(),entity.getZ(),entity.getYRot(),state);
    }
    public void update(double x,double y,double z,double yaw,DnvVehiclePresentation state) {
        Map<String,Matrix4d> transforms=new HashMap<>();
        Matrix4d body=new Matrix4d().translation(x,y,z).rotateY(Math.toRadians(-yaw));
        Vec3 pivot=movementInfo.getPosition();
        movement.setCenter(body.transformPosition(new Vector3d(pivot.x,pivot.y,pivot.z)));
        movement.updateRotation(body.getNormalizedRotation(new Quaterniond()));
        for(int i=0;i<boxes.size();i++) {
            Box box=model.boxes.get(i);
            Matrix4d matrix=transforms.computeIfAbsent(box.group,group->new Matrix4d(body).mul(groupPose(model,group,yaw,state,heaven)));
            OBB target=boxes.get(i);
            target.setCenter(matrix.transformPosition(new Vector3d(box.center)));
            target.updateRotation(matrix.getNormalizedRotation(new Quaterniond()));
        }
    }
    /** Same neutral coordinates and pivot order as the measured renderer; poses are never persisted here. */
    public static Matrix4d groupPose(Model model,String group,double bodyYaw,DnvVehiclePresentation state,boolean heaven) {
        Matrix4d matrix=new Matrix4d();
        var p=model.pivots;
        if(group.equals("core")||group.startsWith("main_gun_")||group.startsWith("missile_pod_")||group.startsWith("launch_hatch_"))
            aroundY(matrix,0,p.get("CORE_PIVOT_Y"),0,-(state.getCoreYaw()-bodyYaw));
        if(group.startsWith("main_gun_")) {
            String side=group.endsWith("_l")?"L":"R";
            aroundX(matrix,p.get("MAIN_GUN_"+side+"_PIVOT_X"),p.get("MAIN_GUN_PIVOT_Y"),p.get("MAIN_GUN_"+side+"_PIVOT_Z"),-state.getMainGunPitch());
        } else if(group.startsWith("autocannon_")) {
            boolean left=group.startsWith("autocannon_l_");String side=left?"L":"R";
            aroundY(matrix,p.get("AUTOCANNON_"+side+"_YAW_X"),p.get("AUTOCANNON_YAW_Y"),p.get("AUTOCANNON_YAW_Z"),
                    -((left?state.getAutocannonLYaw():state.getAutocannonRYaw())-bodyYaw));
            if(group.endsWith("_pitch"))aroundX(matrix,p.get("AUTOCANNON_"+side+"_PITCH_X"),p.get("AUTOCANNON_PITCH_Y"),p.get("AUTOCANNON_PITCH_Z"),
                    -(left?state.getAutocannonLPitch():state.getAutocannonRPitch()));
        } else if(group.startsWith("missile_pod_")) {
            boolean left=group.startsWith("missile_pod_l_");String side=left?"L":"R";
            aroundY(matrix,p.get("MISSILE_POD_"+side+"_YAW_X"),p.get("MISSILE_POD_YAW_Y"),p.get("MISSILE_POD_YAW_Z"),
                    -((left?state.getMissilePodLYaw():state.getMissilePodRYaw())-state.getCoreYaw()));
            if(group.endsWith("_pitch"))aroundX(matrix,p.get("MISSILE_POD_"+side+"_PITCH_X"),p.get("MISSILE_POD_PITCH_Y"),p.get("MISSILE_POD_PITCH_Z"),
                    -(left?state.getMissilePodLPitch():state.getMissilePodRPitch()));
        } else if(heaven&&group.startsWith("launch_hatch_")) {
            boolean left=group.endsWith("_l");
            double open=DnvVehicleTransitionTimeline.hatchOpenProgress(state.isPhaseTwo(),state.isTransitioning(),state.getTransitionTick());
            aroundY(matrix,p.get("LAUNCH_HATCH_"+(left?"L":"R")+"_X"),p.get("LAUNCH_HATCH_Y"),p.get("LAUNCH_HATCH_Z"),(left?95:-95)*open);
        }
        return matrix;
    }
    private static void aroundY(Matrix4d matrix,double x,double y,double z,double angle) {
        matrix.translate(x,y,z).rotateY(Math.toRadians(angle)).translate(-x,-y,-z);
    }
    private static void aroundX(Matrix4d matrix,double x,double y,double z,double angle) {
        matrix.translate(x,y,z).rotateX(Math.toRadians(angle)).translate(-x,-y,-z);
    }
    private static Model load(String namespace) {
        String root="/assets/"+namespace+"/meshes/"+namespace;
        try(var input=DnvTrackedBossHull.class.getResourceAsStream(root+"_collision.json");
            var mesh=DnvTrackedBossHull.class.getResourceAsStream(root+".shm")) {
            if(input==null||mesh==null)throw new IllegalStateException("Missing tracked BOSS shape");
            JsonObject json=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mesh.readAllBytes())).equals(json.get("mesh_sha256").getAsString()))
                throw new IllegalStateException("Mesh/collider identity mismatch");
            Map<String,Double> pivots=new HashMap<>();json.getAsJsonObject("pivots").entrySet().forEach(entry->pivots.put(entry.getKey(),entry.getValue().getAsDouble()));
            List<Box> boxes=new ArrayList<>();
            for(var value:json.getAsJsonArray("boxes")) {
                var box=value.getAsJsonObject();boxes.add(new Box(box.get("group").getAsString(),vector(box,"center"),vector(box,"half")));
            }
            if(boxes.isEmpty()||boxes.size()>16)throw new IllegalStateException("Tracked shape budget");
            return new Model(List.copyOf(boxes),Map.copyOf(pivots));
        } catch(Exception failure){throw new IllegalStateException("Cannot load "+namespace+" collision model",failure);}
    }
    private static Vector3d vector(JsonObject object,String key) {
        var a=object.getAsJsonArray(key);Vector3d v=new Vector3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
        if(!v.isFinite()||key.equals("half")&&Math.min(v.x,Math.min(v.y,v.z))<=0)throw new IllegalStateException("Invalid shape vector");
        return v;
    }
}
