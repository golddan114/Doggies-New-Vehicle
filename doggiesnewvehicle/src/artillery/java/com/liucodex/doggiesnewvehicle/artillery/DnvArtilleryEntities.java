package com.liucodex.doggiesnewvehicle.artillery;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.ChangfengCannonEntity;
import com.liucodex.doggiesnewvehicle.entity.JingzheTurretEntity;
import com.liucodex.doggiesnewvehicle.entity.StormBulletEntity;
import com.liucodex.doggiesnewvehicle.entity.StormTurretEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registry owned by the optional artillery content assembly. */
public final class DnvArtilleryEntities {
    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,
                    DoggiesNewVehicle.ENTITY_NAMESPACE);

    public static final RegistryObject<EntityType<ChangfengCannonEntity>> CHANGFENG =
            TYPES.register("50_changfeng_cannon", () ->
                    EntityType.Builder.<ChangfengCannonEntity>of(
                                    ChangfengCannonEntity::new, MobCategory.MISC)
                            .clientTrackingRange(512).updateInterval(1).fireImmune()
                            .sized(2.0F, 2.5F)
                            .build("doggievehicle:50_changfeng_cannon"));

    public static final RegistryObject<EntityType<StormTurretEntity>> STORM =
            TYPES.register("storm_turret", () ->
                    EntityType.Builder.<StormTurretEntity>of(
                                    StormTurretEntity::new, MobCategory.MISC)
                            .clientTrackingRange(512).updateInterval(1).fireImmune()
                            // SBW's preview uses a 3 x 3 footprint below width 2.0.
                            .sized(1.95F, 2.6F)
                            .build("doggievehicle:storm_turret"));

    public static final RegistryObject<EntityType<JingzheTurretEntity>> JINGZHE =
            TYPES.register("jingzhe_turret", () ->
                    EntityType.Builder.<JingzheTurretEntity>of(
                                    JingzheTurretEntity::new, MobCategory.MISC)
                            .clientTrackingRange(512).updateInterval(1).fireImmune()
                            .sized(1.95F, 2.9F)
                            .build("doggievehicle:jingzhe_turret"));

    public static final RegistryObject<EntityType<StormBulletEntity>> STORM_BULLET =
            TYPES.register("storm_bullet", () ->
                    EntityType.Builder.<StormBulletEntity>of(
                                    StormBulletEntity::new, MobCategory.MISC)
                            .clientTrackingRange(128).updateInterval(1)
                            .sized(0.1F, 0.1F)
                            .build("doggievehicle:storm_bullet"));

    private DnvArtilleryEntities() { }

    public static final RegistryObject<EntityType<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity>> NUCLEAR_SILO =
            TYPES.register("nuclear_silo", () -> EntityType.Builder.<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity>of(
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity::new, MobCategory.MISC)
                    .clientTrackingRange(512).updateInterval(1).fireImmune().sized(14,9).build("doggievehicle:nuclear_silo"));
    public static final RegistryObject<EntityType<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity>> NUCLEAR_MISSILE =
            TYPES.register("nuclear_missile", () -> EntityType.Builder.<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity>of(
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity::new, MobCategory.MISC)
                    .clientTrackingRange(512).updateInterval(1).fireImmune()
                    .sized((float)com.liucodex.doggiesnewvehicle.artillery.silo.NuclearModelData.DEFAULT.rocketWidth(),
                            (float)com.liucodex.doggiesnewvehicle.artillery.silo.NuclearModelData.DEFAULT.rocketHeight())
                    .build("doggievehicle:nuclear_missile"));
    public static final RegistryObject<EntityType<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity>> DOGEGG_SILO =
            TYPES.register("dogegg_silo", () -> EntityType.Builder.<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity>of(
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity::new, MobCategory.MISC)
                    .clientTrackingRange(512).updateInterval(1).fireImmune().sized(14,9).build("doggievehicle:dogegg_silo"));
    public static final RegistryObject<EntityType<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity>> DOGEGG_MISSILE =
            TYPES.register("dogegg_missile", () -> EntityType.Builder.<com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity>of(
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity::new, MobCategory.MISC)
                    .clientTrackingRange(512).updateInterval(1).fireImmune().sized(2.98F,5.2F).build("doggievehicle:dogegg_missile"));
    public static final RegistryObject<EntityType<com.liucodex.doggiesnewvehicle.artillery.silo.VacuumEffectEntity>> VACUUM_EFFECT =
            TYPES.register("vacuum_effect", () -> EntityType.Builder.<com.liucodex.doggiesnewvehicle.artillery.silo.VacuumEffectEntity>of(
                    com.liucodex.doggiesnewvehicle.artillery.silo.VacuumEffectEntity::new, MobCategory.MISC)
                    .clientTrackingRange(256).updateInterval(1).fireImmune().sized(4,4).build("doggievehicle:vacuum_effect"));
}
