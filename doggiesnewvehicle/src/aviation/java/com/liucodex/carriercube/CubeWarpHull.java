package com.liucodex.carriercube;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class CubeWarpHull {
    // Both supported SBW releases supply the same opaque 1x1 white texture.
    static final RenderType GLOW = RenderType.entityTranslucentEmissive(
            new ResourceLocation("superbwarfare", "textures/entity/white.png"));

    private CubeWarpHull() {}

    static int whiteness(CubeVehicle ship, float partialTick) {
        double progress = Math.max(ship.chargeFraction(partialTick), 1 - ship.arrivalAge(partialTick) / 40);
        return (int) Math.round(32767 * .94 * Mth.clamp(progress, 0, 1));
    }
}
