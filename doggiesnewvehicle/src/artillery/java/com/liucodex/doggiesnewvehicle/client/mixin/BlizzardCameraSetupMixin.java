package com.liucodex.doggiesnewvehicle.client.mixin;

import com.liucodex.doggiesnewvehicle.client.BlizzardTvCamera;
import com.liucodex.doggiesnewvehicle.client.BlizzardCameraFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Finalize after Camera.setup returns, including SBW's cancellable vehicle setup. */
@Mixin(GameRenderer.class)
public abstract class BlizzardCameraSetupMixin {
    // Keep the original invocation and its argument indexes available to other Mixins.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER))
    private void dnv$blizzardAfterSetup(CallbackInfo ci) {
        Camera camera = ((GameRenderer) (Object) this).getMainCamera();
        BlizzardTvCamera.afterSetup(camera, ((BlizzardCameraFrame) camera).dnv$setupPartialTick());
        com.liucodex.doggiesnewvehicle.client.DnvM1RemoteCrewView.afterSetup(camera, ((BlizzardCameraFrame) camera).dnv$setupPartialTick());
    }
}
