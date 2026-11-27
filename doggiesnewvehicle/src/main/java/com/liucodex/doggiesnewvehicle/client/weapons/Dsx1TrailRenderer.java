package com.liucodex.doggiesnewvehicle.client.weapons;

import com.liucodex.doggiesnewvehicle.network.Dsx1TrailNetwork;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Detached warm-white core and faint gold halo. World depth still occludes the trail. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle",value=Dist.CLIENT)
public final class Dsx1TrailRenderer {
    private static final Dsx1TrailStore STORE=new Dsx1TrailStore();
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(4096));
    private static Object world;
    private Dsx1TrailRenderer(){}
    public static void install(){Dsx1TrailNetwork.installClient(sample->{
        var level=Minecraft.getInstance().level;
        if(level==null||!sample.dimension().equals(level.dimension().location()))return;
        if(world!=level){clear();world=level;}
        STORE.accept(sample,level.getGameTime());
    });}
    public static void clear(){STORE.clear();world=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null||world!=level){clear();return;}
        STORE.prune(level.getGameTime());
        double time=level.getGameTime()+event.getPartialTick();var camera=event.getCamera().getPosition();
        var stack=event.getPoseStack();stack.pushPose();
        var consumer=BUFFER.getBuffer(RenderType.lightning());
        boolean minimal=mc.options.particles().get()==ParticleStatus.MINIMAL;int visible=0;
        for(var trail:STORE.trails()){
            if(++visible>(minimal?16:Dsx1TrailStore.MAX_TRAILS))break;
            for(var segment:trail.segments()){
                float alpha=Dsx1TrailStore.alpha(time-segment.tick());if(alpha<.01F)continue;
                Vec3 from=segment.from(),to=segment.to(),axis=to.subtract(from);
                if(axis.lengthSqr()<1e-10||from.add(to).scale(.5).distanceToSqr(camera)>300*300)continue;
                Vec3 side=axis.cross(camera.subtract(from));
                if(side.lengthSqr()<1e-10)side=axis.cross(new Vec3(0,1,0));
                if(side.lengthSqr()<1e-10)side=new Vec3(1,0,0);
                side=side.normalize();
                Vec3 a=from.subtract(camera),b=to.subtract(camera);
                if(!minimal)beam(consumer,stack.last().pose(),a,b,side.scale(.05),255,183,64,alpha*.20F);
                beam(consumer,stack.last().pose(),a,b,side.scale(.0125),255,244,215,alpha*.9F);
            }
        }
        BUFFER.endBatch(RenderType.lightning());stack.popPose();
    }
    private static void beam(VertexConsumer out,org.joml.Matrix4f pose,Vec3 from,Vec3 to,Vec3 side,int r,int g,int b,float a){
        vertex(out,pose,from.add(side),r,g,b,a);vertex(out,pose,to.add(side),r,g,b,a);
        vertex(out,pose,to.subtract(side),r,g,b,a);vertex(out,pose,from.subtract(side),r,g,b,a);
    }
    private static void vertex(VertexConsumer out,org.joml.Matrix4f pose,Vec3 p,int r,int g,int b,float a){
        out.vertex(pose,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,Math.round(255*a)).endVertex();
    }
}
