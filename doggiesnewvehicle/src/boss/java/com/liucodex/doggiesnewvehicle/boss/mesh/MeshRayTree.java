package com.liucodex.doggiesnewvehicle.boss.mesh;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.util.Arrays;
import java.util.Comparator;

/** Immutable median BVH. Created once, traversed only for a pending shot. */
final class MeshRayTree {
    private record Triangle(V a,V b,V c) { }
    private record Node(V min,V max,Node left,Node right,Triangle[] leaf) { }
    private final Node root;
    MeshRayTree(float[] stream) {
        Triangle[] triangles=new Triangle[stream.length/24];
        for(int i=0;i<triangles.length;i++)triangles[i]=new Triangle(v(stream,i*24),v(stream,i*24+8),v(stream,i*24+16));
        root=build(triangles);
    }
    boolean intersects(V a,V b) {return hit(root,a,b.sub(a));}
    private static Node build(Triangle[] ts) {
        if(ts.length==0)return null;
        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={-Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
        for(var t:ts)for(var p:new V[]{t.a,t.b,t.c})for(int axis=0;axis<3;axis++){lo[axis]=Math.min(lo[axis],at(p,axis));hi[axis]=Math.max(hi[axis],at(p,axis));}
        V min=new V(lo[0],lo[1],lo[2]),max=new V(hi[0],hi[1],hi[2]);
        if(ts.length<=8)return new Node(min,max,null,null,ts);
        int axis=0;for(int i=1;i<3;i++)if(hi[i]-lo[i]>hi[axis]-lo[axis])axis=i;
        final int chosen=axis;
        Arrays.sort(ts,Comparator.comparingDouble(t->(at(t.a,chosen)+at(t.b,chosen)+at(t.c,chosen))/3));
        int mid=ts.length/2;
        return new Node(min,max,build(Arrays.copyOfRange(ts,0,mid)),build(Arrays.copyOfRange(ts,mid,ts.length)),null);
    }
    private static boolean hit(Node n,V origin,V d) {
        if(n==null)return false;
        double near=0,far=1;
        for(int i=0;i<3;i++) {
            double delta=at(d,i),p=at(origin,i),lo=at(n.min,i)-1e-7,hi=at(n.max,i)+1e-7;
            if(Math.abs(delta)<1e-12){if(p<lo||p>hi)return false;continue;}
            double a=(lo-p)/delta,b=(hi-p)/delta;near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return false;
        }
        if(n.leaf==null)return hit(n.left,origin,d)||hit(n.right,origin,d);
        for(var t:n.leaf) {
            V e1=t.b.sub(t.a),e2=t.c.sub(t.a),h=d.cross(e2);double det=e1.dot(h);
            if(Math.abs(det)<1e-10)continue;
            double inverse=1/det;V s=origin.sub(t.a);double u=inverse*s.dot(h);if(u<0||u>1)continue;
            V q=s.cross(e1);double v=inverse*d.dot(q);if(v<0||u+v>1)continue;
            double distance=inverse*e2.dot(q);if(distance>1e-7&&distance<1-1e-7)return true;
        }
        return false;
    }
    private static V v(float[] s,int i){return new V(s[i],s[i+1],s[i+2]);}
    private static double at(V v,int axis){return axis==0?v.x():axis==1?v.y():v.z();}
}
