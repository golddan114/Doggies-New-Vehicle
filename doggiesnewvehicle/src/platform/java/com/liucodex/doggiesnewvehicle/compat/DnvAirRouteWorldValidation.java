package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Read-only ServerLevel adapter; unloaded chunks remain unknown and are never force-loaded. */
public final class DnvAirRouteWorldValidation {
  private DnvAirRouteWorldValidation() {}

  public static DnvAirRouteValidation.Result validate(
      ServerLevel level,
      List<DnvRouteWaypoint> waypoints,
      DnvTacticalRouteDrafts.Bounds bounds,
      DnvAirRouteValidation.FlightEnvelope envelope) {
    Objects.requireNonNull(level, "level");
    return DnvAirRouteValidation.validate(
        waypoints,
        bounds,
        envelope,
        (x, z) -> {
          int blockX = (int) Math.floor(x);
          int blockZ = (int) Math.floor(z);
          if (!level.hasChunk(blockX >> 4, blockZ >> 4)) {
            return DnvAirRouteValidation.TerrainSample.unloaded();
          }
          return DnvAirRouteValidation.TerrainSample.loaded(
              level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ));
        });
  }
}
