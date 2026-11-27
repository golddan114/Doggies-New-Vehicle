package com.liucodex.doggiesnewvehicle.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewCoreItemTest {
  private static final UUID CORE = UUID.fromString("a0000000-0000-0000-0000-000000000001");
  private static final UUID OWNER = UUID.fromString("b0000000-0000-0000-0000-000000000002");

  @Test
  void defaultBindingArmsOnlyAiCapableStationsAndKeepsPlayerFlight() {
    DnvVehicleCrewProfile.AdapterTemplate template = template();

    DnvVehicleCrewProfile.Draft draft =
        DnvVehicleCrewProfile.createDefaultDraft(CORE, OWNER, template);

    assertEquals(DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED, draft.flightMode());
    assertEquals(
        List.of(
            new DnvVehicleCrewProfile.StationDraft(
                "seat_0", DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
            new DnvVehicleCrewProfile.StationDraft(
                "seat_2", DnvControlAuthority.WeaponMode.MANUAL_ONLY)),
        draft.stations());
    assertTrue(DnvVehicleCrewProfile.validate(
        draft,
        template,
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false)).accepted());
  }

  private static DnvVehicleCrewProfile.AdapterTemplate template() {
    return new DnvVehicleCrewProfile.AdapterTemplate(
        "sbw_0_8_9_1_hotfix:test",
        1L,
        "superbwarfare:test",
        false,
        List.of(
            new DnvVehicleCrewProfile.StationTemplate(
                "seat_0",
                0,
                true,
                Set.of(
                    DnvControlAuthority.WeaponMode.AUTO_ENGAGE,
                    DnvControlAuthority.WeaponMode.MANUAL_ONLY)),
            new DnvVehicleCrewProfile.StationTemplate(
                "seat_2",
                2,
                false,
                Set.of(
                    DnvControlAuthority.WeaponMode.MANUAL_ONLY,
                    DnvControlAuthority.WeaponMode.HOLD_FIRE))));
  }
}
