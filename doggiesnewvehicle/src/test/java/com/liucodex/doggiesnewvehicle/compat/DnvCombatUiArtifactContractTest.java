package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvCombatUiArtifactContractTest {
  private static final Path SOURCE = Path.of("src", "main", "java");

  @Test
  void separateChannelKeepsCurrentCombatAndTacticalRouteProtocols() throws IOException {
    assertTrue(source("compat/DnvCombatUiNetwork.java").contains("PROTOCOL = \"2\""));
    assertFalse(Files.exists(SOURCE.resolve("com/liucodex/doggiesnewvehicle/compat/DnvCombatSalvoUiNetwork.java")));
    assertFalse(source("compat/DnvCombatUiNetwork.java").contains("DnvCombatSalvoUiNetwork.initialize()"));
    assertTrue(source("compat/DnvManagedVehicleNetwork.java").contains("PROTOCOL = \"1\""));
    assertTrue(Files.readString(Path.of("src/platform/java/com/liucodex/doggiesnewvehicle/compat/"
        + "DnvTacticalRouteNetwork.java")).contains("PROTOCOL = \"3\""));
    assertTrue(source("DoggiesNewVehicle.java").contains("DnvCombatUiNetwork.initialize()"));
  }

  @Test
  void clientDraftCannotImportServerCombatAuthority() throws IOException {
    String panel = source("client/DnvCombatOrderPanel.java");
    assertFalse(panel.contains(".compat."));
    assertTrue(panel.contains("DnvCombatUiClientGateway.send"));
    assertTrue(panel.contains("DnvCombatUi.Action.DESIGNATE_ENTITY"));
    assertTrue(panel.contains("DnvCombatUi.Action.CANCEL"));
    assertFalse(panel.contains("DnvCombatSalvoUi"));
    assertFalse(panel.contains("DnvCombatUi.Action.FIRE_POINT"));
    assertFalse(panel.contains("\"seat_3\""));
    assertTrue(panel.contains("preview.expectedRevision()"));
    assertTrue(panel.contains("cursor = null"));
    assertFalse(source("mission/DnvCombatIntentView.java").contains("dnv$acceptCombatSalvo"));
    assertFalse(source("client/DnvClientNetworkBindings.java")
        .contains("DnvCombatSalvoUiClientProjection.install"));
  }

  @Test
  void retiredCommandsDoNotExposePointOrIndicatorWhileDesignationAndCancelRemain()
      throws IOException {
    String commands = source("compat/DnvCombatCommands.java");
    assertFalse(commands.contains("Commands.literal(\"point\")"));
    assertFalse(commands.contains("Commands.literal(\"indicator\")"));
    assertTrue(commands.contains("Commands.literal(\"designate\")"));
    assertTrue(commands.contains("Commands.literal(\"cancel\")"));
  }

  @Test
  void retiredSalvoHasNoProtocolOrSessionState() throws IOException {
    String session = source("core/DnvEntityDesignationSession.java");
    String attachment = source("compat/DnvVehicleCrewAttachment.java");
    assertFalse(Files.exists(SOURCE.resolve("com/liucodex/doggiesnewvehicle/compat/DnvCombatSalvoUiServices.java")));
    assertFalse(session.contains("record SalvoProgress"));
    assertFalse(session.contains("claimSalvoAttempt"));
    assertFalse(attachment.contains("completeSalvoAttempt"));
    assertFalse(attachment.contains("submitDeferredShot("));
    assertFalse(attachment.contains("shotReceipt()"));
    assertFalse(session.contains("SavedData"));
  }

  @Test
  void serverPathKeepsDesignationWithoutRetiredFireMissionBranches() throws IOException {
    String services = source("compat/DnvCombatUiServices.java");
    String designation = source("compat/DnvEntityDesignationServices.java");
    String autonomy = source("compat/DnvSbwCrewAutonomy.java");
    String runtime = source("compat/DnvSbwStationRuntime.java");
    String attachment = source("compat/DnvVehicleCrewAttachment.java");
    assertTrue(services.contains("DnvEntityDesignationServices.mayOperate"));
    assertTrue(services.contains("DnvEntityDesignationServices.submitEntity"));
    assertFalse(services.contains("DnvEntityDesignationServices.submitPoint"));
    assertFalse(services.contains("\"FIRE_MISSION_RETIRED\""));
    assertTrue(services.contains("DnvEntityDesignationServices.cancel"));
    assertTrue(services.contains("\"NO_ACTIVE_INTENT\""));
    assertFalse(designation.contains("DnvCombatOrderService.Scope"));
    assertFalse(designation.contains("Scope.weaponGroup(\"seat_3\", \"M102\")"));
    assertFalse(designation.contains("submitPoint("));
    assertFalse(designation.contains("submitArea("));
    assertFalse(autonomy.contains("orders.fireMissionOrder().isPresent()"));
    assertFalse(autonomy.contains("tickPoint("));
    assertTrue(runtime.contains("Math.max(0, vehicle.getAmmo(gun))"));
    assertTrue(runtime.contains("gun.canShoot(vehicle.getAmmoSupplier())"));
    assertTrue(autonomy.contains("DnvCombatCoordinator.fireAuthorized"));
    assertFalse(attachment.contains("DnvSbwShotExecution"));
    assertTrue(Files.readString(Path.of("src/artillery/java/com/liucodex/doggiesnewvehicle/compat/"
        + "DnvSbwTurretExecution.java"), StandardCharsets.UTF_8).contains("shots.submitTurret("));
  }

  @Test
  void uiProjectsRecoveredOrderStateWithoutTransientRetiredShotReceipt() throws IOException {
    String services = source("compat/DnvCombatUiServices.java");
    assertFalse(services.contains("shotReceipt()"));
    assertFalse(services.contains("matchingShot("));
    assertTrue(services.contains("view.executionReason()"));
    assertTrue(services.contains("view.state()"));
    assertTrue(services.contains("view.displayedOrder()"));
  }

  private static String source(String relative) throws IOException {
    return Files.readString(SOURCE.resolve(
        "com/liucodex/doggiesnewvehicle/" + relative), StandardCharsets.UTF_8);
  }
}
