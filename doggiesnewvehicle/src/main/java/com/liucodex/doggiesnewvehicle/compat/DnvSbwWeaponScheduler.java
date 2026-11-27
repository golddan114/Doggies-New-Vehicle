package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootPos;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure bounded scheduler helpers extracted from the exact SBW actuator. */
final class DnvSbwWeaponScheduler {
  private DnvSbwWeaponScheduler() {}

  static boolean rememberFireAttempt(
      Map<UUID, DnvSbwCrewAutonomy.FireAudit> audits,
      DnvSbwCrewAutonomy.FireAudit audit,
      int maximumAudits) {
    Objects.requireNonNull(audits, "audits");
    Objects.requireNonNull(audit, "audit");
    if (audits.containsKey(audit.operationId())) {
      return false;
    }
    while (audits.size() >= maximumAudits) {
      audits.remove(audits.keySet().iterator().next());
    }
    audits.put(audit.operationId(), audit);
    return true;
  }

  static UUID fireOperationId(
      UUID coreId,
      long coreRevision,
      String stationId,
      String weaponName,
      UUID targetId,
      long gameTime,
      int fireIndex) {
    String canonical = coreId + "\n" + coreRevision + "\n" + stationId
        + "\n" + weaponName + "\n" + targetId + "\n" + gameTime + "\n" + fireIndex;
    return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
  }

  static int selectReadyWeapon(List<Boolean> ready, int startIndex) {
    Objects.requireNonNull(ready, "ready");
    if (ready.isEmpty()) {
      return -1;
    }
    int start = Math.floorMod(startIndex, ready.size());
    for (int offset = 0; offset < ready.size(); offset++) {
      int index = Math.floorMod(start + offset, ready.size());
      if (Boolean.TRUE.equals(ready.get(index))) {
        return index;
      }
    }
    return -1;
  }

  static boolean isAligned(Vec3 current, Vec3 desired, double minimumDot) {
    return current != null
        && desired != null
        && current.lengthSqr() > 1.0E-8D
        && desired.lengthSqr() > 1.0E-8D
        && current.normalize().dot(desired.normalize()) >= minimumDot;
  }

  static boolean isPointAligned(Vec3 current, Vec3 desired) {
    return isAligned(current, desired, Math.cos(Math.toRadians(0.1D)));
  }

  static int fireIntervalTicks(int rpm) {
    return rpm <= 0 ? 20 : Math.max(1, (int) Math.ceil(1_200.0D / rpm));
  }

  static int currentFireIndex(GunData gunData) {
    ShootPos shootPos = gunData.get(GunProp.SHOOT_POS);
    int size = shootPos.getPositions().size();
    if (size <= 1) {
      return 0;
    }
    if (shootPos.getBoundUpWithAmmoAmount()) {
      return Mth.clamp(gunData.ammo.get() - 1, 0, size - 1);
    }
    return Math.floorMod(gunData.fireIndex.get(), size);
  }
}
