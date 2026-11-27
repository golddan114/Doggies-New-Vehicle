package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;

/** Bounded FABRIK, following the original project's early solver. No mesh stretching. */
public final class HexbotIk {
    private HexbotIk() { }
    public static Point[] solve(Point root,Point target,double[] lengths,Point outward) {
        if(!root.finite()||!target.finite()||lengths.length!=4)throw new IllegalArgumentException("Invalid leg");
        double total=0; for(double n:lengths){if(!(n>0)||!Double.isFinite(n))throw new IllegalArgumentException("Length");total+=n;}
        Point[] nodes=new Point[5];nodes[0]=root;
        Point direction=outward.normalized();
        // An arched seed removes the straight-chain singularity and keeps the knee outward.
        for(int i=0;i<4;i++)nodes[i+1]=nodes[i].add(direction.scale(.65).add(new Point(0,i<2?.7:-1,0)).normalized().scale(lengths[i]));
        if(root.distance(target)>=total-.00001) {
            Point d=target.subtract(root).normalized();
            for(int i=0;i<4;i++)nodes[i+1]=nodes[i].add(d.scale(lengths[i]));
            return nodes;
        }
        for(int iteration=0;iteration<32;iteration++) {
            nodes[4]=target;
            for(int i=3;i>=0;i--)nodes[i]=nodes[i+1].add(nodes[i].subtract(nodes[i+1]).normalized().scale(lengths[i]));
            nodes[0]=root;
            for(int i=0;i<4;i++)nodes[i+1]=nodes[i].add(nodes[i+1].subtract(nodes[i]).normalized().scale(lengths[i]));
            if(nodes[4].distance(target)<.001)break;
        }
        return nodes;
    }
}
