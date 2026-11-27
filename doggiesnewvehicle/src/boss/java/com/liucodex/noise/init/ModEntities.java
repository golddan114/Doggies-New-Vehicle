package com.liucodex.noise.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvInvisibleProjectileEntity;
import com.liucodex.noise.entity.NoiseVehicleEntity;
import com.liucodex.noise.entity.NoiseLaserBoltEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    ForgeRegistries.ENTITY_TYPES, DoggiesNewVehicle.ENTITY_NAMESPACE);

    public static final RegistryObject<EntityType<NoiseVehicleEntity>> NOISE =
            ENTITY_TYPES.register("noise", () ->
                    EntityType.Builder.<NoiseVehicleEntity>of(
                                    NoiseVehicleEntity::new,
                                    MobCategory.MISC)
                            .sized(6.0F, 5.6F)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .fireImmune()
                            .build("doggievehicle:noise"));

    public static final RegistryObject<EntityType<DnvInvisibleProjectileEntity>> AUTOCANNON_ROUND =
            ENTITY_TYPES.register("noise_autocannon_round", () ->
                    EntityType.Builder.<DnvInvisibleProjectileEntity>of(
                                    DnvInvisibleProjectileEntity::new,
                                    MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(1)
                            .updateInterval(1)
                            .build("doggievehicle:noise_autocannon_round"));

    public static final RegistryObject<EntityType<NoiseLaserBoltEntity>> LASER_BOLT =
            ENTITY_TYPES.register("noise_laser_bolt", () ->
                    EntityType.Builder.<NoiseLaserBoltEntity>of(
                                    NoiseLaserBoltEntity::new,
                                    MobCategory.MISC)
                            .sized(0.18F, 0.18F)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .build("doggievehicle:noise_laser_bolt"));

    private ModEntities() {
    }
}

