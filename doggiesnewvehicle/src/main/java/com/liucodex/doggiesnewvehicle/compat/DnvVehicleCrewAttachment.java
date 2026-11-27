package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewRuntime;
import com.liucodex.doggiesnewvehicle.core.DnvSbwWeaponDiagnostics;
import com.liucodex.doggiesnewvehicle.core.DnvEntityDesignationSession;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformWeaponAccess;
import com.liucodex.doggiesnewvehicle.init.ModItems;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreCodec;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreInstallation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Entity-attached bridge from exact SBW inventory/seat state to the pure per-vehicle runtime. */
public final class DnvVehicleCrewAttachment {
  private final VehicleEntity vehicle;
  private final DnvEntityDesignationSession combatOrders;
  private DnvVehicleCrewRuntime runtime;
  private UUID runtimeVehicleId;
  private String runtimeVehicleType;
  private DnvSbwCrewAutonomy autonomy;
  private List<DnvSbwVehicleCrewAdapter.StationActuator> actuators = List.of();
  private boolean weaponDiagnosticsEnabled = true;
  private CompoundTag lastCoreTag;
  private long lastCatalogRevision = -1L;
  private SyncStatus lastStatus = SyncStatus.NO_CORE;

  public DnvVehicleCrewAttachment(VehicleEntity vehicle) {
    this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
    this.combatOrders = new DnvEntityDesignationSession(vehicle.getUUID());
  }

  public SyncResult synchronize(DnvSbwVehicleCrewCatalog catalog) {
    Objects.requireNonNull(catalog, "catalog");
    if (vehicle.level().isClientSide()) {
      return new SyncResult(false, SyncStatus.CLIENT_SIDE, List.of());
    }
    DnvVehicleCrewRuntime current = runtime();
    List<ItemStack> cores = findCoreItems();
    if (cores.isEmpty()) {
      current.clearWhenItemRemoved();
      resetObservedCore();
      return new SyncResult(false, SyncStatus.NO_CORE, List.of());
    }
    if (cores.size() > 1) {
      current.clearWhenItemRemoved();
      disableAutonomy();
      remember(null, catalog.snapshot().revision(), SyncStatus.MULTIPLE_CORES);
      return new SyncResult(
          true, SyncStatus.MULTIPLE_CORES, List.of("inventory.multiple_cores"));
    }

    CompoundTag coreTag = cores.get(0).getTag();
    long catalogRevision = catalog.snapshot().revision();
    Optional<DnvSbwVehicleCrewAdapter.Inspection> inspection = currentInspection(catalog);
    Optional<DnvVehicleCrewProfile.AdapterTemplate> template = inspection
        .flatMap(DnvSbwVehicleCrewAdapter.Inspection::template);
    boolean sameItem = Objects.equals(lastCoreTag, coreTag);
    if (sameItem && lastCatalogRevision == catalogRevision) {
      synchronizeOccupants(template, current);
      synchronizeAutonomy(inspection, current);
      return new SyncResult(true, lastStatus, List.of());
    }

    if (sameItem && current.configuredCoreId().isPresent()) {
      DnvVehicleCrewRuntime.RevalidationResult result =
          current.revalidate(template, catalogRevision);
      SyncStatus status = result.state() == DnvVehicleCrewRuntime.State.ACTIVE
          ? SyncStatus.ACTIVE
          : SyncStatus.CATALOG_REJECTED;
      remember(coreTag, catalogRevision, status);
      synchronizeOccupants(template, current);
      synchronizeAutonomy(inspection, current);
      return new SyncResult(
          true,
          status,
          result.issues().stream()
              .map(issue -> issue.field() + ":" + issue.code())
              .toList());
    }

    current.clearWhenItemRemoved();
    disableAutonomy();
    DnvVehicleCrewCoreCodec.DecodeResult decoded = DnvVehicleCrewCoreCodec.decode(coreTag);
    if (!decoded.accepted()) {
      remember(coreTag, catalogRevision, SyncStatus.INVALID_NBT);
      return new SyncResult(
          true,
          SyncStatus.INVALID_NBT,
          decoded.issues().stream()
              .map(issue -> issue.field() + ":" + issue.code().name())
              .toList());
    }
    if (template.isEmpty()) {
      remember(coreTag, catalogRevision, SyncStatus.CATALOG_REJECTED);
      return new SyncResult(
          true, SyncStatus.CATALOG_REJECTED, List.of("adapter.unavailable"));
    }

    DnvVehicleCrewProfile.Draft draft = decoded.draft().orElseThrow();
    DnvVehicleCrewCoreInstallation.InstallationResult installed = current.install(
        coreTag,
        template.orElseThrow(),
        new DnvVehicleCrewProfile.ValidationContext(draft.configuredBy(), false),
        catalogRevision,
        current.revision().coreRevision());
    if (!installed.accepted()) {
      remember(coreTag, catalogRevision, SyncStatus.PROFILE_REJECTED);
      return new SyncResult(
          true,
          SyncStatus.PROFILE_REJECTED,
          installed.issues().stream()
              .map(issue -> issue.field() + ":" + issue.code())
              .toList());
    }
    remember(coreTag, catalogRevision, SyncStatus.ACTIVE);
    synchronizeOccupants(template, current);
    synchronizeAutonomy(inspection, current);
    return new SyncResult(true, SyncStatus.ACTIVE, List.of());
  }

