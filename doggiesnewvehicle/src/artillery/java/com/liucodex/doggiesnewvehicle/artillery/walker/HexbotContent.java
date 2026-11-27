package com.liucodex.doggiesnewvehicle.artillery.walker;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexbotContent {
    public static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"doggievehicle");
    public static final RegistryObject<EntityType<DnvHexbotEntity>> HEXBOT=TYPES.register("hexbot",()->
            EntityType.Builder.<DnvHexbotEntity>of(DnvHexbotEntity::new,MobCategory.MISC)
                    .sized(1.25F,.7F).clientTrackingRange(12).updateInterval(1).build("doggievehicle:hexbot"));
    private HexbotContent(){ }
}
