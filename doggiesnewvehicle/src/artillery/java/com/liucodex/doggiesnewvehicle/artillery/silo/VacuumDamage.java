package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Fixed blast damage; data-pack tags own resistance, armor, enchantment and vehicle bypass. */
public final class VacuumDamage {
    public static final ResourceKey<DamageType> BLAST=ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","vacuum_blast"));
    private VacuumDamage(){ }
    public static DamageSource blast(Level level,Entity actor,Vec3 position){
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLAST),null,actor,position);
    }
}
