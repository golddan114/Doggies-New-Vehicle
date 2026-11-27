package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.item.material.BlueprintItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

final class ShipyardItems {
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CubePrototype.ID);

    static {
        ITEMS.register("hull_section", () -> new Item(new Item.Properties()));
        ITEMS.register("propulsion_section", () -> new Item(new Item.Properties()));
        ITEMS.register("main_turret", () -> new Item(new Item.Properties()));
        ITEMS.register("secondary_234_turret", () -> new Item(new Item.Properties()));
        ITEMS.register("secondary_127_turret", () -> new Item(new Item.Properties()));
        ITEMS.register("ship_armament", () -> new Item(new Item.Properties()));
        ITEMS.register("flight_deck_section", () -> new Item(new Item.Properties()));
        ITEMS.register("libertad_blueprint", () -> new BlueprintItem(Rarity.EPIC));
        ITEMS.register("yue_blueprint", () -> new BlueprintItem(Rarity.EPIC));
    }

    private ShipyardItems() {}
}
