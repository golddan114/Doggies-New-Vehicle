package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ApocalypseDriverlessGunTest {
    @Test void nativeAutomaticReloadStartsForDriverlessVehicleWhenMagazineEmpties() throws Exception {
        var f=new ExactDriverlessGunFixture();f.data.virtualAmmo.set(16);
        f.handler.autoReload(f.tank,f.data,true);assertEquals(1,f.handler.reloadRequests);
        f.data.reloadAmmo(f.tank,false);assertEquals(4,f.data.ammo.get());assertEquals(12,f.data.virtualAmmo.get());
        f.handler.autoReload(f.tank,f.data,true);assertEquals(1,f.handler.reloadRequests);
        f.data.ammo.set(0);f.handler.autoReload(f.tank,f.data,false);assertEquals(1,f.handler.reloadRequests);
        f.handler.autoReload(f.tank,f.data,true);assertEquals(2,f.handler.reloadRequests);
    }
    @Test void exactNativeVehicleGunAcceptsNonLivingShooterAndConsumesOnlyAcceptedRound() throws Exception {
        var f = new ExactDriverlessGunFixture();
        assertFalse(ExactDriverlessGunFixture.Living.class.isInstance(f.tank));
        f.data.ammo.set(4);
        assertFalse(f.data.canShoot(new ExactDriverlessGunFixture.Living()));
        f.data.shoot(f.parameters);
        assertEquals(3, f.data.ammo.get()); assertEquals(1, f.data.fireIndex.get());
        assertEquals(1, f.data.saves); assertEquals(2, f.data.heat.get());
        f.data.item.admit = false;
        f.data.shoot(f.parameters);
        assertEquals(3, f.data.ammo.get()); assertEquals(1, f.data.fireIndex.get());
        assertEquals(1, f.data.saves); assertEquals(2, f.data.heat.get());
        f.data.reload.state = ExactDriverlessGunFixture.ReloadState.EMPTY_RELOADING;
        f.data.shoot(f.parameters);
        assertEquals(2, f.data.item.bullets);
    }

    @Test void exactReloadTakesOnlyFiniteReserveAndDoesNotCreateRoundsWhenEmpty() throws Exception {
        var f = new ExactDriverlessGunFixture();
        f.tank.reserve = 3;
        f.data.reloadAmmo(f.tank, false);
        assertEquals(3, f.data.ammo.get()); assertEquals(0, f.tank.reserve); assertEquals(3, f.data.withdrawals);
        f.data.ammo.set(0);
        f.data.reloadAmmo(f.tank, false);
        assertEquals(0, f.data.ammo.get()); assertFalse(f.data.canShoot(f.tank));
        f.tank.reserve = 7;
        f.data.reloadAmmo(f.tank, false);
        assertEquals(4, f.data.ammo.get()); assertEquals(3, f.tank.reserve);
    }
}
