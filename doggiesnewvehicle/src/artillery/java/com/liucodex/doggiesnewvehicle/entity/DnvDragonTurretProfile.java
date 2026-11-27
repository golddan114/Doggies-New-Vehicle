package com.liucodex.doggiesnewvehicle.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable authored coordinates shared by native data, placement and the renderer. */
public final class DnvDragonTurretProfile {
    public static final List<String> MODELS = List.of("bofors_twin", "bofors_quad", "flak38", "flak88", "m1_open", "flak40");
    public static final Map<String, DnvDragonTurretProfile> ALL = load();
    public static final DnvDragonTurretProfile M1 = ALL.get("m1_yunlong");
    public final String model, entity;
    public final Vec3 yaw, pitch, minimum, maximum, camera, ads;
    public final int seats, hp, servo, recoilTicks, barrels;
    public final float recoilStroke;
    private final float collisionWidth, collisionHeight;
    public final double renderRadius;
    public final List<String> groups;
    public final List<Link> links;
    public final Map<String,Link> linkByGroup;
    public final List<Vec3> muzzles, projectiles, seatPositions, exits;
    public final List<Vec3> seatContacts;

    private DnvDragonTurretProfile(JsonObject data) {
        model=data.get("model").getAsString();entity=data.get("entity").getAsString();
        yaw=vec(data.getAsJsonArray("yaw"));pitch=vec(data.getAsJsonArray("pitch"));
        minimum=vec(data.getAsJsonArray("minimum"));maximum=vec(data.getAsJsonArray("maximum"));
        collisionWidth=data.get("collision_width").getAsFloat();collisionHeight=data.get("collision_height").getAsFloat();
        // Full articulated geometry must remain visible after the physical body becomes smaller.
        renderRadius=pitch.length()+new Vec3(
            Math.max(Math.abs(minimum.x-pitch.x),Math.abs(maximum.x-pitch.x)),
            Math.max(Math.abs(minimum.y-pitch.y),Math.abs(maximum.y-pitch.y)),
            Math.max(Math.abs(minimum.z-pitch.z),Math.abs(maximum.z-pitch.z))).length()+1;
        seats=data.get("seats").getAsInt();hp=data.get("hp").getAsInt();servo=data.get("servo").getAsInt();
        recoilTicks=data.get("recoil_ticks").getAsInt();recoilStroke=data.get("recoil_stroke").getAsFloat();
        muzzles=vectors(data.getAsJsonArray("muzzles"));projectiles=vectors(data.getAsJsonArray("projectile_positions"));barrels=muzzles.size();
        seatPositions=vectors(data.getAsJsonArray("seat_positions"));exits=vectors(data.getAsJsonArray("exit_positions"));
        seatContacts=data.has("runtime_seat_contacts") ? vectors(data.getAsJsonArray("runtime_seat_contacts")) : List.of();
        var cameras=data.getAsJsonObject("cameras");
        camera=vec(data.has("runtime_camera_position")?data.getAsJsonArray("runtime_camera_position"):
            cameras.getAsJsonObject("first_person").getAsJsonArray("game_deployment_position"));
        ads=vec(data.has("runtime_ads_position")?data.getAsJsonArray("runtime_ads_position"):cameras.getAsJsonObject("ads").getAsJsonArray("game_deployment_position"));
        groups=data.getAsJsonArray("groups").asList().stream().map(x->x.getAsString()).toList();
        var list=new ArrayList<Link>();
        for(var item:data.getAsJsonArray("links")) {
            var l=item.getAsJsonObject();list.add(new Link(l.get("group").getAsString(),vec(l.getAsJsonArray("pivot")),
                vec(l.getAsJsonArray("target")),l.get("pivot_pitched").getAsBoolean(),l.get("target_pitched").getAsBoolean()));
        }
        links=List.copyOf(list);
        linkByGroup=links.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Link::group,l->l));
        if(barrels<1||barrels>4||seats<0||seats>2||(seats==0?servo<1:servo!=0)||recoilTicks<1||hp<1||seatPositions.size()!=seats||seatContacts.size()!=seats
            ||!Float.isFinite(collisionWidth)||!Float.isFinite(collisionHeight)||collisionWidth<=0||collisionHeight<=0)
            throw new IllegalArgumentException("Invalid authored turret profile: "+model);
    }
    public static DnvDragonTurretProfile forEntity(String name) {
        var profile=ALL.get(name);if(profile==null)throw new IllegalArgumentException("Unknown turret: "+name);return profile;
    }
    private static Map<String,DnvDragonTurretProfile> load() {
        var all=new LinkedHashMap<String,DnvDragonTurretProfile>();
        for(String name:MODELS) {
            String path="/data/doggiesnewvehicle/artillery/dragon/"+name+".json";
            var in=DnvDragonTurretProfile.class.getResourceAsStream(path);
            if(in==null)throw new IllegalStateException("Missing authored profile "+path);
            try(var reader=new InputStreamReader(in,StandardCharsets.UTF_8)) {
                var p=new DnvDragonTurretProfile(JsonParser.parseReader(reader).getAsJsonObject());
                if(all.put(p.entity,p)!=null)throw new IllegalStateException("Duplicate turret identity");
            }catch(java.io.IOException e){throw new IllegalStateException(path,e);}
        }
        return java.util.Collections.unmodifiableMap(all);
    }
    private static Vec3 vec(JsonArray a) {
        if(a.size()!=3)throw new IllegalArgumentException("Expected xyz");
        var p=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
        if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z))throw new IllegalArgumentException("Nonfinite anchor");return p;
    }
    private static List<Vec3> vectors(JsonArray a){return a.asList().stream().map(x->vec(x.getAsJsonArray())).toList();}
    public float width(){return collisionWidth;}
    public float height(){return collisionHeight;}
    /** Actual cushion contacts, relative to yaw. Original author anchors identify the footplates. */
    public Vec3 nativeSeatPosition(int index) {
        // Vanilla adult player: .9375 renderer scale, 1.501 lift, riding thigh lower surface at its hip.
        return seatContacts.get(index).add(0, -.5882301080848004, 0);
    }
    /** Neutral, on the mean bore axis and ahead of all authored sight/barrel geometry. */
    public Vec3 clearAdsPosition(){
        Vec3 center=Vec3.ZERO;
        for(Vec3 muzzle:projectiles)center=center.add(muzzle);
        center=pitch.add(center.scale(1.0/barrels));
        return new Vec3(center.x,center.y,maximum.z+.25);
    }
    public Vec3 pitched(Vec3 point,float elevation){return pitch.add(point.subtract(pitch).xRot(elevation*Mth.DEG_TO_RAD));}
    public Vec3 yawed(Vec3 point,float visualYaw){return yaw.add(point.subtract(yaw).yRot(-visualYaw*Mth.DEG_TO_RAD));}
    public Vec3 posed(Vec3 point,float visualYaw,float elevation){return yawed(pitched(point,elevation),visualYaw);}
    public LinkPose linkPose(Link link,float elevation) {
        Vec3 a=link.pivotPitched?pitched(link.pivot,elevation):link.pivot;
        Vec3 b=link.targetPitched?pitched(link.target,elevation):link.target;
        var from=link.target.subtract(link.pivot).normalize();var to=b.subtract(a).normalize();
        var rotation=new Quaternionf().rotationTo((float)from.x,(float)from.y,(float)from.z,(float)to.x,(float)to.y,(float)to.z);
        return new LinkPose(link.pivot,a,rotation);
    }
    public Vec3 transformGroup(Vec3 point,String group,float elevation,float recoil) {
        for(var link:links)if(link.group.equals(group))return linkPose(link,elevation).apply(point);
        if(group.startsWith("recoil_barrel_"))point=point.add(0,0,-recoilStroke*recoil);
        return group.equals("pitch_assembly")||group.startsWith("recoil_barrel_")?pitched(point,elevation):point;
    }
    public List<AABB> clearance(float visualYaw) {
        AABB bounds=null;
        for(double x:new double[]{minimum.x,maximum.x})for(double z:new double[]{minimum.z,maximum.z}) {
            Vec3 p=yawed(new Vec3(x,0,z),visualYaw);var column=new AABB(p.x,0,p.z,p.x,maximum.y,p.z).inflate(.05,0,.05);
            bounds=bounds==null?column:bounds.minmax(column);
        }
        return List.of(bounds);
    }
    public record Link(String group,Vec3 pivot,Vec3 target,boolean pivotPitched,boolean targetPitched){}
    public record LinkPose(Vec3 original,Vec3 pivot,Quaternionf rotation) {
        public Vec3 apply(Vec3 point){var p=point.subtract(original);var v=new Vector3f((float)p.x,(float)p.y,(float)p.z).rotate(rotation);return pivot.add(v.x,v.y,v.z);}
    }
}
