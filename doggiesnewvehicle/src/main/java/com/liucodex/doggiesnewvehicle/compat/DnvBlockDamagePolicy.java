package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.config.server.ExplosionConfig;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shared read-only admission. Each terrain owner still performs its own protected world writes. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvBlockDamagePolicy {
    private static final String ORIGIN = "DnvBlockDamageOrigin";
    private static final String SMALL_AP = "DnvSmallApImpact";
    private static final String NAVAL_SHOT = "LibertadShot";
    private DnvBlockDamagePolicy() { }

    public static boolean allowed(Level level, Entity actor) {
        return ExplosionConfig.EXPLOSION_DESTROY.get() && terrainAllowed(level, actor);
    }

    public static boolean terrainAllowed(Level level, Entity actor) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && (actor == null || ForgeEventFactory.getMobGriefingEvent(level, actor));
    }

    public static boolean owned(Entity source) {
        for (int depth = 0; source != null && depth < 8; depth++) {
            String type = source.getClass().getName();
            if (source.getPersistentData().getBoolean(ORIGIN) || source.getPersistentData().contains(NAVAL_SHOT, 10)
                    || type.startsWith("com.liucodex.doggiesnewvehicle.")
                    || type.startsWith("com.liucodex.noise.") || type.startsWith("com.liucodex.silentheaven.")) return true;
            if (!(source instanceof Projectile projectile) || projectile.getOwner() == source) break;
            source = projectile.getOwner();
        }
        return false;
    }

    /** Persist origin, never a cached permission: a rule change while airborne applies at impact. */
    public static void mark(Entity projectile, Entity host) {
        if (owned(host)) projectile.getPersistentData().putBoolean(ORIGIN, true);
    }

    /** Snapshot the fired ammunition, never the weapon held later or a cached terrain permission. */
    public static void markShot(Entity projectile, com.atsuishio.superbwarfare.data.gun.ShootParameters shot) {
        mark(projectile,shot.ammoSupplier);
        if(projectile instanceof com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
                && DnvTurretShells.lightTurret(shot.ammoSupplier) && "AP".equals(shot.data.get(com.atsuishio.superbwarfare.data.gun.GunProp.SHELL_TYPE)))
            projectile.getPersistentData().putBoolean(SMALL_AP,true);
    }

    public static boolean deterministicApImpact(Entity source) {
        return source.getPersistentData().getBoolean(SMALL_AP) && owned(source);
    }

    /** SBW can defer crater writes past the initial explosion and its Forge event. */
    public static Collection<BlockPos> nativeBlastBlocks(Level level, Entity source, Vec3 center, Collection<BlockPos> blocks) {
        if (!owned(source)) return blocks;
        if (!allowed(level, source)) return List.of();
        float radius = source.getPersistentData().getCompound(NAVAL_SHOT).getFloat("BlockBlastRadius");
        return blocks.stream().filter(pos -> level.hasChunkAt(pos)
                && (radius <= 0 || Vec3.atCenterOf(pos).distanceToSqr(center) <= radius * radius)).toList();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Projectile projectile)
            mark(projectile, projectile.getOwner());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void detonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source instanceof com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
                && DnvTurretShells.managed(source))
            event.getAffectedEntities().removeIf(target -> DnvTurretShells.directHeTarget(source, target));
        if (owned(source) && !allowed(event.getLevel(), source)) event.getAffectedBlocks().clear();
    }
}
