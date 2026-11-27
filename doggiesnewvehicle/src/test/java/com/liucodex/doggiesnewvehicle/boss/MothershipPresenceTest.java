package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MothershipPresenceTest {
    @Test
    void onePersistentOwnerBlocksNewSpawnsButPreservesOtherLoadedShips() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        MothershipPresence state = new MothershipPresence();
        assertTrue(state.permitsFresh(first));
        assertTrue(state.active(first));
        assertFalse(state.permitsFresh(second));
        assertFalse(state.active(second));

        MothershipPresence restored = MothershipPresence.load(state.save(new CompoundTag()));
        assertFalse(restored.permitsFresh(second), "An unloaded owner still occupies this dimension");
        restored.release(second);
        assertFalse(restored.permitsFresh(second), "A duplicate cannot clear the real owner's lease");
        restored.release(first);
        assertTrue(restored.permitsFresh(second));
        assertTrue(restored.active(second));
    }
}
