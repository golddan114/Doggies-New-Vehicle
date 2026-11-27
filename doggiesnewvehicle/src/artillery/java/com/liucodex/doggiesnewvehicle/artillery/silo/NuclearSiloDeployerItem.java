package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;

/** Existing deployer ID now places the same native box as the creative entry. Expansion belongs to the box. */
public final class NuclearSiloDeployerItem extends Item {
    private final boolean dogEgg;
    public NuclearSiloDeployerItem(Properties properties){this(properties,false);}
    public NuclearSiloDeployerItem(Properties properties,boolean dogEgg){super(properties);this.dogEgg=dogEgg;}
    @Override public InteractionResult useOn(UseOnContext context){
        ItemStack box=com.atsuishio.superbwarfare.item.container.ContainerBlockItem.createInstance(
                dogEgg?DnvArtilleryEntities.DOGEGG_SILO.get():DnvArtilleryEntities.NUCLEAR_SILO.get());
        ItemStack held=context.getItemInHand();
        ItemStack original=held.copy();
        try {
            NuclearContainerData.copyForPlacement(held,box);
            // SBW deliberately returns PASS from ContainerBlockItem.useOn; enter its native placement boundary.
            return ((BlockItem)box.getItem()).place(new BlockPlaceContext(context));
        } finally {
            // Placement owns the count. Temporary native metadata must never replace the user's data.
            NuclearContainerData.restorePlacementData(held,original);
        }
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle."+(dogEgg?"dogegg_silo_deployer":"nuclear_silo_deployer")));
        for(String part:new String[]{"footprint","shaft","excavation","refusal","dimension","cost","packing","detonation"})
            tooltip.add(Component.translatable("tooltip.doggiesnewvehicle."+(dogEgg&&part.equals("detonation")?"dogegg_silo_":"nuclear_silo_")+part));
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle."+(dogEgg?"dogegg_silo_recipe":"nuclear_silo_recipe")));
    }
}
