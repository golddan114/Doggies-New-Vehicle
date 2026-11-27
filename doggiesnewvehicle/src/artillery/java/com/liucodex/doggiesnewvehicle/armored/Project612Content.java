package com.liucodex.doggiesnewvehicle.armored;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Player tank-destroyer identity; native containers own deployment and inventory. */
public final class Project612Content {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "doggievehicle");
    public static final RegistryObject<EntityType<DnvProject612Entity>> TANK = TYPES.register("project612", () ->
            EntityType.Builder.<DnvProject612Entity>of(DnvProject612Entity::new, MobCategory.MISC)
                    .sized((float) (6.125 * Project612Rig.INSTANCE.scale), (float) (5.325 * Project612Rig.INSTANCE.scale))
                    .clientTrackingRange(16).updateInterval(2).build(DnvProject612Entity.TYPE_ID));
    private Project612Content() { }
}
