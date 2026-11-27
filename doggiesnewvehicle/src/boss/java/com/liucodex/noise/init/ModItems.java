package com.liucodex.noise.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.item.DnvBehaviorControllerItem;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleDeployerItem;
import com.liucodex.noise.entity.NoiseVehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DoggiesNewVehicle.MOD_ID);

    public static final RegistryObject<Item> NOISE_DEPLOYER =
            ITEMS.register("noise_deployer", () ->
                    new DnvVehicleDeployerItem<>(
                            new Item.Properties().stacksTo(1),
                            ModEntities.NOISE,
                            vehicle -> {
                                vehicle.alignCoreToHull();
                                vehicle.setHealth(NoiseVehicleEntity.MAX_HEALTH);
                            }));

    public static final RegistryObject<DnvBehaviorControllerItem<NoiseVehicleEntity>>
            NOISE_BEHAVIOR_CONTROLLER =
            ITEMS.register("noise_behavior_controller", () ->
                    new DnvBehaviorControllerItem<>(
                            new Item.Properties().stacksTo(1),
                            NoiseVehicleEntity.class,
                            "Noise",
                            "noise",
                            NoiseVehicleEntity::isHoldingPosition,
                            NoiseVehicleEntity::setHoldingPosition,
                            NoiseVehicleEntity::getTeleportCooldownTicks,
                            NoiseVehicleEntity::commandTeleportTo));

    private ModItems() {
    }
}

