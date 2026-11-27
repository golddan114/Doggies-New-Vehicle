package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.vehicle.subdata.SeatInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.core.DnvPhysicalAimChannelAuthority;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Exact-SBW adapter inspection used only when installing or revalidating a vehicle crew core. */
public final class DnvSbwVehicleCrewAdapter {
  public static final int MAX_WEAPONS_PER_STATION = 8;

  private DnvSbwVehicleCrewAdapter() {}

  public static Inspection inspect(VehicleEntity vehicle, AllowlistEntry allowlist) {
    Objects.requireNonNull(vehicle, "vehicle");
    Objects.requireNonNull(allowlist, "allowlist");
    try {
      ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
      if (typeId == null) {
        return Inspection.rejected(0L, IssueCode.UNREGISTERED_VEHICLE_TYPE);
      }
      if (retiredCarrier(typeId.toString())) {
        return Inspection.rejected(0L, IssueCode.RETIRED_VEHICLE_TYPE);
      }
      List<SeatLayout> seats = new ArrayList<>();
      List<SeatInfo> upstreamSeats = vehicle.computed().seats();
      for (int index = 0; index < upstreamSeats.size(); index++) {
        SeatInfo seat = upstreamSeats.get(index);
        seats.add(seat == null
            ? new SeatLayout(index, List.of())
            : new SeatLayout(
                index,
                seat.weapons(),
                seat.getPosition().x,
                seat.getPosition().y,
                seat.getPosition().z,
                seat.getOrientation(),
                seat.getMinYaw(),
                seat.getMaxYaw(),
                seat.getMinPitch(),
                seat.getMaxPitch()));
      }
      return inspectLayout(
          new VehicleLayout(
              typeId.toString(),
              vehicle.getTurretControllerIndex(),
              vehicle.getPassengerWeaponStationControllerIndex(),
              seats),
          allowlist);
    } catch (RuntimeException | LinkageError exception) {
      return Inspection.rejected(0L, IssueCode.SBW_API_UNAVAILABLE);
    }
  }

