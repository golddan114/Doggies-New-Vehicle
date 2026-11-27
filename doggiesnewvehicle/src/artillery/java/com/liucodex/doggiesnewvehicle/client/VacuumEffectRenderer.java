package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.artillery.silo.VacuumEffectEntity;
import com.liucodex.doggiesnewvehicle.artillery.silo.VacuumPayloadProgress;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Red inward vortex, collapsing core, two-second dog reveal and the server-triggered huge blast. */
public final class VacuumEffectRenderer extends EntityRenderer<VacuumEffectEntity> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/nuclear/white.png");
    private static final ResourceLocation DOG=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/dogegg/burst_dog.png");
    private static final RenderType GLOW=com.liucodex.doggiesnewvehicle.client.fx.DnvJetRenderType.textured(WHITE);
    public VacuumEffectRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(VacuumEffectEntity entity){return WHITE;}
    @Override public boolean shouldRender(VacuumEffectEntity e,Frustum frustum,double x,double y,double z){
        return frustum.isVisible(e.getBoundingBox().inflate(VacuumVisualTimeline.SHOCK_RADIUS+2));
    }
    @Override public void render(VacuumEffectEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=e.age()+partial,core=VacuumVisualTimeline.core(age);
        var out=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        if(core>0){
            sphere(pose,out,core,2,1,0,255);
            var glow=buffers.getBuffer(GLOW);
            pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(14));
            ring(pose,glow,core*1.03F,core*1.12F,210,age*.08,64);
            ring(pose,glow,core*1.12F,core*1.6F,45,age*.08,64);pose.popPose();
            for(int layer=0;layer<3;layer++){
                pose.pushPose();pose.translate(0,layer*1.8-1,0);pose.mulPose(Axis.XP.rotationDegrees(12-layer*11));
                float r=core*(2.7F-layer*.65F);
                ring(pose,glow,r,r+.13F,(int)(65*VacuumVisualTimeline.pullAlpha(age)),age*.12+layer,64);pose.popPose();
            }
        }
        if(age<VacuumPayloadProgress.PULL_TICKS){
            vortex(pose,buffers.getBuffer(GLOW),age);
            for(var debris:e.debris()){
                float progress=(age-debris.age())/VacuumEffectEntity.DEBRIS_TICKS;
                if(progress<0||progress>=1)continue;
                var delta=debris.position().getCenter().subtract(e.position()).scale(1-progress);
                double angle=progress*3,cos=Math.cos(angle),sin=Math.sin(angle);
                pose.pushPose();pose.translate(delta.x*cos-delta.z*sin,delta.y,delta.x*sin+delta.z*cos);
                pose.mulPose(Axis.YP.rotationDegrees(progress*240));pose.mulPose(Axis.XP.rotationDegrees(progress*150));
                float scale=.65F*(float)Math.sqrt(1-progress);pose.scale(scale,scale,scale);pose.translate(-.5,-.5,-.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(debris.state(),pose,buffers,
                        LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);pose.popPose();
            }
        }
        float blast=VacuumVisualTimeline.blastAlpha(age);
        if(blast>0)sphere(pose,buffers.getBuffer(GLOW),VacuumVisualTimeline.blastRadius(age),255,190,85,(int)(blast*125));
        float shock=VacuumVisualTimeline.shockAlpha(age);
        if(shock>0){float radius=VacuumVisualTimeline.shockRadius(age);
            ring(pose,buffers.getBuffer(GLOW),Math.max(0,radius-1.2F),radius+1.2F,(int)(shock*255),0,96);}
        float alpha=VacuumVisualTimeline.dogAlpha(age);
        if(alpha>0){
            pose.pushPose();pose.translate(0,3.2+1.2*(1-alpha),0);pose.mulPose(entityRenderDispatcher.cameraOrientation());
            float reveal=VacuumVisualTimeline.revealGlow(age);
            if(reveal>0){pose.pushPose();pose.translate(0,0,-.05);pose.mulPose(Axis.XP.rotationDegrees(90));
                ring(pose,buffers.getBuffer(GLOW),4.2F,4.3F,(int)(reveal*150),age*.1,64);pose.popPose();}
            float width=VacuumVisualTimeline.dogWidth(age),height=width*1241F/1267F;
            quad(pose,buffers.getBuffer(RenderType.entityTranslucent(DOG)),new double[][]{
                {-width/2,-height/2,0},{width/2,-height/2,0},{width/2,height/2,0},{-width/2,height/2,0}},255,255,255,(int)(255*alpha));
            pose.popPose();
        }
        super.render(e,yaw,partial,pose,buffers,light);
    }
    private static void vortex(PoseStack pose,VertexConsumer out,float age){
        float envelope=VacuumVisualTimeline.pullAlpha(age);
        for(int arm=0;arm<VacuumVisualTimeline.ARMS;arm++)for(int i=0;i<VacuumVisualTimeline.SEGMENTS;i++){
            float t=i/(float)VacuumVisualTimeline.SEGMENTS;
            var a=VacuumVisualTimeline.flow(arm,t,age);
            var b=VacuumVisualTimeline.flow(arm,(i+1F)/VacuumVisualTimeline.SEGMENTS,age);
            double dx=b.x()-a.x(),dz=b.z()-a.z(),length=Math.max(.001,Math.hypot(dx,dz));
            double x=-dz/length,z=dx/length;
            int opacity=(int)(envelope*(65+150*t)*(.6+.4*Math.sin(age*.18-arm+i*.5)));
            quad(pose,out,new double[][]{{a.x()-x*a.width(),a.y(),a.z()-z*a.width()},
                    {a.x()+x*a.width(),a.y(),a.z()+z*a.width()},
                    {b.x()+x*b.width(),b.y(),b.z()+z*b.width()},
                    {b.x()-x*b.width(),b.y(),b.z()-z*b.width()}},255,(int)(45+155*t*t),25,opacity);
        }
    }
    private static void sphere(PoseStack pose,VertexConsumer out,float radius,int red,int green,int blue,int alpha){
        for(int row=0;row<8;row++)for(int segment=0;segment<24;segment++){
            double[][] points=new double[4][3];
            int[][] corner={{row,segment},{row+1,segment},{row+1,segment+1},{row,segment+1}};
            for(int i=0;i<4;i++){
                double a=corner[i][0]*Math.PI/8,b=corner[i][1]*Math.PI*2/24;
                points[i]=new double[]{Math.sin(a)*Math.cos(b)*radius,Math.cos(a)*radius,Math.sin(a)*Math.sin(b)*radius};
            }
            quad(pose,out,points,red,green,blue,alpha);
        }
    }
    private static void ring(PoseStack pose,VertexConsumer out,float inner,float outer,int alpha,double turn,int segments){
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            double pulse=.7+.3*Math.sin(a*7+turn);
            quad(pose,out,new double[][]{{Math.cos(a)*inner,0,Math.sin(a)*inner},
                    {Math.cos(a)*outer,0,Math.sin(a)*outer},{Math.cos(b)*outer,0,Math.sin(b)*outer},
                    {Math.cos(b)*inner,0,Math.sin(b)*inner}},255,(int)(65+105*pulse),30,(int)(alpha*pulse));
        }
    }
    private static void quad(PoseStack pose,VertexConsumer out,double[][] points,int r,int g,int b,int alpha){
        var p=pose.last();float[][] uv={{0,1},{1,1},{1,0},{0,0}};
        for(int i=0;i<4;i++)out.vertex(p.pose(),(float)points[i][0],(float)points[i][1],(float)points[i][2])
                .color(r,g,b,alpha).uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(p.normal(),0,1,0).endVertex();
    }
}
