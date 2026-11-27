package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.network.message.send.ArtilleryIndicatorFireMessage;
import com.liucodex.doggiesnewvehicle.compat.DnvM1IndicatorOrders;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SBW's fire packet only casts ArtilleryEntity; process M1 without changing its other targets. */
@Mixin(value = ArtilleryIndicatorFireMessage.class, remap = false)
public abstract class ArtilleryIndicatorFireMessageMixin {
  @Inject(method = "handler", at = @At("HEAD"), cancellable = true, remap = false)
  private void dnv$fireM1(Supplier<NetworkEvent.Context> context, CallbackInfo callback) {
    var player = context.get().getSender();
    if (player == null) return;
    var stack = DnvM1IndicatorOrders.heldIndicator(player);
    if (!DnvM1IndicatorOrders.isM1Indicator(stack)) return;
    DnvM1IndicatorOrders.fire(player, stack);
    callback.cancel();
  }
}
