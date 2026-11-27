package com.liucodex.doggiesnewvehicle.api.combat;

import java.util.List;
import net.minecraft.world.phys.AABB;

/** Read-only physical volumes; the entity remains the sole pose/state writer. */
public interface DnvCompoundCollider {
    List<AABB> dnv$collisionBoxes();
}
