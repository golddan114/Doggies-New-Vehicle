package com.liucodex.carriercube;

import com.liucodex.doggiesnewvehicle.fx.DnvJetMesh;
import net.minecraft.util.Mth;

/** SES arrivalWarp's collapsing blue/amber streak and faceted shock front, scaled for the long deck. */
final class CubeWarpExit {
    static void draw(DnvJetMesh.Sink sink,double age) {
        if(age<0 || age>=40)return;
        double flash=1-smooth(age/14);
        if(flash>0) {
            double length=(2+43*flash)*1.8;
            streak(sink,length,2.4*flash+.15,.2,.72F,.90F,1F,(float)(.7*flash));
            streak(sink,length*.8,4.5*flash+.2,.7,1F,.61F,.2F,(float)(.25*flash));
        }
        double life=1-smooth(age/32);
        double radius=5+35*smooth(age/20),outer=radius+1.4+life*2;
        for(int i=0;i<8;i++) {
            corner(sink,i,radius,0,.75F,.9F,1F,(float)(.65*life));
            corner(sink,i+1,radius,0,.75F,.9F,1F,(float)(.65*life));
            corner(sink,i+1,outer,0,1F,.68F,.25F,0);
            corner(sink,i,outer,0,1F,.68F,.25F,0);
        }
    }

    private static void streak(DnvJetMesh.Sink sink,double length,double r0,double r1,float r,float g,float b,float alpha) {
        for(int i=0;i<8;i++){
            corner(sink,i,r0,-length,r,g,b,alpha);
            corner(sink,i+1,r0,-length,r,g,b,alpha);
            corner(sink,i+1,r1,length,r,g,b,0);
            corner(sink,i,r1,length,r,g,b,0);
        }
    }

    private static void corner(DnvJetMesh.Sink sink,int index,double radius,double z,float r,float g,float b,float alpha) {
        double theta=index*Math.PI/4;
        sink.vertex(radius*Math.cos(theta),16+radius*Math.sin(theta),z,r,g,b,alpha);
    }
    private static double smooth(double x){double t=Mth.clamp(x,0,1);return t*t*(3-2*t);}
    private CubeWarpExit(){}
}