  public void synchronizeSeats(DnvSbwVehicleCrewCatalog catalog) {
    if (runtime == null || runtime.state() != DnvVehicleCrewRuntime.State.ACTIVE) {
      return;
    }
    Optional<DnvSbwVehicleCrewAdapter.Inspection> inspection = currentInspection(catalog);
    synchronizeOccupants(
        inspection.flatMap(DnvSbwVehicleCrewAdapter.Inspection::template), runtime);
    synchronizeAutonomy(inspection, runtime);
  }

  public void tickAutonomy() {
    if (!(vehicle.level() instanceof ServerLevel)) {
      disableAutonomy();
      return;
    }
    if (runtime == null || runtime.state() != DnvVehicleCrewRuntime.State.ACTIVE) {
      disableAutonomy();
      return;
    }
    Optional<UUID> configuredBy = runtime.configuredBy();
    Optional<UUID> coreId = runtime.configuredCoreId();
    Optional<DnvVehicleCrewProfile.FlightMode> flightMode = runtime.flightMode();
    if (configuredBy.isEmpty() || coreId.isEmpty() || flightMode.isEmpty()) {
      disableAutonomy();
      return;
    }
    if (actuators.isEmpty()) {
      disableTurretAutonomy();
    } else {
      if (autonomy == null) {
        autonomy = new DnvSbwCrewAutonomy(vehicle, DnvPlatformWeaponAccess.UNAVAILABLE);
        autonomy.configure(actuators);
        autonomy.setDiagnosticsEnabled(weaponDiagnosticsEnabled);
      }
      var orders = DnvEntityDesignationServices.observe(vehicle, this);
      autonomy.tick(runtime, orders);
    }
  }

  public Optional<DnvVehicleCrewRuntime> runtimeIfInitialized() {
    return Optional.ofNullable(runtime);
  }

  /** Low-frequency read-only weapon state; absence means the station runtime is not active. */
  public Optional<DnvSbwWeaponDiagnostics.Snapshot> weaponDiagnostics() {
    return autonomy == null ? Optional.empty() : Optional.of(autonomy.diagnostics());
  }

  Optional<DnvSbwWeaponDiagnostics.Snapshot> currentWeaponDiagnostics() {
    if (runtime == null) {
      return Optional.empty();
    }
    DnvVehicleCrewController.RuntimeRevision revision = runtime.revision();
    return weaponDiagnostics().filter(sample -> sample.matchesRevision(
        revision.coreRevision(), revision.controlRevision()));
  }

  boolean hasRouteActuator() {
    // The old AC-130 route executor is retired; legacy adapter metadata is not an active grant.
    return false;
  }

  boolean hasWeaponActuator() {
    return actuators.stream().anyMatch(actuator -> !actuator.weapons().isEmpty());
  }

  DnvEntityDesignationSession combatOrders() {
    return combatOrders;
  }

  public void setWeaponDiagnosticsEnabled(boolean enabled) {
    weaponDiagnosticsEnabled = enabled;
    if (autonomy != null) {
      autonomy.setDiagnosticsEnabled(enabled);
    }
  }

  public Optional<ManagedState> managedState() {
    if (runtime == null || runtime.state() != DnvVehicleCrewRuntime.State.ACTIVE) {
      return Optional.empty();
    }
    Optional<UUID> ownerId = runtime.configuredBy();
    Optional<UUID> coreId = runtime.configuredCoreId();
    Optional<DnvVehicleCrewProfile.FlightMode> flightMode = runtime.flightMode();
    if (ownerId.isEmpty() || coreId.isEmpty() || flightMode.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new ManagedState(
        ownerId.orElseThrow(), coreId.orElseThrow(),
        flightMode.orElseThrow().name(), runtime.state().name(), runtime.revision().coreRevision()));
  }

  public record ManagedState(
      UUID ownerId, UUID coreId, String flightMode, String runtimeState, long coreRevision) {
    public ManagedState {
      Objects.requireNonNull(ownerId, "ownerId");
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(flightMode, "flightMode");
      Objects.requireNonNull(runtimeState, "runtimeState");
      if (coreRevision < 0L) {
        throw new IllegalArgumentException("negative managed core revision");
      }
    }
  }

