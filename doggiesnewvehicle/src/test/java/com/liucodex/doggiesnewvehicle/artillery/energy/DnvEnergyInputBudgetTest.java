package com.liucodex.doggiesnewvehicle.artillery.energy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DnvEnergyInputBudgetTest {
    @Test void portsAndInventoryShareActualReceivedBudgetAndSimulationDoesNotSpendIt() {
        var budget = new DnvEnergyInputBudget(2048);
        assertEquals(2048, budget.allowance(10, Integer.MAX_VALUE));
        budget.received(10, 2048, true);
        assertEquals(2048, budget.allowance(10, Integer.MAX_VALUE));
        budget.received(10, 300, false); // One receiver accepted less than requested.
        assertEquals(1748, budget.allowance(10, 9000));
        budget.received(10, 700, false); // Second port.
        assertEquals(1048, budget.allowance(10, 9000)); // Remaining allowance for inventory.
        budget.received(10, 1048, false);
        assertEquals(0, budget.allowance(10, 100));
        assertEquals(2048, budget.allowance(11, 9000));
        assertEquals(0, budget.allowance(11, -10));
    }
    @Test void authoredPolicyCoversPlayersExcludesAiInputAndLeavesNativeVehiclesAlone() {
        assertEquals(200000, DnvEnergyInputPolicy.forVehicle("doggievehicle:m1_yunlong").allowance(1, Integer.MAX_VALUE));
        assertEquals(2000000, DnvEnergyInputPolicy.forVehicle("doggievehicle:blizzard").allowance(1, Integer.MAX_VALUE));
        assertEquals(5000000, DnvEnergyInputPolicy.forVehicle("doggievehicle:apocalypse").allowance(1, Integer.MAX_VALUE));
        assertEquals(20000000, DnvEnergyInputPolicy.forVehicle("doggievehicle:nuclear_silo").allowance(1, Integer.MAX_VALUE));
        assertEquals(0, DnvEnergyInputPolicy.forVehicle("doggievehicle:apocalypse_ai").allowance(1, 99999));
        assertNull(DnvEnergyInputPolicy.forVehicle("superbwarfare:t_90a"));
    }
    @Test void servoIdleCostsNothingMovementPaysOnceAndInsufficientPowerCannotAim() {
        var servo = new M1ServoEnergy();
        assertFalse(servo.canAim(1, 7));
        assertEquals(0, servo.charge(1, 20, -5, 20, -5));
        assertTrue(servo.canAim(1, 8));
        assertEquals(8, servo.charge(1, 20, -5, 21, -5));
        assertTrue(servo.canAim(1, 0));
        assertEquals(0, servo.charge(1, 21, -5, 21, -6));
        assertFalse(servo.canAim(2, 0));
        assertEquals(8, servo.charge(2, 21, -6, 22, -6));
    }
}
