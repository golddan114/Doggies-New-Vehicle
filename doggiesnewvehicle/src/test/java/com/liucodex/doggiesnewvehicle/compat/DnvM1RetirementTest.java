package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the compiled/runtime resource boundary, including stale build output. */
class DnvM1RetirementTest {
  @Test void runtimeContainsNoRetiredManagementControlOrConnector() {
    for (String type : List.of(
        "api/rws/DnvRemoteWeaponMenu", "api/rws/DnvRemoteWeaponUi", "api/rws/DnvRemoteWeaponSeatView",
        "client/DnvRemoteWeaponScreen", "client/DnvRemoteWeaponCamera", "client/DnvRemoteWeaponMouse",
        "client/DnvRemoteWeaponAimInput", "client/DnvRemoteWeaponUiState", "client/DnvM1Optics",
        "client/DnvRemoteWeaponLayout", "client/DnvRemoteWeaponCameraState",
        "client/DnvRemoteWeaponSeatedView", "client/DnvRemoteWeaponSeatedViewState", "client/DnvRemoteWeaponSeatRenderer",
        "compat/DnvRemoteWeaponUiNetwork", "compat/DnvRemoteWeaponUiCodec",
        "compat/DnvRemoteWeaponManagement", "compat/DnvRemoteWeaponConnectorItem",
        "compat/DnvRemoteWeaponSeat", "compat/DnvRemoteWeaponSessions",
        "core/DnvRemoteWeaponSession", "core/DnvRemoteWeaponMechanism")) {
      assertNull(getClass().getResource("/com/liucodex/doggiesnewvehicle/" + type + ".class"), type);
    }
    assertNotNull(getClass().getResource("/com/liucodex/doggiesnewvehicle/compat/DnvM1RemoteCrew.class"));
    assertNotNull(getClass().getResource("/com/liucodex/doggiesnewvehicle/compat/DnvM1CrewSafety.class"));
  }

  @Test void nativeStationMessagesCoverCurrentReasonsWithoutOldScreenNamespace() throws Exception {
    for (String language : List.of("zh_cn", "en_us")) {
      try (var input = getClass().getResourceAsStream("/assets/doggiesnewvehicle/lang/" + language + ".json")) {
        assertNotNull(input);
        var messages = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var reason : DnvRemoteWeaponLinks.Reason.values()) {
          assertTrue(messages.has("message.doggiesnewvehicle.remote_station.reason." + reason), reason.toString());
        }
        for (String reason : List.of("ALREADY_RIDING", "SEAT_BLOCKED")) {
          assertTrue(messages.has("message.doggiesnewvehicle.remote_station.reason." + reason), reason);
        }
        assertFalse(messages.keySet().stream().anyMatch(key -> key.startsWith("screen.doggiesnewvehicle.rws.")
            || key.startsWith("message.doggiesnewvehicle.connector.")));
      }
    }
  }
}
