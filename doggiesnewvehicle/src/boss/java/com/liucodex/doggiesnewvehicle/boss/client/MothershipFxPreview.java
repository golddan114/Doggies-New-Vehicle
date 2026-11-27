package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.boss.MothershipDoomsday;
import com.liucodex.doggiesnewvehicle.boss.MothershipSounds;
import com.liucodex.doggiesnewvehicle.boss.fx.MothershipFx;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit, single-instance, client-only preview; never applies damage or mutates the world. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MothershipFxPreview {
    private static Session session;
    // Allocated only on explicit start and released with the preview.
    private static MultiBufferSource.BufferSource buffers;
    private static final List<PreviewSound> sounds = new ArrayList<>();

    private record Session(ClientLevel level, long started, Vec3 hull, Vec3 muzzle, Vec3 endpoint,
            List<MothershipFxRenderer.Nozzle> nozzles, boolean impact, boolean ground, MothershipDoomsday shot) { }

    private static final class PreviewSound extends AbstractTickableSoundInstance {
        private PreviewSound(SoundEvent event, Vec3 position, float volume, float pitch) {
            super(event, SoundSource.HOSTILE, RandomSource.create());
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override public void tick() { }
    }

    private MothershipFxPreview() { }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dnv_mothership_fx")
                .then(Commands.literal("start").executes(context -> start()))
                .then(Commands.literal("stop").executes(context -> { stop(); return 1; })));
    }

    private static int start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        stop();
        Vec3 forward = mc.player.getLookAngle().normalize();
        Vec3 muzzle = mc.player.getEyePosition().add(forward.scale(6));
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-8) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        List<MothershipFxRenderer.Nozzle> nozzles = new ArrayList<>(10);
        var rig = com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.get();
        Vec3 hull = muzzle.subtract(right.scale(rig.doomsday.x()))
                .subtract(up.scale(rig.doomsday.y() + rig.lift))
                .subtract(forward.scale(rig.doomsday.z()));
        for (var engine : rig.engines) {
            var offset = engine.position().sub(rig.doomsday);
            nozzles.add(new MothershipFxRenderer.Nozzle(muzzle.add(right.scale(offset.x()))
                    .add(up.scale(offset.y())).add(forward.scale(offset.z())), forward.scale(-1), engine.radius()));
        }
        HitResult hit = mc.level.clip(new ClipContext(muzzle, muzzle.add(forward.scale(50)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        MothershipDoomsday shot = new MothershipDoomsday();
        shot.begin(0);
        session = new Session(mc.level, mc.level.getGameTime(), hull, muzzle, hit.getLocation(), List.copyOf(nozzles),
                hit.getType() != HitResult.Type.MISS,
                hit instanceof BlockHitResult block && hit.getType() != HitResult.Type.MISS && block.getDirection() == Direction.UP, shot);
        buffers = MultiBufferSource.immediate(new BufferBuilder(128 * 1024));
        mc.player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.mothership_preview.start"), false);
        return 1;
    }

    private static void stop() {
        if (session != null) session.shot().cancel();
        Minecraft mc = Minecraft.getInstance();
        for (PreviewSound sound : sounds) mc.getSoundManager().stop(sound);
        sounds.clear();
        session = null;
        buffers = null;
    }

    private static void sound(Minecraft mc, SoundEvent event, Vec3 position, float volume, float pitch) {
        PreviewSound sound = new PreviewSound(event, position, volume, pitch);
        sounds.add(sound);
        mc.getSoundManager().play(sound);
    }

    private static boolean valid(Minecraft mc) {
        if (session == null) return false;
        if (mc.level != session.level() || mc.player == null) { stop(); return false; }
        long age = mc.level.getGameTime() - session.started();
        if (age < 0 || age >= MothershipFx.TOTAL_TICKS) { stop(); return false; }
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || !valid(mc) || mc.isPaused()) return;
        long age = mc.level.getGameTime() - session.started();
        Vec3 p = session.muzzle();
        var state = session.shot().advance(age, new MothershipDoomsday.Pose(p.x, p.y, p.z, 0, 0));
        int mask = state.hitMask();
        if ((state.soundMask() & 1) != 0) sound(mc, MothershipSounds.LOCK.get(), p,
                MothershipSounds.LOCK_VOLUME, MothershipSounds.LOCK_PITCH);
        if ((state.soundMask() & 2) != 0) sound(mc, MothershipSounds.CHARGE.get(), p,
                MothershipSounds.DOOMSDAY_VOLUME, 1);
        if ((state.soundMask() & 4) != 0) sound(mc, MothershipSounds.FIRE.get(), p,
                MothershipSounds.DOOMSDAY_VOLUME, 1);
        if ((state.soundMask() & 8) != 0) sound(mc, MothershipSounds.COOL.get(), p,
                MothershipSounds.DOOMSDAY_VOLUME, 1);
        if (mask != 0 && session.impact()) for (int ordinal = 1; ordinal <= 4; ordinal++) {
            if ((mask & (1 << (ordinal - 1))) != 0) sound(mc, MothershipSounds.BLAST.get(),
                    session.endpoint(), MothershipSounds.DOOMSDAY_VOLUME, 1);
        }
        if (mask != 0) {
            int ordinal = session.shot().consumedHits();
            var pulse = session.shot().hit(ordinal);
            int percent = Math.round(pulse.directDamageAgainst(100) - pulse.directDamage());
            mc.player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.mothership_preview.pulse",
                    ordinal, pulse.directDamage(), percent, pulse.blastDamage(), pulse.blastRadius(),
                    pulse.igniteGround() ? Component.translatable("message.doggiesnewvehicle.mothership_preview.ignite")
                            : Component.empty()), true);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (!valid(mc)) return;
        double age = mc.level.getGameTime() - session.started() + event.getPartialTick();
        MothershipFxRenderer.render(event.getPoseStack(), buffers, event.getCamera().getPosition(),
                session.muzzle(), session.endpoint(), session.nozzles(), age, 0.65, session.impact());
        MothershipFxRenderer.hullCharge(event.getPoseStack(), buffers, session.hull(),
                event.getCamera().getPosition(), event.getCamera().getPosition(), age);
        if (session.ground()) {
            MothershipFxRenderer.groundPulse(event.getPoseStack(), buffers, session.endpoint(),
                    event.getCamera().getPosition(), event.getCamera().getPosition(), age);
            MothershipFxRenderer.groundFirePreview(event.getPoseStack(), buffers,
                    event.getCamera().getPosition(), session.endpoint(), age);
        }
        buffers.endBatch(MothershipFxRenderer.TYPE);
    }
}
