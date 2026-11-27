package com.liucodex.libertad.client;

import com.liucodex.libertad.Libertad;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Short, depth-tested ribbons from completed movements of tracked naval shells. */
@Mod.EventBusSubscriber(modid = Libertad.ID, value = Dist.CLIENT)
public final class ShellTracer {
    static final int LIFE_TICKS = 4;
    private static final int MAX_SEGMENTS = 192;
    private static final double MAX_VIEW_DISTANCE = 240;
    private static final ArrayDeque<Segment> segments = new ArrayDeque<>();
    private static final MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static ClientLevel world;
    private static long frameTick;

    private ShellTracer() {}

    public static void accept(ClientLevel level, Vec3 from, Vec3 to) {
        if (world != level) {
            segments.clear();
            world = level;
        }
        double distance = from.distanceToSqr(to);
        if (!(distance > 1e-8 && distance <= 24 * 24)) return;
        if (segments.size() >= MAX_SEGMENTS) segments.removeFirst();
        segments.addLast(new Segment(frameTick, from, to));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        // Minecraft advances level time after ticking entities. Both capture and rendering
        // must use the same client tick so the newest segment still needs interpolation.
        if (event.phase == TickEvent.Phase.START && !Minecraft.getInstance().isPaused()) frameTick++;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        segments.clear();
        world = null;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || segments.isEmpty()) return;
        var client = Minecraft.getInstance();
        if (client.level != world) {
            segments.clear();
            world = null;
            return;
        }
        long tick = frameTick;
        while (!segments.isEmpty() && tick - segments.peekFirst().tick() >= LIFE_TICKS) segments.removeFirst();
        if (segments.isEmpty()) return;

        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick();
        boolean minimal = client.options.particles().get() == ParticleStatus.MINIMAL;
        var out = buffer.getBuffer(RenderType.lightning());
        for (Segment segment : segments) {
            Vec3 from = segment.from();
            Vec3 to = segment.visibleTip(tick, partial);
            float alpha = segment.opacity(tick, partial);
            if (alpha <= .02F || from.distanceToSqr(to) < 1e-8
                    || from.distanceToSqr(camera) > MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE) continue;
            Vec3 axis = to.subtract(from);
            Vec3 side = axis.cross(camera.subtract(from));
            if (side.lengthSqr() < 1e-10) side = axis.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-10) side = new Vec3(1, 0, 0);
            side = side.normalize();
            Vec3 a = from.subtract(camera), b = to.subtract(camera);
            if (!minimal) ribbon(out, pose, a, b, side.scale(.10), 255, 184, 83, alpha * .15F);
            ribbon(out, pose, a, b, side.scale(.035), 255, 241, 212, alpha * .64F);
        }
        buffer.endBatch(RenderType.lightning());
    }

    private static void ribbon(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, int r, int g, int b, float alpha) {
        int opacity = Math.round(alpha * 255);
        vertex(out, pose, from.add(side), r, g, b, opacity);
        vertex(out, pose, to.add(side), r, g, b, opacity);
        vertex(out, pose, to.subtract(side), r, g, b, opacity);
        vertex(out, pose, from.subtract(side), r, g, b, opacity);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vec3 p, int r, int g, int b, int alpha) {
        out.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, alpha).endVertex();
    }

    record Segment(long tick, Vec3 from, Vec3 to) {
        Vec3 visibleTip(long renderTick, float partial) {
            return from.lerp(to, Mth.clamp(renderTick - tick + partial, 0, 1));
        }

        float opacity(long renderTick, float partial) {
            float remaining = Mth.clamp(1 - (renderTick - tick + partial) / LIFE_TICKS, 0, 1);
            return remaining * remaining;
        }
    }
}
