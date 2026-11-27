package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.item.misc.ArtilleryIndicatorItem;
import com.liucodex.doggiesnewvehicle.compat.DnvM1IndicatorOrders;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNV player artillery takes the adjacent native path; SBW guns keep their original method. */
@Mixin(value = ArtilleryIndicatorItem.class, remap = false)
public abstract class ArtilleryIndicatorItemMixin {
  @Inject(method = "onInteractVehicle", at = @At("HEAD"), cancellable = true, remap = false)
  private void dnv$bindM1(VehicleEntity vehicle, ItemStack stack, Player player,
      InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
    if (vehicle instanceof DnvPlayerArtilleryTarget target) callback.setReturnValue(
        DnvM1IndicatorOrders.bind((ArtilleryIndicatorItem) (Object) this,
            vehicle, target, stack, player, hand));
  }

  @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true, remap = false)
  private void dnv$targetM1(ItemStack stack, Player player, CallbackInfo callback) {
    if (!DnvM1IndicatorOrders.isM1Indicator(stack)) return;
    DnvM1IndicatorOrders.setTarget(stack, player);
    callback.cancel();
  }
}
