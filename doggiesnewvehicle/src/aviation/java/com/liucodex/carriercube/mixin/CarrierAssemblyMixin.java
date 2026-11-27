package com.liucodex.carriercube.mixin;

import com.atsuishio.superbwarfare.init.ModBlockEntities;
import com.atsuishio.superbwarfare.recipe.vehicle.VehicleAssemblingResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleAssemblingResult.class, remap = false)
public abstract class CarrierAssemblyMixin {
    @Shadow public String entityTypeString;

    // SBW caches assembling results. Only the fresh Yue recipe crate needs an empty hangar.
    @Inject(method = "getResult", at = @At("RETURN"))
    private void carrier$emptyShipyardHangar(CallbackInfoReturnable<ItemStack> callback) {
        if (!"dnvcarriercube:carrier".equals(entityTypeString)) return;
        ItemStack stack = callback.getReturnValue();
        CompoundTag box = BlockItem.getBlockEntityData(stack);
        if (box == null || box.contains("Entity")) return;

        CompoundTag deck = new CompoundTag();
        deck.put("hangar", new CompoundTag());
        CompoundTag ship = new CompoundTag();
        ship.putString("id", entityTypeString);
        ship.put("ShanyueDeck", deck);
        box.put("Entity", ship);
        BlockItem.setBlockEntityData(stack, ModBlockEntities.CONTAINER.get(), box);
    }
}
