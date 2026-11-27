package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Original 192T shell plus 2T inner image, with independent depth-aware emissive/translucent passes. */
final class DogEggVisual {
    static final ResourceLocation SILO=id("silo_base");
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/nuclear/white.png");
    private static final ResourceLocation DOG=id("egg_dog");
    private DogEggVisual() { }
    private static ResourceLocation id(String file){return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/dogegg/"+file+".png");}
    static void render(PoseStack pose,MultiBufferSource buffers,float age,boolean burning,int light){
        var mesh=DnvTurretMesh.DOGEGG.get();
        // The bright inner image is drawn before the front transparent shell.
        DnvTurretMeshRenderSupport.renderTintedQuads(DnvQuadStream.quads(mesh.part("dog").main()),pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(DOG)),LightTexture.FULL_BRIGHT,0xffffff);
        var skin=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        emit(DnvQuadStream.quads(mesh.part("shell").main()),pose,skin,LightTexture.FULL_BRIGHT,0xffe7ae,105);
        // A second low-alpha pass gives the silhouette a warm aura without increasing the authored mesh.
        pose.pushPose();pose.translate(0,2.6,0);float pulse=1.035F+.012F*(float)Math.sin(age*.08);
        pose.scale(pulse,pulse,pulse);pose.translate(0,-2.6,0);
        emit(DnvQuadStream.quads(mesh.part("shell").main()),pose,skin,LightTexture.FULL_BRIGHT,0xffdb70,24);
        pose.popPose();
        if(burning){
            var consumer=buffers.getBuffer(com.liucodex.doggiesnewvehicle.client.fx.DnvJetRenderType.TYPE);
            double thrust=.2+.8*NuclearExhaust.strength(age),radius=.48;
            double length=4+8*NuclearExhaust.strength(age),scale=length/(radius*(1.3+thrust*6));
            com.liucodex.doggiesnewvehicle.fx.DnvJetMesh.exhaust((x,y,z,r,g,b,a)->{
                var p=pose.last();
                consumer.vertex(p.pose(),(float)x,(float)(-z*scale),(float)y).color(255,210,35,a)
                        .uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(p.normal(),0,-1,0).endVertex();
            },thrust,radius,age,2);
        }
    }
    private static void emit(float[] data,PoseStack stack,VertexConsumer out,int light,int color,int alpha){
        var pose=stack.last();
        for(int i=0;i<data.length;i+=8)out.vertex(pose.pose(),data[i],data[i+1],data[i+2])
                .color(color>>16&255,color>>8&255,color&255,alpha).uv(data[i+6],data[i+7])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(),data[i+3],data[i+4],data[i+5]).endVertex();
    }
}
