package com.liucodex.doggiesnewvehicle.init;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvCombatProxyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    ForgeRegistries.ENTITY_TYPES,
                    DoggiesNewVehicle.ENTITY_NAMESPACE);

    public static final RegistryObject<EntityType<DnvCombatProxyEntity>> COMBAT_PROXY =
            ENTITY_TYPES.register("combat_proxy", () ->
                    EntityType.Builder.<DnvCombatProxyEntity>of(
                                    DnvCombatProxyEntity::new,
                                    MobCategory.MISC)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .sized(0.6F, 1.8F)
                            .build("doggievehicle:combat_proxy"));

    private ModEntities() {
    }

    @Mod.EventBusSubscriber(
            modid = DoggiesNewVehicle.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        static void createAttributes(EntityAttributeCreationEvent event) {
            event.put(
                    COMBAT_PROXY.get(),
                    Mob.createMobAttributes()
                            .add(Attributes.MAX_HEALTH, 20.0D)
                            .add(Attributes.MOVEMENT_SPEED, 0.0D)
                            .build());
        }
    }
}
