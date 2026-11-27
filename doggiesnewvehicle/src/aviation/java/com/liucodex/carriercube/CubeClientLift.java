package com.liucodex.carriercube;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = CubePrototype.ID, value = Dist.CLIENT)
public final class CubeClientLift {
    private static final Set<CubeVehicle> carriers = new HashSet<>();

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof CubeVehicle ship && ship.carrier()) carriers.add(ship);
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof CubeVehicle ship && ship.carrier()) carriers.remove(ship);
    }

    @SubscribeEvent
    public static void afterEntityMovement(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.isPaused()) return;
        // Vanilla and SBW have now finished entity tracking, collision support and passenger placement.
        for (var ship : carriers) {
            if (ship.level() == client.level && !ship.isRemoved()) ship.placeClientLiftRider();
        }
    }
}
