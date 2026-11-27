package com.liucodex.silentheaven.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvInvisibleProjectileEntity;
import com.liucodex.silentheaven.entity.SilentHeavenVehicleEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    ForgeRegistries.ENTITY_TYPES, DoggiesNewVehicle.ENTITY_NAMESPACE);

    public static final RegistryObject<EntityType<SilentHeavenVehicleEntity>> SILENT_HEAVEN =
            ENTITY_TYPES.register("silent_heaven", () ->
                    EntityType.Builder.<SilentHeavenVehicleEntity>of(
                                    SilentHeavenVehicleEntity::new,
                                    MobCategory.MISC)
                            .sized(6.0F, 5.6F)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .fireImmune()
                            .build("doggievehicle:silent_heaven"));

    public static final RegistryObject<EntityType<DnvInvisibleProjectileEntity>> AUTOCANNON_ROUND =
            ENTITY_TYPES.register("silent_heaven_autocannon_round", () ->
                    EntityType.Builder.<DnvInvisibleProjectileEntity>of(
                                    DnvInvisibleProjectileEntity::new,
                                    MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(1)
                            .updateInterval(1)
                            .build("doggievehicle:silent_heaven_autocannon_round"));

    private ModEntities() {
    }
}
