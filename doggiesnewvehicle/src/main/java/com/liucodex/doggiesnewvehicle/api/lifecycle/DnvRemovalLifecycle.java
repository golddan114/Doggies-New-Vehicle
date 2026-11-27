package com.liucodex.doggiesnewvehicle.api.lifecycle;

import net.minecraft.world.entity.Entity.RemovalReason;

/** Domain-owned, idempotent cleanup shared by native removal and missed-callback recovery. */
public interface DnvRemovalLifecycle {
    void releaseExternalState(RemovalReason reason);
}
