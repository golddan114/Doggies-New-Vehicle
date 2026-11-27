package com.liucodex.doggiesnewvehicle.api.combat;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Platform boundary for the station adapter. The entity owns the actual native passenger relation. */
public interface DnvNativeCrewTarget {
  Entity remoteCrewEntity();
  boolean isDestroyed();
  UUID remoteManualOwner();
  boolean beginRemoteCrew(ServerPlayer player, UUID station, BlockPos stationPos, long revision, BlockPos bodyPos);
  UUID remoteStation();
  BlockPos remoteStationPosition();
  long remoteStationRevision();
  long remoteCrewGeneration();
  BlockPos remoteBodyPosition();
  boolean isRemoteOperator(Entity entity);
  /** Read-only operational state; station/UUID validity is checked separately. */
  default String remoteCrewReadiness() { return "ready"; }
}
