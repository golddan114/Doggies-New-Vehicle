package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverCubeBossEntity;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvMobRetaliationPolicy;
import com.liucodex.doggiesnewvehicle.init.ModCreativeTabs;
import com.liucodex.doggiesnewvehicle.item.DnvMothershipDeployerItem;
import com.liucodex.noise.NoiseContent;
import com.liucodex.silentheaven.SilentHeavenContent;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers optional CUBE, mothership, Noise and Silent Heaven content together. */
public final class DnvBossContent {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DoggiesNewVehicle.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,
                    DoggiesNewVehicle.ENTITY_NAMESPACE);

    public static final RegistryObject<EntityType<DnvHoverCubeBossEntity>> HOVER_CUBE_BOSS =
            ENTITY_TYPES.register("hover_cube_boss", () ->
                    EntityType.Builder.<DnvHoverCubeBossEntity>of(
                                    DnvHoverCubeBossEntity::new, MobCategory.MISC)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .sized(3.0F, 3.0F)
                            .build("doggievehicle:hover_cube_boss"));

    public static final RegistryObject<EntityType<DnvMothershipEntity>> MOTHERSHIP =
            ENTITY_TYPES.register("ses_executor_of_truth", () ->
                    EntityType.Builder.<DnvMothershipEntity>of(DnvMothershipEntity::new, MobCategory.MISC)
                            .clientTrackingRange(192).updateInterval(2).fireImmune()
                            .sized(12.022F,6.706F).build("doggievehicle:ses_executor_of_truth"));

    public static final RegistryObject<Item> MOTHERSHIP_DEPLOYER = ITEMS.register("ses_executor_of_truth_deployer",
            () -> new DnvMothershipDeployerItem(new Item.Properties().stacksTo(1)));

    private DnvBossContent() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        MothershipSounds.SOUNDS.register(modBus);
        SilentHeavenContent.register(modBus);
        NoiseContent.register(modBus);
        DnvMobRetaliationPolicy.installBossTypes(Set.of(
                ResourceLocation.fromNamespaceAndPath(DoggiesNewVehicle.ENTITY_NAMESPACE,
                        "silent_heaven"),
                ResourceLocation.fromNamespaceAndPath(DoggiesNewVehicle.ENTITY_NAMESPACE,
                        "noise"),
                ResourceLocation.fromNamespaceAndPath(DoggiesNewVehicle.ENTITY_NAMESPACE,
                        "ses_executor_of_truth")));
        ModCreativeTabs.installBossItems((category, output) -> {
            if (category == ModCreativeTabs.Category.DEPLOYERS) {
                output.accept(MOTHERSHIP_DEPLOYER.get());
                output.accept(com.liucodex.silentheaven.init.ModItems.SILENT_HEAVEN_DEPLOYER.get());
                output.accept(com.liucodex.noise.init.ModItems.NOISE_DEPLOYER.get());
            } else if (category == ModCreativeTabs.Category.TOOLS) {
                output.accept(com.liucodex.silentheaven.init.ModItems.SILENT_HEAVEN_BEHAVIOR_CONTROLLER.get());
                output.accept(com.liucodex.noise.init.ModItems.NOISE_BEHAVIOR_CONTROLLER.get());
            }
        });
    }

    @Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void createAttributes(EntityAttributeCreationEvent event) {
            event.put(HOVER_CUBE_BOSS.get(),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 400.0D)
                            .add(Attributes.MOVEMENT_SPEED, 0.0D)
                            .build());
        }
    }
}
