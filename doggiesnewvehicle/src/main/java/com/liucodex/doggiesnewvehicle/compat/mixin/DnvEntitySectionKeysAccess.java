package com.liucodex.doggiesnewvehicle.compat.mixin;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntitySectionStorage.class)
public interface DnvEntitySectionKeysAccess {
    @Accessor("sectionIds")
    LongSortedSet dnv$getSectionIds();
}
