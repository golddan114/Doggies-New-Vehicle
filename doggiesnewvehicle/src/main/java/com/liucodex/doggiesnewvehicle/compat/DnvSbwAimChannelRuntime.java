package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController;
import com.liucodex.doggiesnewvehicle.core.DnvPhysicalAimChannelAuthority;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformWeaponAccess;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Exact-SBW owner for physical angle registers which may be shared by several logical seats.
 * It observes real player input before writing one bounded upstream aim step.
 */
final class DnvSbwAimChannelRuntime {
  static final float MANUAL_MOUSE_EPSILON = 1.0E-4F;

  private final VehicleEntity vehicle;
  private final DnvPlatformWeaponAccess platformAccess;
  private final Map<String, ChannelState> channels = new LinkedHashMap<>();
  private final Map<String, StationBinding> stations = new LinkedHashMap<>();

  DnvSbwAimChannelRuntime(VehicleEntity vehicle, DnvPlatformWeaponAccess platformAccess) {
    this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
    this.platformAccess = Objects.requireNonNull(platformAccess, "platformAccess");
  }

  void configure(List<ActuatorBinding> actuators) {
    clear();
    for (ActuatorBinding actuator : actuators) {
      ChannelState channel = channels.computeIfAbsent(
          actuator.aimChannelId(),
          ignored -> new ChannelState(
              actuator.aimChannelId(), actuator.aimChannel(), actuator.sharedWith()));
      if (channel.aimChannel != actuator.aimChannel()
          || !channel.sharedWith.equals(actuator.sharedWith())) {
        channel.valid = false;
      }
      if (stations.putIfAbsent(
          actuator.stationId(),
          new StationBinding(actuator.aimChannelId(), actuator.seatIndex())) != null) {
        channel.valid = false;
      }
    }
    for (ChannelState channel : channels.values()) {
      channel.valid &= channel.sharedWith.stream().allMatch(this::knownDomain);
    }
  }

  void beginTick(Map<String, Boolean> aiRequests, long gameTime) {
    Objects.requireNonNull(aiRequests, "aiRequests");
    for (ChannelState channel : channels.values()) {
      channel.issuedGameTime = gameTime;
      channel.selectedStation = stations.entrySet().stream()
          .filter(entry -> entry.getValue().channelId.equals(channel.channelId))
          .map(Map.Entry::getKey)
          .filter(stationId -> aiRequests.getOrDefault(stationId, false))
          .min(String::compareTo)
          .orElse(null);
      observe(channel, gameTime);
      if (channel.decision.owner() != DnvPhysicalAimChannelAuthority.Owner.AI) {
        clearSyncedTarget(channel);
      }
    }
  }

  ApplyResult apply(
      String stationId,
      String aimChannelId,
      String weaponName,
      Vec3 desired,
      UUID targetId,
      DnvVehicleCrewController.RuntimeRevision expectedLogicalRevision,
      DnvVehicleCrewController.RuntimeRevision currentLogicalRevision,
      long currentGameTime) {
    Objects.requireNonNull(stationId, "stationId");
    Objects.requireNonNull(aimChannelId, "aimChannelId");
    Objects.requireNonNull(desired, "desired");
    ChannelState channel = channels.get(aimChannelId);
    if (channel == null
        || !stationId.equals(channel.selectedStation)
        || !Objects.equals(expectedLogicalRevision, currentLogicalRevision)
        || !channel.authority.authorizeAiCommand(
            channel.decision.revision(), channel.issuedGameTime, currentGameTime)) {
      return result(channel, false);
    }
    try {
      if (channel.aimChannel == AimChannel.NATIVE_SEAT) {
        observe(channel, currentGameTime);
        if (!channel.authority.authorizeAiCommand(channel.decision.revision(), channel.issuedGameTime, currentGameTime)
            || !platformAccess.applies(weaponName) || channel.lastAdaptedAimGameTime == currentGameTime
            || !platformAccess.aim(weaponName, new Point(desired.x, desired.y, desired.z))) return result(channel, false);
        channel.lastAdaptedAimGameTime = currentGameTime;
        return result(channel, true);
      } else if (channel.aimChannel == AimChannel.TURRET) {
        vehicle.turretAutoAimFromVector(desired);
        vehicle.setAiTurretTargetUUID(targetId == null ? "" : targetId.toString());
      } else {
        if (platformAccess.applies(weaponName)) {
          observe(channel, currentGameTime);
          if (!channel.authority.authorizeAiCommand(channel.decision.revision(), channel.issuedGameTime, currentGameTime)) {
            clearSyncedTarget(channel);
            return result(channel, false);
          }
          if (channel.lastAdaptedAimGameTime == currentGameTime
              || !platformAccess.aim(weaponName, new Point(desired.x, desired.y, desired.z))) return result(channel, false);
          channel.lastAdaptedAimGameTime = currentGameTime;
        } else {
          vehicle.passengerWeaponAutoAimFormVector(desired);
        }
        // Several weapons share these registers. Project the resulting pose after either path.
        vehicle.setAiPassengerWeaponTargetUUID(targetId == null ? "" : targetId.toString());
      }
      channel.wroteSyncedTarget = true;
      return result(channel, true);
    } catch (RuntimeException | LinkageError exception) {
      channel.valid = false;
      channel.decision = channel.authority.observe(new DnvPhysicalAimChannelAuthority.Observation(
          currentGameTime,
          loaded(),
          false,
          false,
          Optional.empty(),
          true,
          false,
          false));
      clearSyncedTarget(channel);
      return result(channel, false);
    }
  }

