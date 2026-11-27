package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.artillery.silo.VacuumPayloadProgress;

/** Pure projection of the synchronized server age; no independent damage or animation clock. */
public final class VacuumVisualTimeline {
    public static final int ARMS=12, SEGMENTS=24;
    public static final float SHOCK_RADIUS=48, FIREBALL_RADIUS=18;
    private VacuumVisualTimeline() { }
    private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    public static float core(float age){
        if(age<0||age>=VacuumPayloadProgress.TOTAL_TICKS)return 0;
        return (1.6F+.6F*smooth(age/100))*smooth(age/12)*(1-smooth((age-112)/8));
    }
    public static float dogAlpha(float age){
        if(age<VacuumPayloadProgress.PULL_TICKS)return 0;
        return 1-smooth((age-VacuumPayloadProgress.BURST_TICK)/
                (VacuumPayloadProgress.TOTAL_TICKS-VacuumPayloadProgress.BURST_TICK));
    }
    public static float dogWidth(float age){return age<VacuumPayloadProgress.PULL_TICKS?0:
            8*(.15F+.85F*smooth((age-VacuumPayloadProgress.PULL_TICKS)/4));}
    public static float shockRadius(float age){return SHOCK_RADIUS*smooth((age-VacuumPayloadProgress.BURST_TICK)/26);}
    public static float shockAlpha(float age){return age<VacuumPayloadProgress.BURST_TICK?0:
            1-smooth((age-VacuumPayloadProgress.BURST_TICK)/30);}
    public static float blastRadius(float age){return age<VacuumPayloadProgress.BURST_TICK?0:
            2+(FIREBALL_RADIUS-2)*smooth((age-VacuumPayloadProgress.BURST_TICK)/8);}
    public static float blastAlpha(float age){return age<VacuumPayloadProgress.BURST_TICK?0:
            1-smooth((age-VacuumPayloadProgress.BURST_TICK-3)/23);}
    public static float pullAlpha(float age){return smooth(age/12)*(1-smooth((age-112)/8));}
    public static float revealGlow(float age){return age<120||age>=160?0:smooth((age-120)/4)*(.45F+.3F*smooth((age-148)/12));}

    /** Inward, curved filaments wrap around the core; fixed work per rendered effect. */
    public static Flow flow(int arm,float progress,float age){
        float t=Math.max(0,Math.min(1,progress)),inward=(float)Math.pow(1-t,1.6);
        double angle=arm*Math.PI*2/ARMS-age*.035-t*4.6;
        float radius=1.8F+26*inward;
        return new Flow((float)Math.cos(angle)*radius,
                -.7F+2.7F*t+(float)Math.sin(angle*1.5+arm)*inward*2,
                (float)Math.sin(angle)*radius,.025F+.15F*t*t);
    }
    public record Flow(float x,float y,float z,float width) { }
}
