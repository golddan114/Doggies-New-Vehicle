package com.liucodex.doggiesnewvehicle.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvHoverPlatformStationsTest {
    @Test
    void aiStaysIndependentAndPlayerStationNeverFallsBackToAi() {
        DnvHoverPlatformStations stations = new DnvHoverPlatformStations();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        assertTrue(stations.aiMayFire(100L));
        assertFalse(stations.playerMayFire(owner, owner, 100L));
        assertEquals(DnvHoverPlatformStations.RejectReason.NOT_OWNER,
                stations.claimPlayer(stranger, owner, 100L, 0L).reason());

        var claim = stations.claimPlayer(owner, owner, 100L, 0L);
        assertTrue(claim.accepted());
        assertTrue(stations.playerMayFire(owner, owner, 100L));
        assertFalse(stations.playerMayFire(stranger, owner, 100L));
        assertTrue(stations.aiMayFire(100L));

        assertFalse(stations.playerMayFire(owner, owner,
                100L + DnvHoverPlatformStations.PLAYER_LEASE_TICKS));
        assertTrue(stations.aiMayFire(100L + DnvHoverPlatformStations.PLAYER_LEASE_TICKS));
        var reclaim = stations.claimPlayer(owner, owner, 400L, claim.revision());
        assertTrue(reclaim.accepted());
        stations.clearPlayer(owner);
        assertFalse(stations.playerMayFire(owner, owner, 401L));
        assertTrue(stations.aiMayFire(401L));
    }

    @Test
    void unloadClearsPlayerLeaseWithoutChangingAiStation() {
        DnvHoverPlatformStations stations = new DnvHoverPlatformStations();
        UUID owner = UUID.randomUUID();
        assertTrue(stations.claimPlayer(owner, owner, 1L, 0L).accepted());
        stations.clearAll();
        assertFalse(stations.playerMayFire(owner, owner, 2L));
        assertTrue(stations.aiMayFire(2L));
    }
}
