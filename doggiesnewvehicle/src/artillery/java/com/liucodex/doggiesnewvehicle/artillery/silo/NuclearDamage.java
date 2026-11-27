package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class NuclearDamage {
    public static final ResourceKey<DamageType> BLAST=key("nuclear_cold_blast"),RADIATION=key("nuclear_radiation");
    private NuclearDamage(){ }
    private static ResourceKey<DamageType> key(String name){return ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle",name));}
    public static DamageSource source(Level level,ResourceKey<DamageType> type,Entity actor){
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),actor);
    }
    public static DamageSource blast(Level level,Entity actor,net.minecraft.world.phys.Vec3 position){
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLAST),null,actor,position);
    }
}
