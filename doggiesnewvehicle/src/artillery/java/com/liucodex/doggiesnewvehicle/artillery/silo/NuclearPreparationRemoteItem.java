package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Creative-only shortcut into the existing preparation animation; the server facility owns all state. */
public final class NuclearPreparationRemoteItem extends Item {
    public NuclearPreparationRemoteItem(Properties properties){super(properties);}

    public static InteractionResult useOnSilo(NuclearSiloEntity silo,Player player){
        if(player.level().isClientSide)return InteractionResult.SUCCESS;
        if(player instanceof ServerPlayer server&&silo.startCreativePreparation(server))return InteractionResult.CONSUME;
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.creative_start_denied"),false);
        return InteractionResult.FAIL;
    }

    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.nuclear_preparation_remote"));
    }
}
