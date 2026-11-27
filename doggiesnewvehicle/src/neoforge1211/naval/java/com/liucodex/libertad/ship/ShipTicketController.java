package com.liucodex.libertad.ship;

import com.liucodex.libertad.Libertad;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** NeoForge ticket transport. The shared ShipTickets owns the active footprint. */
public final class ShipTicketController {
    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(Libertad.ID, "ship"),
            (level, helper) -> java.util.List.copyOf(helper.getEntityTickets().keySet())
                    .forEach(helper::removeAllTickets));

    private ShipTicketController() {}

    public static void register(IEventBus bus) {
        bus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
    }

    static boolean forceChunk(ServerLevel level, UUID owner, int x, int z, boolean add, boolean ticking) {
        return CONTROLLER.forceChunk(level, owner, x, z, add, ticking);
    }
}
