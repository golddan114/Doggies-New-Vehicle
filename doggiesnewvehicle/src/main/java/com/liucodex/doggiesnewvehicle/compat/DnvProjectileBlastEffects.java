package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCameraShakeSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Explicit per-shot presentation opt-in shared by artillery and boss content. No damage/radius writes. */
public final class DnvProjectileBlastEffects {
    private static final String KEY = "DnvBlastPresentation";
    private static final String NO_SHAKE = "DnvSuppressCameraShake";
    private static final ThreadLocal<Boolean> PARTICLE_NO_SHAKE = new ThreadLocal<>();
    private DnvProjectileBlastEffects() { }

    public static void rocket(Entity projectile) { projectile.getPersistentData().putInt(KEY, 1); }
    public static void cannon(Entity projectile) { projectile.getPersistentData().putInt(KEY, 2); }

    /** Persist the shot policy even if its owner unloads before impact. */
    public static void suppressCameraShake(Entity projectile) {
        projectile.getPersistentData().putBoolean(NO_SHAKE, true);
    }

    public static boolean cameraShakeAllowed(Entity source) {
        if (suppressed(source)) return false;
        return !(source instanceof Projectile projectile) || !suppressed(projectile.getOwner());
    }

    private static boolean suppressed(Entity source) {
        return source != null && (source.getPersistentData().getBoolean(NO_SHAKE)
                || source instanceof DnvCameraShakeSource policy && !policy.dnvCameraShakeAllowed());
    }

    /** Native particles omit their source argument. Scope only their synchronous shake call, with exception cleanup. */
    public static void spawnExplosionParticles(Entity source, ParticleTool.ParticleType type, Level level, Vec3 point) {
        Boolean previous = PARTICLE_NO_SHAKE.get();
        PARTICLE_NO_SHAKE.set(!cameraShakeAllowed(source));
        try {
            ParticleTool.spawnExplosionParticles(type, level, point);
        } finally {
            if (previous == null) PARTICLE_NO_SHAKE.remove();
            else PARTICLE_NO_SHAKE.set(previous);
        }
    }

    public static boolean particleShakeSuppressed() { return Boolean.TRUE.equals(PARTICLE_NO_SHAKE.get()); }

    public static void apply(CustomExplosion.Builder builder, Entity source) {
        if (!(source instanceof Projectile)) return;
        int style = source.getPersistentData().getInt(KEY);
        if (style == 1) builder.withParticleType(ParticleTool.ParticleType.MEDIUM);
        else if (style == 2) builder.withParticleType(ParticleTool.ParticleType.LARGE);
    }
}
