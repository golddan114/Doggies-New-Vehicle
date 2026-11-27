package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewClientProjection;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewClientProjection.CommitResponse;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewConfigSessions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Bounded request/response protocol for editing a held vehicle crew core. */
public final class DnvVehicleCrewConfigNetwork {
  private static final String PROTOCOL = "2";
  private static final int MAX_ID_LENGTH = 256;
  private static final SimpleChannel CHANNEL =
      net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(
              DoggiesNewVehicle.MOD_ID, "vehicle_crew_config"),
          () -> PROTOCOL,
          PROTOCOL::equals,
          PROTOCOL::equals);
  private static boolean initialized;

  private DnvVehicleCrewConfigNetwork() {}

  public static synchronized void initialize() {
    if (initialized) {
      return;
    }
    CHANNEL.registerMessage(
        0,
        OpenView.class,
        DnvVehicleCrewConfigNetwork::encodeOpen,
        DnvVehicleCrewConfigNetwork::decodeOpen,
        DnvVehicleCrewConfigNetwork::handleOpen,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    CHANNEL.registerMessage(
        1,
        CommitRequest.class,
        DnvVehicleCrewConfigNetwork::encodeCommit,
        DnvVehicleCrewConfigNetwork::decodeCommit,
        DnvVehicleCrewConfigNetwork::handleCommit,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        2,
        CommitResponse.class,
        DnvVehicleCrewConfigNetwork::encodeResponse,
        DnvVehicleCrewConfigNetwork::decodeResponse,
        DnvVehicleCrewConfigNetwork::handleResponse,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    CHANNEL.registerMessage(
        3,
        CancelRequest.class,
        DnvVehicleCrewConfigNetwork::encodeCancel,
        DnvVehicleCrewConfigNetwork::decodeCancel,
        DnvVehicleCrewConfigNetwork::handleCancel,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    initialized = true;
  }

  public static void open(ServerPlayer player, DnvVehicleCrewConfigSessions.View view) {
    if (!initialized) {
      return;
    }
    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenView(view));
  }

  public static void respond(
      ServerPlayer player,
      UUID token,
      boolean accepted,
      DnvVehicleCrewConfigSessions.Issue issue,
      Optional<String> savedFlightModeName,
      List<String> savedStationIds,
      List<String> savedStationModeNames) {
    if (!initialized) {
      return;
    }
    CHANNEL.send(
        PacketDistributor.PLAYER.with(() -> player),
        new CommitResponse(
            token, accepted, issue, savedFlightModeName,
            savedStationIds, savedStationModeNames));
  }

  @OnlyIn(Dist.CLIENT)
  public static void commit(
      UUID token,
      DnvVehicleCrewConfigSessions.HandSlot hand,
      DnvVehicleCrewProfile.FlightMode flightMode,
      List<DnvVehicleCrewProfile.StationDraft> stations) {
    CHANNEL.sendToServer(new CommitRequest(token, hand, flightMode, stations));
  }

  @OnlyIn(Dist.CLIENT)
  public static void cancel(UUID token) {
    CHANNEL.sendToServer(new CancelRequest(token));
  }

  private static void encodeOpen(OpenView message, FriendlyByteBuf buffer) {
    DnvVehicleCrewConfigSessions.View view = message.view();
    buffer.writeUUID(view.token());
    buffer.writeEnum(view.hand());
    buffer.writeUUID(view.coreId());
    buffer.writeUtf(view.vehicleTypeId(), MAX_ID_LENGTH);
    buffer.writeEnum(view.flightMode());
    writeEnums(buffer, view.allowedFlightModes());
    buffer.writeVarInt(view.stations().size());
    for (DnvVehicleCrewConfigSessions.StationView station : view.stations()) {
      buffer.writeUtf(station.stationId(), MAX_ID_LENGTH);
      buffer.writeVarInt(station.seatIndex());
      buffer.writeBoolean(station.aiCapable());
      buffer.writeEnum(station.mode());
      writeEnums(buffer, station.allowedModes());
    }
  }

  private static OpenView decodeOpen(FriendlyByteBuf buffer) {
    UUID token = buffer.readUUID();
    DnvVehicleCrewConfigSessions.HandSlot hand =
        buffer.readEnum(DnvVehicleCrewConfigSessions.HandSlot.class);
    UUID coreId = buffer.readUUID();
    String vehicleTypeId = buffer.readUtf(MAX_ID_LENGTH);
    DnvVehicleCrewProfile.FlightMode flightMode =
        buffer.readEnum(DnvVehicleCrewProfile.FlightMode.class);
    List<DnvVehicleCrewProfile.FlightMode> flightModes = readEnums(
        buffer,
        DnvVehicleCrewProfile.FlightMode.class,
        DnvVehicleCrewProfile.FlightMode.values().length);
    int stationCount = boundedCount(
        buffer, DnvVehicleCrewProfile.MAX_STATIONS, "station count");
    List<DnvVehicleCrewConfigSessions.StationView> stations = new ArrayList<>();
    for (int index = 0; index < stationCount; index++) {
      String stationId = buffer.readUtf(MAX_ID_LENGTH);
      int seatIndex = buffer.readVarInt();
      boolean aiCapable = buffer.readBoolean();
      DnvControlAuthority.WeaponMode mode =
          buffer.readEnum(DnvControlAuthority.WeaponMode.class);
      List<DnvControlAuthority.WeaponMode> modes = readEnums(
          buffer,
          DnvControlAuthority.WeaponMode.class,
          DnvControlAuthority.WeaponMode.values().length);
      stations.add(new DnvVehicleCrewConfigSessions.StationView(
          stationId, seatIndex, aiCapable, mode, modes));
    }
    return new OpenView(new DnvVehicleCrewConfigSessions.View(
        token,
        hand,
        coreId,
        vehicleTypeId,
        flightMode,
        flightModes,
        stations));
  }

  private static void encodeCommit(CommitRequest message, FriendlyByteBuf buffer) {
    buffer.writeUUID(message.token());
    buffer.writeEnum(message.hand());
    buffer.writeEnum(message.flightMode());
    buffer.writeVarInt(message.stations().size());
    for (DnvVehicleCrewProfile.StationDraft station : message.stations()) {
      buffer.writeUtf(station.stationId(), MAX_ID_LENGTH);
      buffer.writeEnum(station.mode());
    }
  }

  private static CommitRequest decodeCommit(FriendlyByteBuf buffer) {
    UUID token = buffer.readUUID();
    DnvVehicleCrewConfigSessions.HandSlot hand =
        buffer.readEnum(DnvVehicleCrewConfigSessions.HandSlot.class);
    DnvVehicleCrewProfile.FlightMode flightMode =
        buffer.readEnum(DnvVehicleCrewProfile.FlightMode.class);
    int count = boundedCount(
        buffer, DnvVehicleCrewProfile.MAX_STATIONS, "station count");
    List<DnvVehicleCrewProfile.StationDraft> stations = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      stations.add(new DnvVehicleCrewProfile.StationDraft(
          buffer.readUtf(MAX_ID_LENGTH),
          buffer.readEnum(DnvControlAuthority.WeaponMode.class)));
    }
    return new CommitRequest(token, hand, flightMode, stations);
  }

  private static void encodeResponse(CommitResponse message, FriendlyByteBuf buffer) {
    buffer.writeUUID(message.token());
    buffer.writeBoolean(message.accepted());
    buffer.writeEnum(message.issue());
    buffer.writeBoolean(message.savedFlightModeName().isPresent());
    message.savedFlightModeName().ifPresent(savedFlightModeName -> {
      buffer.writeUtf(savedFlightModeName, 64);
      buffer.writeVarInt(message.savedStationIds().size());
      for (int index = 0; index < message.savedStationIds().size(); index++) {
        buffer.writeUtf(message.savedStationIds().get(index), MAX_ID_LENGTH);
        buffer.writeUtf(message.savedStationModeNames().get(index), 64);
      }
    });
  }

  private static CommitResponse decodeResponse(FriendlyByteBuf buffer) {
    UUID token = buffer.readUUID();
    boolean accepted = buffer.readBoolean();
    DnvVehicleCrewConfigSessions.Issue issue =
        buffer.readEnum(DnvVehicleCrewConfigSessions.Issue.class);
    Optional<String> savedFlightModeName = Optional.empty();
    List<String> savedStationIds = List.of();
    List<String> savedStationModeNames = List.of();
    if (buffer.readBoolean()) {
      savedFlightModeName = Optional.of(buffer.readUtf(64));
      int count = boundedCount(
          buffer, DnvVehicleCrewProfile.MAX_STATIONS, "saved station count");
      List<String> stationIds = new ArrayList<>();
      List<String> stationModeNames = new ArrayList<>();
      for (int index = 0; index < count; index++) {
        stationIds.add(buffer.readUtf(MAX_ID_LENGTH));
        stationModeNames.add(buffer.readUtf(64));
      }
      savedStationIds = List.copyOf(stationIds);
      savedStationModeNames = List.copyOf(stationModeNames);
    }
    return new CommitResponse(
        token, accepted, issue, savedFlightModeName,
        savedStationIds, savedStationModeNames);
  }

  private static void encodeCancel(CancelRequest message, FriendlyByteBuf buffer) {
    buffer.writeUUID(message.token());
  }

  private static CancelRequest decodeCancel(FriendlyByteBuf buffer) {
    return new CancelRequest(buffer.readUUID());
  }

  private static void handleOpen(
      OpenView message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> DnvVehicleCrewClientProjection.open(message.view())));
    context.setPacketHandled(true);
  }

  private static void handleCommit(
      CommitRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() ->
          DnvVehicleCrewServices.commitCoreConfiguration(player, message));
    }
    context.setPacketHandled(true);
  }

  private static void handleResponse(
      CommitResponse message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> DnvVehicleCrewClientProjection.respond(message)));
    context.setPacketHandled(true);
  }

  private static void handleCancel(
      CancelRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() ->
          DnvVehicleCrewServices.cancelCoreConfiguration(player, message.token()));
    }
    context.setPacketHandled(true);
  }

  private static <E extends Enum<E>> void writeEnums(
      FriendlyByteBuf buffer, List<E> values) {
    buffer.writeVarInt(values.size());
    values.forEach(buffer::writeEnum);
  }

  private static <E extends Enum<E>> List<E> readEnums(
      FriendlyByteBuf buffer, Class<E> type, int maximum) {
    int count = boundedCount(buffer, maximum, "enum count");
    List<E> values = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      values.add(buffer.readEnum(type));
    }
    return List.copyOf(values);
  }

  private static int boundedCount(FriendlyByteBuf buffer, int maximum, String label) {
    int count = buffer.readVarInt();
    if (count < 0 || count > maximum) {
      throw new IllegalArgumentException(label + " is outside protocol bounds");
    }
    return count;
  }

  private record OpenView(DnvVehicleCrewConfigSessions.View view) {
    private OpenView {
      Objects.requireNonNull(view, "view");
    }
  }

  public record CommitRequest(
      UUID token,
      DnvVehicleCrewConfigSessions.HandSlot hand,
      DnvVehicleCrewProfile.FlightMode flightMode,
      List<DnvVehicleCrewProfile.StationDraft> stations) {
    public CommitRequest {
      Objects.requireNonNull(token, "token");
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(flightMode, "flightMode");
      stations = List.copyOf(stations);
      if (stations.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
        throw new IllegalArgumentException("too many station updates");
      }
    }

    String flightModeName() {
      return flightMode.name();
    }

    List<String> stationIds() {
      return stations.stream().map(DnvVehicleCrewProfile.StationDraft::stationId).toList();
    }

    List<String> stationModeNames() {
      return stations.stream().map(DnvVehicleCrewProfile.StationDraft::modeName).toList();
    }
  }

  private record CancelRequest(UUID token) {
    private CancelRequest {
      Objects.requireNonNull(token, "token");
    }
  }
}
