package com.liucodex.doggiesnewvehicle.client.mesh;

import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMotion;
import java.util.Arrays;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Renderer-owned, at most one pose per authored group. Reload identity invalidates it; no entity/world references. */
public final class NuclearPoseCache {
    private NuclearMotion motion;
    private NuclearMotion.Sampler sampler;
    private float[] frames;
    private Matrix4f[] positions;
    private Matrix3f[] normals;
    public void prepare(NuclearMotion next){
        if(motion==next)return;
        if(next==null){motion=null;sampler=null;frames=null;positions=null;normals=null;return;}
        motion=next;sampler=next.sampler();frames=new float[next.groupCount()];Arrays.fill(frames,Float.NaN);
        positions=new Matrix4f[frames.length];normals=new Matrix3f[frames.length];
    }
    public Matrix4f position(int group,float frame){
        if(frames[group]!=frame){
            if(positions[group]==null){positions[group]=new Matrix4f();normals[group]=new Matrix3f();}
            sampler.sample(group,frame,positions[group]);
            normals[group].set(positions[group]).invert().transpose();frames[group]=frame;
        }
        return positions[group];
    }
    public Matrix3f normal(int group){return normals[group];}
}
