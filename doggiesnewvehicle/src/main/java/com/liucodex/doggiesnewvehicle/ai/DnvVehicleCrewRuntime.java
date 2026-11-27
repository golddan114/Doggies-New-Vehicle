package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreInstallation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-vehicle lifecycle owner separating portable core configuration from currently armed state.
 * It contains no world/entity/player references and performs no scans.
 */
public final class DnvVehicleCrewRuntime {
  private final DnvVehicleCrewController controller;
  private DnvVehicleCrewProfile.Snapshot configuredCore;
  private long validatedCatalogRevision = -1L;
  private State state = State.NO_CORE;

  public DnvVehicleCrewRuntime(UUID vehicleId, String vehicleTypeId) {
    controller = new DnvVehicleCrewController(vehicleId, vehicleTypeId);
  }

  public State state() {
    return state;
  }

  public long validatedCatalogRevision() {
    return validatedCatalogRevision;
  }

  public DnvVehicleCrewController.RuntimeRevision revision() {
    return controller.revision();
  }

  public Optional<UUID> configuredCoreId() {
    return configuredCore == null
        ? Optional.empty()
        : Optional.of(configuredCore.coreId());
  }

  public Optional<UUID> configuredBy() {
    return configuredCore == null
        ? Optional.empty()
        : Optional.of(configuredCore.configuredBy());
  }

  public Optional<DnvVehicleCrewProfile.FlightMode> flightMode() {
    return configuredCore == null
        ? Optional.empty()
        : Optional.of(configuredCore.flightMode());
  }

  public DnvVehicleCrewCoreInstallation.InstallationResult install(
      CompoundTag itemTag,
      DnvVehicleCrewProfile.AdapterTemplate currentTemplate,
      DnvVehicleCrewProfile.ValidationContext context,
      long catalogRevision,
      long expectedCoreRevision) {
    requireCatalogRevision(catalogRevision);
    DnvVehicleCrewCoreInstallation.InstallationResult result =
        DnvVehicleCrewCoreInstallation.install(
            itemTag, currentTemplate, context, controller, expectedCoreRevision);
    if (result.accepted()) {
      configuredCore = result.snapshot().orElseThrow();
      validatedCatalogRevision = catalogRevision;
      state = State.ACTIVE;
    }
    return result;
  }

  /**
   * Revalidates a retained item policy after data-pack revision changes. Rejection disarms the
   * controller but keeps the portable configuration so a corrected reload can recover it.
   */
  public RevalidationResult revalidate(
      Optional<DnvVehicleCrewProfile.AdapterTemplate> currentTemplate,
      long catalogRevision) {
    Objects.requireNonNull(currentTemplate, "currentTemplate");
    requireCatalogRevision(catalogRevision);
    if (configuredCore == null) {
      state = State.NO_CORE;
      validatedCatalogRevision = catalogRevision;
      return new RevalidationResult(
          Action.NO_CORE, state, List.of(), controller.revision());
    }
    if (state == State.ACTIVE && validatedCatalogRevision == catalogRevision) {
      return new RevalidationResult(
          Action.NO_CHANGE, state, List.of(), controller.revision());
    }
    if (currentTemplate.isEmpty()) {
      disarm(catalogRevision);
      return new RevalidationResult(
          Action.DISARMED,
          state,
          List.of(new RuntimeIssue(
              "adapterId", DnvVehicleCrewProfile.IssueCode.UNKNOWN_ADAPTER.name())),
          controller.revision());
    }

    DnvVehicleCrewProfile.ValidationResult validation = DnvVehicleCrewProfile.validate(
        draft(configuredCore),
        currentTemplate.orElseThrow(),
        new DnvVehicleCrewProfile.ValidationContext(
            configuredCore.configuredBy(), false));
    if (!validation.accepted()) {
      disarm(catalogRevision);
      return new RevalidationResult(
          Action.DISARMED,
          state,
          validation.issues().stream()
              .map(issue -> new RuntimeIssue(issue.field(), issue.code().name()))
              .toList(),
          controller.revision());
    }

    DnvVehicleCrewProfile.Snapshot validated = validation.snapshot().orElseThrow();
    DnvVehicleCrewController.MutationResult installed =
        controller.installCore(validated, controller.coreRevision());
    if (!installed.accepted()) {
      disarm(catalogRevision);
      return new RevalidationResult(
          Action.DISARMED,
          state,
          List.of(new RuntimeIssue("core", installed.reason().name())),
          controller.revision());
    }
    configuredCore = validated;
    validatedCatalogRevision = catalogRevision;
    state = State.ACTIVE;
    return new RevalidationResult(
        Action.REARMED, state, List.of(), controller.revision());
  }

  public DnvVehicleCrewController.MutationResult synchronizePhysicalOccupants(
      Map<String, UUID> occupants,
      long serverTick,
      int aiResumeDelayTicks,
      DnvVehicleCrewController.RuntimeRevision expectedRevision) {
    return controller.synchronizePhysicalOccupants(
        occupants, serverTick, aiResumeDelayTicks, expectedRevision);
  }

  public DnvVehicleCrewController.StationDecision stationDecision(
      String stationId, long serverTick) {
    return controller.stationDecision(stationId, serverTick);
  }

  public DnvVehicleCrewController.MutationResult remove(
      UUID coreId, long expectedCoreRevision) {
    DnvVehicleCrewController.MutationResult result =
        controller.removeCore(coreId, expectedCoreRevision);
    if (result.accepted()) {
      configuredCore = null;
      validatedCatalogRevision = -1L;
      state = State.NO_CORE;
    }
    return result;
  }

  /** Inventory removal or replacement boundary; unlike catalog rejection no policy is retained. */
  public void clearWhenItemRemoved() {
    controller.clearRuntime();
    configuredCore = null;
    validatedCatalogRevision = -1L;
    state = State.NO_CORE;
  }

  /** Entity unload/server stop boundary; the persisted item can install into a new runtime. */
  public void clearOnUnload() {
    clearWhenItemRemoved();
  }

  private void disarm(long catalogRevision) {
    controller.clearRuntime();
    validatedCatalogRevision = catalogRevision;
    state = State.CATALOG_REJECTED;
  }

  private static DnvVehicleCrewProfile.Draft draft(
      DnvVehicleCrewProfile.Snapshot snapshot) {
    List<DnvVehicleCrewProfile.StationDraft> stations = snapshot.stations().values()
        .stream()
        .map(station -> new DnvVehicleCrewProfile.StationDraft(
            station.id(), station.mode()))
        .toList();
    return new DnvVehicleCrewProfile.Draft(
        snapshot.schemaVersion(),
        snapshot.coreId(),
        snapshot.configuredBy(),
        snapshot.adapterId(),
        snapshot.adapterRevision(),
        snapshot.vehicleTypeId(),
        snapshot.flightMode(),
        stations);
  }

  private static void requireCatalogRevision(long revision) {
    if (revision < 0L) {
      throw new IllegalArgumentException("catalog revision must not be negative");
    }
  }

  public record RevalidationResult(
      Action action,
      State state,
      List<RuntimeIssue> issues,
      DnvVehicleCrewController.RuntimeRevision revision) {
    public RevalidationResult {
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(state, "state");
      issues = List.copyOf(issues);
      Objects.requireNonNull(revision, "revision");
    }
  }

  public record RuntimeIssue(String field, String code) {
    public RuntimeIssue {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(code, "code");
    }
  }

  public enum State {
    NO_CORE,
    ACTIVE,
    CATALOG_REJECTED
  }

  public enum Action {
    NO_CORE,
    NO_CHANGE,
    REARMED,
    DISARMED
  }
}
