package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.client.lighting.MuzzleFlashHelper;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.FormatTool;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Client-only visual companion for server-owned autonomous SBW shots. */
public final class DnvVehicleAutonomousFireNetwork {
  private static final String PROTOCOL = "1";
  private static final int MAX_WEAPON_NAME = 96;
  private static final SimpleChannel CHANNEL =
      net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(
              DoggiesNewVehicle.MOD_ID, "vehicle_autonomous_fire"),
          () -> PROTOCOL,
          PROTOCOL::equals,
          PROTOCOL::equals);
  private static boolean initialized;

  private DnvVehicleAutonomousFireNetwork() {}

  public static synchronized void initialize() {
    if (initialized) {
      return;
    }
    CHANNEL.registerMessage(
        0,
        FireVisual.class,
        DnvVehicleAutonomousFireNetwork::encode,
        DnvVehicleAutonomousFireNetwork::decode,
        DnvVehicleAutonomousFireNetwork::handle,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    initialized = true;
  }

  public static void send(VehicleEntity vehicle, String weaponName, int fireIndex) {
    if (!initialized || vehicle.level().isClientSide()) {
      return;
    }
    CHANNEL.send(
        PacketDistributor.TRACKING_ENTITY.with(() -> vehicle),
        new FireVisual(vehicle.getId(), weaponName, fireIndex));
  }

  private static void encode(FireVisual message, FriendlyByteBuf buffer) {
    buffer.writeVarInt(message.vehicleEntityId());
    buffer.writeUtf(message.weaponName(), MAX_WEAPON_NAME);
    buffer.writeVarInt(message.fireIndex());
  }

  private static FireVisual decode(FriendlyByteBuf buffer) {
    return new FireVisual(
        buffer.readVarInt(),
        buffer.readUtf(MAX_WEAPON_NAME),
        buffer.readVarInt());
  }

  private static void handle(
      FireVisual message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT, () -> () -> ClientHandler.handle(message)));
    context.setPacketHandled(true);
  }

  record FireVisual(int vehicleEntityId, String weaponName, int fireIndex) {
    FireVisual {
      weaponName = Objects.requireNonNull(weaponName, "weaponName").trim();
      if (vehicleEntityId < 0 || weaponName.isEmpty()
          || weaponName.length() > MAX_WEAPON_NAME || fireIndex < 0) {
        throw new IllegalArgumentException("invalid autonomous fire visual");
      }
    }
  }

  @OnlyIn(Dist.CLIENT)
  private static final class ClientHandler {
    private ClientHandler() {}

    private static void handle(FireVisual message) {
      if (Minecraft.getInstance().level == null) {
        return;
      }
      Entity entity = Minecraft.getInstance().level.getEntity(message.vehicleEntityId());
      if (!(entity instanceof VehicleEntity vehicle) || vehicle.isWreck()) {
        return;
      }
      var gunData = vehicle.getGunData(message.weaponName());
      if (gunData == null) {
        return;
      }
      var animation = vehicle.getAnimationInstance();
      if (animation != null) {
        animation.fire(
            FormatTool.INSTANCE.camelToSnake(message.weaponName()),
            message.fireIndex());
      }
      Vec3 direction = vehicle.getShootVec(message.weaponName(), 1.0F);
      if (direction.lengthSqr() > 1.0E-6D) {
        MuzzleFlashHelper.spawnFlashCone(
            vehicle.getShootPos(message.weaponName(), 1.0F),
            direction,
            MuzzleFlashHelper.calculateFromGunData(gunData));
      }
    }
  }
}
