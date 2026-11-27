package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Packaged component volumes share authored motion. Bounds are conservative, not a filled shaft envelope. */
public final class NuclearCollision {
    private record Piece(int group,AABB box) { }
    private static final List<Piece> SILO,MISSILE,DOGEGG;
    private static final NuclearMotion MOTION;
    static {
        try(var input=NuclearCollision.class.getResourceAsStream("/data/doggiesnewvehicle/nuclear/collision.json");
                var animation=NuclearCollision.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/nuclear_motion.nsm");
                var egg=NuclearCollision.class.getResourceAsStream("/data/doggiesnewvehicle/nuclear/dogegg_collision.json")){
            if(input==null||animation==null||egg==null)throw new IllegalStateException("Missing silo collision data");
            var data=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            SILO=parse(data.getAsJsonArray("silo"),768);MISSILE=parse(data.getAsJsonArray("missile"),64);
            DOGEGG=parse(JsonParser.parseReader(new InputStreamReader(egg,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("missile"),12);
            MOTION=NuclearMotion.parse(animation.readAllBytes());
        }catch(java.io.IOException error){throw new ExceptionInInitializerError(error);}
    }
    private NuclearCollision(){ }
    private static List<Piece> parse(com.google.gson.JsonArray values,int limit){
        if(values.isEmpty()||values.size()>limit)throw new IllegalArgumentException("Collision budget");
        var result=new ArrayList<Piece>();
        for(var value:values){var p=value.getAsJsonObject();var b=p.getAsJsonArray("bounds");
            if(b.size()!=6)throw new IllegalArgumentException("Collision bounds");
            double[] v=new double[6];for(int i=0;i<6;i++){v[i]=b.get(i).getAsDouble();if(!Double.isFinite(v[i]))throw new IllegalArgumentException("Non-finite bounds");}
            if(v[0]>=v[3]||v[1]>=v[4]||v[2]>=v[5])throw new IllegalArgumentException("Empty collision piece");
            result.add(new Piece(p.get("group").getAsInt(),new AABB(v[0],v[1],v[2],v[3],v[4],v[5])));
        }
        return List.copyOf(result);
    }
    public static List<AABB> silo(double frame,float yaw,Vec3 origin){
        var body=new Matrix4f().rotateY((float)Math.toRadians(-yaw));
        Matrix4f[] groups=new Matrix4f[MOTION.groupCount()];var result=new ArrayList<AABB>(SILO.size());
        for(var piece:SILO){int group=piece.group;
            if(groups[group]==null)groups[group]=new Matrix4f(body).mul(MOTION.sample(group,(float)frame));
            result.add(transform(piece.box,groups[group]).move(origin));
        }
        return List.copyOf(result);
    }
    public static List<AABB> missile(float yaw,float pitch,Vec3 origin){
        var pose=new Matrix4f().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch+90));
        return MISSILE.stream().map(p->transform(p.box,pose).move(origin)).toList();
    }
    public static List<AABB> dogEgg(float yaw,float pitch,Vec3 origin){
        var pose=new Matrix4f().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch+90));
        return DOGEGG.stream().map(p->transform(p.box,pose).move(origin)).toList();
    }
    private static AABB transform(AABB box,Matrix4f matrix){
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
        var point=new Vector3f();
        for(int i=0;i<8;i++){
            point.set((float)((i&1)==0?box.minX:box.maxX),(float)((i&2)==0?box.minY:box.maxY),(float)((i&4)==0?box.minZ:box.maxZ));
            matrix.transformPosition(point);x0=Math.min(x0,point.x);y0=Math.min(y0,point.y);z0=Math.min(z0,point.z);
            x1=Math.max(x1,point.x);y1=Math.max(y1,point.y);z1=Math.max(z1,point.z);
        }
        return new AABB(x0,y0,z0,x1,y1,z1);
    }
    public static AABB envelope(List<AABB> boxes){
        var result=boxes.get(0);for(int i=1;i<boxes.size();i++)result=result.minmax(boxes.get(i));return result;
    }
    /** Continuous translation via Minkowski expansion; includes side/booster grazing and starting overlap. */
    public static double sweep(List<AABB> body,Vec3 motion,AABB obstacle){
        double nearest=Double.POSITIVE_INFINITY;
        for(var box:body){
            if(box.intersects(obstacle))return 0;
            var centre=box.getCenter();var expanded=obstacle.inflate(box.getXsize()/2,box.getYsize()/2,box.getZsize()/2);
            var hit=expanded.clip(centre,centre.add(motion));
            if(hit.isPresent()&&motion.lengthSqr()>0)nearest=Math.min(nearest,centre.distanceTo(hit.get())/motion.length());
        }
        return nearest;
    }
    public static Vec3 contact(List<AABB> body,Vec3 motion,AABB obstacle,double fraction){
        Vec3 closest=null;double distance=Double.POSITIVE_INFINITY;
        for(var box:body){
            if(Math.abs(sweep(List.of(box),motion,obstacle)-fraction)>1e-7)continue;
            var centre=box.getCenter().add(motion.scale(fraction));
            var point=new Vec3(Math.max(obstacle.minX,Math.min(obstacle.maxX,centre.x)),
                    Math.max(obstacle.minY,Math.min(obstacle.maxY,centre.y)),Math.max(obstacle.minZ,Math.min(obstacle.maxZ,centre.z)));
            if(point.distanceToSqr(centre)<distance){distance=point.distanceToSqr(centre);closest=point;}
        }
        return closest;
    }
}
