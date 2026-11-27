package com.liucodex.doggiesnewvehicle.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class ModCreativeTabs {
    public enum Category { WEAPONS, VEHICLES, DEPLOYERS, BLUEPRINTS, AMMUNITION, MATERIALS, TOOLS }

    private static final BiConsumer<Category, CreativeModeTab.Output> EMPTY_ARTILLERY_ITEMS = (category, output) -> {};
    private static volatile BiConsumer<Category, CreativeModeTab.Output> artilleryItems = EMPTY_ARTILLERY_ITEMS;
    private static final BiConsumer<Category, CreativeModeTab.Output> EMPTY_BOSS_ITEMS = (category, output) -> {};
    private static volatile BiConsumer<Category, CreativeModeTab.Output> bossItems = EMPTY_BOSS_ITEMS;
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoggiesNewVehicle.MOD_ID);

    public static final RegistryObject<CreativeModeTab> DOGGIES_VEHICLE_TAB =
            CREATIVE_MODE_TABS.register("doggies_vehicle", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable(
                                    "creativetab.doggiesnewvehicle.doggies_vehicle"))
                            .icon(() -> new ItemStack(ModItems.DNV_ICON.get()))
                            .displayItems((parameters, output) -> {
                                for (Category category : Category.values()) {
                                    if (category == Category.WEAPONS) output.accept(ModItems.DSX1.get());
                                    artilleryItems.accept(category, output);
                                    bossItems.accept(category, output);
                                    if (category == Category.TOOLS) {
                                        output.accept(com.liucodex.doggiesnewvehicle.guide.DnvWrittenBook.create("zh_cn"));
                                        output.accept(com.liucodex.doggiesnewvehicle.guide.DnvWrittenBook.create("en_us"));
                                        output.accept(ModItems.VEHICLE_CREW_CORE.get());
                                        output.accept(ModItems.VEHICLE_OPERATIONS.get());
                                    }
                                }
                            })
                            .build());

    private ModCreativeTabs() {
    }

    public static void installArtilleryItems(BiConsumer<Category, CreativeModeTab.Output> items) {
        if (artilleryItems != EMPTY_ARTILLERY_ITEMS) {
            throw new IllegalStateException("artillery creative items already installed");
        }
        artilleryItems = Objects.requireNonNull(items, "items");
    }

    public static void installBossItems(BiConsumer<Category, CreativeModeTab.Output> items) {
        if (bossItems != EMPTY_BOSS_ITEMS) {
            throw new IllegalStateException("boss creative items already installed");
        }
        bossItems = Objects.requireNonNull(items, "items");
    }
}
