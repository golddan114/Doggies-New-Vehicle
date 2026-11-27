package com.liucodex.silentheaven.client;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.boss.DnvVehicleTransitionTimeline;
import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import com.liucodex.doggiesnewvehicle.entity.DnvVehicleTeleportTimeline;
import com.liucodex.doggiesnewvehicle.boss.DnvBossRunningGear;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvVehicleMesh;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvVehicleMesh.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class SilentHeavenMeshLayer {
    private static final ResourceLocation BODY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("silent_heaven", "textures/entity/silent_heaven_body.png");
    private static final ResourceLocation TRACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/vehicle/t_90a.png");
    private static final ResourceLocation TRACK_INNER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("silent_heaven", "textures/entity/silent_heaven_track_inner.png");
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
    private static final int ENTRANCE_WHITE_TICKS = 40;

    private static final String[] BASE_GROUPS = {
        "chassis", "core", "main_gun_l", "main_gun_r",
        "autocannon_l_yaw", "autocannon_l_pitch",
        "autocannon_r_yaw", "autocannon_r_pitch",
        "missile_pod_l_yaw", "missile_pod_l_pitch",
        "missile_pod_r_yaw", "missile_pod_r_pitch",
        "launch_hatch_l", "launch_hatch_r", "halo_ring",
        "halo_needle_0", "halo_needle_1", "halo_needle_2",
        "halo_needle_3", "halo_needle_4", "halo_needle_5"
    };
    private static final String[] GROUPS = createGroups();
    private static final int TRACK_SHOE_COUNT = DnvBossRunningGear.SHOE_COUNT;
    private static final int ROAD_WHEEL_COUNT = 4;
    private static final double ROAD_WHEEL_L_X = 1.7925D;
    private static final double ROAD_WHEEL_R_X = -1.7925D;
    private static final double ROAD_WHEEL_Y = 0.7198D;
    private static final double[] ROAD_WHEEL_Z = {
        2.0339D, 0.6084D, -0.8865D, -2.3391D
    };
    private static final float ROAD_WHEEL_DEGREES_PER_TRACK_PHASE =
            DnvBossRunningGear.WHEEL_DEGREES_PER_PHASE;
    private static final double CORE_PIVOT_Y = 2.304092D;
    private static final double MAIN_GUN_L_PIVOT_X = 1.880550D;
    private static final double MAIN_GUN_R_PIVOT_X = -1.880550D;
    private static final double MAIN_GUN_PIVOT_Y = 3.574424D;
    private static final double MAIN_GUN_L_PIVOT_Z = -1.085218D;
    private static final double MAIN_GUN_R_PIVOT_Z = -1.085219D;
    private static final double AUTOCANNON_L_YAW_X = 1.705815D;
    private static final double AUTOCANNON_R_YAW_X = -1.705815D;
    private static final double AUTOCANNON_YAW_Y = 1.528245D;
    private static final double AUTOCANNON_YAW_Z = 1.050607D;
    private static final double AUTOCANNON_L_PITCH_X = 1.705815D;
    private static final double AUTOCANNON_R_PITCH_X = -1.705815D;
    private static final double AUTOCANNON_PITCH_Y = 1.709482D;
    private static final double AUTOCANNON_PITCH_Z = 1.330202D;
    private static final double MISSILE_POD_L_YAW_X = 1.345815D;
    private static final double MISSILE_POD_R_YAW_X = -1.325815D;
    private static final double MISSILE_POD_YAW_Y = 4.678438D;
    private static final double MISSILE_POD_YAW_Z = -0.797716D;
    private static final double MISSILE_POD_L_PITCH_X = 1.349663D;
    private static final double MISSILE_POD_R_PITCH_X = -1.329664D;
    private static final double MISSILE_POD_PITCH_Y = 4.890142D;
    private static final double MISSILE_POD_PITCH_Z = -0.799954D;
    private static final double HALO_PIVOT_Y = 7.840268D;
    private static final double LAUNCH_HATCH_L_X = 2.968506D;
    private static final double LAUNCH_HATCH_R_X = -2.968506D;
    private static final double LAUNCH_HATCH_Y = 3.574424D;
    private static final double LAUNCH_HATCH_Z = -1.570013D;
    private static final double[] NEEDLE_ROOT_X = {
        1.403068D, 2.748500D, 1.345431D, -1.403069D, -2.748500D, -1.345432D
    };
    private static final double[] NEEDLE_ROOT_Y = {
        7.882872D, 7.882872D, 7.882872D, 7.882872D, 7.882872D, 7.882872D
    };
    private static final double[] NEEDLE_ROOT_Z = {
        2.363633D, -0.033277D, -2.396909D, -2.363632D, 0.033276D, 2.396909D
    };

    /**
     * Renders the same SHM hierarchy for the Superb Warfare vehicle entity.
     * EntityRenderer has already translated to the entity position, but it has
     * not applied LivingEntityRenderer's body-yaw transform.
     */
    public static void renderVehicle(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            DnvVehiclePresentation visual,
            float partialTick) {
        MeshData mesh = DnvVehicleMesh.SILENT_HEAVEN.get(com.liucodex.doggiesnewvehicle.client.DnvRenderDetail.distanceSquared(visual.asEntity()));
        if (mesh.isEmpty()) {
            return;
        }
        Entity entity = visual.asEntity();
        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        renderContents(mesh, entity, visual, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private static void renderContents(
            MeshData mesh,
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        int entranceTicks = visual.getEntranceTicks();
        if (entranceTicks > ENTRANCE_WHITE_TICKS) {
            // The first third of the arrival is particle-only. Normal vehicle
            // textures must not leak through before materialization begins.
            return;
        }
        if (entranceTicks > 0) {
            renderEntranceSilhouette(
                    mesh, entity, visual, partialTick, poseStack, bufferSource, entranceTicks);
            return;
        }

        int teleportTicks = visual.getTeleportTicks();
        if (DnvVehicleTeleportTimeline.isHidden(teleportTicks)) {
            return;
        }
        renderNormalVehicle(
                mesh, entity, visual, partialTick, poseStack, bufferSource, packedLight);
        if (DnvVehicleTeleportTimeline.isActive(teleportTicks)) {
            renderTeleportWhiteOverlay(
                    mesh,
                    entity,
                    visual,
                    partialTick,
                    poseStack,
                    bufferSource,
                    DnvVehicleTeleportTimeline.whiteOverlayProgress(teleportTicks));
        }
    }

    private static void renderNormalVehicle(
            MeshData mesh,
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        DamageVisual damage = damageVisual(entity);
        renderMaterial(mesh, entity, visual, partialTick, poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(BODY_TEXTURE)),
                packedLight, "body", damage.red(), damage.green(), damage.blue(), 255);
        renderMaterial(mesh, entity, visual, partialTick, poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(TRACK_TEXTURE)),
                packedLight, "track", damage.red(), damage.green(), damage.blue(), 255);
        renderMaterial(mesh, entity, visual, partialTick, poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(TRACK_INNER_TEXTURE)),
                packedLight, "track_inner", damage.red(), damage.green(), damage.blue(), 255);

        boolean phaseTwoLighting = visual.isPhaseTwo() || visual.isTransitioning();
        int red = phaseTwoLighting ? 255 : 228;
        int green = phaseTwoLighting ? 32 : 185;
        int blue = phaseTwoLighting ? 18 : 63;
        if (damage.lightAlpha() > 0) {
            renderMaterial(mesh, entity, visual, partialTick, poseStack,
                    bufferSource.getBuffer(RenderType.eyes(WHITE_TEXTURE)),
                    LightTexture.FULL_BRIGHT,
                    "light",
                    red,
                    green,
                    blue,
                    damage.lightAlpha());
        }
    }

    private static DamageVisual damageVisual(Entity entity) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            return new DamageVisual(255, 255, 255, 235);
        }
        if (vehicle.isWreck() || vehicle.getHealth() <= 0.0F) {
            return new DamageVisual(28, 26, 24, 0);
        }
        float healthRatio = Mth.clamp(
                vehicle.getHealth() / Math.max(1.0F, vehicle.getMaxHealth()),
                0.0F,
                1.0F);
        if (healthRatio >= 0.40F) {
            return new DamageVisual(255, 255, 255, 235);
        }
        float damageProgress = healthRatio / 0.40F;
        int brightness = Mth.clamp(
                Math.round(Mth.lerp(damageProgress, 136.0F, 255.0F)),
                0,
                255);
        int lightAlpha = Mth.clamp(
                Math.round(Mth.lerp(damageProgress, 84.0F, 235.0F)),
                0,
                255);
        return new DamageVisual(brightness, brightness, brightness, lightAlpha);
    }

    private record DamageVisual(int red, int green, int blue, int lightAlpha) {
    }

    private static void renderTeleportWhiteOverlay(
            MeshData mesh,
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float progress) {
        int alpha = Mth.clamp(Math.round(progress * 255.0F), 0, 255);
        if (alpha <= 0) {
            return;
        }
        renderWhiteSilhouette(
                mesh, entity, visual, partialTick, poseStack, bufferSource, alpha);
    }

    private static void renderEntranceSilhouette(
            MeshData mesh,
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int entranceTicks) {
        float progress = 1.0F - entranceTicks / (float) ENTRANCE_WHITE_TICKS;
        int alpha = Mth.clamp(Math.round(Mth.lerp(progress, 48.0F, 255.0F)), 48, 255);
        renderWhiteSilhouette(
                mesh, entity, visual, partialTick, poseStack, bufferSource, alpha);
    }

    private static void renderWhiteSilhouette(
            MeshData mesh,
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int alpha) {
        VertexConsumer white = bufferSource.getBuffer(RenderType.entityTranslucent(WHITE_TEXTURE));
        for (String material : List.of("body", "track", "track_inner", "light")) {
            renderMaterial(
                    mesh,
                    entity,
                    visual,
                    partialTick,
                    poseStack,
                    white,
                    LightTexture.FULL_BRIGHT,
                    material,
                    255,
                    255,
                    255,
                    alpha);
        }
    }

    private static void renderMaterial(
            MeshData mesh,
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            String material,
            int red,
            int green,
            int blue,
        int alpha) {
        for (String group : GROUPS) {
            if (group.startsWith("halo_needle_")) {
                int index = group.charAt(group.length() - 1) - '0';
                if (!haloNeedleVisible(visual, index)) {
                    continue;
                }
            }
            if (group.equals("halo_ring") && !haloRingVisible(visual)) {
                continue;
            }
            float[] stream = mesh.part(group + "/" + material).stream();
            if (stream.length == 0) {
                continue;
            }
            poseStack.pushPose();
            applyGroupTransform(entity, visual, partialTick, group, poseStack);
            int groupAlpha = group.equals("halo_ring")
                    ? Math.round(alpha * haloRingOpacity(visual, partialTick))
                    : alpha;
            renderStream(stream, poseStack, consumer, packedLight, red, green, blue, groupAlpha);
            poseStack.popPose();
        }
    }

    private static void applyGroupTransform(
            Entity entity,
            DnvVehiclePresentation visual,
            float partialTick,
            String group,
            PoseStack poseStack) {
        float bodyYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float coreYaw = Mth.wrapDegrees(visual.getCoreYaw() - bodyYaw);
        if (group.equals("core")
                || group.startsWith("main_gun_")
                || group.startsWith("missile_pod_")
                || group.startsWith("launch_hatch_")
                || group.equals("halo_ring")
                || group.startsWith("halo_needle_")) {
            rotateAroundY(poseStack, 0.0D, CORE_PIVOT_Y, 0.0D, -coreYaw);
        }
        if (group.equals("main_gun_l")) {
            rotateAroundX(
                    poseStack,
                    MAIN_GUN_L_PIVOT_X,
                    MAIN_GUN_PIVOT_Y,
                    MAIN_GUN_L_PIVOT_Z,
                    -visual.getMainGunPitch());
        } else if (group.equals("main_gun_r")) {
            rotateAroundX(
                    poseStack,
                    MAIN_GUN_R_PIVOT_X,
                    MAIN_GUN_PIVOT_Y,
                    MAIN_GUN_R_PIVOT_Z,
                    -visual.getMainGunPitch());
        } else if (group.equals("launch_hatch_l")) {
            float open = hatchOpenProgress(visual, partialTick);
            rotateAroundY(
                    poseStack,
                    LAUNCH_HATCH_L_X,
                    LAUNCH_HATCH_Y,
                    LAUNCH_HATCH_Z,
                    95.0F * open);
        } else if (group.equals("launch_hatch_r")) {
            float open = hatchOpenProgress(visual, partialTick);
            rotateAroundY(
                    poseStack,
                    LAUNCH_HATCH_R_X,
                    LAUNCH_HATCH_Y,
                    LAUNCH_HATCH_Z,
                    -95.0F * open);
        } else if (group.equals("halo_ring")) {
            rotateAroundY(poseStack, 0.0D, HALO_PIVOT_Y, 0.0D, -visual.getHaloAngle());
        } else if (group.startsWith("halo_needle_")) {
            int index = group.charAt(group.length() - 1) - '0';
            applyNeedleDeployment(visual, partialTick, index, poseStack);
            if (haloRotationActive(visual, partialTick)) {
                rotateAroundY(poseStack, 0.0D, HALO_PIVOT_Y, 0.0D, -visual.getHaloAngle());
            }
        } else if (group.startsWith("road_wheel_")) {
            boolean left = group.startsWith("road_wheel_l_");
            int index = Integer.parseInt(group.substring(group.lastIndexOf('_') + 1));
            float angle = left
                    ? visual.getRoadWheelAngleL()
                            + visual.getTrackSpeedL() * partialTick
                                    * ROAD_WHEEL_DEGREES_PER_TRACK_PHASE
                    : visual.getRoadWheelAngleR()
                            + visual.getTrackSpeedR() * partialTick
                                    * ROAD_WHEEL_DEGREES_PER_TRACK_PHASE;
            rotateAroundX(
                    poseStack,
                    left ? ROAD_WHEEL_L_X : ROAD_WHEEL_R_X,
                    ROAD_WHEEL_Y,
                    ROAD_WHEEL_Z[index],
                    angle);
        } else if (group.startsWith("track_shoe_")) {
            boolean left = group.startsWith("track_shoe_l_");
            int index = Integer.parseInt(group.substring(group.lastIndexOf('_') + 1));
            float phase = left
                    ? visual.getTrackPhaseL() + visual.getTrackSpeedL() * partialTick
                    : visual.getTrackPhaseR() + visual.getTrackSpeedR() * partialTick;
            applyTrackShoe(poseStack, left, index, phase);
        } else if (group.startsWith("autocannon_l_")) {
            rotateAroundY(
                    poseStack,
                    AUTOCANNON_L_YAW_X,
                    AUTOCANNON_YAW_Y,
                    AUTOCANNON_YAW_Z,
                    -Mth.wrapDegrees(visual.getAutocannonLYaw() - bodyYaw));
            if (group.endsWith("_pitch")) {
                rotateAroundX(
                        poseStack,
                        AUTOCANNON_L_PITCH_X,
                        AUTOCANNON_PITCH_Y,
                        AUTOCANNON_PITCH_Z,
                        -visual.getAutocannonLPitch());
            }
        } else if (group.startsWith("autocannon_r_")) {
            rotateAroundY(
                    poseStack,
                    AUTOCANNON_R_YAW_X,
                    AUTOCANNON_YAW_Y,
                    AUTOCANNON_YAW_Z,
                    -Mth.wrapDegrees(visual.getAutocannonRYaw() - bodyYaw));
            if (group.endsWith("_pitch")) {
                rotateAroundX(
                        poseStack,
                        AUTOCANNON_R_PITCH_X,
                        AUTOCANNON_PITCH_Y,
                        AUTOCANNON_PITCH_Z,
                        -visual.getAutocannonRPitch());
            }
        } else if (group.startsWith("missile_pod_l_")) {
            rotateAroundY(
                    poseStack,
                    MISSILE_POD_L_YAW_X,
                    MISSILE_POD_YAW_Y,
                    MISSILE_POD_YAW_Z,
                    -Mth.wrapDegrees(visual.getMissilePodLYaw() - visual.getCoreYaw()));
            if (group.endsWith("_pitch")) {
                rotateAroundX(
                        poseStack,
                        MISSILE_POD_L_PITCH_X,
                        MISSILE_POD_PITCH_Y,
                        MISSILE_POD_PITCH_Z,
                        -visual.getMissilePodLPitch());
            }
        } else if (group.startsWith("missile_pod_r_")) {
            rotateAroundY(
                    poseStack,
                    MISSILE_POD_R_YAW_X,
                    MISSILE_POD_YAW_Y,
                    MISSILE_POD_YAW_Z,
                    -Mth.wrapDegrees(visual.getMissilePodRYaw() - visual.getCoreYaw()));
            if (group.endsWith("_pitch")) {
                rotateAroundX(
                        poseStack,
                        MISSILE_POD_R_PITCH_X,
                        MISSILE_POD_PITCH_Y,
                        MISSILE_POD_PITCH_Z,
                        -visual.getMissilePodRPitch());
            }
        }
    }

    private static boolean haloNeedleVisible(DnvVehiclePresentation entity, int index) {
        return DnvVehicleTransitionTimeline.haloNeedleVisible(
                entity.isPhaseTwo(),
                entity.isTransitioning(),
                entity.getTransitionTick(),
                index);
    }

    private static boolean haloRingVisible(DnvVehiclePresentation entity) {
        return DnvVehicleTransitionTimeline.haloRingVisible(
                entity.isPhaseTwo(),
                entity.isTransitioning(),
                entity.getTransitionTick());
    }

    private static float hatchOpenProgress(DnvVehiclePresentation entity, float partialTick) {
        return DnvVehicleTransitionTimeline.hatchOpenProgress(
                entity.isPhaseTwo(),
                entity.isTransitioning(),
                entity.getTransitionTick() + partialTick);
    }

    private static float haloRingOpacity(DnvVehiclePresentation entity, float partialTick) {
        return DnvVehicleTransitionTimeline.haloRingOpacity(
                entity.isPhaseTwo(),
                entity.isTransitioning(),
                entity.getTransitionTick() + partialTick);
    }

    private static boolean haloRotationActive(
            DnvVehiclePresentation entity,
            float partialTick) {
        return DnvVehicleTransitionTimeline.haloRotationActive(
                entity.isPhaseTwo(),
                entity.isTransitioning(),
                entity.getTransitionTick() + partialTick);
    }

    private static void applyNeedleDeployment(
            DnvVehiclePresentation entity,
            float partialTick,
            int index,
            PoseStack poseStack) {
        if (entity.isPhaseTwo()) {
            return;
        }
        float startTick = needleStartTick(index);
        float tick = entity.isTransitioning()
                ? entity.getTransitionTick() + partialTick
                : startTick;
        float ejectProgress = DnvVehicleTransitionTimeline.needleEjectProgress(tick, index);
        float settleProgress = DnvVehicleTransitionTimeline.needleSettleProgress(tick, index);
        float uprightProgress = DnvVehicleTransitionTimeline.needleUprightProgress(tick, index);
        boolean left = index < 3;
        double startX = left ? 3.324988D : -3.324988D;
        double startY = 3.574424D + (index % 3 - 1) * 0.16D;
        double startZ = -1.117110D;
        double ejectX = startX + (left ? 1.25D : -1.25D);
        double currentX;
        double currentY;
        double currentZ;
        if (settleProgress <= 0.0F) {
            currentX = Mth.lerp(ejectProgress, startX, ejectX);
            currentY = startY;
            currentZ = startZ;
        } else {
            currentX = Mth.lerp(settleProgress, ejectX, NEEDLE_ROOT_X[index]);
            currentY = Mth.lerp(settleProgress, startY, NEEDLE_ROOT_Y[index]);
            currentZ = Mth.lerp(settleProgress, startZ, NEEDLE_ROOT_Z[index]);
        }
        poseStack.translate(
                currentX - NEEDLE_ROOT_X[index],
                currentY - NEEDLE_ROOT_Y[index],
                currentZ - NEEDLE_ROOT_Z[index]);
        rotateAroundZ(
                poseStack,
                NEEDLE_ROOT_X[index],
                NEEDLE_ROOT_Y[index],
                NEEDLE_ROOT_Z[index],
                (left ? -90.0F : 90.0F) * (1.0F - uprightProgress));
    }

    private static float needleStartTick(int index) {
        return DnvVehicleTransitionTimeline.needleStartTick(index);
    }

    private static String[] createGroups() {
        List<String> groups = new ArrayList<>(List.of(BASE_GROUPS));
        for (String side : List.of("l", "r")) {
            for (int index = 0; index < ROAD_WHEEL_COUNT; index++) {
                groups.add("road_wheel_" + side + "_" + index);
            }
            for (int index = 0; index < TRACK_SHOE_COUNT; index++) {
                groups.add("track_shoe_" + side + "_" + index);
            }
        }
        return groups.toArray(String[]::new);
    }

    private static void applyTrackShoe(
            PoseStack poseStack,
            boolean left,
            int sourceIndex,
            float trackPhase) {
        var source = DnvBossRunningGear.shoePose(sourceIndex, 0.0D);
        var target = DnvBossRunningGear.shoePose(sourceIndex, trackPhase);
        double x = left ? DnvBossRunningGear.HALF_WIDTH : -DnvBossRunningGear.HALF_WIDTH;
        poseStack.translate(x, target.y(), target.z());
        poseStack.mulPose(Axis.XP.rotation((float) (target.pitchRadians() - source.pitchRadians())));
        poseStack.translate(-x, -source.y(), -source.z());
    }

    private static void rotateAroundY(
            PoseStack poseStack,
            double x,
            double y,
            double z,
            float degrees) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
        poseStack.translate(-x, -y, -z);
    }

    private static void rotateAroundX(
            PoseStack poseStack,
            double x,
            double y,
            double z,
            float degrees) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(degrees));
        poseStack.translate(-x, -y, -z);
    }

    private static void rotateAroundZ(
            PoseStack poseStack,
            double x,
            double y,
            double z,
            float degrees) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(degrees));
        poseStack.translate(-x, -y, -z);
    }

    private static void renderStream(
            float[] stream,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int red,
            int green,
            int blue,
            int alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        float[] quads = DnvQuadStream.quads(stream);
        int vertexStride = DnvVehicleMesh.FLOATS_PER_VERTEX;
        for (int quad = 0; quad < quads.length; quad += vertexStride * 4) {
            emitVertex(quads, quad, positionMatrix, normalMatrix, consumer,
                    packedLight, red, green, blue, alpha);
            emitVertex(quads, quad + vertexStride, positionMatrix, normalMatrix, consumer,
                    packedLight, red, green, blue, alpha);
            emitVertex(quads, quad + vertexStride * 2, positionMatrix, normalMatrix, consumer,
                    packedLight, red, green, blue, alpha);
            emitVertex(quads, quad + vertexStride * 3, positionMatrix, normalMatrix, consumer,
                    packedLight, red, green, blue, alpha);
        }
    }

    private static void emitVertex(
            float[] stream,
            int index,
            Matrix4f positionMatrix,
            Matrix3f normalMatrix,
            VertexConsumer consumer,
            int packedLight,
            int red,
            int green,
            int blue,
            int alpha) {
        consumer.vertex(positionMatrix, stream[index], stream[index + 1], stream[index + 2])
                .color(red, green, blue, alpha)
                .uv(stream[index + 6], stream[index + 7])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normalMatrix, stream[index + 3], stream[index + 4], stream[index + 5])
                .endVertex();
    }
}
