package com.liucodex.doggiesnewvehicle.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage owned by optional DNV features that retains its specialist handling. */
public final class DnvArmorDamageTags {
    public static final TagKey<DamageType> SPECIAL = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "armor_special"));

    public static final net.minecraft.resources.ResourceKey<DamageType> BOSS_PIERCING_LASER =
            net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "boss_piercing_laser"));

    private DnvArmorDamageTags() { }
}
