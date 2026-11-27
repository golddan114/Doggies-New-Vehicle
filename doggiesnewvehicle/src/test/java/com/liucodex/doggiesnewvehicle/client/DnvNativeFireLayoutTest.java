package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The retired indicator confirmation screen has no production layout or launcher. */
class DnvNativeFireLayoutTest {
  private static final Path CLIENT_SOURCE = Path.of(
      "src/main/java/com/liucodex/doggiesnewvehicle/client");

  @Test
  void retiredIndicatorConfirmationScreenAndDedicatedLayoutAreAbsent() {
    assertFalse(Files.exists(CLIENT_SOURCE.resolve("DnvIndicatorFireConfirmScreen.java")));
    assertFalse(Files.exists(CLIENT_SOURCE.resolve("DnvNativeFireLayout.java")));
  }

  @Test
  void clientEventsDoNotOpenTheRetiredScreen() throws Exception {
    String events = Files.readString(Path.of("src/artillery/java/com/liucodex/"
        + "doggiesnewvehicle/artillery/client/DnvArtilleryClientEvents.java"));
    String bindings = Files.readString(CLIENT_SOURCE.resolve("DnvClientNetworkBindings.java"));
    assertFalse(events.contains("ScreenEvent.Init.Post"));
    assertFalse(events.contains("DnvIndicatorFireConfirmScreen"));
    assertFalse(bindings.contains("DnvIndicatorFireConfirmScreen"));
    assertTrue(bindings.contains("DnvCombatIntentView"));
  }
}
