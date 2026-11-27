package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.boss.fx.MothershipFx;
import com.liucodex.doggiesnewvehicle.boss.fx.MothershipFxMesh;
import com.liucodex.doggiesnewvehicle.boss.MothershipArrival;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Camera-relative world rendering. No entity creation, world scans, collision queries or network packets. */
public final class MothershipFxRenderer {
    public static final RenderType TYPE = MothershipFxRenderType.TYPE;

    public record Nozzle(Vec3 origin, Vec3 exhaustDirection, double radius) { }

    private MothershipFxRenderer() { }

    /** Caller provides interpolated world anchors and a solid-surface-clipped endpoint. */
    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera,
            Vec3 muzzle, Vec3 endpoint, List<Nozzle> nozzles, double elapsed, double throttle, boolean impact) {
        renderAt(pose,buffers,camera,camera,muzzle,endpoint,nozzles,elapsed,throttle,impact);
    }

    /** EntityRenderer already translates to its origin; distance detail still uses the real camera. */
    public static void renderAt(PoseStack pose, MultiBufferSource buffers, Vec3 origin, Vec3 camera,
            Vec3 muzzle, Vec3 endpoint, List<Nozzle> nozzles, double elapsed, double throttle, boolean impact) {
        renderAt(pose,buffers,origin,camera,muzzle,endpoint,nozzles,elapsed,throttle,impact,elapsed);
    }
    public static void renderAt(PoseStack pose, MultiBufferSource buffers, Vec3 origin, Vec3 camera,
            Vec3 muzzle, Vec3 endpoint, List<Nozzle> nozzles, double elapsed, double throttle, boolean impact,double animationTick) {
        renderSample(pose,buffers,origin,camera,muzzle,endpoint,nozzles,elapsed,
                MothershipFx.sample(elapsed,throttle),impact,animationTick);
    }
    public static void renderAbortAt(PoseStack pose, MultiBufferSource buffers, Vec3 origin, Vec3 camera,
            Vec3 muzzle, Vec3 endpoint, List<Nozzle> nozzles, double interrupted,double age,double throttle,double animationTick) {
        renderSample(pose,buffers,origin,camera,muzzle,endpoint,nozzles,interrupted,
                MothershipFx.sampleAbort(interrupted,age,throttle),false,animationTick);
    }
    private static void renderSample(PoseStack pose, MultiBufferSource buffers, Vec3 origin, Vec3 camera,
            Vec3 muzzle,Vec3 endpoint,List<Nozzle> nozzles,double elapsed,MothershipFx.Sample sample,boolean impact,double animationTick) {
        if (!finite(origin) || !finite(camera) || !finite(muzzle) || !finite(endpoint) || !Double.isFinite(elapsed)) return;
        Vec3 delta = endpoint.subtract(muzzle);
        double length = MothershipFx.length(delta.length());
        Vec3 direction = delta.normalize();
        MothershipFx.Detail detail = MothershipFx.detail(segmentDistanceSquared(camera, muzzle, direction, length));
        if (length > 0.01 && detail != MothershipFx.Detail.HIDDEN) {
            pose.pushPose();
            orient(pose, muzzle.subtract(origin), direction);
            MothershipFxMesh.lance(sink(pose, buffers.getBuffer(TYPE)), sample, length, detail, elapsed, impact);
            pose.popPose();
        }
        for (int i = 0; i < Math.min(nozzles.size(), MothershipFx.MAX_NOZZLES); i++) {
            Nozzle nozzle = nozzles.get(i);
            if (!finite(nozzle.origin()) || !finite(nozzle.exhaustDirection()) || nozzle.exhaustDirection().lengthSqr() < 1.0E-8) continue;
            Vec3 gatherEnd = muzzle.subtract(nozzle.origin());
            MothershipFx.Detail gatherDetail = MothershipFx.detail(segmentDistanceSquared(camera,
                    nozzle.origin(), gatherEnd.normalize(), gatherEnd.length()));
            if (gatherDetail != MothershipFx.Detail.HIDDEN) {
                pose.pushPose();
                Vec3 relative = nozzle.origin().subtract(origin);
                pose.translate(relative.x, relative.y, relative.z);
                MothershipFxMesh.gather(sink(pose,buffers.getBuffer(TYPE)), sample, gatherEnd.x, gatherEnd.y,
                        gatherEnd.z, i, elapsed, gatherDetail);
                pose.popPose();
            }
            detail = MothershipFx.detail(camera.distanceToSqr(nozzle.origin()));
            if (detail == MothershipFx.Detail.HIDDEN) continue;
            pose.pushPose();
            orient(pose, nozzle.origin().subtract(origin), nozzle.exhaustDirection().normalize());
            MothershipFxMesh.exhaust(sink(pose, buffers.getBuffer(TYPE)), sample.thrust(), nozzle.radius(), animationTick + i * 7, detail);
            pose.popPose();
        }
    }

    public static void dropCharge(PoseStack pose,MultiBufferSource buffers,Vec3 anchor,Vec3 origin,Vec3 camera,double charge,double tick) {
        if(MothershipFx.detail(camera.distanceToSqr(anchor))==MothershipFx.Detail.HIDDEN||charge<=0)return;
        pose.pushPose();Vec3 local=anchor.subtract(origin);pose.translate(local.x,local.y,local.z);
        MothershipFxMesh.dropCharge(sink(pose,buffers.getBuffer(TYPE)),charge,tick);pose.popPose();
    }

    public static void groundPulse(PoseStack pose, MultiBufferSource buffers, Vec3 ground, Vec3 origin,
            Vec3 camera, double elapsed) {
        if (!finite(ground) || !finite(origin) || !finite(camera) || !Double.isFinite(elapsed)) return;
        var detail = MothershipFx.detail(camera.distanceToSqr(ground));
        if (detail == MothershipFx.Detail.HIDDEN) return;
        pose.pushPose();
        Vec3 relative = ground.subtract(origin);
        pose.translate(relative.x,relative.y,relative.z);
        MothershipFxMesh.groundPulse(sink(pose,buffers.getBuffer(TYPE)),elapsed,detail);
        pose.popPose();
    }

    public static void hullCharge(PoseStack pose, MultiBufferSource buffers, Vec3 hull, Vec3 origin,
            Vec3 camera, double elapsed) {
        hullCharge(pose,buffers,hull,origin,camera,elapsed,MothershipFx.sample(elapsed,0));
    }
    public static void hullCharge(PoseStack pose, MultiBufferSource buffers, Vec3 hull, Vec3 origin,
            Vec3 camera,double elapsed,MothershipFx.Sample sample) {
        if (!finite(hull) || !finite(origin) || !finite(camera) || !Double.isFinite(elapsed)) return;
        var detail = MothershipFx.detail(camera.distanceToSqr(hull));
        if (detail == MothershipFx.Detail.HIDDEN) return;
        pose.pushPose();
        Vec3 relative = hull.subtract(origin);
        pose.translate(relative.x,relative.y,relative.z);
        MothershipFxMesh.hullCharge(sink(pose,buffers.getBuffer(TYPE)),sample,detail,elapsed);
        pose.popPose();
    }

    public static void arrival(PoseStack pose,MultiBufferSource buffers,Vec3 beacon,Vec3 ship,
            Vec3 camera,double age,float yaw) {
        if(!finite(beacon)||!finite(ship)||!finite(camera)||!Double.isFinite(age))return;
        if(age<MothershipArrival.BEACON_TICKS+12) {
            var detail=MothershipFx.detail(camera.distanceToSqr(beacon));
            if(detail!=MothershipFx.Detail.HIDDEN) {
                pose.pushPose();Vec3 relative=beacon.subtract(camera);
                pose.translate(relative.x,relative.y,relative.z);
                MothershipFxMesh.arrivalBeacon(sink(pose,buffers.getBuffer(TYPE)),age,ship.y-beacon.y,detail);
                pose.popPose();
            }
        }
        if(age>=MothershipArrival.BEACON_TICKS) {
            var detail=MothershipFx.detail(camera.distanceToSqr(ship));
            if(detail!=MothershipFx.Detail.HIDDEN) {
                pose.pushPose();Vec3 relative=ship.subtract(camera);
                pose.translate(relative.x,relative.y,relative.z);
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                MothershipFxMesh.arrivalWarp(sink(pose,buffers.getBuffer(TYPE)),age,detail);
                pose.popPose();
            }
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    /** Debug flat-ground projection, called only by the explicit client preview. */
    public static void groundFirePreview(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 impact, double elapsed) {
        if (!finite(camera) || !finite(impact)) return;
        pose.pushPose();
        Vec3 relative = impact.subtract(camera);
        pose.translate(relative.x, relative.y, relative.z);
        MothershipFxMesh.groundFirePreview(sink(pose, buffers.getBuffer(TYPE)), elapsed,
                MothershipFx.detail(camera.distanceToSqr(impact)));
        pose.popPose();
    }

    private static double segmentDistanceSquared(Vec3 camera, Vec3 start, Vec3 unit, double length) {
        Vec3 offset = camera.subtract(start);
        double t = Math.max(0, Math.min(length, offset.dot(unit)));
        return offset.subtract(unit.scale(t)).lengthSqr();
    }

    private static void orient(PoseStack pose, Vec3 position, Vec3 unit) {
        pose.translate(position.x, position.y, position.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                new Vector3f((float) unit.x, (float) unit.y, (float) unit.z)));
    }

    private static MothershipFxMesh.Sink sink(PoseStack pose, VertexConsumer buffer) {
        PoseStack.Pose current = pose.last();
        return (x, y, z, red, green, blue, alpha) -> buffer.vertex(current.pose(), (float) x, (float) y, (float) z)
                .color(red, green, blue, alpha).uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(current.normal(), 0, 1, 0).endVertex();
    }
}
