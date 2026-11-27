package com.liucodex.doggiesnewvehicle.core;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;

/** Armor shared by SES and Yue. Unknown identities keep the shared superheavy baseline. */
public final class MothershipDefense {
    private static final Set<String> LIGHT_GUNS = Set.of(
            "superbwarfare:aa_12", "superbwarfare:ak_12", "superbwarfare:ak_47", "superbwarfare:awm",
            "superbwarfare:devotion", "superbwarfare:glock_17", "superbwarfare:glock_18", "superbwarfare:hk_416",
            "superbwarfare:homemade_shotgun", "superbwarfare:hunting_rifle", "superbwarfare:insidious", "superbwarfare:k_98",
            "superbwarfare:m_1911", "superbwarfare:m_4", "superbwarfare:m_60", "superbwarfare:m_870",
            "superbwarfare:m_98b", "superbwarfare:marlin", "superbwarfare:minigun", "superbwarfare:mk_14",
            "superbwarfare:mosin_nagant", "superbwarfare:mp_443", "superbwarfare:mp_5", "superbwarfare:qbz_191",
            "superbwarfare:qbz_95", "superbwarfare:rpk", "superbwarfare:sentinel", "superbwarfare:sks",
            "superbwarfare:svd", "superbwarfare:trachelium", "superbwarfare:vector");
    private MothershipDefense() { }

    /** Caller first classifies ordinary physical damage, then applies existing direct penetration once. */
    public static float apply(DamageSource source, float amount) {
        return DnvArmorRules.apply(reduction(source), amount);
    }

    public static float mitigate(DamageSource source, float amount, boolean laser) {
        if (laser) amount = MothershipAnnihilatorDefense.laser(amount);
        return DnvArmorRules.ordinaryPhysical(source)
                ? DnvTaggedProjectileArmor.apply(source, amount, apply(source, amount)) : amount;
    }

    static float reduction(DamageSource source) {
        if (source.is(ModDamageTypes.SUPER_STAR_HIT) || source.is(ModDamageTypes.SUPER_STAR_SLASH)) return .95F;
        var direct = source.getDirectEntity();
        if (direct == null) return .80F;
        if (direct instanceof ProjectileEntity bullet) {
            String gun = bullet.getGunItemId();
            // Native setGunItemId(ItemStack) stores getDescriptionId(), while other callers use registry IDs.
            if (gun != null && gun.startsWith("item.superbwarfare."))
                gun = "superbwarfare:" + gun.substring("item.superbwarfare.".length());
            if (gun != null && LIGHT_GUNS.contains(gun)) return .98F;
            if ("superbwarfare:m_2_hb".equals(gun) || "superbwarfare:ntw_20".equals(gun)) return .90F;
        }
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
        if (id == null || !id.getNamespace().equals("superbwarfare")) return .80F;
        return switch (id.getPath()) {
            case "small_cannon_shell", "gun_grenade", "grapeshot" -> .95F;
            case "cannon_shell", "mortar_shell", "small_rocket" -> .90F;
            case "medium_rocket" -> .85F;
            default -> .80F;
        };
    }

}
