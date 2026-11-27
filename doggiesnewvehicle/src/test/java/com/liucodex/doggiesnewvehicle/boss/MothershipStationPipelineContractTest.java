package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Wiring evidence for the actual six-station tick, not a stand-in helper. */
class MothershipStationPipelineContractTest {
  @Test
  void actualStationTickUsesOnePureAllocationBoundary() {
    assertEquals(1, new ClassFileImporter().importClass(DnvMothershipEntity.class)
        .getMethodCallsFromSelf().stream()
        .filter(call -> call.getOrigin().getName().equals("tickStations")
            && call.getTarget().getOwner().getName().equals(MothershipStationTracking.class.getName())
            && call.getTarget().getName().equals("select")).count());
    assertEquals(1, new ClassFileImporter().importClass(MothershipStationTracking.class)
        .getMethodCallsFromSelf().stream()
        .filter(call -> call.getOrigin().getName().equals("select")
            && call.getTarget().getOwner().getName().equals(
                "com.liucodex.doggiesnewvehicle.core.DnvMultiStationTargetAllocator")
            && call.getTarget().getName().equals("assign")).count());
  }
}
