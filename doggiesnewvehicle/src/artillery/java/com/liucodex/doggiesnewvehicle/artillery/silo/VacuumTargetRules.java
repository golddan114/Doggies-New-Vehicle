package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.compat.DnvDefaultTeams;

/** Different arbitrary scoreboard teams alone never establish hostility. */
public final class VacuumTargetRules {
    private VacuumTargetRules() { }
    public static boolean hostile(String sourceTeam,String targetTeam,boolean friendly,boolean hostileMob) {
        if(friendly)return false;
        if(sourceTeam!=null&&sourceTeam.equals(targetTeam))return false;
        return hostileMob||faction(sourceTeam)&&faction(targetTeam)&&!sourceTeam.equals(targetTeam);
    }
    public static boolean pullable(String sourceTeam,String targetTeam,boolean friendly,boolean hostileMob,
                                   boolean emptyVehicle) {
        return hostile(sourceTeam,targetTeam,friendly,hostileMob)||!friendly&&targetTeam==null&&emptyVehicle;
    }
    private static boolean faction(String team) {
        return DnvDefaultTeams.PLAYERS.equals(team)||DnvDefaultTeams.HELLDIVERS.equals(team)||DnvDefaultTeams.GUARDIANS.equals(team);
    }
}
