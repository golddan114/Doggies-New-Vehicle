package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.tools.DamageHandlerKt;
import com.liucodex.doggiesnewvehicle.api.combat.DnvProjectileLifetime;
import com.liucodex.doggiesnewvehicle.api.combat.DnvProjectilePath;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Shot-local boundaries for DNV's borrowed SBW projectiles. */
public final class DnvTurretShells {
    private static final Set<String> TYPES = Set.of("m1_yunlong", "dragon_bofors_twin", "dragon_bofors_quad",
            "dragon_flak38", "dragon_flak88", "dragon_flak40", "50_changfeng_cannon", "storm_turret", "jingzhe_turret",
            "apocalypse", "apocalypse_ai", "project612");
    private static final ResourceLocation DSX1 = ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "dsx1");
    private static final String EXPIRY = DnvProjectileLifetime.EXPIRY, HE = "DnvLightHeShell", DIRECT = "DnvLightHeDirect";
    private DnvTurretShells() {}

    public static boolean lightTurret(Entity host) {
        if (host == null) return false;
        String id = EntityType.getKey(host.getType()).toString();
        return id.equals("doggievehicle:dragon_bofors_twin") || id.equals("doggievehicle:dragon_bofors_quad")
                || id.equals("doggievehicle:dragon_flak38");
    }
    public static void mark(Entity entity, ShootParameters shot) {
        boolean waterShot = shot.ammoSupplier instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity
                && EntityType.getKey(shot.ammoSupplier.getType()).getNamespace().equals("doggievehicle")
                && (entity instanceof ProjectileEntity
                || entity instanceof com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity
                || entity instanceof SmallCannonShellEntity);
        if (waterShot)
            entity.getPersistentData().putBoolean(DnvProjectileLifetime.WATER_SENSITIVE, true);
        if (entity instanceof ProjectileEntity bullet
                && (waterShot || DSX1.equals(BuiltInRegistries.ITEM.getKey(shot.data.item)) || vehicleSource(shot.ammoSupplier))) {
            track(bullet);
            return;
        }
        if (!(entity instanceof FastThrowableProjectile shell) || !(waterShot || vehicleSource(shot.ammoSupplier))) return;
        track(shell);
        if (shell instanceof SmallCannonShellEntity && lightTurret(shot.ammoSupplier)) {
            if ("HE".equals(shot.data.get(GunProp.SHELL_TYPE))) entity.getPersistentData().putBoolean(HE, true);
            // Native SmallCannonShell ignores BypassesArmor; use the existing direct-hit bridge.
            else if ("AP".equals(shot.data.get(GunProp.SHELL_TYPE))) {
                String mount = EntityType.getKey(shot.ammoSupplier.getType()).getPath();
                float penetration = mount.equals("dragon_flak38") ? .05F : .03F;
                com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(entity, penetration);
            }
        }
    }

    private static boolean vehicleSource(Entity source) {
        if (source == null) return false;
        var id = EntityType.getKey(source.getType());
        return id.getNamespace().equals("doggievehicle") && TYPES.contains(id.getPath());
    }

    public static void track(FastThrowableProjectile shell) {
        // Native Life is saved, but its timeout compares vanilla tickCount, which restarts after reload.
        deadline(shell, (long) shell.getLife() - Math.max(0, shell.getSyncedTick()));
    }

    public static void track(ProjectileEntity bullet) {
        deadline(bullet, bullet.getLife());
    }

    private static void deadline(Entity projectile, long remaining) {
        DnvProjectileLifetime.track(projectile.getPersistentData(), projectile.level().getGameTime(), remaining);
    }

    public static double stepLimit(Entity shell) {
        if (!(shell.level() instanceof ServerLevel level) || !managed(shell)) return 1;
        var from = shell.position();
        Vec3 to = from.add(shell.getDeltaMovement());
        double loaded = DnvProjectilePath.limit(level, from, to);
        return shell.getPersistentData().getBoolean(DnvProjectileLifetime.WATER_SENSITIVE) && loaded > 0
                ? loaded * com.liucodex.doggiesnewvehicle.api.combat.DnvWaterEntry.limit(level, shell, from, from.lerp(to, loaded))
                : loaded;
    }

    @Nullable public static Vec3 limitSecondaryTrace(Entity shell, Vec3 from, Vec3 motion) {
        if (!(shell.level() instanceof ServerLevel level) || !managed(shell)) return motion;
        double limit = DnvProjectilePath.limit(level, from, from.add(motion));
        if (limit > 0 && shell.getPersistentData().getBoolean(DnvProjectileLifetime.WATER_SENSITIVE))
            limit *= com.liucodex.doggiesnewvehicle.api.combat.DnvWaterEntry.limit(level, shell, from, from.add(motion.scale(limit)));
        if (limit == 0) return null;
        return limit == 1 ? motion : motion.scale(limit);
    }

    public static boolean blastAreaReady(Entity source, Vec3 point, float radius) {
        return !(source instanceof FastThrowableProjectile shell)
                || !(shell.level() instanceof ServerLevel level)
                || !managed(shell)
                || DnvProjectilePath.blastLoaded(level, point, radius);
    }
    public static boolean beforeTick(Entity projectile) {
        if (projectile.level().isClientSide || !managed(projectile)) return false;
        var data = projectile.getPersistentData();
        if (!data.contains(EXPIRY) && projectile instanceof FastThrowableProjectile shell) track(shell);
        if (projectile.isRemoved() || projectile.level().getGameTime() > data.getLong(EXPIRY)) {
            projectile.discard();
            return true;
        }
        return false;
    }
    public static boolean managed(Entity entity) {
        var data = entity.getPersistentData();
        return data.contains(EXPIRY) || data.getBoolean(DnvVlsFlight.OWNED);
    }
    public static boolean emptyApBlast(SmallCannonShellEntity shell) {
        return DnvBlockDamagePolicy.deterministicApImpact(shell)
                && shell.getExplosionDamage() <= 0 && shell.getExplosionRadius() <= 0;
    }

    /** A confirmed HE collision has its own explosion-type direct leg, independent of blast falloff. */
    public static void directHe(SmallCannonShellEntity shell, EntityHitResult hit) {
        if (shell.level().isClientSide || !shell.getPersistentData().getBoolean(HE)
                || shell.getPersistentData().hasUUID(DIRECT) || shell.getExplosionDamage() <= 0) return;
        Entity target = hit.getEntity();
        shell.getPersistentData().putUUID(DIRECT, target.getUUID());
        DamageHandlerKt.forceHurt(target, ModDamageTypes.causeCustomExplosionDamage(
                shell.level().registryAccess(), shell, shell.getOwner()), shell.getExplosionDamage());
    }
    public static boolean directHeTarget(Entity source, Entity target) {
        return source != null && source.getPersistentData().hasUUID(DIRECT)
                && source.getPersistentData().getUUID(DIRECT).equals(target.getUUID());
    }
}
