package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewRuntime;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreCodec;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreInstallation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Retired-carrier boundary: old portable data remains readable, no DNV live adapter ships. */
class DnvAc130HContractTest {
  private static final String NATIVE_TYPE = "superbwarfare:ac_130h";
  private static final String LEGACY_TYPE = "dragonrise_reforge:ac130";
  private static final String RESOURCE_ROOT =
      "data/doggiesnewvehicle/dnv/vehicle_crew_adapters/sbw_0_8_9_1/";

  @Test void liveInspectorDisarmsBothRetiredCarriersBeforeReadingStations() throws Exception {
    String adapter = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwVehicleCrewAdapter.java"),
        StandardCharsets.UTF_8);
    assertTrue(adapter.contains("retiredCarrier(typeId.toString())"));
    assertTrue(adapter.contains("\"superbwarfare:ac_130h\".equals(typeId)"));
    assertTrue(adapter.contains("\"dragonrise_reforge:ac130\".equals(typeId)"));
    assertTrue(adapter.contains("Inspection.rejected(0L, IssueCode.RETIRED_VEHICLE_TYPE)"));
    assertTrue(adapter.indexOf("IssueCode.RETIRED_VEHICLE_TYPE") < adapter.indexOf("vehicle.computed().seats()"));
    assertTrue(adapter.indexOf("retiredCarrier(layout.vehicleTypeId())") < adapter.indexOf("layout.seats()"));
    String attachment = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvVehicleCrewAttachment.java"),
        StandardCharsets.UTF_8);
    assertTrue(attachment.contains("DnvSbwVehicleCrewAdapter.inspect("));
    assertTrue(attachment.contains("current.revalidate(template, catalogRevision)"));
    assertTrue(attachment.contains("current.state() != DnvVehicleCrewRuntime.State.ACTIVE"));
  }

  @Test void sharedCrewAndTargetDecisionsNoLongerBindTheRetiredPlatform() throws Exception {
    String attachment = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvVehicleCrewAttachment.java"),
        StandardCharsets.UTF_8);
    String engagement = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwStationEngagementAdapter.java"),
        StandardCharsets.UTF_8);
    assertTrue(attachment.contains("DnvPlatformWeaponAccess.UNAVAILABLE"));
    assertFalse(Files.exists(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvAc130Platform.java")));
    assertFalse(Files.exists(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwAerialRouteAutopilot.java")));
    assertFalse(engagement.contains("DnvAc130Platform.TYPE"));
    assertFalse(engagement.contains("nativeFrame"));
    assertTrue(engagement.contains("targets.add(target(candidate, ambientEligible))"));
  }

  @Test void retiredSideGunWorldProbeIsAbsentButSharedStationStateRemains() throws Exception {
    String station = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwStationRuntime.java"),
        StandardCharsets.UTF_8);
    assertFalse(station.contains("class SideGuns"));
    assertFalse(station.contains("record WorldProbe"));
    assertTrue(station.contains("class WeaponState"));
    assertTrue(station.contains("class Readiness"));
    assertNull(getClass().getResource("/com/liucodex/doggiesnewvehicle/compat/DnvSbwStationRuntime$SideGuns.class"));
    assertNull(getClass().getResource("/com/liucodex/doggiesnewvehicle/compat/DnvSbwStationRuntime$SideGuns$WorldProbe.class"));
  }

  @Test void packagedCatalogHasNoRetiredCarrierDefinition() {
    assertNull(getClass().getClassLoader().getResource(RESOURCE_ROOT + "ac_130h.json"));
    assertNull(getClass().getClassLoader().getResource(RESOURCE_ROOT + "dragonrise_reforge_ac130.json"));
    assertNotNull(getClass().getClassLoader().getResource(RESOURCE_ROOT + "bradley.json"));
  }

  @Test void upstreamSbwStillOwnsItsOriginalAircraftData() throws Exception {
    try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      assertNotNull(zip.getEntry("data/superbwarfare/sbw/vehicles/ac_130h.json"));
      assertNotNull(zip.getEntry("assets/superbwarfare/models/bedrock/vehicle/ac_130h.geo.json"));
    }
  }

  @Test void legacyCoreKeepsIdentityAndCannotInstallInTheNativeAircraft() {
    UUID core = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    var old = new DnvVehicleCrewProfile.Snapshot(1, core, owner,
        "doggiesnewvehicle:dragonrise_reforge_ac130_v1", 1, LEGACY_TYPE,
        DnvVehicleCrewProfile.FlightMode.AI_PREFERRED,
        Map.of("seat_1", new DnvVehicleCrewProfile.StationPolicy(
            "seat_1", 1, true, DnvControlAuthority.WeaponMode.AUTO_ENGAGE)));
    var tag = DnvVehicleCrewCoreCodec.encode(old);
    var preserved = tag.copy();
    var controller = new com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController(UUID.randomUUID(), NATIVE_TYPE);
    var installed = DnvVehicleCrewCoreInstallation.install(tag, nativeTemplate(),
        new DnvVehicleCrewProfile.ValidationContext(owner, false), controller, 0);
    assertFalse(installed.accepted());
    assertTrue(controller.installedCoreId().isEmpty());
    assertEquals(preserved, tag);
    var decoded = DnvVehicleCrewCoreCodec.decode(tag).draft().orElseThrow();
    assertEquals(core, decoded.coreId());
    assertEquals(LEGACY_TYPE, decoded.vehicleTypeId());
  }

  @Test void nativeCoreTagSurvivesCatalogRetirementWhileRuntimeDisarms() {
    var template = nativeTemplate();
    UUID owner = UUID.randomUUID();
    UUID core = UUID.randomUUID();
    var draft = DnvVehicleCrewProfile.createDefaultDraft(core, owner, template);
    var snapshot = DnvVehicleCrewProfile.validate(draft, template,
        new DnvVehicleCrewProfile.ValidationContext(owner, false)).snapshot().orElseThrow();
    var tag = DnvVehicleCrewCoreCodec.encode(snapshot);
    var preserved = tag.copy();
    var runtime = new DnvVehicleCrewRuntime(UUID.randomUUID(), NATIVE_TYPE);
    assertTrue(runtime.install(tag, template,
        new DnvVehicleCrewProfile.ValidationContext(owner, false), 0, 0).accepted());
    var result = runtime.revalidate(java.util.Optional.empty(), 1);
    assertEquals(DnvVehicleCrewRuntime.Action.DISARMED, result.action());
    assertEquals(DnvVehicleCrewRuntime.State.CATALOG_REJECTED, runtime.state());
    assertEquals(core, runtime.configuredCoreId().orElseThrow());
    assertFalse(runtime.stationDecision("seat_3", 20).mayFire());
    assertEquals(preserved, tag);
    assertEquals(core, DnvVehicleCrewCoreCodec.decode(tag).draft().orElseThrow().coreId());
  }

  private static DnvVehicleCrewProfile.AdapterTemplate nativeTemplate() {
    Set<DnvControlAuthority.WeaponMode> modes = Set.of(
        DnvControlAuthority.WeaponMode.AUTO_ENGAGE,
        DnvControlAuthority.WeaponMode.MANUAL_ONLY);
    return new DnvVehicleCrewProfile.AdapterTemplate(
        "sbw_0_8_9_1_hotfix:ac_130h", 2, NATIVE_TYPE, true,
        List.of(new DnvVehicleCrewProfile.StationTemplate("seat_1", 1, true, modes),
            new DnvVehicleCrewProfile.StationTemplate("seat_2", 2, true, modes),
            new DnvVehicleCrewProfile.StationTemplate("seat_3", 3, true, modes)));
  }
}
