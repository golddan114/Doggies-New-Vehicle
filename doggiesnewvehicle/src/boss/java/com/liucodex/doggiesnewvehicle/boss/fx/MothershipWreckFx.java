package com.liucodex.doggiesnewvehicle.boss.fx;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Immutable surface samples and bounded client emission policy; never touches gameplay state. */
public final class MothershipWreckFx {
    public record Anchor(V point, V normal, int region) { }
    public record Plan(int interval, int sites, int particlesPerSite) { }
    private record Face(V a, V b, V c, V normal, double area) { }
    private final MothershipRig rig;
    private final List<Anchor> anchors;
    public MothershipWreckFx(MothershipRig rig) {
        this.rig=rig;
        List<List<Face>> regions=new ArrayList<>();
        for(int i=0;i<5;i++)regions.add(new ArrayList<>());
        float[] mesh=rig.parts.get("body_static/body").stream();
        for(int i=0;i<mesh.length;i+=24) {
            V a=point(mesh,i),b=point(mesh,i+8),c=point(mesh,i+16);
            V cross=b.sub(a).cross(c.sub(a));double length=cross.length();
            if(length<1e-7)continue;
            V normal=cross.scale(1/length);if(normal.y()<.25)continue;
            V center=a.add(b).add(c).scale(1.0/3);
            int region=center.z()>rig.dimensions.z()*.24?0:center.z()<-rig.dimensions.z()*.24?1
                    :center.x()<-rig.dimensions.x()*.27?2:center.x()>rig.dimensions.x()*.27?3:4;
            regions.get(region).add(new Face(a,b,c,normal,length*.5));
        }
        List<Anchor> samples=new ArrayList<>();
        // Eight area-stratified anchors per region; no mesh walk during rendering.
        for(int r=0;r<5;r++) {
            var faces=regions.get(r);double area=faces.stream().mapToDouble(Face::area).sum();
            if(area<=0)throw new IllegalStateException("Missing wreck surface region "+r);
            int face=0;double cumulative=faces.get(0).area();
            for(int n=0;n<8;n++) {
                double selected=area*(n+.5)/8;
                while(cumulative<selected && face+1<faces.size())cumulative+=faces.get(++face).area();
                Face f=faces.get(face);double u=.22+((n*.61803398875)%1)*.38,v=.18+((n*.41421356237)%1)*.18;
                samples.add(new Anchor(f.a().scale(1-u-v).add(f.b().scale(u)).add(f.c().scale(v)),f.normal(),r));
            }
        }
        anchors=List.copyOf(samples);
    }
    public List<Anchor> anchors() {return anchors;}
    public Anchor anchor(int tick,int site) {
        int region=Math.floorMod(site,5),index=Math.floorMod(tick/2+site/5,8);
        return anchors.get(region*8+index);
    }
    public static Quaterniond rotation(double yaw,double pitch,double roll) {
        return new Quaterniond().rotationY(-Math.toRadians(yaw)).rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
    }
    public V world(Anchor anchor,V bottom,Quaterniond body) {
        V p=anchor.point().add(anchor.normal().scale(.18));
        Vector3d transformed=body.transform(new Vector3d(p.x(),p.y()+rig.lift,p.z()));
        return bottom.add(new V(transformed.x,transformed.y,transformed.z));
    }
    /** Nearest major rigid hull box, in its own coordinates, rather than distance from the ship origin. */
    public double distance(V camera,V bottom,Quaterniond body) {
        V relative=camera.sub(bottom);
        Vector3d local=new Quaterniond(body).conjugate().transform(new Vector3d(relative.x(),relative.y(),relative.z()));
        local.y-=rig.lift;double nearest=Double.POSITIVE_INFINITY;
        for(var box:rig.hullBoxes)if(box.attachment().equals("body_static")) {
            var center=box.center();var half=box.half();
            Vector3d p=new Quaterniond(box.qx(),box.qy(),box.qz(),box.qw()).conjugate()
                    .transform(new Vector3d(local).sub(center.x(),center.y(),center.z()));
            double x=Math.max(0,Math.abs(p.x)-half.x()),y=Math.max(0,Math.abs(p.y)-half.y()),z=Math.max(0,Math.abs(p.z)-half.z());
            nearest=Math.min(nearest,x*x+y*y+z*z);
        }
        return Math.sqrt(nearest);
    }
    public static Plan plan(double distance,int setting) {
        if(!Double.isFinite(distance)||distance<0||distance>=256)return new Plan(1,0,0);
        if(setting>=2)return new Plan(6,5,1);
        int sites=distance<96?14:distance<192?8:4;
        return new Plan(distance<96?3:distance<192?4:8,setting==1?Math.max(4,sites/2):sites,2);
    }
    private static V point(float[] mesh,int i) {return new V(mesh[i],mesh[i+1],mesh[i+2]);}

    /** Weak entity keys, one emission per world tick, shared cap across every visible wreck. */
    public static final class Budget {
        private java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
        private long tick=Long.MIN_VALUE;
        private int used;
        private final Map<Object,Long> seen=new WeakHashMap<>();
        public int claim(Object current,long now,Object ship,int requested,int setting) {
            if(world.get()!=current||now<tick){world=new java.lang.ref.WeakReference<>(current);seen.clear();tick=Long.MIN_VALUE;}
            if(now!=tick){tick=now;used=0;}
            if(current==null||requested<=0||Long.valueOf(now).equals(seen.get(ship)))return 0;
            seen.put(ship,now);
            int cap=setting>=2?8:setting==1?20:36;
            int accepted=Math.max(0,Math.min(requested,cap-used));used+=accepted;return accepted;
        }
    }
}
