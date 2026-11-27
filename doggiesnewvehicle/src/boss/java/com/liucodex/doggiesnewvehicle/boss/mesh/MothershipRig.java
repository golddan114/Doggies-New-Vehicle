package com.liucodex.doggiesnewvehicle.boss.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.boss.MothershipArmament;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** One authored rig for the renderer, muzzle solutions and server hull obstruction. Loaded once per JVM. */
public final class MothershipRig {
    public record V(double x, double y, double z) {
        public V { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite rig point"); }
        public V add(V p) { return new V(x+p.x,y+p.y,z+p.z); }
        public V sub(V p) { return new V(x-p.x,y-p.y,z-p.z); }
        public V scale(double n) { return new V(x*n,y*n,z*n); }
        public double dot(V p) { return x*p.x+y*p.y+z*p.z; }
        public V cross(V p) { return new V(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x); }
        public double length() { return Math.sqrt(dot(this)); }
    }
    public record Angles(double yaw, double elevation) { }
    public record Station(String id, V yawPivot, V pitchOffset, List<V> muzzles,
                          double centerYaw, double restElevation, double yawMin, double yawMax, double minElevation, double maxElevation) { }
    public record Engine(V position, V direction, double radius) { }
    public record HullBox(String attachment,V center,V half,double qx,double qy,double qz,double qw) { }
    public final List<Station> stations;
    public final List<Engine> engines;
    public final List<V> drops;
    public final V doomsday;
    public final double lift;
    public final V dimensions;
    public final Map<String,DnvMeshCodec.Part> parts;
    public final List<HullBox> hullBoxes;
    private final Map<String,MeshRayTree> collision;

    private static final class Holder { static final MothershipRig INSTANCE = load(); }
    public static MothershipRig get() { return Holder.INSTANCE; }

    private static MothershipRig load() {
        try (InputStream manifest = MothershipRig.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/mothership_rig.json");
             InputStream mesh = MothershipRig.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/mothership.shm")) {
            if (manifest == null || mesh == null) throw new IllegalStateException("Missing mothership rig assets");
            return new MothershipRig(JsonParser.parseString(new String(manifest.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(), mesh.readAllBytes());
        } catch (Exception e) { throw new IllegalStateException("Cannot load authoritative mothership rig", e); }
    }

    public MothershipRig(JsonObject json, byte[] mesh) {
        try {
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mesh)).equals(json.get("mesh_sha256").getAsString()))
                throw new IllegalArgumentException("Rig/mesh identity mismatch");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        parts = DnvMeshCodec.parse(mesh);
        lift = number(json,"origin_lift"); dimensions = point(json.getAsJsonArray("dimensions_mc"));
        doomsday = point(json.getAsJsonArray("doomsday_muzzle"));
        List<Station> mounts = new ArrayList<>();
        for (var entry : json.getAsJsonArray("stations")) {
            JsonObject s = entry.getAsJsonObject();
            int index = mounts.size();
            String id = s.get("id").getAsString();
            if (index >= 6 || !id.equals(MothershipArmament.Station.values()[index].name().toLowerCase(java.util.Locale.ROOT)))
                throw new IllegalArgumentException("Station identity/order");
            JsonArray yaw = s.getAsJsonArray("yaw_limits"), elevation = s.getAsJsonArray("elevation_limits");
            mounts.add(new Station(id,point(s.getAsJsonArray("yaw_pivot")),point(s.getAsJsonArray("pitch_offset")),
                    points(s.getAsJsonArray("muzzle_offsets")),number(s,"center_yaw"),number(s,"rest_elevation"),
                    yaw.get(0).getAsDouble(),yaw.get(1).getAsDouble(),elevation.get(0).getAsDouble(),elevation.get(1).getAsDouble()));
            requirePart("yaw_assembly_"+id+"/body"); requirePart("pitch_assembly_"+id+"/body");
        }
        if (mounts.size()!=6 || dimensions.x()<=0 || dimensions.y()<=0 || dimensions.z()<=0) throw new IllegalArgumentException("Rig dimensions/stations");
        requirePart("body_static/body"); requirePart("body_static/light"); stations=List.copyOf(mounts);
        List<Engine> nozzles = new ArrayList<>();
        for (var entry:json.getAsJsonArray("engines")) {
            var e=entry.getAsJsonObject(); nozzles.add(new Engine(point(e.getAsJsonArray("position")),point(e.getAsJsonArray("direction")),number(e,"radius")));
        }
        if (nozzles.size()!=10) throw new IllegalArgumentException("Engine count");
        engines=List.copyOf(nozzles); drops=points(json.getAsJsonArray("drop_anchors"));
        var trees=new java.util.LinkedHashMap<String,MeshRayTree>();
        parts.forEach((name,part)->{ if(part.material()==0) trees.put(name,new MeshRayTree(part.stream())); });
        collision=Map.copyOf(trees);
        List<HullBox> boxes=new ArrayList<>();
        for(var value:json.getAsJsonArray("collision_boxes")) {
            var box=value.getAsJsonObject();var q=box.getAsJsonArray("rotation");
            String attachment=box.get("attachment").getAsString();requirePart(attachment+"/body");
            V half=point(box.getAsJsonArray("half"));
            if(Math.min(half.x,Math.min(half.y,half.z))<=0)throw new IllegalArgumentException("Hull extent");
            double x=q.get(0).getAsDouble(),y=q.get(1).getAsDouble(),z=q.get(2).getAsDouble(),w=q.get(3).getAsDouble();
            if(!Double.isFinite(x+y+z+w)||Math.abs(x*x+y*y+z*z+w*w-1)>.001)throw new IllegalArgumentException("Hull rotation");
            boxes.add(new HullBox(attachment,point(box.getAsJsonArray("center")),half,x,y,z,w));
        }
        if(boxes.isEmpty()||boxes.size()>256)throw new IllegalArgumentException("Hull box budget");
        hullBoxes=List.copyOf(boxes);
    }

