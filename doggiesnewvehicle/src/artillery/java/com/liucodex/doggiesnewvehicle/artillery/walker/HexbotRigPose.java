package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Client-tick IK projection; rendering interpolates the cached nodes instead of solving every frame. */
public final class HexbotRigPose {
    private final HexbotFrame frame;
    private final Point[][] nodes;
    private HexbotRigPose(HexbotFrame frame,Point[][] nodes){this.frame=frame;this.nodes=nodes;}
    public HexbotFrame frame(){return frame;}
    public Point node(int leg,int joint){return nodes[leg][joint];}
    public static HexbotRigPose solve(HexbotFrame frame) {
        Quaterniond q=new Quaterniond().rotationYXZ(-Math.toRadians(frame.yaw()),Math.toRadians(frame.pitch()),Math.toRadians(frame.roll()));
        Point[][] nodes=new Point[6][];
        for(int i=0;i<6;i++){
            Point a=HexbotGeometry.attachment(i);Vector3d p=q.transform(new Vector3d(a.x(),a.y(),a.z()));
            Point root=frame.origin().add(new Point(p.x,p.y,p.z));
            Point out=HexbotGeometry.rest(i).rotate(-Math.toRadians(frame.yaw()));
            nodes[i]=HexbotIk.solve(root,frame.foot(i),HexbotGeometry.lengths(i),new Point(out.x(),0,out.z()));
        }
        return new HexbotRigPose(frame,nodes);
    }
    public HexbotRigPose lerp(HexbotRigPose next,double t){
        if(frame.origin().distance(next.frame.origin())>8)return next;
        Point[][] result=new Point[6][5];for(int i=0;i<6;i++)for(int j=0;j<5;j++)result[i][j]=nodes[i][j].lerp(next.nodes[i][j],t);
        return new HexbotRigPose(frame.lerp(next.frame,t),result);
    }
    public boolean matches(HexbotFrame other){
        return frame.origin().equals(other.origin())&&frame.yaw()==other.yaw()&&frame.pitch()==other.pitch()&&frame.roll()==other.roll()
                &&java.util.Arrays.equals(frame.feet(),other.feet());
    }
}
