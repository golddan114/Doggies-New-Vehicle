package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;

/** Atomic presentation projection. Foot coordinates remain in world space while planted. */
public record HexbotFrame(Point origin,float yaw,float pitch,float roll,Point[] feet) {
    public HexbotFrame {
        feet=feet.clone();
        if(feet.length!=6||!origin.finite()||!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(roll))
            throw new IllegalArgumentException("HexBot snapshot");
        for(Point p:feet)if(p==null||!p.finite()||p.distance(origin)>8)throw new IllegalArgumentException("Foot bounds");
    }
    @Override public Point[] feet(){return feet.clone();}
    public Point foot(int i){return feet[i];}
    public HexbotFrame lerp(HexbotFrame next,double t) {
        t=Math.max(0,Math.min(1,t));
        if(origin.distance(next.origin)>8)return next;
        Point[] out=new Point[6];for(int i=0;i<6;i++)out[i]=feet[i].lerp(next.feet[i],t);
        float yawDelta=(float)Math.IEEEremainder((double)next.yaw-yaw,360);
        return new HexbotFrame(origin.lerp(next.origin,t),yaw+yawDelta*(float)t,
                pitch+(next.pitch-pitch)*(float)t,roll+(next.roll-roll)*(float)t,out);
    }
}
