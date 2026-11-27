package com.liucodex.libertad.ship;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** One SavedData entry per dimension, including when the ship's chunk is unloaded. */
public final class ShipPresence extends SavedData {
    private UUID ship;

    public static ShipPresence get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipPresence::read, ShipPresence::new, "libertad_dimension_ship");
    }

    private static ShipPresence read(CompoundTag tag) {
        var presence = new ShipPresence();
        if (tag.hasUUID("Ship")) presence.ship = tag.getUUID("Ship");
        return presence;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        if (ship != null) tag.putUUID("Ship", ship);
        return tag;
    }

    public boolean occupied() { return ship != null; }

    boolean matches(UUID id) { return id.equals(ship); }

    void release(UUID id) {
        if (matches(id)) {
            ship = null;
            setDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LibertadEntity ship) || !(event.getLevel() instanceof ServerLevel level)) return;
        ShipPresence state = get(level);
        if (state.occupied() && (!state.matches(ship.getUUID()) || level.getEntity(ship.getUUID()) != null)) {
            event.setCanceled(true);
        }
    }

    // Forge calls onAddedToWorld only after world admission, including cancellations by other listeners.
    void accept(UUID id) {
        if (ship == null) {
            ship = id;
            setDirty();
        }
    }
}
