package com.liucodex.doggiesnewvehicle.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** One immutable player order; the M1 entity alone owns its live and queued state. */
record DnvM1IndicatorAim(UUID actor, BlockPos target, int radius, boolean depressed,
                         Vec3 launch, long expiresAt) {
  boolean matches(UUID requester, BlockPos position, int spread, boolean lowArc) {
    return actor.equals(requester) && target.equals(position) && radius == spread && depressed == lowArc;
  }

  boolean expired(long now) { return now > expiresAt; }

  boolean aligned(Vec3 actual) {
    return actual != null && actual.lengthSqr() > 1.0E-8D
        && actual.normalize().dot(launch) > 0.9993D;
  }
}