  public static Inspection inspectLayout(VehicleLayout layout, AllowlistEntry allowlist) {
    Objects.requireNonNull(layout, "layout");
    Objects.requireNonNull(allowlist, "allowlist");
    if (retiredCarrier(layout.vehicleTypeId())) {
      return Inspection.rejected(0L, IssueCode.RETIRED_VEHICLE_TYPE);
    }
    List<IssueCode> issues = new ArrayList<>();
    if (!allowlist.vehicleTypeId().equals(layout.vehicleTypeId())) {
      issues.add(IssueCode.WRONG_VEHICLE_TYPE);
    }

    Map<Integer, SeatLayout> seats = new LinkedHashMap<>();
    for (SeatLayout seat : layout.seats()) {
      if (seat == null || seat.seatIndex() < 0) {
        issues.add(IssueCode.INVALID_SEAT);
        continue;
      }
      if (seats.putIfAbsent(seat.seatIndex(), seat) != null) {
        issues.add(IssueCode.DUPLICATE_SEAT);
      }
    }
    long actualFingerprint = fingerprint(layout);
    if (actualFingerprint != allowlist.expectedLayoutFingerprint()) {
      issues.add(IssueCode.LAYOUT_CHANGED);
    }

    List<SeatLayout> weaponSeats = seats.values().stream()
        .filter(seat -> !seat.weapons().isEmpty())
        .toList();
    if (weaponSeats.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
      issues.add(IssueCode.TOO_MANY_WEAPON_STATIONS);
    }
    if (weaponSeats.stream()
        .anyMatch(seat -> seat.weapons().size() > MAX_WEAPONS_PER_STATION)) {
      issues.add(IssueCode.TOO_MANY_WEAPONS_IN_STATION);
    }
    Set<String> stationIds = new LinkedHashSet<>();
    Set<String> upstreamControllerStations = new LinkedHashSet<>();
    for (SeatLayout seat : weaponSeats) {
      String stationId = stationId(seat.seatIndex());
      stationIds.add(stationId);
      if (seat.seatIndex() == layout.turretControllerIndex()
          || seat.seatIndex() == layout.passengerWeaponControllerIndex()) {
        upstreamControllerStations.add(stationId);
      }
    }
    if (!stationIds.containsAll(allowlist.aiStationIds())) {
      issues.add(IssueCode.UNKNOWN_AI_STATION);
    } else if (!upstreamControllerStations.containsAll(allowlist.aiStationIds())) {
      issues.add(IssueCode.AI_STATION_NOT_UPSTREAM_CONTROLLER);
    }
    if (!allowlist.aiStationIds().containsAll(allowlist.aimChannels().keySet())) {
      issues.add(IssueCode.UNKNOWN_AIM_CHANNEL_STATION);
    }
    if (!allowlist.aiStationIds().containsAll(allowlist.stationPolicies().keySet())) {
      issues.add(IssueCode.UNKNOWN_POLICY_STATION);
    }
    if (allowlist.aimChannels().entrySet().stream()
        .anyMatch(entry -> !entry.getValue().sharedWith().contains(entry.getKey()))) {
      issues.add(IssueCode.INVALID_AIM_CHANNEL_BINDING);
    }
    Optional<FlightActuator> flightActuator = resolveFlightActuator(allowlist);
    if (!allowlist.flightActuatorId().isEmpty() && flightActuator.isEmpty()) {
      issues.add(IssueCode.UNKNOWN_OR_MISMATCHED_FLIGHT_ACTUATOR);
    }
    if (!issues.isEmpty()) {
      return new Inspection(
          Optional.empty(), List.copyOf(issues), actualFingerprint, List.of(), Optional.empty());
    }

    List<DnvVehicleCrewProfile.StationTemplate> stationTemplates = new ArrayList<>();
    List<StationActuator> stationActuators = new ArrayList<>();
    for (SeatLayout seat : weaponSeats) {
      String stationId = stationId(seat.seatIndex());
      boolean upstreamAimController =
          seat.seatIndex() == layout.turretControllerIndex()
              || seat.seatIndex() == layout.passengerWeaponControllerIndex();
      boolean aiCapable = upstreamAimController && allowlist.aiStationIds().contains(stationId);
      Set<DnvControlAuthority.WeaponMode> modes = aiCapable
          ? Set.of(
              DnvControlAuthority.WeaponMode.AUTO_ENGAGE,
              DnvControlAuthority.WeaponMode.TRACK_ONLY,
              DnvControlAuthority.WeaponMode.MANUAL_PREFERRED,
              DnvControlAuthority.WeaponMode.MANUAL_ONLY,
              DnvControlAuthority.WeaponMode.HOLD_FIRE)
          : Set.of(
              DnvControlAuthority.WeaponMode.MANUAL_ONLY,
              DnvControlAuthority.WeaponMode.HOLD_FIRE);
      stationTemplates.add(new DnvVehicleCrewProfile.StationTemplate(
          stationId, seat.seatIndex(), aiCapable, modes));
      if (aiCapable) {
        AimChannel channel = seat.seatIndex() == layout.turretControllerIndex()
            ? AimChannel.TURRET
            : AimChannel.PASSENGER_WEAPON;
        AimChannelBinding binding = allowlist.aimChannels().getOrDefault(
            stationId, defaultBinding(stationId, channel));
        stationActuators.add(new StationActuator(
            stationId,
            seat.seatIndex(),
            channel,
            binding.aimChannelId(),
            binding.sharedWith(),
            seat.weapons(),
            stationGeometry(allowlist, stationId, seat),
            allowlist.stationPolicies().getOrDefault(stationId, defaultStationPolicy())));
      }
    }
    return new Inspection(
        Optional.of(new DnvVehicleCrewProfile.AdapterTemplate(
            allowlist.adapterId(),
            allowlist.revision(),
            allowlist.vehicleTypeId(),
            flightActuator.isPresent(),
            flightActuator.isPresent()
                ? List.of(
                    DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
                    DnvVehicleCrewProfile.FlightMode.AI_PREFERRED,
                    DnvVehicleCrewProfile.FlightMode.MANUAL_ONLY)
                : List.of(
                    DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
                    DnvVehicleCrewProfile.FlightMode.MANUAL_ONLY),
            stationTemplates)),
        List.of(),
        actualFingerprint,
        stationActuators,
        flightActuator);
  }

  public static Optional<FlightActuator> resolveFlightActuator(AllowlistEntry allowlist) {
    Objects.requireNonNull(allowlist, "allowlist");
    // No live aircraft actuator is registered; old core/preset data remains readable but cannot arm it.
    return Optional.empty();
  }

  private static boolean retiredCarrier(String typeId) {
    return "superbwarfare:ac_130h".equals(typeId) || "dragonrise_reforge:ac130".equals(typeId);
  }

