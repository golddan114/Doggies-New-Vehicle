package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Internal relay entities must never become visible combat targets. */
public final class DnvTargetExclusions {
    private static final Set<ResourceLocation> INTERNAL_COMBAT_PROXIES = Set.of(
            ResourceLocation.fromNamespaceAndPath("doggievehicle", "combat_proxy")
    );

    private DnvTargetExclusions() {
    }

    public static boolean isInternalCombatProxy(Entity entity) {
        return entity != null && isInternalCombatProxy(
                ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())
        );
    }

    /**
     * Shared, cheap rejection used before any relation, geometry or threat
     * calculation. SBW wrecks remain {@link Entity#isAlive() alive}, so that
     * flag alone is not sufficient to keep destroyed turrets out of target
     * caches.
     */
    public static boolean isUnavailableCombatTarget(Entity entity) {
        return unavailable(entity, false);
    }

    /** Only the two existing boss laser adapters may acquire the otherwise harmless Spore utility. */
    public static boolean isUnavailableLaserTarget(Entity entity) {
        return unavailable(entity, true);
    }

    public static boolean isSporeScent(Entity entity) {
        if (entity == null) return false;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return com.liucodex.doggiesnewvehicle.core.DnvSporeScentIdentity.matches(
                entity.getClass().getName(), id == null ? null : id.toString());
    }

    private static boolean unavailable(Entity entity, boolean laser) {
        if (entity == null
                || entity.isRemoved()
                || !entity.isAlive()
                || isInternalCombatProxy(entity)
                || !laser && isSporeScent(entity)
                || DnvCombatCoordinator.isExcluded(entity)) {
            return true;
        }
        if (entity instanceof VehicleEntity vehicle) {
            return vehicle.isWreck() || vehicle.getHealth() <= 0.0F;
        }
        return entity instanceof LivingEntity living && living.getHealth() <= 0.0F;
    }

    static boolean isInternalCombatProxy(ResourceLocation entityTypeId) {
        return entityTypeId != null && INTERNAL_COMBAT_PROXIES.contains(entityTypeId);
    }
}
