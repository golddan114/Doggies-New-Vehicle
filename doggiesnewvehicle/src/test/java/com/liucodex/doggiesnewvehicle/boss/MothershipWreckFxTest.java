package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.fx.MothershipWreckFx;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import org.junit.jupiter.api.Test;

class MothershipWreckFxTest {
    private final MothershipRig rig=MothershipRig.get();
    private final MothershipWreckFx fx=new MothershipWreckFx(rig);
    @Test void allFiveRegionsHaveEightDistinctAnchorsOnTheActualUpwardAuthoredSkin() {
        assertEquals(40,fx.anchors().size());assertEquals(40,fx.anchors().stream().map(MothershipWreckFx.Anchor::point).distinct().count());
        float[] mesh=rig.parts.get("body_static/body").stream();
        for(var anchor:fx.anchors()) {
            assertTrue(anchor.normal().y()>=.25);boolean found=false;
            for(int i=0;i<mesh.length;i+=24) {
                V a=point(mesh,i),b=point(mesh,i+8),c=point(mesh,i+16),p=anchor.point();
                V u=b.sub(a),v=c.sub(a),w=p.sub(a),normal=u.cross(v);
                if(normal.length()<1e-7||Math.abs(w.dot(normal))/normal.length()>1e-5)continue;
                double uu=u.dot(u),uv=u.dot(v),vv=v.dot(v),wu=w.dot(u),wv=w.dot(v),den=uu*vv-uv*uv;
                if(Math.abs(den)<1e-10)continue;
                double s=(wu*vv-wv*uv)/den,t=(wv*uu-wu*uv)/den;
                if(s>=-1e-5&&t>=-1e-5&&s+t<=1.00001){found=true;break;}
            }
            assertTrue(found,"Floating anchor "+anchor);
        }
        for(int region=0;region<5;region++) {
            int r=region;assertEquals(8,fx.anchors().stream().filter(a->a.region()==r).count());
        }
    }
    @Test void worldPointsFollowTheSameYawPitchRollAndLiftAsTheHullWithoutWorldAabbTopDrift() {
        V bottom=new V(7,84,-13);double yaw=71,pitch=23,roll=-17;
        for(var a:fx.anchors()) {
            V p=a.point().add(a.normal().scale(.18)).add(new V(0,rig.lift,0));
            double z=Math.toRadians(roll),x=Math.toRadians(pitch),y=Math.toRadians(-yaw);
            V rz=new V(p.x()*Math.cos(z)-p.y()*Math.sin(z),p.x()*Math.sin(z)+p.y()*Math.cos(z),p.z());
            V rx=new V(rz.x(),rz.y()*Math.cos(x)-rz.z()*Math.sin(x),rz.y()*Math.sin(x)+rz.z()*Math.cos(x));
            V expected=bottom.add(new V(rx.x()*Math.cos(y)+rx.z()*Math.sin(y),rx.y(),-rx.x()*Math.sin(y)+rx.z()*Math.cos(y)));
            assertEquals(0,fx.world(a,bottom,MothershipWreckFx.rotation(yaw,pitch,roll)).sub(expected).length(),1e-8);
        }
        var box=rig.hullBoxes.stream().filter(b->b.attachment().equals("body_static")).findFirst().orElseThrow();
        var center=fx.world(new MothershipWreckFx.Anchor(box.center(),new V(0,0,0),0),bottom,MothershipWreckFx.rotation(yaw,pitch,roll));
        assertEquals(0,fx.distance(center,bottom,MothershipWreckFx.rotation(yaw,pitch,roll)),1e-8);
    }
    @Test void distanceAndQualityPlansBoundCountsAndEachFiveSitesCoverTheWholeHull() {
        assertEquals(new MothershipWreckFx.Plan(3,14,2),MothershipWreckFx.plan(95,0));
        assertEquals(new MothershipWreckFx.Plan(4,8,2),MothershipWreckFx.plan(96,0));
        assertEquals(new MothershipWreckFx.Plan(8,4,2),MothershipWreckFx.plan(192,0));
        assertEquals(0,MothershipWreckFx.plan(256,0).sites());assertEquals(0,MothershipWreckFx.plan(Double.NaN,0).sites());
        assertEquals(new MothershipWreckFx.Plan(3,7,2),MothershipWreckFx.plan(0,1));
        assertEquals(new MothershipWreckFx.Plan(6,5,1),MothershipWreckFx.plan(0,2));
        for(int tick=0;tick<240;tick++)for(int region=0;region<5;region++)assertEquals(region,fx.anchor(tick,region).region());
    }
    @Test void repeatedFramesManyWrecksPauseRollbackAndWorldChangeCannotBypassTheGlobalBudget() {
        var budget=new MothershipWreckFx.Budget();Object world=new Object(),ship=new Object();
        assertEquals(36,budget.claim(world,10,ship,1000,0));assertEquals(0,budget.claim(world,10,ship,1000,0));
        assertEquals(0,budget.claim(world,10,new Object(),1000,0));
        assertEquals(20,budget.claim(world,11,ship,1000,1));assertEquals(8,budget.claim(world,12,ship,1000,2));
        assertEquals(36,budget.claim(world,1,ship,1000,0));assertEquals(36,budget.claim(new Object(),1,ship,1000,0));
        assertEquals(0,budget.claim(null,1,ship,1000,0));
    }
    private static V point(float[] mesh,int i){return new V(mesh[i],mesh[i+1],mesh[i+2]);}
}
