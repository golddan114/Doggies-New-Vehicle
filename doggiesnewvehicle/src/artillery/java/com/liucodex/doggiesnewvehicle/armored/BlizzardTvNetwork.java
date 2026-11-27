package com.liucodex.doggiesnewvehicle.armored;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Only the current seated owner with the server-issued session can steer; common code never loads client classes. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BlizzardTvNetwork {
    public record State(UUID vehicle, UUID missile, UUID session, int entityId, boolean active) { }
    public record Control(UUID missile, UUID session, float yaw, float pitch, boolean exit) { }
    private static SimpleChannel channel;
    private static Consumer<State> client = state -> { };
    private BlizzardTvNetwork() { }
    public static void installClient(Consumer<State> receiver) { client = receiver; }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "blizzard_tv"),
                () -> "1", "1"::equals, "1"::equals);
        channel.registerMessage(0, State.class, BlizzardTvNetwork::encodeState, BlizzardTvNetwork::decodeState,
                BlizzardTvNetwork::receiveState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(1, Control.class, BlizzardTvNetwork::encodeControl, BlizzardTvNetwork::decodeControl,
                BlizzardTvNetwork::receiveControl, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void sendControl(Control message) { if (channel != null) channel.sendToServer(message); }
    static void sendState(ServerPlayer player, State state) { if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), state); }
    static void encodeState(State s, FriendlyByteBuf b) {
        b.writeUUID(s.vehicle()); b.writeUUID(s.missile()); b.writeUUID(s.session()); b.writeVarInt(s.entityId()); b.writeBoolean(s.active());
    }
    static State decodeState(FriendlyByteBuf b) { return new State(b.readUUID(), b.readUUID(), b.readUUID(), b.readVarInt(), b.readBoolean()); }
    static void encodeControl(Control s, FriendlyByteBuf b) {
        b.writeUUID(s.missile()); b.writeUUID(s.session()); b.writeFloat(s.yaw()); b.writeFloat(s.pitch()); b.writeBoolean(s.exit());
    }
    static Control decodeControl(FriendlyByteBuf b) { return new Control(b.readUUID(), b.readUUID(), b.readFloat(), b.readFloat(), b.readBoolean()); }
    private static void receiveState(State state, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> client.accept(state)); context.setPacketHandled(true);
    }
    private static void receiveControl(Control message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null || !(player.getVehicle() instanceof DnvBlizzardEntity tank) || tank.getFirstPassenger() != player) return;
            var missile = player.serverLevel().getEntity(message.missile());
            if (missile instanceof DnvBlizzardMissile guided) guided.steer(player, message.session(), message.yaw(), message.pitch(), message.exit());
        });
        context.setPacketHandled(true);
    }
}
