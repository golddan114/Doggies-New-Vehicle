package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Protection callbacks run first; consuming this interaction also prevents the owned wolf's sit toggle. */
@Mod.EventBusSubscriber(modid=DoggiesNewVehicle.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class DogMilkCollection {
    private DogMilkCollection(){ }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.EntityInteract event){
        if(event.isCanceled()||!(event.getTarget() instanceof Wolf wolf))return;
        var result=collect(event.getEntity(),event.getHand(),wolf);
        if(result.consumesAction()){event.setCanceled(true);event.setCancellationResult(result);}
    }
    static InteractionResult collect(Player player,InteractionHand hand,Wolf wolf){
        var empty=player.getItemInHand(hand);
        if(!player.isAlive()||player.isSpectator()||!wolf.isAlive()||player.level()!=wolf.level()
                ||empty.isEmpty()||!empty.is(DnvArtilleryItems.DOG_MILK_CARTON.get()))return InteractionResult.PASS;
        if(!player.level().isClientSide){
            player.setItemInHand(hand,ItemUtils.createFilledResult(empty,player,new ItemStack(DnvArtilleryItems.WILD_DOG_MILK.get())));
            wolf.playSound(SoundEvents.BOTTLE_FILL,1F,1F);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
