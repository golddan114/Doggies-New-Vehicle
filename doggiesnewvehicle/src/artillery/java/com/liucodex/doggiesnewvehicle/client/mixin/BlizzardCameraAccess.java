package com.liucodex.doggiesnewvehicle.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla client camera position boundary, independent of SBW adapters. */
@Mixin(Camera.class)
public interface BlizzardCameraAccess {
    @Invoker("setPosition") void dnv$blizzardPosition(double x, double y, double z);
    @Invoker("setRotation") void dnv$blizzardRotation(float yaw, float pitch);
}
