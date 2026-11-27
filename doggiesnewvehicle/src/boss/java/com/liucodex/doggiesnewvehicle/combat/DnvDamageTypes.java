package com.liucodex.doggiesnewvehicle.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Custom damage-type keys used by DNV vehicles. */
public final class DnvDamageTypes {
    public static final ResourceKey<DamageType> BOSS_PIERCING_LASER = com.liucodex.doggiesnewvehicle.core.DnvArmorDamageTags.BOSS_PIERCING_LASER;
    public static final ResourceKey<DamageType> MOTHERSHIP_DOOMSDAY_BLAST = bossType("mothership_doomsday_blast");
    public static final ResourceKey<DamageType> SILENT_HEAVEN_HALO_PIERCING =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            "silent_heaven", "halo_piercing"));

    private DnvDamageTypes() {
    }

    private static ResourceKey<DamageType> bossType(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", path));
    }

    /** Scoped tags bypass vanilla armor and SBW vehicle mitigation without changing other lasers. */
    public static DamageSource piercingLaser(RegistryAccess registries, Entity direct, Entity attacker) {
        return new DamageSource(registries.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BOSS_PIERCING_LASER), direct, attacker);
    }

    public static DamageSource piercingLaser(RegistryAccess registries, Entity direct, Entity attacker, float percent) {
        return new com.liucodex.doggiesnewvehicle.core.DnvDirectHitSource(
                registries.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BOSS_PIERCING_LASER), direct, attacker, percent);
    }

    public static DamageSource doomsdayBlast(RegistryAccess registries, Entity direct, Entity attacker) {
        return new DamageSource(registries.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(MOTHERSHIP_DOOMSDAY_BLAST), direct, attacker);
    }
}
