package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.boss.MothershipSounds;
import com.liucodex.doggiesnewvehicle.client.DnvVehicleLoopSoundManager;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;

/** Client-only selected machinery idle and throttle-controlled propulsion. */
public final class MothershipSoundManager {
    private static DnvVehicleLoopSoundManager<DnvMothershipEntity> loops;
    private MothershipSoundManager() { }
    public static void tick() {
        if(loops==null)loops=new DnvVehicleLoopSoundManager<>(DnvMothershipEntity.class,13,
                MothershipSounds.IDLE.get(),MothershipSounds.PROPULSION.get(),null,
                ship->ship.isAlive()&&!ship.isRemoved()&&!ship.isWreck()&&ship.getHealth()>0,
                DnvMothershipEntity::renderThrottle,96,112,true);
        loops.tick();
    }
}
