package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.network.message.send.FireKeyMessage;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse SBW's existing release packet; other vehicles and native hand-held guns continue unchanged. */
@Mixin(value = FireKeyMessage.class, remap = false)
public abstract class DnvBlizzardFireReleaseMixin {
    @Shadow public abstract int getType();
    @Inject(method = "handler", at = @At("HEAD"), require = 1, remap = false)
    private void dnv$releaseColdBeam(Supplier<NetworkEvent.Context> context, CallbackInfo callback) {
        if (getType() != 1) return;
        var player = context.get().getSender();
        if (player != null && player.getVehicle() instanceof DnvNativeShotAdmission host) host.releaseNativeFire(player);
    }
}
