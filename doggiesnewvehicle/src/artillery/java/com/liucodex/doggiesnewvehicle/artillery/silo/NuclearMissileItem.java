package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Recipe and use discovery for the existing physical ammunition ID. */
public final class NuclearMissileItem extends Item {
    public NuclearMissileItem(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle."+
                (stack.is(com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryItems.DOGEGG_MISSILE.get())?"dogegg_missile":"nuclear_missile")));
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle."+
                (stack.is(com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryItems.DOGEGG_MISSILE.get())?"dogegg_missile_inventory":"nuclear_missile_inventory")));
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.nuclear_missile_cost"));
    }
}
