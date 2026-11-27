package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Wiring evidence accompanies value/state tests; this does not simulate Minecraft execution. */
class DnvSbwStationPipelineContractTest {
  @Test
  void realGunAndCooldownObservationsReachTheExistingStationCore() {
    calls(DnvSbwStationRuntime.Readiness.class, "inspect", VehicleEntity.class, "getAmmo");
    calls(DnvSbwStationRuntime.Readiness.class, "inspect", GunData.class, "canShoot");
    calls(DnvSbwStationRuntime.Readiness.class, "inspect", DnvSbwStationRuntime.WeaponState.class, "cooldownReady");
    calls(DnvSbwStationEngagementAdapter.class, "input", DnvSbwStationRuntime.Readiness.class, "inspect");
    calls(DnvSbwStationEngagementAdapter.class, "input", DnvSbwStationRuntime.class, "observedLines");
    calls(DnvSbwStationEngagementAdapter.class, "decide", DnvStationEngagementCore.class, "decide");
  }

  @Test
  void productionTickUsesTheStationPlanInsteadOfTheUnusedPreflight() {
    calls(DnvSbwCrewAutonomy.class, "tick", DnvSbwStationEngagementAdapter.class, "decide");
    neverCalls(DnvSbwCrewAutonomy.class, "tick", DnvSbwStationEngagementAdapter.class,
        "revalidateFire");
    neverCalls(DnvSbwCrewAutonomy.class, "tickStation", DnvSbwStationEngagementAdapter.class,
        "revalidateFire");
  }

  @Test
  void finalExecutionStillChecksNativeReadinessFreshLineAndExternalAuthority() {
    calls(DnvSbwCrewAutonomy.class, "tick", DnvCombatPolicyRuntime.class, "crewExecutionAllowed");
    calls(DnvSbwCrewAutonomy.class, "tickStation", GunData.class, "canShoot");
    calls(DnvSbwCrewAutonomy.class, "tickStation", DnvSbwStationRuntime.WeaponState.class, "revalidateForFire");
    calls(DnvSbwCrewAutonomy.class, "tickStation", DnvCombatCoordinator.class, "fireAuthorized");
    calls(DnvSbwCrewAutonomy.class, "tickStation", DnvWeaponFireGate.class, "evaluate");
    calls(DnvSbwCrewAutonomy.class, "tickStation", VehicleEntity.class, "vehicleShoot");
  }

  @Test
  void currentStationEnvelopeGuardsTheLiveFireEntrypoint() {
    calls(DnvSbwCrewAutonomy.class, "tickStation", DnvStationEngagementCore.class,
        "withinExecutionEnvelope");
  }

  @Test
  void periodicGeometryUsesAQuotaButFinalFireSafetyDoesNot() {
    calls(DnvSbwCrewAutonomy.class, "tickStation",
        DnvSbwStationRuntime.WeaponState.class, "refreshLineOfSight");
    calls(DnvSbwStationRuntime.WeaponState.class, "refreshLineOfSight",
        DnvTargetingBudget.class, "admitGeometryCheck");
    neverCalls(DnvSbwStationRuntime.WeaponState.class, "revalidateForFire",
        DnvTargetingBudget.class, "admitGeometryCheck");
  }

  @Test
  void sharedEntityFireDoesNotApplyRetiredCarrierPreflight() {
    neverCalls(DnvSbwCrewAutonomy.class, "tickStation", DnvProjectilePreflight.class, "trace");
    neverCalls(DnvSbwStationEngagementAdapter.class, "revalidateFire", DnvProjectilePreflight.class, "trace");
    calls(DnvSbwCrewAutonomy.class, "tickStation", DnvSbwStationRuntime.WeaponState.class, "revalidateForFire");
  }

  @Test
  void reusablePreflightRemainsIndependentOfTheRetiredNativeAdapter() {
    assertTrue(new ClassFileImporter().importClass(DnvProjectilePreflight.class)
        .getDirectDependenciesFromSelf().stream().noneMatch(dependency ->
            dependency.getTargetClass().getName().startsWith("com.atsuishio.")
                || dependency.getTargetClass().getName().startsWith("com.redabysslucia.")
                || dependency.getTargetClass().getName().startsWith("net.minecraft.")));
  }

  private static void calls(Class<?> source, String method, Class<?> target, String targetMethod) {
    assertTrue(new ClassFileImporter().importClass(source).getMethodCallsFromSelf().stream()
        .anyMatch(call -> call.getOrigin().getName().equals(method)
            && call.getTarget().getOwner().getName().equals(target.getName())
            && call.getTarget().getName().equals(targetMethod)),
        () -> source.getSimpleName() + "." + method + " -> " + target.getSimpleName() + "." + targetMethod);
  }

  private static void neverCalls(Class<?> source, String method, Class<?> target, String targetMethod) {
    assertFalse(new ClassFileImporter().importClass(source).getMethodCallsFromSelf().stream()
        .anyMatch(call -> call.getOrigin().getName().equals(method)
            && call.getTarget().getOwner().getName().equals(target.getName())
            && call.getTarget().getName().equals(targetMethod)));
  }
}
