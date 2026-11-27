package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.network.message.receive.ShakeClientMessage;
import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CustomExplosion.class, remap = false)
public abstract class ExplosionCameraShakeMixin {
    @Shadow @Final private Entity entity;

    @Redirect(method = {
            "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/damagesource/DamageSource;FDDDFLnet/minecraft/world/level/Explosion$BlockInteraction;)V",
            "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FDDDF)V"},
            at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/network/message/receive/ShakeClientMessage$Companion;sendToNearbyPlayers(Lnet/minecraft/world/level/Level;DDDDDD)V"), require = 2)
    private void dnv$cameraShake(ShakeClientMessage.Companion sender, Level level,
            double x, double y, double z, double range, double duration, double intensity) {
        if (DnvProjectileBlastEffects.cameraShakeAllowed(entity)) {
            sender.sendToNearbyPlayers(level, x, y, z, range, duration, intensity);
        }
    }
}