    public Angles rest(int index) { var s=stations.get(index);return new Angles(s.centerYaw,s.restElevation); }
    public boolean permits(int index, Angles a) {
        var s=stations.get(index);double yaw=wrap(a.yaw-s.centerYaw);
        return Double.isFinite(a.yaw)&&Double.isFinite(a.elevation)&&yaw>=s.yawMin&&yaw<=s.yawMax&&a.elevation>=s.minElevation&&a.elevation<=s.maxElevation;
    }
    public Angles clamp(int index, Angles a) {
        var s=stations.get(index);
        return new Angles(s.centerYaw+Math.max(s.yawMin,Math.min(s.yawMax,wrap(a.yaw-s.centerYaw))),Math.max(s.minElevation,Math.min(s.maxElevation,a.elevation)));
    }
    /** Parked rockets retain the authored level pose; their firing gate still requires at least five degrees down. */
    public Angles savedPose(int index,Angles a) {
        var s=stations.get(index);
        return new Angles(s.centerYaw+Math.max(s.yawMin,Math.min(s.yawMax,wrap(a.yaw-s.centerYaw))),
                Math.max(s.minElevation,Math.min(Math.max(s.maxElevation,s.restElevation),a.elevation)));
    }
    public V muzzle(int index, int barrel, Angles a) { return pitchPoint(index,stations.get(index).muzzles.get(barrel),a); }
    public V pitchPoint(int index,V p,Angles a) {
        var s=stations.get(index);return s.yawPivot.add(yaw(s.pitchOffset.add(pitch(p,a.elevation)),a.yaw));
    }
    public V direction(Angles a) { return yaw(pitch(new V(0,0,1),a.elevation),a.yaw); }
    public Angles aim(int index,V target) {
        var s=stations.get(index);V d=target.sub(s.yawPivot);
        double y=Math.toDegrees(Math.atan2(-d.x,d.z));
        d=target.sub(s.yawPivot.add(yaw(s.pitchOffset,y)));
        return new Angles(Math.toDegrees(Math.atan2(-d.x,d.z)),Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    public V modelToWorld(V p,V bottom,double hullYaw) { return bottom.add(yaw(p.add(new V(0,lift,0)),hullYaw)); }
    public V worldToModel(V p,V bottom,double hullYaw) { return yaw(p.sub(bottom),-hullYaw).sub(new V(0,lift,0)); }

    /** Rays start beyond the muzzle skin; every other moving gun is included. BVHs stay in attachment coordinates. */
    public boolean obstructed(V from,V to,Angles[] poses) {
        if (poses.length!=6) throw new IllegalArgumentException("Six poses required");
        for(var entry:collision.entrySet()) {
            String name=entry.getKey();V a=from,b=to;
            if(!name.startsWith("body_static/")) {
                int index=-1;
                for(int i=0;i<6;i++) if(name.contains(stations.get(i).id)) {index=i;break;}
                if(index<0) throw new IllegalStateException("Unbound mesh part "+name);
                var s=stations.get(index);var pose=poses[index];
                a=yaw(a.sub(s.yawPivot),-pose.yaw); b=yaw(b.sub(s.yawPivot),-pose.yaw);
                if(name.startsWith("pitch_")) {a=pitch(a.sub(s.pitchOffset),-pose.elevation);b=pitch(b.sub(s.pitchOffset),-pose.elevation);}
            }
            if(entry.getValue().intersects(a,b)) return true;
        }
        return false;
    }
    public static V yaw(V p,double degrees) {double r=Math.toRadians(degrees),c=Math.cos(r),s=Math.sin(r);return new V(c*p.x-s*p.z,p.y,s*p.x+c*p.z);}
    public static V pitch(V p,double elevation) {double r=Math.toRadians(elevation),c=Math.cos(r),s=Math.sin(r);return new V(p.x,c*p.y+s*p.z,-s*p.y+c*p.z);}
    public static double wrap(double v) {return (v%360+540)%360-180;}
    private void requirePart(String name) {if(!parts.containsKey(name))throw new IllegalArgumentException("Missing part "+name);}
    private static double number(JsonObject o,String key) {double v=o.get(key).getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException(key);return v;}
    private static V point(JsonArray a) {
        if(a.size()!=3)throw new IllegalArgumentException("Point size");
        return new V(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
    private static List<V> points(JsonArray a) {List<V> p=new ArrayList<>();a.forEach(v->p.add(point(v.getAsJsonArray())));return List.copyOf(p);}
}
