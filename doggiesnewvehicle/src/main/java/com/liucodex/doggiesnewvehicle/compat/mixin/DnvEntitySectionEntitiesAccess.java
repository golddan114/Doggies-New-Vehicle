package com.liucodex.doggiesnewvehicle.compat.mixin;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntitySection.class)
public interface DnvEntitySectionEntitiesAccess {
    @Accessor("storage")
    ClassInstanceMultiMap<Entity> dnv$getStorage();
}
