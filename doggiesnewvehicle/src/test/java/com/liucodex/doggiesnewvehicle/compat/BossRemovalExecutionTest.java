package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.junit.jupiter.api.Test;

/** Invoke compiled world-removal callbacks, including already-cleared combat state and unload. */
class BossRemovalExecutionTest {
    @Test void noiseAndHeavenAlwaysRemoveTheirBarsWhenWorldTrackingEnds() throws Exception {
        for(String boss:new String[]{"noise","silentheaven"}){
            String title=boss.equals("noise")?"Noise":"SilentHeaven";
            String root="com/liucodex/"+boss+"/entity/Abstract"+title+"Vehicle";
            var names=new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names,root+"Weapons",Body.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerBossEvent",Bar.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/compat/DnvBossTeamMembership",Teams.class);
            var type=NuclearRecoveryExecutionTest.extract(root+"Combat",Set.of("onRemovedFromWorld","clearBossBar","releaseExternalState"),names,Body.class);
            for(var reason:new RemovalReason[]{RemovalReason.KILLED,RemovalReason.DISCARDED,RemovalReason.UNLOADED_TO_CHUNK}){
                var body=(Body)type.getConstructor().newInstance();body.reason=reason;body.combatCleared=true;
                body.bossEvent.visible=true;body.bossEvent.players.add("nearby");body.bossEvent.players.add("disconnected");
                type.getMethod("onRemovedFromWorld").invoke(body);
                assertFalse(body.bossEvent.visible);assertTrue(body.bossEvent.players.isEmpty());assertEquals(1,body.nativeRemovals);
                assertEquals(reason,body.released);type.getMethod("onRemovedFromWorld").invoke(body);
                assertTrue(body.bossEvent.players.isEmpty());
            }
        }
    }
    public static class Body {
        public final Bar bossEvent=new Bar();public RemovalReason reason,released;public boolean combatCleared;public int nativeRemovals;
        public RemovalReason getRemovalReason(){return reason;}
        public void clearCombatStateForWreck(){if(combatCleared)return;combatCleared=true;}
        public void onRemovedFromWorld(){nativeRemovals++;}
    }
    public static class Bar {public boolean visible;public final Set<String> players=new HashSet<>();
        public void setVisible(boolean visible){this.visible=visible;}public void removeAllPlayers(){players.clear();}}
    public static class Teams {public static void releaseOnTerminalRemoval(Body body,RemovalReason reason){body.released=reason;}}
}
