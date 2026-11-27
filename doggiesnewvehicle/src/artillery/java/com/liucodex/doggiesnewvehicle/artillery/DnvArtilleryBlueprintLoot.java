package com.liucodex.doggiesnewvehicle.artillery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** Adds a first copy of one artillery blueprint to SBW blueprint boxes. */
public final class DnvArtilleryBlueprintLoot extends LootModifier {
    public static final Codec<DnvArtilleryBlueprintLoot> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, DnvArtilleryBlueprintLoot::new));
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "doggiesnewvehicle");
    public static final RegistryObject<Codec<DnvArtilleryBlueprintLoot>> BLUEPRINT_BOX =
            SERIALIZERS.register("artillery_blueprint_box", () -> CODEC);

    private static final ResourceLocation BOX = ResourceLocation.fromNamespaceAndPath("superbwarfare", "containers/blueprints");
    private static final ResourceLocation EPIC_CHEST = ResourceLocation.fromNamespaceAndPath("superbwarfare", "chests/blue_print_epic");
    private static final List<RegistryObject<Item>> BLUEPRINTS = List.of(
            DnvArtilleryItems.CHANGFENG_BLUEPRINT,
            DnvArtilleryItems.STORM_BLUEPRINT,
            DnvArtilleryItems.JINGZHE_BLUEPRINT,
            DnvArtilleryItems.M1_BLUEPRINT,
            DnvArtilleryItems.APOCALYPSE_BLUEPRINT,
            DnvArtilleryItems.BLIZZARD_BLUEPRINT);

    private DnvArtilleryBlueprintLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        int chance = BOX.equals(table) ? 20 : EPIC_CHEST.equals(table) ? 12 : 0;
        if (chance == 0 || context.getRandom().nextInt(chance) != 0) return loot;
        var blueprints=new java.util.ArrayList<>(BLUEPRINTS);
        blueprints.addAll(DnvDragonTurrets.BLUEPRINTS.values());
        loot.add(new ItemStack(blueprints.get(context.getRandom().nextInt(blueprints.size())).get()));
        return loot;
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
