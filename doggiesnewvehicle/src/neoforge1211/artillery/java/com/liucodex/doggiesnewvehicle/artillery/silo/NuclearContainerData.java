package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Native container data uses BLOCK_ENTITY_DATA, never CUSTOM_DATA, on Minecraft 1.21.1. */
public final class NuclearContainerData {
    private NuclearContainerData(){ }
    public static CompoundTag read(ItemStack stack){
        return stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag();
    }
    public static void copyForPlacement(ItemStack target,ItemStack nativeBox){
        target.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(read(nativeBox)));
    }
    public static void restorePlacementData(ItemStack target,ItemStack original){
        var saved=original.get(DataComponents.BLOCK_ENTITY_DATA);
        if(saved==null)target.remove(DataComponents.BLOCK_ENTITY_DATA);
        else target.set(DataComponents.BLOCK_ENTITY_DATA,saved);
    }
}
