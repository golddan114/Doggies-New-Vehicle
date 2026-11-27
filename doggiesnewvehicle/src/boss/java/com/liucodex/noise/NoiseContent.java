package com.liucodex.noise;

import com.liucodex.noise.init.ModEntities;
import com.liucodex.noise.init.ModItems;
import com.liucodex.noise.init.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registers Noise as an independent first-party DNV entity. */
public final class NoiseContent {
    /**
     * Runtime textures, sounds and translation keys retain this namespace so
     * the final DNV release does not needlessly duplicate large authored assets.
     */
    public static final String ASSET_NAMESPACE = "noise";

    public static void register(IEventBus modBus) {
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
    }

    private NoiseContent() {
    }
}

