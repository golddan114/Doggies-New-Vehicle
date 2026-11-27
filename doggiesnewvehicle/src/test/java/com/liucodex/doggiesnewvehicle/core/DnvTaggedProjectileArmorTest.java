package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DnvTaggedProjectileArmorTest {
    @Test void boundedDirectHitBoostPreservesImmunityAndVulnerability() {
        assertEquals(55, DnvTaggedProjectileArmor.penetrate(100, 50, .1F));
        assertEquals(0, DnvTaggedProjectileArmor.penetrate(100, 0, .1F));
        assertEquals(150, DnvTaggedProjectileArmor.penetrate(100, 150, .1F));
        assertEquals(50, DnvTaggedProjectileArmor.penetrate(100, 50, 0));
        assertEquals(50, DnvTaggedProjectileArmor.penetrate(100, 50, Float.NaN));
        assertEquals(50, DnvTaggedProjectileArmor.penetrate(Float.NaN, 50, .1F));
    }

    @Test void penetrationOnlyRecoversTheApprovedFractionOfTheArmorReduction() {
        assertEquals(68, DnvTaggedProjectileArmor.penetrate(100, 20, .60F), .0001F);
        assertEquals(44, DnvTaggedProjectileArmor.penetrate(100, 20, .30F), .0001F);
        assertEquals(70, DnvTaggedProjectileArmor.penetrate(100, 70, 0), .0001F);
        assertEquals(25, DnvTaggedProjectileArmor.penetrate(100, 25, 0), .0001F);
    }
}
