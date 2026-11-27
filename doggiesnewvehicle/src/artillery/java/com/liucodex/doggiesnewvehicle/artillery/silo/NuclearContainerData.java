package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Forge's native box metadata edge; the NeoForge component implementation is explicitly selected. */
public final class NuclearContainerData {
    private NuclearContainerData(){ }
    public static CompoundTag read(ItemStack stack){
        CompoundTag tag=BlockItem.getBlockEntityData(stack);return tag==null?new CompoundTag():tag.copy();
    }
    public static void copyForPlacement(ItemStack target,ItemStack nativeBox){
        target.setTag(nativeBox.getTag()==null?null:nativeBox.getTag().copy());
    }
    public static void restorePlacementData(ItemStack target,ItemStack original){
        target.setTag(original.getTag()==null?null:original.getTag().copy());
    }
}
