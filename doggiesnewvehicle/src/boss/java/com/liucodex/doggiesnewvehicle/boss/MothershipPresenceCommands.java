package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** An explicit, exact-UUID operator action is required for unverifiable legacy occupancy. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class MothershipPresenceCommands {
    private MothershipPresenceCommands() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dnv")
                .then(Commands.literal("mothership").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("presence").executes(context -> {
                            var source = context.getSource();
                            var state = MothershipPresence.get(source.getLevel());
                            state.reconcile(source.getLevel());
                            source.sendSuccess(() -> Component.translatable("message.doggiesnewvehicle.mothership_presence.status",
                                    state.owner() == null ? "—" : state.owner().toString(),
                                    state.ownerChunk() == null ? Component.translatable("message.doggiesnewvehicle.mothership_presence.unknown")
                                            : new net.minecraft.world.level.ChunkPos(state.ownerChunk()).toString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("forget")
                                .then(Commands.argument("owner", UuidArgument.uuid())
                                        .then(Commands.literal("confirm_missing").executes(context -> {
                                            var source = context.getSource();
                                            boolean cleared = MothershipPresence.get(source.getLevel()).forgetConfirmedAbsent(
                                                    source.getLevel(), UuidArgument.getUuid(context, "owner"));
                                            if (cleared) source.sendSuccess(() -> Component.translatable(
                                                    "message.doggiesnewvehicle.mothership_presence.cleared"), true);
                                            else source.sendFailure(Component.translatable("message.doggiesnewvehicle.mothership_presence.refused"));
                                            return cleared ? 1 : 0;
                                        }))))));
    }
}
