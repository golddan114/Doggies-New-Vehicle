package com.liucodex.doggiesnewvehicle.client;

/** Render-only power/damage projection shared by the six new authors. */
public final class DnvTurretLampBrightness {
    private DnvTurretLampBrightness(){}
    public static float value(boolean powered,boolean damaged,float ticks){
        if(!powered)return 0;
        if(!damaged)return 1;
        double phase=ticks*Math.PI*2/16;
        return .12F+.88F*(float)((Math.sin(phase)+1)*.5);
    }
}