  /** Read-only projection for setup checks without leaking runtime policy types. */
  public boolean hasAiFlightMode() {
    return runtime != null
        && runtime.state() == DnvVehicleCrewRuntime.State.ACTIVE
        && runtime.flightMode()
            .filter(DnvVehicleCrewProfile.FlightMode::aiControlled)
            .isPresent();
  }

  public boolean hasConfiguredCore(UUID coreId) {
    return runtime != null
        && runtime.configuredCoreId().filter(coreId::equals).isPresent();
  }

  public void clearOnUnload() {
    if (runtime != null) {
      runtime.clearOnUnload();
    }
    disableAutonomy();
    resetObservedCore();
  }

  private DnvVehicleCrewRuntime runtime() {
    ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
    String vehicleType = typeId == null ? "unregistered:vehicle" : typeId.toString();
    UUID vehicleId = vehicle.getUUID();
    if (runtime == null
        || !vehicleId.equals(runtimeVehicleId)
        || !vehicleType.equals(runtimeVehicleType)) {
      if (runtime != null) {
        runtime.clearOnUnload();
      }
      runtime = new DnvVehicleCrewRuntime(vehicleId, vehicleType);
      runtimeVehicleId = vehicleId;
      runtimeVehicleType = vehicleType;
      resetObservedCore();
    }
    return runtime;
  }

  private List<ItemStack> findCoreItems() {
    List<ItemStack> cores = new ArrayList<>();
    for (int slot = 0; slot < vehicle.getInventory().getSlots(); slot++) {
      ItemStack stack = vehicle.getInventory().getStackInSlot(slot);
      if (!stack.isEmpty() && stack.is(ModItems.VEHICLE_CREW_CORE.get())) {
        cores.add(stack);
      }
    }
    return cores;
  }

  private Optional<DnvSbwVehicleCrewAdapter.Inspection> currentInspection(
      DnvSbwVehicleCrewCatalog catalog) {
    ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
    if (typeId == null) {
      return Optional.empty();
    }
    Optional<DnvSbwVehicleCrewAdapter.AllowlistEntry> allowlist =
        catalog.find(typeId.toString());
    if (allowlist.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(DnvSbwVehicleCrewAdapter.inspect(
        vehicle, allowlist.orElseThrow()));
  }

  private void synchronizeOccupants(
      Optional<DnvVehicleCrewProfile.AdapterTemplate> template,
      DnvVehicleCrewRuntime current) {
    if (template.isEmpty() || current.state() != DnvVehicleCrewRuntime.State.ACTIVE) {
      return;
    }
    Map<String, UUID> occupants = new LinkedHashMap<>();
    for (DnvVehicleCrewProfile.StationTemplate station : template.orElseThrow().stations()) {
      Entity occupant = vehicle.getNthEntity(station.seatIndex());
      if (occupant != null) {
        occupants.put(station.id(), occupant.getUUID());
      }
    }
    DnvVehicleCrewController.RuntimeRevision expected = current.revision();
    current.synchronizePhysicalOccupants(
        occupants,
        vehicle.level().getGameTime(),
        DnvVehicleCrewController.DEFAULT_AI_RESUME_DELAY_TICKS,
        expected);
  }

  private void remember(CompoundTag tag, long catalogRevision, SyncStatus status) {
    lastCoreTag = tag == null ? null : tag.copy();
    lastCatalogRevision = catalogRevision;
    lastStatus = status;
  }

  private void resetObservedCore() {
    disableAutonomy();
    lastCoreTag = null;
    lastCatalogRevision = -1L;
    lastStatus = SyncStatus.NO_CORE;
  }

  private void synchronizeAutonomy(
      Optional<DnvSbwVehicleCrewAdapter.Inspection> inspection,
      DnvVehicleCrewRuntime current) {
    if (current.state() != DnvVehicleCrewRuntime.State.ACTIVE
        || inspection.isEmpty()
        || !inspection.orElseThrow().accepted()) {
      disableAutonomy();
      return;
    }
    List<DnvSbwVehicleCrewAdapter.StationActuator> next =
        inspection.orElseThrow().actuators();
    if (!next.equals(actuators)) {
      actuators = next;
      if (autonomy != null) {
        autonomy.configure(actuators);
      }
    }
  }

  private void disableAutonomy() {
    disableTurretAutonomy();
  }

  private void disableTurretAutonomy() {
    combatOrders.synchronize(Optional.empty());
    actuators = List.of();
    if (autonomy != null) {
      autonomy.clear();
      autonomy = null;
    }
  }

  public record SyncResult(boolean corePresent, SyncStatus status, List<String> issues) {
    public SyncResult {
      Objects.requireNonNull(status, "status");
      issues = List.copyOf(issues);
    }
  }

  public enum SyncStatus {
    CLIENT_SIDE,
    NO_CORE,
    MULTIPLE_CORES,
    INVALID_NBT,
    CATALOG_REJECTED,
    PROFILE_REJECTED,
    ACTIVE
  }
}
