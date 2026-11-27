package com.liucodex.doggiesnewvehicle.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewCoreInstallationTest {
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-000000000041");
  private static final UUID CORE = UUID.fromString("00000000-0000-0000-0000-000000000042");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000043");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000044");
  private static final String TYPE = "superbwarfare:test_vehicle";

  @Test
  void validItemInstallsOnlyAfterDecodeAndCurrentTemplateValidation() {
    DnvVehicleCrewController controller = controller();
    DnvVehicleCrewCoreInstallation.InstallationResult result =
        DnvVehicleCrewCoreInstallation.install(
            DnvVehicleCrewCoreCodec.encode(snapshot()),
            template(),
            new DnvVehicleCrewProfile.ValidationContext(OWNER, false),
            controller,
            0L);

    assertTrue(result.accepted());
    assertEquals(CORE, result.coreId().orElseThrow());
    assertEquals(CORE, controller.installedCoreId().orElseThrow());
    assertTrue(controller.stationDecision("nose", 0L).mayFire());
  }

  @Test
  void malformedNbtDoesNotPartiallyInstallAController() {
    DnvVehicleCrewController controller = controller();
    DnvVehicleCrewCoreInstallation.InstallationResult result =
        DnvVehicleCrewCoreInstallation.install(
            new CompoundTag(),
            template(),
            new DnvVehicleCrewProfile.ValidationContext(OWNER, false),
            controller,
            0L);

    assertFalse(result.accepted());
    assertEquals(DnvVehicleCrewCoreInstallation.Stage.DECODE,
        result.issues().get(0).stage());
    assertTrue(controller.installedCoreId().isEmpty());
  }

  @Test
  void callerAndStaleTemplateFailuresRemainAtTheProfileBoundary() {
    DnvVehicleCrewController controller = controller();
    DnvVehicleCrewCoreInstallation.InstallationResult caller =
        DnvVehicleCrewCoreInstallation.install(
            DnvVehicleCrewCoreCodec.encode(snapshot()),
            template(),
            new DnvVehicleCrewProfile.ValidationContext(OTHER, false),
            controller,
            0L);
    assertFalse(caller.accepted());
    assertTrue(caller.issues().stream().allMatch(issue ->
        issue.stage() == DnvVehicleCrewCoreInstallation.Stage.PROFILE));
    assertTrue(controller.installedCoreId().isEmpty());

    DnvVehicleCrewProfile.AdapterTemplate stale = new DnvVehicleCrewProfile.AdapterTemplate(
        "dnv:test", 8L, TYPE, false, template().stations());
    DnvVehicleCrewCoreInstallation.InstallationResult revision =
        DnvVehicleCrewCoreInstallation.install(
            DnvVehicleCrewCoreCodec.encode(snapshot()),
            stale,
            new DnvVehicleCrewProfile.ValidationContext(OWNER, false),
            controller,
            0L);
    assertFalse(revision.accepted());
    assertTrue(revision.issues().stream().anyMatch(issue ->
        issue.code().equals(DnvVehicleCrewProfile.IssueCode.STALE_ADAPTER.name())));
  }

  @Test
  void staleCoreRevisionCannotReplaceAnInstalledPolicy() {
    DnvVehicleCrewController controller = controller();
    CompoundTag item = DnvVehicleCrewCoreCodec.encode(snapshot());
    assertTrue(DnvVehicleCrewCoreInstallation.install(
        item, template(), context(), controller, 0L).accepted());

    DnvVehicleCrewCoreInstallation.InstallationResult stale =
        DnvVehicleCrewCoreInstallation.install(
            item, template(), context(), controller, 0L);
    assertFalse(stale.accepted());
    assertEquals(DnvVehicleCrewCoreInstallation.Stage.CONTROLLER,
        stale.issues().get(0).stage());
    assertEquals(DnvVehicleCrewController.RejectReason.STALE_CORE_REVISION.name(),
        stale.issues().get(0).code());
    assertEquals(CORE, controller.installedCoreId().orElseThrow());
  }

  private static DnvVehicleCrewController controller() {
    return new DnvVehicleCrewController(VEHICLE, TYPE);
  }

  private static DnvVehicleCrewProfile.ValidationContext context() {
    return new DnvVehicleCrewProfile.ValidationContext(OWNER, false);
  }

  private static DnvVehicleCrewProfile.AdapterTemplate template() {
    return new DnvVehicleCrewProfile.AdapterTemplate(
        "dnv:test",
        7L,
        TYPE,
        false,
        List.of(new DnvVehicleCrewProfile.StationTemplate(
            "nose",
            0,
            true,
            Set.of(DnvControlAuthority.WeaponMode.AUTO_ENGAGE))));
  }

  private static DnvVehicleCrewProfile.Snapshot snapshot() {
    Map<String, DnvVehicleCrewProfile.StationPolicy> stations = new LinkedHashMap<>();
    stations.put("nose", new DnvVehicleCrewProfile.StationPolicy(
        "nose", 0, true, DnvControlAuthority.WeaponMode.AUTO_ENGAGE));
    return new DnvVehicleCrewProfile.Snapshot(
        1,
        CORE,
        OWNER,
        "dnv:test",
        7L,
        TYPE,
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        stations);
  }
}
