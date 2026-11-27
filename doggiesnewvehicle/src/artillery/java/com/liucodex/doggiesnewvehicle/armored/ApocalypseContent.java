package com.liucodex.doggiesnewvehicle.armored;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Artillery registration boundary for player/AI tank identities and their shared guided projectile. */
public final class ApocalypseContent {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "doggievehicle");
    public static final RegistryObject<EntityType<DnvApocalypseEntity>> TANK = TYPES.register("apocalypse", () ->
            EntityType.Builder.<DnvApocalypseEntity>of(DnvApocalypseEntity::new, MobCategory.MISC)
                    .sized(5.6F, 4.3F).clientTrackingRange(16).updateInterval(2).build(DnvApocalypseEntity.TYPE_ID));
    public static final RegistryObject<EntityType<DnvApocalypseMissile>> MISSILE = TYPES.register("apocalypse_missile", () ->
            EntityType.Builder.<DnvApocalypseMissile>of(DnvApocalypseMissile::new, MobCategory.MISC)
                    .sized(.25F, .25F).clientTrackingRange(16).updateInterval(1).build("doggievehicle:apocalypse_missile"));
    public static final RegistryObject<EntityType<DnvAlliedApocalypseEntity>> ALLIED_TANK = TYPES.register("apocalypse_ai", () ->
            EntityType.Builder.<DnvAlliedApocalypseEntity>of(DnvAlliedApocalypseEntity::new, MobCategory.MISC)
                    .sized(5.6F, 4.3F).clientTrackingRange(16).updateInterval(2).build(DnvAlliedApocalypseEntity.TYPE_ID));
    private ApocalypseContent() { }
}
