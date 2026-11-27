package com.liucodex.doggiesnewvehicle.armored;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Player hover vehicle belongs exclusively to the optional artillery module. */
public final class BlizzardContent {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "doggievehicle");
    public static final RegistryObject<EntityType<DnvBlizzardEntity>> TANK = TYPES.register("blizzard", () ->
            EntityType.Builder.<DnvBlizzardEntity>of(DnvBlizzardEntity::new, MobCategory.MISC)
                    .sized(4.2F, 3.02F).clientTrackingRange(16).updateInterval(1).build("doggievehicle:blizzard"));
    public static final RegistryObject<EntityType<DnvBlizzardMissile>> MISSILE = TYPES.register("blizzard_missile", () ->
            EntityType.Builder.<DnvBlizzardMissile>of(DnvBlizzardMissile::new, MobCategory.MISC)
                    .sized(.25F, .25F).clientTrackingRange(16).updateInterval(1).build("doggievehicle:blizzard_missile"));
    public static final RegistryObject<EntityType<DnvBlizzardColdBolt>> BOLT = TYPES.register("blizzard_cold_bolt", () ->
            EntityType.Builder.<DnvBlizzardColdBolt>of(DnvBlizzardColdBolt::new, MobCategory.MISC)
                    .sized(.12F, .12F).clientTrackingRange(16).updateInterval(1).build("doggievehicle:blizzard_cold_bolt"));
    private BlizzardContent() { }
}
