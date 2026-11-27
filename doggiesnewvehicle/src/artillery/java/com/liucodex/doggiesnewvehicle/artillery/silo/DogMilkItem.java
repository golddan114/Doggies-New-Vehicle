package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Two material states. Vanilla inventory and crafting remainder rules own their contents. */
public final class DogMilkItem extends Item {
    private final boolean filled;
    public DogMilkItem(Properties properties,boolean filled){super(properties);this.filled=filled;}
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle."+(filled?"wild_dog_milk":"dog_milk_carton")));
    }
}
