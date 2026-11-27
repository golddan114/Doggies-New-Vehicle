package com.liucodex.doggiesnewvehicle.platform;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvHoverRoutePlatformServices;
import com.liucodex.doggiesnewvehicle.compat.DnvHoverPlatformGunNetwork;
import com.liucodex.doggiesnewvehicle.compat.DnvTacticalRouteServices;
import com.liucodex.doggiesnewvehicle.compat.DnvTacticalRouteNetwork;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Owns the platform entity registration and its attributes. */
public final class DnvPlatformContent {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DoggiesNewVehicle.ENTITY_NAMESPACE);

    public static final RegistryObject<EntityType<DnvHoverRoutePlatformEntity>> HOVER_ROUTE_PLATFORM =
            ENTITY_TYPES.register("hover_route_platform", () ->
                    EntityType.Builder.<DnvHoverRoutePlatformEntity>of(
                                    DnvHoverRoutePlatformEntity::new,
                                    MobCategory.MISC)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .sized(4.0F, 1.5F)
                            .build("doggievehicle:hover_route_platform"));

    private DnvPlatformContent() {
    }

    public static void register(IEventBus modBus) {
        DnvTacticalRouteNetwork.initialize();
        DnvHoverPlatformGunNetwork.initialize();
        DnvTacticalRouteServices.installPlatformValidation(
                DnvHoverRoutePlatformServices::validation);
        ENTITY_TYPES.register(modBus);
    }

    @Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void createAttributes(EntityAttributeCreationEvent event) {
            event.put(HOVER_ROUTE_PLATFORM.get(),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 200.0D)
                            .add(Attributes.MOVEMENT_SPEED, 0.0D)
                            .build());
        }
    }
}
