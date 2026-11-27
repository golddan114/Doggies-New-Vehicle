package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle;

/** Read-only, one-shot edge detector. Joining mid-motion never replays a complete sound late. */
public final class NuclearSoundCues {
    public enum Cue {
        TURNTABLE(31,181), COVER(211,661), LIFT(781,1381);
        public final int start,end;
        Cue(int start,int end){this.start=start;this.end=end;}
        public boolean active(SiloCycle.Phase phase,float frame){return phase==SiloCycle.Phase.RELOADING&&frame>=start&&frame<end+1.5F;}
    }
    private float previous=Float.NaN;
    private int fired;
    public Cue observe(SiloCycle.Phase phase,float frame){
        if(!Float.isFinite(frame)){previous=Float.NaN;return null;}
        if(phase==SiloCycle.Phase.EMPTY||(phase==SiloCycle.Phase.RELOADING&&frame<=1)){fired=0;previous=frame;return null;}
        Cue result=null;
        if(phase==SiloCycle.Phase.RELOADING&&Float.isFinite(previous)&&frame>=previous&&frame-previous<=3.01F){
            for(Cue cue:Cue.values())if((fired&(1<<cue.ordinal()))==0&&previous<cue.start&&frame>=cue.start){
                fired|=1<<cue.ordinal();result=cue;break;
            }
        }
        previous=frame;return result;
    }
}