  ChannelSnapshot snapshot(String stationId, String aimChannelId) {
    ChannelState channel = channels.get(aimChannelId);
    if (channel == null) {
      return new ChannelSnapshot(
          aimChannelId,
          DnvPhysicalAimChannelAuthority.Owner.NONE,
          DnvPhysicalAimChannelAuthority.Reason.CHANNEL_INVALID,
          false,
          Float.NaN,
          Float.NaN);
    }
    Angles actual = safeAngles(channel);
    return new ChannelSnapshot(
        channel.channelId,
        channel.decision.owner(),
        channel.decision.reason(),
        stationId.equals(channel.selectedStation),
        actual.yaw,
        actual.pitch);
  }

  void clear() {
    disarm();
    channels.clear();
    stations.clear();
  }

  long revision(String channelId) {
    ChannelState channel = channels.get(channelId);
    return channel == null ? -1L : channel.decision.revision();
  }

  boolean revalidate(String stationId, String channelId, long expectedRevision, long now) {
    ChannelState channel = channels.get(channelId);
    if (channel == null || !stationId.equals(channel.selectedStation)) return false;
    observe(channel, now);
    return channel.authority.authorizeAiCommand(expectedRevision, channel.issuedGameTime, now);
  }

  void disarm() {
    for (ChannelState channel : channels.values()) {
      clearSyncedTarget(channel);
      channel.authority.clear();
      channel.decision = channel.authority.snapshot();
      channel.selectedStation = null;
      channel.issuedGameTime = 0L;
      channel.lastAdaptedAimGameTime = Long.MIN_VALUE;
    }
  }

  private void observe(ChannelState channel, long gameTime) {
    Optional<UUID> manualPlayer = Optional.empty();
    boolean reliable = true;
    boolean manualAim = false;
    boolean manualFire = false;
    try {
      List<ServerPlayer> participants = manualParticipants(channel.sharedWith);
      if (participants.size() > 1) {
        manualPlayer = Optional.of(participants.get(0).getUUID());
        reliable = false;
      } else if (participants.size() == 1) {
        manualPlayer = Optional.of(participants.get(0).getUUID());
        manualAim = manualAimActive(
            vehicle.getMouseMoveSpeedX(),
            vehicle.getMouseMoveSpeedY());
        manualFire = vehicle.fireInputDown();
      }
    } catch (RuntimeException | LinkageError exception) {
      channel.valid = false;
    }
    channel.decision = channel.authority.observe(new DnvPhysicalAimChannelAuthority.Observation(
        gameTime,
        loaded(),
        channel.valid,
        channel.selectedStation != null,
        manualPlayer,
        reliable,
        manualAim,
        manualFire));
  }

  private List<ServerPlayer> manualParticipants(Set<String> domains) {
    Map<UUID, ServerPlayer> unique = new LinkedHashMap<>();
    for (String domain : domains) {
      Entity occupant = "driver".equals(domain)
          ? vehicle.getNthEntity(0)
          : vehicle.getNthEntity(stations.get(domain).seatIndex);
      if (occupant instanceof ServerPlayer player) {
        unique.put(player.getUUID(), player);
      }
    }
    List<ServerPlayer> ordered = new ArrayList<>(unique.values());
    ordered.sort(Comparator.comparing(ServerPlayer::getUUID));
    return ordered;
  }

