package com.liucodex.carriercube;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** NeoForge ticket for the single chunk occupied by the carrier controller. */
public final class CarrierTickets {
    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(CubePrototype.ID, "carrier"),
            (level, helper) -> java.util.List.copyOf(helper.getEntityTickets().keySet())
                    .forEach(helper::removeAllTickets));

    private CarrierTickets() {}

    public static void register(IEventBus bus) {
        bus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
    }

    static boolean forceChunk(ServerLevel level, UUID owner, int x, int z, boolean add, boolean ticking) {
        return CONTROLLER.forceChunk(level, owner, x, z, add, ticking);
    }
}
