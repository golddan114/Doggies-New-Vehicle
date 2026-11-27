package com.liucodex.silentheaven.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import com.liucodex.doggiesnewvehicle.core.DnvMultiStationTargetAllocator;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Checks the active five-station path, including launch-time safety after allocation. */
class SilentHeavenStationPipelineContractTest {
  @Test void everyExplosiveLaunchPersistsTheHeavenCameraPolicy() {
    for (String method : new String[]{"fireMainGun", "fireUnguidedSideRocket", "fireTrackingMissile"}) {
      calls(AbstractSilentHeavenVehicleWeapons.class, method,
          com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.class, "suppressCameraShake");
    }
    for (String method : new String[]{"tickDroneLauncher", "spawnFallingWarhead"}) {
      calls(AbstractSilentHeavenVehicleCombat.class, method,
          com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.class, "suppressCameraShake");
    }
  }
  @Test void oneCandidateViewFeedsAllocationBeforeSecondaryExecution() {
    callsNamed(AbstractSilentHeavenVehicleLifecycle.class, "tick", "prepareSecondaryAllocation");
    calls(AbstractSilentHeavenVehicleWeapons.class, "prepareSecondaryAllocation",
        DnvMultiStationTargetAllocator.class, "assign");
    calls(AbstractSilentHeavenVehicleWeapons.class, "prepareSecondaryAllocation",
        DnvLazyCandidateFrame.class, "get");
    calls(AbstractSilentHeavenVehicleLifecycle.class, "tick", DnvLazyCandidateFrame.class, "close");
  }

  @Test void explorationWaitsForGeometryQuotaAndLaunchRechecksImmediately() {
    calls(AbstractSilentHeavenVehicleWeapons.class, "hasCachedMainLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractSilentHeavenVehicleWeapons.class, "hasCachedStationLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractSilentHeavenVehicleState.AutocannonStation.class, "hasCachedLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractSilentHeavenVehicleNavigation.class, "hasCachedAttackLine",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractSilentHeavenVehicleNavigation.class, "trySwitchObstructedTarget",
        DnvTargetingBudget.class, "admitGeometryCheck");
    for (String launcher : new String[]{"fireUnguidedSideRocket", "fireTrackingMissile"}) {
      callsNamed(AbstractSilentHeavenVehicleWeapons.class, launcher, "isTrackedTargetValid");
      callsNamed(AbstractSilentHeavenVehicleWeapons.class, launcher, "hasClearShot");
    }
  }

  private static void calls(Class<?> owner, String method, Class<?> target, String targetMethod) {
    assertTrue(new ClassFileImporter().importClass(owner).getMethodCallsFromSelf().stream()
        .anyMatch(call -> call.getOrigin().getName().equals(method)
            && call.getTarget().getOwner().getName().equals(target.getName())
            && call.getTarget().getName().equals(targetMethod)),
        () -> owner.getSimpleName() + "." + method + " -> " + target.getSimpleName() + "." + targetMethod);
  }

  private static void callsNamed(Class<?> owner, String method, String targetMethod) {
    assertTrue(new ClassFileImporter().importClass(owner).getMethodCallsFromSelf().stream()
        .anyMatch(call -> call.getOrigin().getName().equals(method)
            && call.getTarget().getName().equals(targetMethod)),
        () -> owner.getSimpleName() + "." + method + " -> " + targetMethod);
  }
}
