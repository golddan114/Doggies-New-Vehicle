package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional artillery entry; the facility remains the only preparation/resource writer. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle")
public final class NuclearSiloCommands {
    private NuclearSiloCommands(){ }
    @SubscribeEvent public static void register(RegisterCommandsEvent event){register(event.getDispatcher());}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("dnv").then(Commands.literal("nuclear")
                .requires(NuclearSiloCommands::creativeSource)
                .then(Commands.literal("ready").executes(NuclearSiloCommands::ready))));
    }
    private static boolean creativeSource(CommandSourceStack source){
        return source.getEntity() instanceof ServerPlayer player&&player.isCreative()&&!player.isSpectator();
    }
    private static int ready(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player=context.getSource().getPlayerOrException();
        var world=player.serverLevel();var id=NuclearSiloStore.get(world).facility();
        if(id!=null&&world.getEntity(id) instanceof NuclearSiloEntity silo&&silo.completeCreativePreparation(player))return 1;
        context.getSource().sendFailure(Component.translatable("message.doggiesnewvehicle.nuclear.creative_rejected"));return 0;
    }
}
