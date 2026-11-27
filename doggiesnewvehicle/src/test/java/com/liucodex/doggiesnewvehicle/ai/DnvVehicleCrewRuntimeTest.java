package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreCodec;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreInstallation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewRuntimeTest {
  private static final UUID VEHICLE = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID CORE = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000003");
  private static final UUID OTHER = UUID.fromString("40000000-0000-0000-0000-000000000004");
  private static final String VEHICLE_TYPE = "superbwarfare:mi_28";

  @Test
  void installAndSameCatalogRevisionAreIdempotent() {
    DnvVehicleCrewRuntime runtime = new DnvVehicleCrewRuntime(VEHICLE, VEHICLE_TYPE);

    DnvVehicleCrewCoreInstallation.InstallationResult installed = runtime.install(
        coreTag(CORE, OWNER, template(1L)),
        template(1L),
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false),
        7L,
        0L);
    DnvVehicleCrewRuntime.RevalidationResult unchanged =
        runtime.revalidate(Optional.of(template(1L)), 7L);

    assertTrue(installed.accepted());
    assertEquals(DnvVehicleCrewRuntime.State.ACTIVE, runtime.state());
    assertEquals(DnvVehicleCrewRuntime.Action.NO_CHANGE, unchanged.action());
    assertEquals(installed.revision(), unchanged.revision());
  }

  @Test
  void missingCatalogDisarmsButCorrectedReloadRearmsTheRetainedPolicy() {
    DnvVehicleCrewRuntime runtime = installedRuntime();

    DnvVehicleCrewRuntime.RevalidationResult disarmed =
        runtime.revalidate(Optional.empty(), 8L);

    assertEquals(DnvVehicleCrewRuntime.Action.DISARMED, disarmed.action());
    assertEquals(DnvVehicleCrewRuntime.State.CATALOG_REJECTED, runtime.state());
    assertEquals(CORE, runtime.configuredCoreId().orElseThrow());
    assertFalse(runtime.stationDecision("seat_1", 20L).mayFire());

    DnvVehicleCrewRuntime.RevalidationResult rearmed =
        runtime.revalidate(Optional.of(template(1L)), 9L);
    assertEquals(DnvVehicleCrewRuntime.Action.REARMED, rearmed.action());
    assertEquals(DnvVehicleCrewRuntime.State.ACTIVE, runtime.state());
    assertTrue(runtime.stationDecision("seat_1", 21L).mayFire());
  }

  @Test
  void staleAdapterRevisionFailsClosedAndReportsTheProfileField() {
    DnvVehicleCrewRuntime runtime = installedRuntime();

    DnvVehicleCrewRuntime.RevalidationResult result =
        runtime.revalidate(Optional.of(template(2L)), 8L);

    assertEquals(DnvVehicleCrewRuntime.Action.DISARMED, result.action());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.field().equals("adapterRevision")
            && issue.code().equals(DnvVehicleCrewProfile.IssueCode.STALE_ADAPTER.name())));
    assertFalse(runtime.stationDecision("seat_1", 30L).mayFire());
  }

  @Test
  void rejectedReplacementCannotOverwriteAnActiveCore() {
    DnvVehicleCrewRuntime runtime = installedRuntime();
    CompoundTag unauthorized = coreTag(
        UUID.fromString("50000000-0000-0000-0000-000000000005"),
        OTHER,
        template(1L));

    DnvVehicleCrewCoreInstallation.InstallationResult result = runtime.install(
        unauthorized,
        template(1L),
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false),
        7L,
        runtime.revision().coreRevision());

    assertFalse(result.accepted());
    assertEquals(CORE, runtime.configuredCoreId().orElseThrow());
    assertEquals(DnvVehicleCrewRuntime.State.ACTIVE, runtime.state());
  }

  @Test
  void unloadAndExactRemovalClearPortableAndArmedState() {
    DnvVehicleCrewRuntime runtime = installedRuntime();
    assertFalse(runtime.remove(OTHER, runtime.revision().coreRevision()).accepted());
    assertTrue(runtime.remove(CORE, runtime.revision().coreRevision()).accepted());
    assertEquals(DnvVehicleCrewRuntime.State.NO_CORE, runtime.state());

    runtime = installedRuntime();
    runtime.clearOnUnload();
    assertEquals(DnvVehicleCrewRuntime.State.NO_CORE, runtime.state());
    assertTrue(runtime.configuredCoreId().isEmpty());
    assertFalse(runtime.stationDecision("seat_1", 40L).mayFire());
  }

  private static DnvVehicleCrewRuntime installedRuntime() {
    DnvVehicleCrewRuntime runtime = new DnvVehicleCrewRuntime(VEHICLE, VEHICLE_TYPE);
    assertTrue(runtime.install(
        coreTag(CORE, OWNER, template(1L)),
        template(1L),
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false),
        7L,
        0L).accepted());
    return runtime;
  }

  private static CompoundTag coreTag(
      UUID coreId,
      UUID configuredBy,
      DnvVehicleCrewProfile.AdapterTemplate template) {
    DnvVehicleCrewProfile.Draft draft = new DnvVehicleCrewProfile.Draft(
        DnvVehicleCrewProfile.CURRENT_SCHEMA_VERSION,
        coreId,
        configuredBy,
        template.id(),
        template.revision(),
        VEHICLE_TYPE,
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        List.of(new DnvVehicleCrewProfile.StationDraft(
            "seat_1", DnvControlAuthority.WeaponMode.AUTO_ENGAGE)));
    DnvVehicleCrewProfile.Snapshot snapshot = DnvVehicleCrewProfile.validate(
        draft,
        template,
        new DnvVehicleCrewProfile.ValidationContext(configuredBy, false))
        .snapshot()
        .orElseThrow();
    return DnvVehicleCrewCoreCodec.encode(snapshot);
  }

  private static DnvVehicleCrewProfile.AdapterTemplate template(long revision) {
    return new DnvVehicleCrewProfile.AdapterTemplate(
        "sbw_0_8_9_1_hotfix:mi_28",
        revision,
        VEHICLE_TYPE,
        false,
        List.of(new DnvVehicleCrewProfile.StationTemplate(
            "seat_1",
            1,
            true,
            Set.of(
                DnvControlAuthority.WeaponMode.AUTO_ENGAGE,
                DnvControlAuthority.WeaponMode.TRACK_ONLY,
                DnvControlAuthority.WeaponMode.MANUAL_PREFERRED,
                DnvControlAuthority.WeaponMode.MANUAL_ONLY,
                DnvControlAuthority.WeaponMode.HOLD_FIRE))));
  }
}
