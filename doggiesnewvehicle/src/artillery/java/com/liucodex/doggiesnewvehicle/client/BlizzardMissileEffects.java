package com.liucodex.doggiesnewvehicle.client;

import com.atsuishio.superbwarfare.init.ModSounds;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardMissile;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Spawn-discovered rocket audio also works in TV view. No particles or world scan. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT)
public final class BlizzardMissileEffects {
    private static final Map<Integer, FlightSound> ACTIVE = new LinkedHashMap<>();
    private BlizzardMissileEffects() { }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide || !(event.getEntity() instanceof DnvBlizzardMissile missile)) return;
        FlightSound old = ACTIVE.remove(missile.getId());
        if (old != null) old.finish();
        if (ACTIVE.size() < 24) ACTIVE.put(missile.getId(), new FlightSound(missile));
    }
    public static void reset() { ACTIVE.values().forEach(FlightSound::finish); ACTIVE.clear(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { reset(); return; }
        if (mc.isPaused()) return;
        var iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            var sound = iterator.next();
            if (sound.missile.isRemoved() || sound.missile.level() != mc.level) { sound.finish(); iterator.remove(); continue; }
            boolean audible = mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(sound.missile.position()) <= 48 * 48;
            if (audible && !sound.playing) { sound.playing = true; mc.getSoundManager().play(sound); }
            else if (!audible && sound.playing) { sound.finish(); iterator.remove(); }
        }
    }
    private static final class FlightSound extends AbstractTickableSoundInstance {
        private final DnvBlizzardMissile missile;
        private boolean playing;
        FlightSound(DnvBlizzardMissile missile) {
            super(ModSounds.ROCKET_FLY.get(), SoundSource.BLOCKS, RandomSource.create());
            this.missile = missile; looping = true; delay = 0; volume = .85F; pitch = 1F; position();
        }
        void finish() { Minecraft.getInstance().getSoundManager().stop(this); stop(); }
        private void position() { x = missile.getX(); y = missile.getY(); z = missile.getZ(); }
        @Override public void tick() {
            if (missile.isRemoved() || missile.level() != Minecraft.getInstance().level) { stop(); return; }
            position();
        }
    }
}
