package com.liucodex.doggiesnewvehicle.artillery.silo;

/** Server-only sound observers; loading an already reached state never replays its cue. */
public final class NuclearReadyCue {
    private boolean observed,ready;
    private boolean announcementObserved,announced;
    public boolean observe(SiloCycle.Snapshot before,SiloCycle.Snapshot after){
        if(!observed)observe(before);
        return observe(after);
    }
    public boolean observe(SiloCycle.Snapshot state){
        boolean posed=switch(state.phase()){
            case RELOADING -> NuclearMechanicalTimeline.frame(state.phase(),state.remaining(),NuclearRules.DEFAULT)
                    >=NuclearMechanicalTimeline.RACK_ARRIVED_FRAME;
            case READY,OPENING,RETURNING -> true;
            default -> false;
        };
        return observe(posed);
    }
    public boolean observe(boolean now){
        boolean cue=observed&&!ready&&now;observed=true;ready=now;return cue;
    }
    public boolean announce(SiloCycle.Snapshot before,SiloCycle.Snapshot after,boolean resourcesReady){
        if(!announcementObserved){
            announcementObserved=true;
            announced=before.phase()==SiloCycle.Phase.READY&&resourcesReady;
        }
        if(after.phase()==SiloCycle.Phase.EMPTY||after.phase()==SiloCycle.Phase.RELOADING){
            announced=false;return false;
        }
        if(after.phase()==SiloCycle.Phase.READY&&resourcesReady&&!announced){
            announced=true;return true;
        }
        return false;
    }
}
