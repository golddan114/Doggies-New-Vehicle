package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewLifecycleArtifactContractTest {
  private static final Path ROOT = Path.of(System.getProperty("user.dir"));

  @Test
  void capabilityHooksTrackOnlyCoreVehiclesAndClearEveryLifecycleBoundary()
      throws IOException {
    String services = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewServices.java");
    String attachment = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewAttachment.java");

    assertTrue(services.contains("AttachCapabilitiesEvent<Entity>"));
    assertTrue(services.contains("EntityJoinLevelEvent"));
    assertTrue(services.contains("EntityLeaveLevelEvent"));
    assertTrue(services.contains("EntityMountEvent"));
    assertTrue(services.contains("DRIFT_CORRECTION_INTERVAL_TICKS = 20"));
    assertTrue(services.contains("tickTrackedAutonomy"));
    assertTrue(services.contains("result.corePresent()"));
    assertTrue(services.contains("ServerStoppedEvent"));
    assertFalse(services.contains("getAllEntities"));
    assertTrue(attachment.contains("findCoreItems()"));
    assertTrue(attachment.contains("cores.size() > 1"));
    assertTrue(attachment.contains("clearWhenItemRemoved"));
    assertTrue(attachment.contains("synchronizePhysicalOccupants"));
    assertTrue(attachment.contains("tickAutonomy"));
  }

  @Test
  void exactInventoryAndSeatMixinsArePackagedAndRequired() throws IOException {
    String mixin = source("src/main/resources/mixins.doggiesnewvehicle.json");
    String build = source("build.gradle");
    String inventoryHook = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/mixin/"
            + "VehicleContainerHandlerMixin.java");
    String seatHook = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/mixin/"
            + "VehicleSeatChangeMixin.java");

    assertTrue(mixin.contains("\"required\": true"));
    assertTrue(mixin.contains("VehicleContainerHandlerMixin"));
    assertTrue(mixin.contains("VehicleSeatChangeMixin"));
    assertTrue(build.contains("'MixinConfigs'"));
    assertTrue(inventoryHook.contains("method = \"onContentsChanged\""));
    assertTrue(inventoryHook.contains("remap = false"));
    assertTrue(seatHook.contains("method = \"changeSeat\""));
    assertTrue(seatHook.contains("callback.getReturnValueZ()"));
  }

  @Test
  void registeredCoreHasModelTranslationsAndNoEmptyGlobalManager() throws IOException {
    String items = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/init/ModItems.java");
    String model = source(
        "src/main/resources/assets/doggiesnewvehicle/models/item/"
            + "vehicle_crew_core.json");
    String english = source(
        "src/main/resources/assets/doggiesnewvehicle/lang/en_us.json");
    String chinese = source(
        "src/main/resources/assets/doggiesnewvehicle/lang/zh_cn.json");

    assertTrue(items.contains("VEHICLE_CREW_CORE"));
    assertTrue(items.contains("DnvVehicleCrewCoreItem"));
    assertTrue(model.contains("doggiesnewvehicle:item/dnv_icon"));
    assertTrue(english.contains("item.doggiesnewvehicle.vehicle_crew_core"));
    assertTrue(chinese.contains("item.doggiesnewvehicle.vehicle_crew_core"));
  }

  @Test
  void autonomousFireUsesBoundedTargetingAndDedicatedClientVisuals() throws IOException {
    String autonomy = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwCrewAutonomy.java");
    String stationRuntime = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwStationRuntime.java");
    String aimRuntime = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwAimChannelRuntime.java");
    String network = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleAutonomousFireNetwork.java");
    String mod = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/DoggiesNewVehicle.java");

    assertTrue(autonomy.contains("new DnvTargetCandidateCache"));
    assertTrue(stationRuntime.contains("DnvTargetLineOfSight.clearAimPoint"));
    assertTrue(stationRuntime.contains("hasClearEntityCorridor"));
    assertTrue(stationRuntime.contains("new AABB(from, to)"));
    assertTrue(autonomy.contains("decision.owner() != DnvControlAuthority.ControlOwner.AI"));
    assertTrue(aimRuntime.contains("DnvPhysicalAimChannelAuthority"));
    assertTrue(aimRuntime.contains("vehicle.getMouseMoveSpeedX()"));
    assertTrue(aimRuntime.contains("vehicle.fireInputDown()"));
    assertTrue(autonomy.contains(
        "vehicle.vehicleWeaponRpm(fireFrame.weaponName())"));
    assertFalse(autonomy.contains("getAllEntities"));
    assertTrue(network.contains("PacketDistributor.TRACKING_ENTITY"));
    assertTrue(network.contains("getShootPos(message.weaponName()"));
    assertTrue(network.contains("getShootVec(message.weaponName()"));
    assertTrue(network.contains("animation.fire"));
    assertTrue(mod.contains("DnvVehicleAutonomousFireNetwork.initialize()"));
  }

  @Test
  void heldCoreConfigurationHasServerSessionsAndRealResponsiveUi() throws IOException {
    String sessions = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/item/"
            + "DnvVehicleCrewConfigSessions.java");
    String network = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewConfigNetwork.java");
    String services = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewServices.java");
    String screen = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvVehicleCrewCoreScreen.java");
    String layout = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvVehicleCrewCoreLayout.java");
    String item = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/item/"
            + "DnvVehicleCrewCoreItem.java");

    assertTrue(sessions.contains("MAX_SESSION_AGE_TICKS = 1_200"));
    assertTrue(sessions.contains("CORE_CHANGED"));
    assertTrue(sessions.contains("CATALOG_CHANGED"));
    assertTrue(network.contains("PLAY_TO_SERVER"));
    assertTrue(network.contains("MAX_STATIONS"));
    assertTrue(network.contains("savedFlightModeName"));
    assertTrue(network.contains("savedStationModeNames"));
    assertTrue(services.contains("PlayerLoggedOutEvent"));
    assertTrue(services.contains("PlayerChangedDimensionEvent"));
    assertTrue(services.contains("stack.setTag(DnvVehicleCrewCoreCodec.encode"));
    assertTrue(screen.contains("DnvVehicleCrewCoreLayout.calculate"));
    assertTrue(layout.contains("WIDE_BREAKPOINT"));
    assertTrue(screen.contains("mouseScrolled"));
    assertTrue(screen.contains("DnvVehicleCrewConfigNetwork.commit"));
    assertTrue(screen.contains("saved_verified"));
    assertTrue(screen.contains("private boolean dirty()"));
    assertTrue(screen.contains("pendingTicks < 200"));
    assertFalse(screen.contains("minecraft.setScreen(null)"));
    assertTrue(item.contains("openCoreConfiguration"));
  }

  @Test
  void tacticalMapRestoresDraftEditingWithoutAc130Execution() throws IOException {
    String route = source(
        "route-core/src/main/java/com/liucodex/doggiesnewvehicle/mission/"
            + "DnvTacticalRouteDrafts.java");
    String network = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvTacticalRouteNetwork.java");
    String mixin = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/compat/mixin/"
            + "TacticalMapScreenMixin.java");
    String mixinConfig = source("src/platform/resources/mixins.dnvplatform.json");
    String bindings = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvClientNetworkBindings.java");
    String services = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewServices.java");
    String routeServices = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvTacticalRouteServices.java");

    assertTrue(route.contains("MAX_TOTAL_LENGTH"));
    assertTrue(route.contains("record DraftKey(UUID playerId, UUID coreId, UUID draftId)"));
    assertTrue(route.contains("MAX_DRAFTS_PER_PLAYER"));
    assertTrue(route.contains("OPERATION_MISMATCH"));
    assertTrue(route.contains("STALE_REVISION"));
    assertTrue(network.contains("MAX_WAYPOINTS"));
    assertTrue(network.contains("private static final String PROTOCOL = \"3\""));
    assertTrue(network.contains("RequestContext"));
    assertTrue(network.contains("PLAY_TO_SERVER"));
    assertTrue(mixin.contains("TacticalMapScreen.class"));
    assertTrue(mixin.contains("renderPositionMarkers"));
    assertFalse(mixin.contains("DnvCombatIntentView"));
    assertFalse(mixin.contains("Screen.hasShiftDown()"));
    assertTrue(mixin.contains("button == 0"));
    assertFalse(mixin.contains("DnvNativeFireSession"));
    assertFalse(mixin.contains("dnv$fireButton"));
    String mapInit = mixin.substring(
        mixin.indexOf("private void dnv$addRouteControls("),
        mixin.indexOf("private void dnv$layoutRouteControls("));
    assertTrue(mapInit.contains("DnvTacticalRouteNetwork.requestSnapshot"));
    assertTrue(mixin.contains("DnvTacticalRouteNetwork.requestSnapshot"));
    assertTrue(mixin.contains("DnvTacticalRouteNetwork.commit"));
    assertTrue(mixin.contains("DnvTacticalRouteView"));
    assertTrue(mixin.contains("dnv$routeGate.accept("));
    assertTrue(mixin.contains("dnv$routeButton.active = dnv$controlsVisible && dnv$routeLoaded"));
    String platformClient = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/platform/client/"
            + "DnvPlatformClientEvents.java");
    assertTrue(platformClient.contains("DnvTacticalRouteClientProjection.install"));
    assertTrue(platformClient.contains("instanceof DnvTacticalRouteView"));
    assertFalse(mixin.contains("dnv$renderMissionTaskPanel"));
    assertFalse(mixin.contains("DnvSbwAerialRouteAutopilot"));
    assertTrue(bindings.contains("DnvCombatIntentView"));
    assertFalse(mixin.contains("method = {\"mouseDragged\""));
    assertFalse(mixin.contains("method = {\"mouseScrolled\""));
    assertFalse(mixin.contains("method = {\"keyPressed\""));
    assertFalse(mixin.contains("selectedEntities"));
    assertFalse(mixin.contains("MarkerPersistence"));
    assertTrue(mixinConfig.contains("\"client\""));
    assertTrue(mixinConfig.contains("\"defaultRequire\": 1"));
    assertTrue(mixinConfig.contains("TacticalMapScreenMixin"));
    String routeOwner = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/platform/DnvRouteDraftServices.java");
    assertTrue(routeOwner.contains("getWorldBorder"));
    assertTrue(routeOwner.contains("routes.invalidate(serverPlayer.getUUID())"));
    assertTrue(routeOwner.contains("routes.expire(event.getServer().getTickCount())"));
    assertTrue(routeServices.contains("routes.active("));
    assertTrue(routeServices.contains("platformValidation.validate(player, current)"));
    assertTrue(routeServices.contains("DnvAirRouteValidation.configurationUnavailable("));
    String platformContent = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/platform/DnvPlatformContent.java");
    assertTrue(platformContent.contains("DnvHoverRoutePlatformServices::validation"));
    String hoverRoute = source(
        "src/platform/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvHoverRoutePlatformServices.java");
    assertTrue(hoverRoute.contains("DnvAirRouteValidation.configurationUnavailable("));
    assertTrue(hoverRoute.contains("DnvAirRouteWorldValidation.validate("));
    assertFalse(routeServices.contains("compatibleMissionPreset("));
  }

  @Test
  void nativeIndicatorKeepsSbwScreenWithoutDnvInjection() throws IOException {
    String events = source(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/artillery/client/"
            + "DnvArtilleryClientEvents.java");
    String bindings = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/DnvClientNetworkBindings.java");
    assertFalse(events.contains("ScreenEvent.Init.Post"));
    assertFalse(events.contains("ArtilleryIndicatorScreen"));
    assertFalse(events.contains("new DnvIndicatorFireConfirmScreen"));
    assertTrue(bindings.contains("instanceof DnvCombatIntentView"));
    assertFalse(bindings.contains("new DnvIndicatorFireConfirmScreen"));
  }

  @Test
  void retiredAc130hRouteKeepsLegacyGuardsWithoutRuntimeBinding() throws IOException {
    Path adapters = ROOT.resolve("src/main/resources/data/doggiesnewvehicle/dnv/vehicle_crew_adapters/sbw_0_8_9_1");
    String route = source(
        "route-core/src/main/java/com/liucodex/doggiesnewvehicle/mission/"
            + "DnvTacticalRouteDrafts.java");
    String services = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewServices.java");
    String attachment = source(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/"
            + "DnvVehicleCrewAttachment.java");

    assertFalse(Files.exists(adapters.resolve("ac_130h.json")));
    assertFalse(Files.exists(adapters.resolve("dragonrise_reforge_ac130.json")));
    assertFalse(Files.exists(ROOT.resolve(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwAerialForwardProbe.java")));
    assertFalse(Files.exists(ROOT.resolve(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/DnvSbwAerialRouteAutopilot.java")));
    assertFalse(Files.exists(ROOT.resolve(
        "src/main/java/com/liucodex/doggiesnewvehicle/mission/DnvAerialForwardRiskPolicy.java")));
    assertFalse(Files.exists(ROOT.resolve(
        "src/main/java/com/liucodex/doggiesnewvehicle/mission/DnvAerialLandingController.java")));
    assertFalse(Files.exists(ROOT.resolve(
        "src/main/java/com/liucodex/doggiesnewvehicle/mission/DnvAerialRouteFollower.java")));
    assertTrue(Files.exists(ROOT.resolve(
        "route-core/src/main/java/com/liucodex/doggiesnewvehicle/mission/DnvRouteFollower.java")));
    assertFalse(Files.exists(ROOT.resolve(
        "src/main/java/com/liucodex/doggiesnewvehicle/mission/DnvAerialLandingSite.java")));
    assertTrue(route.contains("DraftProjection"));
    assertTrue(route.contains("binding().vehicleId().equals(vehicleId)"));
    assertTrue(route.contains("VehicleBinding"));
    assertFalse(services.contains("routeDrafts(player.server).bindVehicle"));
    assertFalse(attachment.contains("DnvSbwAerialRouteAutopilot"));
    String routeCapability = attachment.substring(
        attachment.indexOf("boolean hasRouteActuator()"),
        attachment.indexOf("boolean hasWeaponActuator()"));
    assertTrue(routeCapability.contains("return false;"));
  }

  private static String source(String relative) throws IOException {
    return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
  }
}
