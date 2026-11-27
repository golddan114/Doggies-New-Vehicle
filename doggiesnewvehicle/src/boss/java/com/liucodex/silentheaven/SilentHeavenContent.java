package com.liucodex.silentheaven;

import com.liucodex.silentheaven.init.ModEntities;
import com.liucodex.silentheaven.init.ModItems;
import com.liucodex.silentheaven.init.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registers Silent Heaven as first-party content of Doggie's New Vehicle. */
public final class SilentHeavenContent {
    /**
     * Runtime textures, sounds and translation keys retain this namespace so
     * the final DNV release does not needlessly duplicate large authored assets.
     */
    public static final String ASSET_NAMESPACE = "silent_heaven";

    public static void register(IEventBus modBus) {
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
    }

    private SilentHeavenContent() {
    }
}
