package com.liucodex.doggiesnewvehicle.client.weapons;

import com.atsuishio.superbwarfare.client.animation.AnimationHelper;
import com.atsuishio.superbwarfare.client.renderer.CustomGunRenderer;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.item.gun.sniper.AwmItem;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.weapons.Dsx1Item;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** Native bone traversal with the author's exact UV-mapped triangles at each leaf. */
public final class Dsx1Renderer extends CustomGunRenderer<AwmItem> {
    private static final ResourceLocation EMISSION = new ResourceLocation(DoggiesNewVehicle.MOD_ID, "textures/item/dsx1_emissive.png");
    private static final ResourceLocation RETICLE = new ResourceLocation(DoggiesNewVehicle.MOD_ID, "textures/crosshair/dsx1_kh4.png");
    public Dsx1Renderer() { super(new Dsx1Model()); }

    @Override public void renderRecursively(PoseStack stack, AwmItem item, GeoBone bone, RenderType type,
            MultiBufferSource buffers, VertexConsumer vertices, boolean reRender, float partialTick,
            int light, int overlay, float red, float green, float blue, float alpha) {
        var player = Minecraft.getInstance().player;
        String name = bone.getName();
        boolean arm = name.equals("Lefthand") || name.equals("Righthand");
        boolean firstPerson = renderPerspective.firstPerson();
        boolean held = Minecraft.getInstance().level != null && player != null && player.isAlive() && player.getMainHandItem().getItem() instanceof Dsx1Item
                && GeoItem.getId(player.getMainHandItem()) == getInstanceId(item);
        bone.setHidden(arm || (name.equals("dsx_glass") && firstPerson && held
                && ClientEventHandler.zoomPos > .7));
        if (held && (firstPerson || renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            AnimationHelper.handleShootFlare(name, stack, player.getMainHandItem(), bone, buffers, light);
        }
        if (arm && firstPerson && held) {
            String physicalArm = renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    ? (name.equals("Lefthand") ? "Righthand" : "Lefthand") : name;
            AnimationHelper.renderArms(player, renderPerspective, stack, physicalArm, bone, buffers, type, light, false);
        }
        super.renderRecursively(stack, item, bone, type, buffers, vertices, reRender, partialTick,
                light, overlay, red, green, blue, alpha);
    }

    @Override public void renderCubesOfBone(PoseStack stack, GeoBone bone, VertexConsumer vertices,
            int light, int overlay, float red, float green, float blue, float alpha) {
        if (bone.isHidden()) return;
        if (bone.getName().equals("bone") && activeFirstPerson()) alignPupil(stack);
        if (bone.getName().equals("cross") && activeFirstPerson()) renderReticle(stack, overlay, alpha);
        var part = Dsx1MeshCache.get().part(bone.getName());
        if (part == null) return;
        // GeoRenderer already prepared the complete parent + leaf transform.
        boolean lamp = bone.getName().equals("dsx_lamps");
        // The lamp surface itself is luminous in every display context, even if a shader
        // does not implement bloom for the additional emissive pass.
        emit(stack, vertices, part, lamp ? 15728880 : light, overlay,
                lamp ? 1 : red, lamp ? 1 : green, lamp ? 1 : blue, alpha);
        if (bone.getName().equals("dsx_lamps") && currentBuffer != null) {
            emit(stack, currentBuffer.getBuffer(RenderType.eyes(EMISSION)), part, 15728880, overlay, 1, 1, 1, alpha);
            currentBuffer.getBuffer(renderType);
        }
    }

    private boolean activeFirstPerson() {
        var player = Minecraft.getInstance().player;
        return renderPerspective.firstPerson() && Minecraft.getInstance().level != null && player != null && player.isAlive()
                && player.getMainHandItem().getItem() instanceof Dsx1Item
                && GeoItem.getId(player.getMainHandItem()) == getInstanceId(animatable);
    }

    private void alignPupil(PoseStack stack) {
        var optics = Dsx1MeshCache.optics();
        if (optics == null || ClientEventHandler.drawTime >= .01 || ClientEventHandler.isEditing) return;
        var current = new Matrix4f(stack.last().pose());
        float determinant = current.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return;
        stack.pushPose();
        // Account for the actual animated parent, while leaving native recoil visible afterwards.
        RenderUtils.prepMatrixForBone(stack, getGeoModel().getAnimationProcessor().getBone("0"));
        var reference = new Matrix4f(stack.last().pose());
        stack.popPose();
        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, getGeoModel().getAnimationProcessor().getBone("fire"));
        RenderUtils.prepMatrixForBone(stack, getGeoModel().getAnimationProcessor().getBone("0"));
        var correction = optics.renderCorrection(RenderSystem.getModelViewMatrix(), reference, stack.last().pose(),
                RenderSystem.getProjectionMatrix(), ((Dsx1Model)getGeoModel()).displayAds());
        stack.popPose();
        var local = new Matrix4f(current).invert().mul(correction).mul(current);
        stack.mulPoseMatrix(local);
        stack.last().normal().mul(new Matrix3f(local).invert().transpose());
    }

