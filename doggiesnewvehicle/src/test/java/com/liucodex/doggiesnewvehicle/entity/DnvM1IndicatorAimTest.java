package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvM1IndicatorAimTest {
  @Test void onlyTheIssuingPlayerAndExactTargetParametersCanRequestThisOrder() {
    UUID owner = UUID.randomUUID();
    BlockPos target = new BlockPos(120, 70, -30);
    var aim = new DnvM1IndicatorAim(owner, target, 8, true, new Vec3(1, 0, 0), 1200);
    assertTrue(aim.matches(owner, target, 8, true));
    assertFalse(aim.matches(UUID.randomUUID(), target, 8, true));
    assertFalse(aim.matches(owner, target.offset(1, 0, 0), 8, true));
    assertFalse(aim.matches(owner, target, 9, true));
    assertFalse(aim.matches(owner, target, 8, false));
  }

  @Test void orderExpiresAndCannotFireBeforeTheBarrelIsAligned() {
    var aim = new DnvM1IndicatorAim(UUID.randomUUID(), BlockPos.ZERO, 0, false,
        new Vec3(1, 0, 0), 200);
    assertFalse(aim.expired(200));
    assertTrue(aim.expired(201));
    assertFalse(aim.aligned(null));
    assertFalse(aim.aligned(Vec3.ZERO));
    assertFalse(aim.aligned(new Vec3(0, 0, 1)));
    assertTrue(aim.aligned(new Vec3(10, 0, 0)));
  }
}
