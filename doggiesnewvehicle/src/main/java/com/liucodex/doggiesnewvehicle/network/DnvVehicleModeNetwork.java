package com.liucodex.doggiesnewvehicle.network;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Seated-driver request; synchronized entity data is the authoritative acknowledgement. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DnvVehicleModeNetwork {
    public record Request(UUID vehicle) { }
    private static SimpleChannel channel;
    private DnvVehicleModeNetwork() { }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "vehicle_mode"),
                () -> "1", "1"::equals, "1"::equals);
        channel.registerMessage(0, Request.class, (s, b) -> b.writeUUID(s.vehicle()),
                b -> new Request(b.readUUID()), (s, supplier) -> {
                    var context = supplier.get(); var player = context.getSender();
                    context.enqueueWork(() -> accept(player, s.vehicle()));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void request(UUID vehicle) { if (channel != null) channel.sendToServer(new Request(vehicle)); }
    static boolean accept(net.minecraft.server.level.ServerPlayer player, UUID vehicleId) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().isClientSide) return false;
        var vehicle = player.getVehicle();
        return vehicle != null && vehicle.isAlive() && !vehicle.isRemoved() && vehicle.level() == player.level()
                && vehicle.getFirstPassenger() == player && vehicle.getUUID().equals(vehicleId)
                && vehicle instanceof com.liucodex.doggiesnewvehicle.api.DnvVehicleModeSwitch modes
                && modes.requestMode(player);
    }
}
