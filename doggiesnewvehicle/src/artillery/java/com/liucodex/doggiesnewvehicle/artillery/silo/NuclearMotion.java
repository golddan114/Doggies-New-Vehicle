package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable authored affine samples, shared by the server rack and the read-only renderer. */
public final class NuclearMotion {
    private final Matrix4f[][] samples;
    private final Quaternionf[][] rotations;
    private final Matrix3f[][] stretches;
    private NuclearMotion(Matrix4f[][] samples) {
        this.samples = samples;
        rotations=new Quaternionf[samples.length][];
        stretches=new Matrix3f[samples.length][];
        for(int g=0;g<samples.length;g++){
            rotations[g]=new Quaternionf[samples[g].length];
            stretches[g]=new Matrix3f[samples[g].length];
            for(int f=0;f<samples[g].length;f++){
                if(f>0&&samples[g][f].equals(samples[g][f-1])){
                    samples[g][f]=samples[g][f-1];rotations[g][f]=rotations[g][f-1];stretches[g][f]=stretches[g][f-1];
                    continue;
                }
                Matrix3f linear=new Matrix3f(samples[g][f]);
                if(linear.determinant()<=1e-8F)throw new IllegalArgumentException("Singular or reflected motion");
                Matrix3f rotation=new Matrix3f(linear);
                // Polar decomposition A=R*S retains nonuniform scale AND shear. Resolve once on load.
                for(int step=0;step<32;step++){
                    Matrix3f next=new Matrix3f(rotation).invert().transpose().add(rotation).scale(.5F);
                    boolean converged=next.equals(rotation,1e-6F);
                    rotation=next;if(converged)break;
                }
                rotations[g][f]=rotation.getNormalizedRotation(new Quaternionf()).normalize();
                stretches[g][f]=new Matrix3f(rotation).transpose().mul(linear);
            }
        }
    }
    public int groupCount() { return samples.length; }
    public static NuclearMotion parse(byte[] bytes) {
        if (bytes.length > 18_900_000) throw new IllegalArgumentException("Motion budget exceeded");
        var b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != 0x314d534e || b.getInt() != 1) throw new IllegalArgumentException("Invalid NSM1");
        int groups=b.getInt(), frames=b.getInt();
        if (groups < 1 || groups > 96 || frames < 2 || frames > 3072
                || b.remaining() != frames * 4 + groups * frames * 64) throw new IllegalArgumentException("Invalid motion shape");
        for (int f=0; f<frames; f++) if (b.getFloat() != f+1) throw new IllegalArgumentException("Non-contiguous motion");
        Matrix4f[][] data=new Matrix4f[groups][frames];
        for(int g=0;g<groups;g++) for(int f=0;f<frames;f++) {
            float[] matrix=new float[16];
            for(int i=0;i<16;i++){ matrix[i]=b.getFloat(); if(!Float.isFinite(matrix[i])) throw new IllegalArgumentException("Invalid matrix"); }
            data[g][f]=new Matrix4f().set(matrix);
        }
        return new NuclearMotion(data);
    }
    public Matrix4f sample(int group, float frame) {
        if(group<0||group>=samples.length||!Float.isFinite(frame)) throw new IllegalArgumentException("Invalid motion sample");
        float cursor=Math.max(0,Math.min(samples[group].length-1,frame-1));
        int a=(int)cursor,b=Math.min(a+1,samples[group].length-1);float fraction=cursor-a;
        Matrix4f left=samples[group][a];if(fraction==0)return new Matrix4f(left);
        Matrix4f right=samples[group][b];
        Vector3f position=left.getTranslation(new Vector3f()).lerp(right.getTranslation(new Vector3f()),fraction);
        Quaternionf rotation=new Quaternionf(rotations[group][a]).slerp(rotations[group][b],fraction);
        Matrix3f stretch=new Matrix3f(stretches[group][a]).lerp(stretches[group][b],fraction);
        Matrix3f linear=new Matrix3f().rotation(rotation).mul(stretch);
        return new Matrix4f().set(linear).setTranslation(position);
    }
    /** Scratch belongs to one caller, never to the immutable motion or a shared global. */
    public Sampler sampler() { return new Sampler(); }
    public final class Sampler {
        private final Vector3f position = new Vector3f(), nextPosition = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Matrix3f stretch = new Matrix3f(), linear = new Matrix3f();
        public Matrix4f sample(int group, float frame, Matrix4f destination) {
        if(group<0||group>=samples.length||!Float.isFinite(frame)) throw new IllegalArgumentException("Invalid motion sample");
        float cursor=Math.max(0,Math.min(samples[group].length-1,frame-1));
        int a=(int)cursor,b=Math.min(a+1,samples[group].length-1);float fraction=cursor-a;
        Matrix4f left=samples[group][a];if(fraction==0)return destination.set(left);
        Matrix4f right=samples[group][b];
        left.getTranslation(position).lerp(right.getTranslation(nextPosition),fraction);
        rotation.set(rotations[group][a]).slerp(rotations[group][b],fraction);
        stretch.set(stretches[group][a]).lerp(stretches[group][b],fraction);
        linear.rotation(rotation).mul(stretch);
        return destination.set(linear).setTranslation(position);
        }
    }
}
