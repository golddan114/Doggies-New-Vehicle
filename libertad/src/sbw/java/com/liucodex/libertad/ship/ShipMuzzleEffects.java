package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareOption;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** One short SBW flare per barrel, sized by the battery. */
enum ShipMuzzleEffects {
    MAIN(0xffe2af, 6, .66F, .75F, .65),
    HEAVY_SECONDARY(0xffd491, 4, .57F, .34F, .42),
    LIGHT_SECONDARY(0xffd491, 3, .54F, .18F, .32),
    AIR_DEFENSE(0xffdc97, 2, .40F, 0, .16);

    private final CannonMuzzleFlareOption flare;
    private final double speed;

    ShipMuzzleEffects(int color, int life, float fade, float expansion, double speed) {
        this.flare = new CannonMuzzleFlareOption(color, life, fade, 1, expansion);
        this.speed = speed;
    }

    void flash(ServerLevel level, Vec3 muzzle, Vec3 forward) {
        Vec3 at = muzzle.add(forward.scale(.16));
        // Zero count encodes a single particle with an explicit velocity. Long-distance delivery
        // also covers Libertad's chase camera, which is beyond the normal particle cutoff.
        ParticleTool.sendParticle(level, flare, at.x, at.y, at.z, 0, forward.x, forward.y, forward.z, speed, true);
    }
}
