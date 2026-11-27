package com.liucodex.doggiesnewvehicle.client.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.DnvHexbotEntity;
import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry;
import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** One vehicle, 130 original model pieces, ordinary vanilla block geometry and lighting. */
public final class HexbotRenderer extends EntityRenderer<DnvHexbotEntity> {
    public HexbotRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.8F;}
    @Override public ResourceLocation getTextureLocation(DnvHexbotEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(DnvHexbotEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var model=HexbotModel.current();if(model==null)return;
        var rig=entity.rig(partial);var frame=rig.frame();Point origin=frame.origin();
        double x=Mth.lerp(partial,entity.xo,entity.getX()),y=Mth.lerp(partial,entity.yo,entity.getY()),z=Mth.lerp(partial,entity.zo,entity.getZ());
        // Native animated blocks (including the shulker-box eye) select their own texture atlas.
        MultiBufferSource pieces=buffers;
        Quaternionf rotation = new Quaternionf();
        stack.pushPose();stack.translate(origin.x()-x,origin.y()-y,origin.z()-z);
        stack.pushPose();stack.mulPose(rotation.rotationYXZ(-frame.yaw()*Mth.DEG_TO_RAD,frame.pitch()*Mth.DEG_TO_RAD,frame.roll()*Mth.DEG_TO_RAD));
        draw(model.torso(),stack,pieces,light);stack.popPose();
        for (int leg = 0; leg < HexbotGeometry.LEGS; leg++) {
            double[] lengths = HexbotGeometry.lengths(leg);
            for (int segment = 0; segment < lengths.length; segment++) {
                Point start = rig.node(leg, segment);
                Point direction = rig.node(leg, segment + 1).subtract(start).normalized();
                stack.pushPose();
                stack.translate(start.x() - origin.x(), start.y() - origin.y(), start.z() - origin.z());
                stack.mulPose(rotation.rotationTo(0, 0, 1, (float) direction.x(), (float) direction.y(), (float) direction.z()));
                stack.scale(1, 1, (float) lengths[segment]);
                draw(model.legs().get(segment), stack, pieces, light);
                stack.popPose();
            }
        }
        stack.popPose();super.render(entity,yaw,partial,stack,buffers,light);
    }
    private static void draw(List<HexbotModel.Piece> pieces,PoseStack stack,MultiBufferSource buffers,int light){
        var renderer=Minecraft.getInstance().getBlockRenderer();
        Matrix3f normalTransform = new Matrix3f();
        for(var piece:pieces){
            stack.pushPose();stack.mulPoseMatrix(piece.matrix());
            stack.last().normal().mul(normalTransform.set(piece.matrix()).invert().transpose());
            renderer.renderSingleBlock(piece.block(),stack,buffers,piece.emissive()?LightTexture.FULL_BRIGHT:light,OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
    }
}
