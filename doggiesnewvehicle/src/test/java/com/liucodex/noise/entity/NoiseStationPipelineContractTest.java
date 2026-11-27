package com.liucodex.noise.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import com.liucodex.doggiesnewvehicle.core.DnvMultiStationTargetAllocator;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Checks the currently executed station methods instead of an unused targeting adapter. */
class NoiseStationPipelineContractTest {
  @Test
  void vehicleAllocatesFromOneSharedFrameBeforeItsSecondaryStationsRun() {
    callsNamed(AbstractNoiseVehicleLifecycle.class, "tick", "prepareSecondaryAllocation");
    calls(AbstractNoiseVehicleWeapons.class, "prepareSecondaryAllocation",
        DnvMultiStationTargetAllocator.class, "assign");
    calls(AbstractNoiseVehicleWeapons.class, "prepareSecondaryAllocation",
        DnvLazyCandidateFrame.class, "get");
    calls(AbstractNoiseVehicleLifecycle.class, "tick", DnvLazyCandidateFrame.class, "close");
  }

  @Test
  void actualExploratorySightMethodsUseDimensionGeometryAdmission() {
    calls(AbstractNoiseVehicleWeapons.class, "hasCachedStationLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractNoiseVehicleState.AutocannonStation.class, "hasCachedLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractNoiseVehicleWeapons.class, "hasCachedMainLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractNoiseVehicleNavigation.class, "hasCachedAttackLine",
        DnvTargetingBudget.class, "admitGeometryCheck");
    calls(AbstractNoiseVehicleNavigation.class, "trySwitchObstructedTarget",
        DnvTargetingBudget.class, "admitGeometryCheck");
  }

  @Test
  void rocketAndVlsLaunchRecheckTargetAndLineImmediately() {
    for (String launcher : new String[]{"fireUnguidedSideRocket", "fireTrackingMissile"}) {
      callsNamed(AbstractNoiseVehicleWeapons.class, launcher, "isTrackedTargetValid");
      callsNamed(AbstractNoiseVehicleWeapons.class, launcher, "hasClearShot");
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
