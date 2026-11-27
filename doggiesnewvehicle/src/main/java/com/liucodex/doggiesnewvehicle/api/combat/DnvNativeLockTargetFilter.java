package com.liucodex.doggiesnewvehicle.api.combat;

import net.minecraft.world.entity.Entity;

/** Vehicle-owned target policy exposed to the optional native seek/HUD adapter. */
public interface DnvNativeLockTargetFilter {
    boolean nativeLockFilterActive();
    boolean admitsNativeLockTarget(Entity operator, Entity target);
}
