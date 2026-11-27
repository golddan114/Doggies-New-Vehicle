package com.liucodex.doggiesnewvehicle.armored;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Caller proves the complete probe loaded; any nonempty fluid can support hover mode. */
final class BlizzardSurface {
    record Support(double height, boolean fluid) { }
    static Support sample(Entity tank, Vec3 from, Vec3 to, boolean allowFluid) {
        var solid = tank.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank));
        double ground = solid.getType() == HitResult.Type.MISS ? Double.NaN : solid.getLocation().y;
        if (allowFluid) {
            var surface = tank.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, tank));
            if (surface.getType() != HitResult.Type.MISS
                    && !tank.level().getFluidState(surface.getBlockPos()).isEmpty()
                    && (!Double.isFinite(ground) || surface.getLocation().y > ground))
                return new Support(surface.getLocation().y, true);
        }
        return new Support(ground, false);
    }
    private BlizzardSurface() { }
}