  public static long fingerprint(VehicleLayout layout) {
    Objects.requireNonNull(layout, "layout");
    CRC32 checksum = new CRC32();
    update(checksum, layout.vehicleTypeId());
    update(checksum, Integer.toString(layout.turretControllerIndex()));
    update(checksum, Integer.toString(layout.passengerWeaponControllerIndex()));
    for (SeatLayout seat : layout.seats()) {
      if (seat == null) {
        update(checksum, "null-seat");
        continue;
      }
      update(checksum, Integer.toString(seat.seatIndex()));
      for (String weapon : seat.weapons()) {
        update(checksum, weapon);
      }
      update(checksum, "seat-end");
    }
    return checksum.getValue();
  }

  private static void update(CRC32 checksum, String value) {
    byte[] bytes = Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8);
    checksum.update(bytes, 0, bytes.length);
    checksum.update(0);
  }

  private static DnvStationEngagementCore.StationGeometryProfile stationGeometry(
      AllowlistEntry allowlist, String stationId, SeatLayout seat) {
    float minimumYaw = seat.minimumYaw();
    float maximumYaw = seat.maximumYaw();
    DnvStationEngagementCore.YawArc yawArc = maximumYaw - minimumYaw >= 359.999F
        ? new DnvStationEngagementCore.YawArc(-180.0F, 180.0F)
        : new DnvStationEngagementCore.YawArc(
            normalizeEndpoint(minimumYaw), normalizeEndpoint(maximumYaw));
    return new DnvStationEngagementCore.StationGeometryProfile(
        allowlist.adapterId() + "#" + stationId,
        allowlist.revision(),
        seat.mountX(),
        seat.mountY(),
        seat.mountZ(),
        seat.orientationYaw(),
        List.of(yawArc),
        new DnvStationEngagementCore.PitchArc(
            Math.max(-90.0F, seat.minimumPitch()),
            Math.min(90.0F, seat.maximumPitch())),
        List.of(),
        false);
  }

  private static float normalizeEndpoint(float value) {
    float wrapped = value % 360.0F;
    if (wrapped > 180.0F) {
      wrapped -= 360.0F;
    }
    if (wrapped < -180.0F) {
      wrapped += 360.0F;
    }
    return wrapped;
  }

  private static String stationId(int seatIndex) {
    return "seat_" + seatIndex;
  }

  private static AimChannelBinding defaultBinding(String stationId, AimChannel channel) {
    return new AimChannelBinding(
        channel == AimChannel.TURRET ? "sbw:turret" : "sbw:passenger",
        Set.of(stationId));
  }

  public record AllowlistEntry(
      String adapterId,
      long revision,
      String vehicleTypeId,
      long expectedLayoutFingerprint,
      Set<String> aiStationIds,
      String flightActuatorId,
      Map<String, AimChannelBinding> aimChannels,
      Map<String, DnvStationEngagementCore.StationEngagementPolicy> stationPolicies) {
    public AllowlistEntry {
      adapterId = requireId(adapterId, "adapterId");
      vehicleTypeId = requireId(vehicleTypeId, "vehicleTypeId");
      if (revision < 0L || expectedLayoutFingerprint < 0L) {
        throw new IllegalArgumentException("revision and layout fingerprint must not be negative");
      }
      Objects.requireNonNull(aiStationIds, "aiStationIds");
      aiStationIds = Set.copyOf(aiStationIds);
      flightActuatorId = Objects.requireNonNullElse(flightActuatorId, "").trim();
      Objects.requireNonNull(aimChannels, "aimChannels");
      Map<String, AimChannelBinding> checkedChannels = new LinkedHashMap<>();
      aimChannels.forEach((station, binding) -> checkedChannels.put(
          requireId(station, "aimChannel station"),
          Objects.requireNonNull(binding, "aimChannel binding")));
      aimChannels = Collections.unmodifiableMap(checkedChannels);
      stationPolicies = Map.copyOf(Objects.requireNonNull(stationPolicies, "stationPolicies"));
      if (stationPolicies.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
        throw new IllegalArgumentException("station policy budget exceeded");
      }
    }

    public AllowlistEntry(
        String adapterId, long revision, String vehicleTypeId, long expectedLayoutFingerprint,
        Set<String> aiStationIds, String flightActuatorId, Map<String, AimChannelBinding> aimChannels) {
      this(adapterId, revision, vehicleTypeId, expectedLayoutFingerprint,
          aiStationIds, flightActuatorId, aimChannels, Map.of());
    }

    public AllowlistEntry(
        String adapterId,
        long revision,
        String vehicleTypeId,
        long expectedLayoutFingerprint,
        Set<String> aiStationIds,
        String flightActuatorId) {
      this(
          adapterId, revision, vehicleTypeId, expectedLayoutFingerprint,
          aiStationIds, flightActuatorId, Map.of());
    }

    public AllowlistEntry(
        String adapterId,
        long revision,
        String vehicleTypeId,
        long expectedLayoutFingerprint,
        Set<String> aiStationIds) {
      this(
          adapterId, revision, vehicleTypeId, expectedLayoutFingerprint,
          aiStationIds, "", Map.of());
    }
  }

  public record AimChannelBinding(String aimChannelId, Set<String> sharedWith) {
    public AimChannelBinding {
      aimChannelId = requireId(aimChannelId, "aimChannelId");
      if (aimChannelId.length() > DnvPhysicalAimChannelAuthority.MAX_ID_LENGTH) {
        throw new IllegalArgumentException("aimChannelId exceeds the authority bound");
      }
      Objects.requireNonNull(sharedWith, "sharedWith");
      if (sharedWith.isEmpty()
          || sharedWith.size() > DnvVehicleCrewProfile.MAX_STATIONS + 1) {
        throw new IllegalArgumentException("sharedWith must be bounded and non-empty");
      }
      sharedWith = sharedWith.stream()
          .map(AimChannelBinding::requireBoundedDomain)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String requireBoundedDomain(String value) {
      String domain = requireId(value, "sharedWith domain");
      if (domain.length() > DnvPhysicalAimChannelAuthority.MAX_ID_LENGTH) {
        throw new IllegalArgumentException("sharedWith domain exceeds the authority bound");
      }
      return domain;
    }
  }

  public record VehicleLayout(
      String vehicleTypeId,
      int turretControllerIndex,
      int passengerWeaponControllerIndex,
      List<SeatLayout> seats) {
    public VehicleLayout {
      vehicleTypeId = requireId(vehicleTypeId, "vehicleTypeId");
      Objects.requireNonNull(seats, "seats");
      seats = List.copyOf(seats);
    }
  }

  public record SeatLayout(
      int seatIndex,
      List<String> weapons,
      double mountX,
      double mountY,
      double mountZ,
      float orientationYaw,
      float minimumYaw,
      float maximumYaw,
      float minimumPitch,
      float maximumPitch) {
    public SeatLayout {
      Objects.requireNonNull(weapons, "weapons");
      weapons = weapons.stream().map(value -> requireId(value, "weapon")).toList();
      if (!Double.isFinite(mountX)
          || !Double.isFinite(mountY)
          || !Double.isFinite(mountZ)
          || !Float.isFinite(orientationYaw)
          || !Float.isFinite(minimumYaw)
          || !Float.isFinite(maximumYaw)
          || !Float.isFinite(minimumPitch)
          || !Float.isFinite(maximumPitch)
          || maximumPitch < minimumPitch) {
        throw new IllegalArgumentException("seat geometry must be finite and ordered");
      }
    }

    public SeatLayout(int seatIndex, List<String> weapons) {
      this(
          seatIndex, weapons,
          0.0D, 0.0D, 0.0D,
          0.0F, -180.0F, 180.0F, -90.0F, 90.0F);
    }
  }

  /** Exact runtime actuator metadata derived from the same fingerprinted layout as the profile. */
  public record StationActuator(
      String stationId,
      int seatIndex,
      AimChannel aimChannel,
      String aimChannelId,
      Set<String> sharedWith,
      List<String> weapons,
      DnvStationEngagementCore.StationGeometryProfile geometry,
      DnvStationEngagementCore.StationEngagementPolicy policy) {
    public StationActuator {
      stationId = requireId(stationId, "stationId");
      if (seatIndex < 0) {
        throw new IllegalArgumentException("seatIndex must not be negative");
      }
      Objects.requireNonNull(aimChannel, "aimChannel");
      aimChannelId = requireId(aimChannelId, "aimChannelId");
      sharedWith = Set.copyOf(Objects.requireNonNull(sharedWith, "sharedWith"));
      if (!sharedWith.contains(stationId)) {
        throw new IllegalArgumentException("physical aim channel must include its station");
      }
      Objects.requireNonNull(weapons, "weapons");
      weapons = weapons.stream().map(value -> requireId(value, "weapon")).toList();
      if (weapons.isEmpty()) {
        throw new IllegalArgumentException("AI station must expose at least one weapon");
      }
      Objects.requireNonNull(geometry, "geometry");
      Objects.requireNonNull(policy, "policy");
    }

    public StationActuator(
        String stationId, int seatIndex, AimChannel aimChannel, String aimChannelId,
        Set<String> sharedWith, List<String> weapons,
        DnvStationEngagementCore.StationGeometryProfile geometry) {
      this(stationId, seatIndex, aimChannel, aimChannelId, sharedWith, weapons,
          geometry, defaultStationPolicy());
    }

    public StationActuator(
        String stationId,
        int seatIndex,
        AimChannel aimChannel,
        List<String> weapons) {
      this(
          stationId,
          seatIndex,
          aimChannel,
          defaultBinding(stationId, aimChannel).aimChannelId(),
          defaultBinding(stationId, aimChannel).sharedWith(),
          weapons,
          new DnvStationEngagementCore.StationGeometryProfile(
              "doggiesnewvehicle:legacy_full_envelope",
              0L,
              0.0D,
              0.0D,
              0.0D,
              0.0F,
              List.of(new DnvStationEngagementCore.YawArc(-180.0F, 180.0F)),
              new DnvStationEngagementCore.PitchArc(-90.0F, 90.0F),
              List.of(),
              false));
    }

    // The scheduler preserves the exact seat weapon order and advances after an accepted attempt.
  }

  static DnvStationEngagementCore.StationEngagementPolicy defaultStationPolicy() {
    return new DnvStationEngagementCore.StationEngagementPolicy(
        DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED,
        Set.of(DnvStationEngagementCore.TargetKind.LIVING, DnvStationEngagementCore.TargetKind.VEHICLE),
        0.1D, DnvCombatPolicyRuntime.CREW_TARGET_RANGE,
        DnvAutonomyCore.TARGET_SWITCH_ADVANTAGE, DnvAutonomyCore.MIN_TARGET_HOLD_TICKS,
        DnvAutonomyCore.TARGET_LOSS_GRACE_TICKS);
  }

  public enum AimChannel {
    TURRET,
    PASSENGER_WEAPON,
    NATIVE_SEAT
  }

  /** Exact, fingerprint-gated flight adapter metadata; it never loads the optional addon class. */
  public record FlightActuator(
      String actuatorId, String vehicleTypeId, boolean requiresPassenger) {
    public FlightActuator {
      actuatorId = requireId(actuatorId, "actuatorId");
      vehicleTypeId = requireId(vehicleTypeId, "vehicleTypeId");
    }
  }

  public record Inspection(
      Optional<DnvVehicleCrewProfile.AdapterTemplate> template,
      List<IssueCode> issues,
      long actualLayoutFingerprint,
      List<StationActuator> actuators,
      Optional<FlightActuator> flightActuator) {
    public Inspection {
      template = Objects.requireNonNull(template, "template");
      issues = List.copyOf(issues);
      actuators = List.copyOf(actuators);
      flightActuator = Objects.requireNonNull(flightActuator, "flightActuator");
    }

    public boolean accepted() {
      return template.isPresent() && issues.isEmpty();
    }

    private static Inspection rejected(long fingerprint, IssueCode issue) {
      return new Inspection(
          Optional.empty(), List.of(issue), fingerprint, List.of(), Optional.empty());
    }
  }

  public enum IssueCode {
    UNKNOWN_POLICY_STATION,
    UNREGISTERED_VEHICLE_TYPE,
    RETIRED_VEHICLE_TYPE,
    SBW_API_UNAVAILABLE,
    WRONG_VEHICLE_TYPE,
    INVALID_SEAT,
    DUPLICATE_SEAT,
    LAYOUT_CHANGED,
    TOO_MANY_WEAPON_STATIONS,
    TOO_MANY_WEAPONS_IN_STATION,
    UNKNOWN_AI_STATION,
    AI_STATION_NOT_UPSTREAM_CONTROLLER,
    UNKNOWN_AIM_CHANNEL_STATION,
    INVALID_AIM_CHANNEL_BINDING,
    UNKNOWN_OR_MISMATCHED_FLIGHT_ACTUATOR
  }

  private static String requireId(String value, String label) {
    String normalized = Objects.requireNonNull(value, label).trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return normalized;
  }
}
