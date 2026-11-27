package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleClientProjection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Vehicle directory and administrator teleport, independent of old mission packets. */
public final class DnvManagedVehicleNetwork {
  private static final String PROTOCOL = "1";
  private static SimpleChannel channel;
  private static boolean initialized;

  private DnvManagedVehicleNetwork() {}

  public static synchronized void initialize() {
    if (initialized) return;
    channel = net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(DoggiesNewVehicle.MOD_ID, "managed_vehicle"),
        () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    channel.registerMessage(0, OpenMessage.class,
        (message, buffer) -> {}, buffer -> new OpenMessage(),
        DnvManagedVehicleNetwork::handleOpen, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    channel.registerMessage(1, DnvManagedVehicleDirectory.Query.class,
        DnvManagedVehicleNetworkCodec::encodeQuery, DnvManagedVehicleNetworkCodec::decodeQuery,
        DnvManagedVehicleNetwork::handleQuery, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    channel.registerMessage(2, DnvManagedVehicleDirectory.View.class,
        DnvManagedVehicleNetworkCodec::encode, DnvManagedVehicleNetworkCodec::decode,
        DnvManagedVehicleNetwork::handleView, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    channel.registerMessage(3, TeleportRequest.class,
        (message, buffer) -> DnvManagedVehicleNetworkCodec.encodeQuery(message.query(), buffer),
        buffer -> new TeleportRequest(DnvManagedVehicleNetworkCodec.decodeQuery(buffer)),
        DnvManagedVehicleNetwork::handleTeleport, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    initialized = true;
  }

  public static void open(ServerPlayer player) {
    requireChannel().send(PacketDistributor.PLAYER.with(() -> player), new OpenMessage());
  }

  public static void requestDirectory(DnvManagedVehicleDirectory.Query query) {
    requireChannel().sendToServer(query);
  }

  public static void teleport(DnvManagedVehicleDirectory.Query query) {
    requireChannel().sendToServer(new TeleportRequest(query));
  }

  static void sendDirectory(ServerPlayer player, DnvManagedVehicleDirectory.View view) {
    requireChannel().send(PacketDistributor.PLAYER.with(() -> player), view);
  }

  private static SimpleChannel requireChannel() {
    if (!initialized || channel == null) {
      throw new IllegalStateException("managed vehicle network was not initialized");
    }
    return channel;
  }

  private static void handleOpen(
      OpenMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> DnvManagedVehicleClientProjection.open()));
    context.setPacketHandled(true);
  }

  private static void handleQuery(
      DnvManagedVehicleDirectory.Query query, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() -> DnvManagedVehicleServices.sendDirectory(player, query, "NONE"));
    }
    context.setPacketHandled(true);
  }

  private static void handleView(
      DnvManagedVehicleDirectory.View view, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> DnvManagedVehicleClientProjection.directory(view)));
    context.setPacketHandled(true);
  }

  private static void handleTeleport(
      TeleportRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() -> DnvManagedVehicleServices.teleport(player, message.query()));
    }
    context.setPacketHandled(true);
  }

  private record OpenMessage() {}

  private record TeleportRequest(DnvManagedVehicleDirectory.Query query) {
    private TeleportRequest {
      Objects.requireNonNull(query, "query");
    }
  }
}
