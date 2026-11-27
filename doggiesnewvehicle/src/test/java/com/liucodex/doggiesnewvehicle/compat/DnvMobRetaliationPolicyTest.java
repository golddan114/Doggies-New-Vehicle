package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DnvMobRetaliationPolicyTest {
    @Test
    void acceptsOnlyFirstPartyDnvCombatUnits() {
        assertTrue(supports("doggievehicle:50_changfeng_cannon"));
        assertTrue(supports("doggievehicle:storm_turret"));
        assertTrue(supports("doggievehicle:jingzhe_turret"));
        assertFalse(supports("doggievehicle:silent_heaven"));
        assertFalse(supports("doggievehicle:noise"));
        DnvMobRetaliationPolicy.installBossTypes(Set.of(
                ResourceLocation.fromNamespaceAndPath("doggievehicle", "silent_heaven"),
                ResourceLocation.fromNamespaceAndPath("doggievehicle", "noise")));
        assertTrue(supports("doggievehicle:silent_heaven"));
        assertTrue(supports("doggievehicle:noise"));
        assertFalse(supports("doggievehicle:combat_proxy"));
        assertFalse(supports("superbwarfare:yx_100"));
        assertFalse(supports("minecraft:zombie"));
    }

    private static boolean supports(String id) {
        return DnvMobRetaliationPolicy.supports(ResourceLocation.tryParse(id));
    }
}
