package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DnvTargetExclusionsTest {
    @Test
    void rejectsTheFirstPartyInternalCombatProxy() {
        assertTrue(DnvTargetExclusions.isInternalCombatProxy(id(
                "doggievehicle",
                "combat_proxy"
        )));
    }

    @Test
    void ordinaryEntitiesRemainEligible() {
        assertFalse(DnvTargetExclusions.isInternalCombatProxy(id(
                "minecraft",
                "zombie"
        )));
        assertFalse(DnvTargetExclusions.isInternalCombatProxy((ResourceLocation) null));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
