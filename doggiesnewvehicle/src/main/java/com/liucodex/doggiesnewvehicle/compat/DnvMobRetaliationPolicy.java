package com.liucodex.doggiesnewvehicle.compat;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Frozen DNV target scope for vanilla-hostile retaliation proxies. */
public final class DnvMobRetaliationPolicy {
    private static final Set<ResourceLocation> SUPPORTED_TARGETS = Set.of(
            id("50_changfeng_cannon"),
            id("storm_turret"),
            id("jingzhe_turret")
    );
    private static volatile Set<ResourceLocation> bossTargets = Set.of();

    private DnvMobRetaliationPolicy() {
    }

    public static boolean supports(ResourceLocation entityTypeId) {
        return entityTypeId != null && (SUPPORTED_TARGETS.contains(entityTypeId)
                || bossTargets.contains(entityTypeId));
    }

    public static synchronized void installBossTypes(Set<ResourceLocation> types) {
        if (!bossTargets.isEmpty()) {
            throw new IllegalStateException("boss retaliation types already installed");
        }
        if (types == null || types.isEmpty()
                || types.stream().anyMatch(type -> type == null
                        || !"doggievehicle".equals(type.getNamespace()))) {
            throw new IllegalArgumentException("invalid boss retaliation types");
        }
        bossTargets = Set.copyOf(types);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("doggievehicle", path);
    }
}