  private boolean knownDomain(String domain) {
    return "driver".equals(domain) || stations.containsKey(domain);
  }

  private boolean loaded() {
    return vehicle.level() instanceof ServerLevel && !vehicle.isRemoved();
  }

  private Angles safeAngles(ChannelState channel) {
    try {
      if (channel.aimChannel == AimChannel.NATIVE_SEAT) {
        var station = stations.values().stream().filter(s -> s.channelId.equals(channel.channelId)).findFirst().orElseThrow();
        Point pose = platformAccess.angles(station.seatIndex);
        return new Angles((float) pose.y(), (float) pose.x());
      }
      return angles(channel.aimChannel);
    } catch (RuntimeException | LinkageError exception) {
      return new Angles(Float.NaN, Float.NaN);
    }
  }

  private Angles angles(AimChannel channel) {
    return channel == AimChannel.TURRET
        ? new Angles(vehicle.getTurretYRot(), vehicle.getTurretXRot())
        : new Angles(vehicle.getGunYRot(), vehicle.getGunXRot());
  }

  private void clearSyncedTarget(ChannelState channel) {
    if (!channel.wroteSyncedTarget) {
      return;
    }
    try {
      if (channel.aimChannel == AimChannel.TURRET) {
        vehicle.setAiTurretTargetUUID("");
      } else {
        vehicle.setAiPassengerWeaponTargetUUID("");
      }
    } catch (RuntimeException | LinkageError exception) {
      channel.valid = false;
    }
    channel.wroteSyncedTarget = false;
  }

  private static ApplyResult result(ChannelState channel, boolean applied) {
    if (channel == null) {
      return new ApplyResult(
          false,
          DnvPhysicalAimChannelAuthority.Owner.NONE,
          DnvPhysicalAimChannelAuthority.Reason.CHANNEL_INVALID);
    }
    return new ApplyResult(applied, channel.decision.owner(), channel.decision.reason());
  }

  static boolean manualAimActive(float mouseX, float mouseY) {
    return Math.abs(mouseX) > MANUAL_MOUSE_EPSILON
        || Math.abs(mouseY) > MANUAL_MOUSE_EPSILON;
  }

  record ApplyResult(
      boolean applied,
      DnvPhysicalAimChannelAuthority.Owner owner,
      DnvPhysicalAimChannelAuthority.Reason reason) {}

  record ChannelSnapshot(
      String channelId,
      DnvPhysicalAimChannelAuthority.Owner owner,
      DnvPhysicalAimChannelAuthority.Reason reason,
      boolean selectedStation,
      float actualYaw,
      float actualPitch) {}

  record Angles(float yaw, float pitch) {}

  record ActuatorBinding(
      String stationId,
      int seatIndex,
      AimChannel aimChannel,
      String aimChannelId,
      Set<String> sharedWith) {
    ActuatorBinding {
      Objects.requireNonNull(stationId, "stationId");
      Objects.requireNonNull(aimChannel, "aimChannel");
      Objects.requireNonNull(aimChannelId, "aimChannelId");
      sharedWith = Set.copyOf(Objects.requireNonNull(sharedWith, "sharedWith"));
      if (seatIndex < 0) {
        throw new IllegalArgumentException("seatIndex must not be negative");
      }
    }
  }

  enum AimChannel {
    TURRET,
    PASSENGER_WEAPON,
    NATIVE_SEAT
  }

  private record StationBinding(String channelId, int seatIndex) {}

  private static final class ChannelState {
    private final String channelId;
    private final AimChannel aimChannel;
    private final Set<String> sharedWith;
    private final DnvPhysicalAimChannelAuthority authority;
    private boolean valid = true;
    private String selectedStation;
    private long issuedGameTime;
    private DnvPhysicalAimChannelAuthority.Decision decision;
    private boolean wroteSyncedTarget;
    private long lastAdaptedAimGameTime = Long.MIN_VALUE;

    private ChannelState(
        String channelId,
        AimChannel aimChannel,
        Set<String> sharedWith) {
      this.channelId = Objects.requireNonNull(channelId, "channelId");
      this.aimChannel = Objects.requireNonNull(aimChannel, "aimChannel");
      this.sharedWith = Set.copyOf(new LinkedHashSet<>(sharedWith));
      authority = new DnvPhysicalAimChannelAuthority(channelId);
      decision = authority.snapshot();
    }
  }
}
