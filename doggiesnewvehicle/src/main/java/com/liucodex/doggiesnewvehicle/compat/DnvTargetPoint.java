package com.liucodex.doggiesnewvehicle.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Uses the current posed hull; entity dimensions describe spawn anchors, not authored geometry. */
public final class DnvTargetPoint {
    private DnvTargetPoint() { }

    public static Vec3 center(Entity target, float partialTick) {
        double remaining = 1 - Math.max(0, Math.min(1, partialTick));
        return target.getBoundingBox().getCenter().add(
                (target.xo - target.getX()) * remaining,
                (target.yo - target.getY()) * remaining,
                (target.zo - target.getZ()) * remaining);
    }
}