    private void renderReticle(PoseStack stack, int overlay, float alpha) {
        var player = Minecraft.getInstance().player;
        if (currentBuffer == null || !ClientEventHandler.zoom || ClientEventHandler.drawTime >= .01
                || ClientEventHandler.isEditing || player.isSprinting()) return;
        double fade = Math.max(0, Math.min(1, (ClientEventHandler.zoomPos - .65) / .35));
        if (!Double.isFinite(fade) || fade <= 0) return;
        var optics = Dsx1MeshCache.optics();
        if (optics == null) return;
        float half = optics.reticleSize() / 2;
        // Alpha blending preserves the texture's thin dark outline on snow; one emissive submission.
        var glow = currentBuffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(RETICLE));
        reticleQuad(stack, glow, optics, half, overlay, (float)(alpha * fade), 1, 0, 0);
        currentBuffer.getBuffer(renderType);
    }

    private static void reticleQuad(PoseStack stack, VertexConsumer target, Dsx1Optics optics, float half,
            int overlay, float alpha, float color, float offsetX, float offsetY) {
        var normal = stack.last().normal().transform(new Vector3f(0, 0, 1)).normalize();
        for (int i = 0; i < 4; i++) {
            float x = optics.x() + offsetX + ((i == 1 || i == 2) ? half : -half);
            float y = optics.y() + offsetY + (i >= 2 ? half : -half);
            target.vertex(stack.last().pose(), x, y, optics.reticleZ()).color(color, color, color, alpha)
                    .uv((i == 1 || i == 2) ? 1 : 0, i >= 2 ? 0 : 1).overlayCoords(overlay).uv2(15728880)
                    .normal(normal.x, normal.y, normal.z).endVertex();
        }
    }

    private static void emit(PoseStack stack, VertexConsumer vertices, Dsx1Mesh.Part part, int light,
            int overlay, float red, float green, float blue, float alpha) {
        var pose = stack.last();
        var normal = new Vector3f();
        for (int triangle = 0; triangle < part.triangles(); triangle++) {
            for (int corner = 0; corner < 4; corner++) {
                // QUADS: a,b,c,c preserves the original triangle; no authored geometry is added.
                int index = triangle * 3 + Math.min(corner, 2);
                normal.set(part.value(index, 5), part.value(index, 6), part.value(index, 7));
                pose.normal().transform(normal).normalize();
                vertices.vertex(pose.pose(), part.value(index, 0), part.value(index, 1), part.value(index, 2))
                        .color(red, green, blue, alpha).uv(part.value(index, 3), part.value(index, 4))
                        .overlayCoords(overlay).uv2(light).normal(
                                normal.x(), normal.y(), normal.z()).endVertex();
            }
        }
    }
}
