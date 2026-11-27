package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUiClientGateway;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUiClientProjection;
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

/** Independent entity-order protocol. */
public final class DnvCombatUiNetwork {
  private static final String PROTOCOL = "2";
  private static SimpleChannel channel;
  private static boolean initialized;

  private DnvCombatUiNetwork() {}

  public static synchronized void initialize() {
    if (initialized) return;
    channel = net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(DoggiesNewVehicle.MOD_ID, "combat_ui"),
        () -> PROTOCOL, DnvCombatUiNetwork::acceptsProtocol, DnvCombatUiNetwork::acceptsProtocol);
    channel.registerMessage(0, DnvCombatUi.Request.class,
        DnvCombatUiCodec::encodeRequest, DnvCombatUiCodec::decodeRequest,
        DnvCombatUiNetwork::handleRequest, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    channel.registerMessage(1, DnvCombatUi.View.class,
        DnvCombatUiCodec::encodeView, DnvCombatUiCodec::decodeView,
        DnvCombatUiNetwork::handleView, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    DnvCombatUiClientGateway.install(request -> requireChannel().sendToServer(request));
    initialized = true;
  }

  static void send(ServerPlayer player, DnvCombatUi.View view) {
    requireChannel().send(PacketDistributor.PLAYER.with(() -> player), view);
  }

  static boolean acceptsProtocol(String version) {
    return PROTOCOL.equals(version);
  }

  private static void handleRequest(
      DnvCombatUi.Request request, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() -> DnvCombatUiServices.handle(player, request));
    }
    context.setPacketHandled(true);
  }

  private static void handleView(
      DnvCombatUi.View view, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> DnvCombatUiClientProjection.accept(view)));
    context.setPacketHandled(true);
  }

  private static SimpleChannel requireChannel() {
    if (!initialized || channel == null) {
      throw new IllegalStateException("combat UI network was not initialized");
    }
    return channel;
  }
}
