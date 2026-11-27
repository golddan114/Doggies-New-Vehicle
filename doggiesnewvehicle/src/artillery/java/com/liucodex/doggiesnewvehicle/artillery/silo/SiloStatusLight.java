package com.liucodex.doggiesnewvehicle.artillery.silo;

/** Stateless display from the server mechanical fact and native FE, including a fully debited launch. */
public final class SiloStatusLight {
    private SiloStatusLight() { }
    public static int color(SiloCycle.Phase phase,int energy,boolean healthy,double tick){
        if(!healthy||phase==null)return 0;
        if(phase==SiloCycle.Phase.OPENING||phase==SiloCycle.Phase.RETURNING||phase==SiloCycle.Phase.DEPARTING||phase==SiloCycle.Phase.CLOSING)
            return breathe(0xFF2818,tick);
        if(energy<=0)return 0;
        if(phase==SiloCycle.Phase.RELOADING)return breathe(0xFF8A18,tick);
        if(phase==SiloCycle.Phase.READY)return 0xFF8A18;
        return 0xFFDC38;
    }
    private static int breathe(int rgb,double tick){
        if(!Double.isFinite(tick))return 0;
        // Three seconds at 20 TPS, including partial ticks. Both ends meet at
        // zero with zero slope, so crossing a cycle boundary never flashes.
        double phase=Math.abs(tick%60);
        if(phase>30)phase=60-phase;
        double gain=.5-.5*Math.cos(phase/60*2*Math.PI);
        int r=(int)Math.round(((rgb>>16)&255)*gain);
        int g=(int)Math.round(((rgb>>8)&255)*gain);
        int b=(int)Math.round((rgb&255)*gain);
        return (r<<16)|(g<<8)|b;
    }
}
