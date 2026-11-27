package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvCombatProxyEntity;
import com.liucodex.doggiesnewvehicle.init.ModEntities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Gives attacked vanilla hostile mobs a living relay for non-living DNV units. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvVanillaMobRetaliationBridge {
    private static final Map<UUID, UUID> PROXIES_BY_TARGET = new HashMap<>();

    private DnvVanillaMobRetaliationBridge() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy)
                || !(mob.level() instanceof ServerLevel level)
                || !isVanilla(mob)
                || attacker == null
                || !isSupportedDnvTarget(attacker)) {
            return;
        }
        DnvCombatProxyEntity proxy = acquireProxy(level, attacker);
        if (proxy != null) {
            mob.setTarget(proxy);
        }
    }

    private static boolean isVanilla(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static boolean isSupportedDnvTarget(Entity target) {
        return DnvMobRetaliationPolicy.supports(
                ForgeRegistries.ENTITY_TYPES.getKey(target.getType()));
    }

    @Nullable
    private static DnvCombatProxyEntity acquireProxy(
            ServerLevel level,
            Entity target) {
        DnvCombatProxyEntity existing = resolveProxy(level, target);
        if (existing != null) {
            return existing;
        }
        DnvCombatProxyEntity created = ModEntities.COMBAT_PROXY.get().create(level);
        if (created == null) {
            return null;
        }
        created.bind(target);
        if (!level.addFreshEntity(created)) {
            created.discard();
            return null;
        }
        PROXIES_BY_TARGET.put(target.getUUID(), created.getUUID());
        return created;
    }

    @Nullable
    private static DnvCombatProxyEntity resolveProxy(
            ServerLevel level,
            Entity target) {
        UUID proxyId = PROXIES_BY_TARGET.get(target.getUUID());
        Entity entity = proxyId == null ? null : level.getEntity(proxyId);
        if (entity instanceof DnvCombatProxyEntity proxy
                && proxy.isAlive()
                && proxy.represents(target)) {
            return proxy;
        }
        PROXIES_BY_TARGET.remove(target.getUUID());
        return null;
    }

    public static void forgetProxy(UUID proxyId) {
        PROXIES_BY_TARGET.values().removeIf(proxyId::equals);
    }
}
