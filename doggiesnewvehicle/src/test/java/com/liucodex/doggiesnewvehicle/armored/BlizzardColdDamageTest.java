package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor;
import org.junit.jupiter.api.Test;

class BlizzardColdDamageTest {
    @Test void approvedMediumAndHeavyCoefficientsDoNotStackOrdinaryPhysicalArmor() {
        class Armor implements DnvColdArmor, DnvPhysicalArmor {
            Tier tier = Tier.MEDIUM;
            public Tier coldArmorTier() { return tier; }
            public float ordinaryArmorReduction() { return .80F; }
        }
        var armor = new Armor();
        assertEquals(6, BlizzardColdDamage.amount(armor));
        armor.tier = DnvColdArmor.Tier.HEAVY;
        assertEquals(3, BlizzardColdDamage.amount(armor));
        assertEquals(6, BlizzardColdDamage.amount((DnvPhysicalArmor) () -> .55F));
        assertEquals(3, BlizzardColdDamage.amount((DnvPhysicalArmor) () -> .75F));
        assertEquals(10, BlizzardColdDamage.amount((DnvPhysicalArmor) () -> .30F));
        assertEquals(10, BlizzardColdDamage.amount(new Object()));
    }
}
