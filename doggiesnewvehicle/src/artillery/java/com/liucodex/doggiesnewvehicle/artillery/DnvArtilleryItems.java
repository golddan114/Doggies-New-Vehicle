package com.liucodex.doggiesnewvehicle.artillery;

import com.atsuishio.superbwarfare.item.material.BlueprintItem;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.item.DnvM1DeployerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blueprints and deployer owned by artillery; base items remain independent. */
public final class DnvArtilleryItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DoggiesNewVehicle.MOD_ID);

    public static final RegistryObject<Item> CHANGFENG_BLUEPRINT = blueprint("changfeng_blueprint");
    public static final RegistryObject<Item> STORM_BLUEPRINT = blueprint("storm_turret_blueprint");
    public static final RegistryObject<Item> JINGZHE_BLUEPRINT = blueprint("jingzhe_turret_blueprint");
    public static final RegistryObject<Item> M1_BLUEPRINT = blueprint("m1_yunlong_blueprint");
    public static final RegistryObject<Item> APOCALYPSE_BLUEPRINT = blueprint("apocalypse_blueprint");
    public static final RegistryObject<Item> BLIZZARD_BLUEPRINT = blueprint("blizzard_blueprint");
    public static final RegistryObject<Item> CRYO_MISSILE = ITEMS.register("cryo_missile", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EXPLOSIVE_CORE = ITEMS.register("explosive_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ABSORPTION_CORE = ITEMS.register("absorption_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WARHEAD_CORE = ITEMS.register("warhead_core", () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DOG_CORE = ITEMS.register("dog_core", () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DOG_MILK_CARTON = ITEMS.register("dog_milk_carton", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.DogMilkItem(new Item.Properties().stacksTo(16),false));
    public static final RegistryObject<Item> WILD_DOG_MILK = ITEMS.register("wild_dog_milk", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.DogMilkItem(
                    new Item.Properties().stacksTo(16).craftRemainder(DOG_MILK_CARTON.get()),true));
    public static final RegistryObject<Item> NUCLEAR_MISSILE = ITEMS.register("nuclear_missile", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> NUCLEAR_SILO_DEPLOYER = ITEMS.register("nuclear_silo_deployer", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloDeployerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DOGEGG_MISSILE = ITEMS.register("dogegg_missile", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DOGEGG_SILO_DEPLOYER = ITEMS.register("dogegg_silo_deployer", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloDeployerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),true));
    public static final RegistryObject<Item> NUCLEAR_DEPLOYMENT_REMOTE = ITEMS.register("nuclear_deployment_remote", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearDeploymentRemoteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NUCLEAR_PREPARATION_REMOTE = ITEMS.register("nuclear_preparation_remote", () ->
            new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPreparationRemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> APOCALYPSE_AI_DEPLOYER = ITEMS.register("apocalypse_ai_deployer", () ->
            new com.liucodex.doggiesnewvehicle.item.ApocalypseAiDeployerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    // Keep old item registry identities loadable if historical merchant stock contains them.
    // Neither alias is offered in creative tabs or new blueprint boxes.
    public static final RegistryObject<Item> M1_MERCHANT_BLUEPRINT_ALIAS = blueprint("m1_howitzer_blueprint");
    public static final RegistryObject<Item> M1_DEPLOYER =
            ITEMS.register("m1_yunlong_deployer", () ->
                    new DnvM1DeployerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> M1_MERCHANT_DEPLOYER_ALIAS =
            ITEMS.register("m1_howitzer_deployer", () ->
                    new DnvM1DeployerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private static RegistryObject<Item> blueprint(String name) {
        return ITEMS.register(name, () -> new BlueprintItem(Rarity.EPIC));
    }

    private DnvArtilleryItems() { }
}
