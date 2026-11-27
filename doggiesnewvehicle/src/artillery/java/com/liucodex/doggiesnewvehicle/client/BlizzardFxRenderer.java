package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.BlizzardRig;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity;
import com.liucodex.doggiesnewvehicle.fx.DnvJetMesh;
import com.liucodex.doggiesnewvehicle.client.fx.DnvJetRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Detached cold pulse and shared executor jets, with bounded per-frame geometry. */
final class BlizzardFxRenderer {
    private BlizzardFxRenderer() { }

    static void render(DnvBlizzardEntity tank, float partial, PoseStack pose, MultiBufferSource buffers,
            double distanceSquared, double bob) {
        Vec3 origin = tank.getPosition(partial);
        if (!tank.presentationPowered() || !tank.hovering() || distanceSquared >= 48 * 48) return;
        var consumer = buffers.getBuffer(DnvJetRenderType.TYPE);
        int detail = distanceSquared < 20 * 20 ? 2 : 1;
        double tick = tank.tickCount + partial;
        for (var entry : BlizzardRig.INSTANCE.anchors.entrySet()) {
            String role = entry.getValue().role();
            if (!role.endsWith("exhaust")) continue;
            Vec3 start = tank.anchorPoint(entry.getKey(), partial).subtract(origin).add(0, bob, 0);
            Vec3 axis = tank.anchorDirection(entry.getKey(), partial);
            Vec3 side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            Vec3 up = axis.cross(side).normalize();
            double thrust = BlizzardVisualMotion.thrust(tank.presentationVelocity().horizontalDistance(),
                    tank.presentationVelocity().y, role.equals("lift_exhaust"));
            DnvJetMesh.exhaust((x, y, z, r, g, b, a) -> {
                Vec3 point = start.add(side.scale(x)).add(up.scale(y)).add(axis.scale(z));
                vertex(consumer, pose, point, 0, 0, (int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));
            }, thrust, role.equals("lift_exhaust") ? .12 : .16, tick, detail);
        }
    }

    static void pulses(DnvBlizzardEntity tank, float partial, PoseStack pose, MultiBufferSource buffers) {
        double time = tank.presentation().time(partial);
        Vec3 origin = tank.getPosition(partial);
        for (var pulse : tank.presentation().pulses()) {
            Vec3 point = pulse.point(time);
            if (point == null) continue;
            pose.pushPose();
            Vec3 offset = point.subtract(origin);
            pose.translate(offset.x, offset.y, offset.z);
            coldBolt(pose, buffers, pulse.velocity(), point.distanceTo(pulse.origin()));
            pose.popPose();
        }
    }

    static void coldBolt(PoseStack pose, MultiBufferSource buffers, Vec3 velocity, double travelled) {
        if (velocity.lengthSqr() < .001) return;
        Vec3 direction = velocity.normalize();
        var consumer = buffers.getBuffer(DnvJetRenderType.TYPE);
        laser(pose, consumer, direction.scale(-Math.min(2, travelled)), Vec3.ZERO, .09, 125, 205, 255);
        laser(pose, buffers.getBuffer(BlizzardShotRenderType.CORE),
                direction.scale(-Math.min(1.25, travelled)), Vec3.ZERO, .035, 225, 245, 255);
    }
    static void continuous(DnvBlizzardEntity tank, float partial, PoseStack pose, MultiBufferSource buffers) {
        Vec3 origin = tank.getPosition(partial);
        double time = tank.presentation().time(partial);
        for (var beam : tank.presentation().lasers()) {
            float alpha = beam.alpha(time);
            if (alpha <= 0) continue;
            Vec3 from = beam.origin().subtract(origin), end = beam.end().subtract(origin);
            laser(pose, buffers.getBuffer(BlizzardShotRenderType.BEAM_GLOW), from, end, .10, 90, 200, 255, (int)(alpha * 70));
            laser(pose, buffers.getBuffer(BlizzardShotRenderType.BEAM_CORE), from, end, .025, 225, 250, 255, (int)(alpha * 255));
        }
    }

    private static void laser(PoseStack pose, VertexConsumer consumer, Vec3 start, Vec3 end,
            double radius, int r, int g, int b) {
        laser(pose, consumer, start, end, radius, r, g, b, 255);
    }
    private static void laser(PoseStack pose, VertexConsumer consumer, Vec3 start, Vec3 end,
            double radius, int r, int g, int b, int alpha) {
        Vec3 axis = end.subtract(start).normalize();
        if (axis.lengthSqr() < .5) return;
        Vec3 side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 other = axis.cross(side).normalize();
        // Crossed planes keep the detached pulse visible from each viewing angle.
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI / 3;
            Vec3 offset = side.scale(Math.cos(angle) * radius).add(other.scale(Math.sin(angle) * radius));
            vertex(consumer, pose, start.subtract(offset), 0, 1, r, g, b, alpha);
            vertex(consumer, pose, start.add(offset), 1, 1, r, g, b, alpha);
            vertex(consumer, pose, end.add(offset), 1, 0, r, g, b, alpha);
            vertex(consumer, pose, end.subtract(offset), 0, 0, r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack pose, Vec3 p, float u, float v,
            int r, int g, int b, int a) {
        var current = pose.last();
        consumer.vertex(current.pose(), (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(current.normal(), 0, 1, 0).endVertex();
    }
}
