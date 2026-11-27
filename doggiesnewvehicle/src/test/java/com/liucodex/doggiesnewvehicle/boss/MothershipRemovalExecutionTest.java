package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.junit.jupiter.api.Test;

class MothershipRemovalExecutionTest {
    @Test void directWorldRemovalClearsBarAndCombatButUnloadKeepsDimensionPresence() throws Exception {
        for(var reason:new RemovalReason[]{RemovalReason.KILLED,RemovalReason.DISCARDED,RemovalReason.UNLOADED_TO_CHUNK}){
            var ship=MothershipEntityExecutionFixture.create();ship.removalReason=reason;
            ship.bossEvent.setVisible(true);ship.bossEvent.addPlayer(new MothershipEntityExecutionFixture.Actor());
            MothershipEntityExecutionFixture.removed(ship);
            assertFalse(ship.bossEvent.visible);assertTrue(ship.bossEvent.players.isEmpty());
            assertEquals(1,ship.nativeRemovals);assertEquals(reason.shouldDestroy()?1:0,ship.presenceReleases);
            assertEquals(reason.shouldDestroy()?1:0,ship.teamReleases);
        }
    }
}
