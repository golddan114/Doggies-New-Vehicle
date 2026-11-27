package com.liucodex.doggiesnewvehicle.client;

/** Ignition is visible immediately; flame strength follows burn age rather than delayed velocity packets. */
final class NuclearExhaust {
    private NuclearExhaust(){ }
    static float strength(float age){return Math.max(0,Math.min(1,age/40F));}
    static float length(boolean central,float age){return central?4F+8F*strength(age):2F+4F*strength(age);}
    static float radius(boolean central,float age){return (central?.42F:.27F)*(.85F+.15F*strength(age));}
    static void render(com.liucodex.doggiesnewvehicle.fx.DnvJetMesh.Sink sink,boolean central,float age,int detail){
        double thrust=.2+.8*strength(age),radius=radius(central,age);
        double scale=length(central,age)/(radius*(1.3+thrust*6));
        // Shared jet profile and alpha falloff, warm palette, tail axis along local -Y.
        com.liucodex.doggiesnewvehicle.fx.DnvJetMesh.exhaust(
                (x,y,z,r,g,b,a)->sink.vertex(x,-z*scale,y,b,g,r,a),thrust,radius,age,detail);
    }
}
