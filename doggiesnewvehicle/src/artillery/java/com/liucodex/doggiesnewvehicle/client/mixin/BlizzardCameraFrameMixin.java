package com.liucodex.doggiesnewvehicle.client.mixin;

import com.liucodex.doggiesnewvehicle.client.BlizzardCameraFrame;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture after caller argument edits, before SBW's priority-1000 cancellable setup. */
@Mixin(value = Camera.class, priority = 2000)
public abstract class BlizzardCameraFrameMixin implements BlizzardCameraFrame {
    @Unique private float dnv$setupPartialTick;

    @Inject(method = "setup", at = @At("HEAD"))
    private void dnv$captureSetupPartial(BlockGetter level, Entity entity,
            boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        dnv$setupPartialTick = partial;
    }

    @Override public float dnv$setupPartialTick() {
        return dnv$setupPartialTick;
    }
}
