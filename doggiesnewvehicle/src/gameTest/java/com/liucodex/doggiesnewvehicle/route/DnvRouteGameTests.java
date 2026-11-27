package com.liucodex.doggiesnewvehicle.route;

import com.liucodex.doggiesnewvehicle.compat.DnvAirRouteWorldValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real ServerLevel boundary for the active route draft and loaded-terrain checks. */
@GameTestHolder("dnvroutegametest")
@PrefixGameTestTemplate(false)
public final class DnvRouteGameTests {
  @GameTest(template = "empty", templateNamespace = "minecraft")
  public static void routeDraftIdentityUsesRealServerClock(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    long gameTime = server.getTickCount();
    UUID owner = UUID.randomUUID();
    UUID coreA = UUID.randomUUID();
    UUID coreB = UUID.randomUUID();
    DnvTacticalRouteDrafts drafts = new DnvTacticalRouteDrafts();
    DnvTacticalRouteDrafts.DraftProjection first = drafts.bindVehicle(
        owner,
        helper.getLevel().dimension().location().toString(),
        coreA,
        UUID.randomUUID(),
        gameTime).projection().orElseThrow();
    DnvTacticalRouteDrafts.OperationResult committed = drafts.commit(
        owner,
        first.reference(),
        UUID.randomUUID(),
        first.snapshot().revision(),
        first.snapshot().dimension(),
        List.of(
            new DnvRouteWaypoint(0.0D, 90.0D, 0.0D),
            new DnvRouteWaypoint(64.0D, 110.0D, 64.0D)),
        new DnvTacticalRouteDrafts.Bounds(
            -30_000_000.0D,
            30_000_000.0D,
            helper.getLevel().getMinBuildHeight(),
            helper.getLevel().getMaxBuildHeight() - 1.0D,
            -30_000_000.0D,
            30_000_000.0D),
        gameTime);
    require(committed.accepted(), "first real-server draft commit must succeed");

    DnvTacticalRouteDrafts.DraftProjection second = drafts.bindVehicle(
        owner,
        first.snapshot().dimension(),
        coreB,
        UUID.randomUUID(),
        gameTime).projection().orElseThrow();
    require(second.snapshot().waypoints().isEmpty(),
        "a second core must not inherit the first core route");
    require(drafts.invalidate(owner) == 2,
        "logout invalidation must clear both runtime drafts");
    require(drafts.active(owner, first.snapshot().dimension(), gameTime).isEmpty(),
        "cleared runtime drafts must not reappear on reconnect");
    helper.succeed();
  }

  @GameTest(template = "empty", templateNamespace = "minecraft")
  public static void airRouteValidationUsesOnlyLoadedServerTerrain(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
    int surface = level.getHeight(
        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
        start.getX(),
        start.getZ());
    int obstacleX = start.getX() + 16;
    for (int y = surface; y < level.getMaxBuildHeight(); y++) {
      level.setBlockAndUpdate(new BlockPos(obstacleX, y, start.getZ()),
          Blocks.AIR.defaultBlockState());
    }
    DnvTacticalRouteDrafts.Bounds bounds = new DnvTacticalRouteDrafts.Bounds(
        level.getWorldBorder().getMinX(),
        level.getWorldBorder().getMaxX(),
        level.getMinBuildHeight(),
        level.getMaxBuildHeight() - 1.0D,
        level.getWorldBorder().getMinZ(),
        level.getWorldBorder().getMaxZ());
    DnvAirRouteValidation.FlightEnvelope envelope =
        new DnvAirRouteValidation.FlightEnvelope(
            surface + 4.0D, surface + 8.0D, surface + 32.0D, 4.0D, 0.0D);
    List<DnvRouteWaypoint> loadedRoute = List.of(
        new DnvRouteWaypoint(start.getX(), surface + 8.0D, start.getZ()),
        new DnvRouteWaypoint(start.getX() + 32.0D, surface + 8.0D, start.getZ()));
    DnvAirRouteValidation.Result clear = DnvAirRouteWorldValidation.validate(
        level, loadedRoute, bounds, envelope);
    require(clear.status() == DnvAirRouteValidation.Status.EXECUTABLE,
        "loaded clear corridor must be executable: " + clear.status()
            + "/" + clear.reason());

    for (int y = surface; y <= surface + 32; y++) {
      level.setBlockAndUpdate(new BlockPos(obstacleX, y, start.getZ()),
          Blocks.STONE.defaultBlockState());
    }
    DnvAirRouteValidation.Result blocked = DnvAirRouteWorldValidation.validate(
        level, loadedRoute, bounds, envelope);
    require(blocked.status() == DnvAirRouteValidation.Status.BLOCKED,
        "loaded terrain above the envelope must block the segment");

    int unloadedX = start.getX() + 2_048;
    BlockPos unloaded = new BlockPos(unloadedX, surface, start.getZ());
    require(!level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4),
        "far validation chunk must begin unloaded");
    List<DnvRouteWaypoint> unloadedRoute = List.of(
        new DnvRouteWaypoint(unloadedX, surface + 8.0D, start.getZ()),
        new DnvRouteWaypoint(unloadedX + 16.0D, surface + 8.0D, start.getZ()));
    DnvAirRouteValidation.Result unknown = DnvAirRouteWorldValidation.validate(
        level, unloadedRoute, bounds, envelope);
    require(unknown.status() == DnvAirRouteValidation.Status.UNVERIFIED,
        "unloaded terrain must remain unverified");
    require(!level.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4),
        "route validation must not force-load an unknown chunk");
    helper.succeed();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
