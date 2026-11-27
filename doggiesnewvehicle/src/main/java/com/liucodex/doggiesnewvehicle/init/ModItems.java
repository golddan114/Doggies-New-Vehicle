package com.liucodex.doggiesnewvehicle.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleOperationsItem;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items owned by the add-on. The DNV icon item is intentionally hidden from tab contents. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DoggiesNewVehicle.MOD_ID);

    public static final RegistryObject<Item> DNV_ICON =
            ITEMS.register("dnv_icon", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DSX1 =
            ITEMS.register("dsx1", com.liucodex.doggiesnewvehicle.weapons.Dsx1Item::new);

    public static final RegistryObject<Item> VEHICLE_CREW_CORE =
            ITEMS.register("vehicle_crew_core", () ->
                    new DnvVehicleCrewCoreItem(new Item.Properties()));

    public static final RegistryObject<Item> VEHICLE_OPERATIONS =
            ITEMS.register("vehicle_operations", () ->
                    new DnvVehicleOperationsItem(new Item.Properties()));

    private ModItems() {
    }
}
