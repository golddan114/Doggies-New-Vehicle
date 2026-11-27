package com.liucodex.carriercube.mixin;

import com.atsuishio.superbwarfare.client.ClientRenderHandler;
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity;
import com.liucodex.carriercube.CubePrototype;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ClientRenderHandler.class,remap=false)
public abstract class CarrierTracerMixin {
    @Inject(method="transformVirtualRenderPosition",at=@At("HEAD"),cancellable=true)
    private static void carrier$useMountedMuzzle(PoseStack pose, Projectile shot, float partial, CallbackInfo callback) {
        if(shot instanceof SmallCannonShellEntity && shot.getOwner() instanceof Player pilot && CubePrototype.fliesFighter(pilot))
            callback.cancel();
    }
}
