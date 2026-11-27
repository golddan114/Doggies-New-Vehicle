package com.liucodex.doggiesnewvehicle.neoforge;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DnvNeoTargets {
    @FunctionalInterface public interface Target<T> {
        Consumer<CustomPacketPayload> with(Supplier<T> target);
    }
    public static final Target<ServerPlayer> PLAYER = target ->
            payload -> PacketDistributor.sendToPlayer(target.get(), payload);
    public static final Target<Entity> TRACKING_ENTITY = target ->
            payload -> PacketDistributor.sendToPlayersTrackingEntity(target.get(), payload);
    private DnvNeoTargets() {}
}
