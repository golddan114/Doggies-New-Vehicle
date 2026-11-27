package com.liucodex.silentheaven.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.item.DnvBehaviorControllerItem;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleDeployerItem;
import com.liucodex.silentheaven.entity.SilentHeavenVehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DoggiesNewVehicle.MOD_ID);

    public static final RegistryObject<Item> SILENT_HEAVEN_DEPLOYER =
            ITEMS.register("silent_heaven_deployer", () ->
                    new DnvVehicleDeployerItem<>(
                            new Item.Properties().stacksTo(1),
                            ModEntities.SILENT_HEAVEN,
                            vehicle -> {
                                vehicle.alignCoreToHull();
                                vehicle.setHealth(
                                        SilentHeavenVehicleEntity.MAX_HEALTH);
                            }));

    public static final RegistryObject<DnvBehaviorControllerItem<SilentHeavenVehicleEntity>>
            SILENT_HEAVEN_BEHAVIOR_CONTROLLER =
            ITEMS.register("silent_heaven_behavior_controller", () ->
                    new DnvBehaviorControllerItem<>(
                            new Item.Properties().stacksTo(1),
                            SilentHeavenVehicleEntity.class,
                            "SilentHeaven",
                            "silent_heaven",
                            SilentHeavenVehicleEntity::isHoldingPosition,
                            SilentHeavenVehicleEntity::setHoldingPosition,
                            SilentHeavenVehicleEntity::getTeleportCooldownTicks,
                            SilentHeavenVehicleEntity::commandTeleportTo));

    private ModItems() {
    }
}
