package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMissileEntity;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloEntity;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearRules;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.client.mesh.NuclearVisualData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** Authored UV mesh, independent facility tracks and five effects attached to the real projectile. */
public final class NuclearRenderer {
    private static final ResourceLocation WHITE=id("white");
    private NuclearRenderer(){ }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/nuclear/"+name+".png");}
    public static final class Silo extends EntityRenderer<NuclearSiloEntity> {
        private final com.liucodex.doggiesnewvehicle.client.mesh.NuclearPoseCache poses=new com.liucodex.doggiesnewvehicle.client.mesh.NuclearPoseCache();
        private final java.util.Map<Long,Integer> lightSamples=new java.util.HashMap<>();
        private final org.joml.Vector3f probe=new org.joml.Vector3f();
        private net.minecraft.world.level.Level lightWorld;
        private long lightTick=Long.MIN_VALUE;
        public Silo(EntityRendererProvider.Context context){super(context);shadowRadius=5;}
        @Override public ResourceLocation getTextureLocation(NuclearSiloEntity entity){return WHITE;}
        @Override public boolean shouldRender(NuclearSiloEntity e,Frustum f,double x,double y,double z){return f.isVisible(e.getBoundingBox().inflate(8));}
        @Override public void render(NuclearSiloEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            var data=NuclearVisualData.get();poses.prepare(data.motion());if(data.motion()==null)return;
            if(lightWorld!=e.level()||lightTick!=e.level().getGameTime()){
                lightSamples.clear();lightWorld=e.level();lightTick=e.level().getGameTime();
            }
            var state=e.visualSnapshot();String phase=state.getString("Phase");
            double time=e.level().getGameTime()+partial;
            double remaining=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.remaining(
                    state.getInt("Remaining"),state.getLong("Observed"),time,e.mechanicsRunning());
            var rules=NuclearRules.DEFAULT;
            var mechanicalPhase=com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle.Phase.EMPTY;
            try{mechanicalPhase=com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle.Phase.valueOf(phase);}catch(IllegalArgumentException unavailable){ }
            float frame=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.frame(mechanicalPhase,remaining,rules);
            boolean hasRack=state.contains("RackEntity")&&e.level().getEntity(state.getInt("RackEntity")) instanceof NuclearMissileEntity;
            boolean showRocket=state.getBoolean("Ammo")&&!hasRack&&!phase.equals("DEPARTING")&&!phase.equals("CLOSING")
                    &&(!phase.equals("RETURNING")||state.getBoolean("PreparationRetained"));
            long haltedAt=e.mechanicsRunning()?-1:state.contains("HaltedAt")?state.getLong("HaltedAt"):state.getLong("Observed");
            float radarFrame=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.radarFrame(haltedAt,time);
            var mesh=DnvTurretMesh.NUCLEAR_SILO.get(DnvRenderDetail.distanceSquared(e));
            com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle.Phase mechanical=null;
            try{mechanical=com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle.Phase.valueOf(phase);}catch(IllegalArgumentException unavailable){ }
            int lamp=com.liucodex.doggiesnewvehicle.artillery.silo.SiloStatusLight.color(mechanical,e.getEnergy(),e.mechanicsRunning(),time);
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            // A group is sampled once; base/glow share it. Material batches also stay consecutive
            // in the shader's shadow pass, where type switches otherwise flush each small part.
            for(int pass=0;pass<2;pass++)for(var batch:pass==0?data.siloBase():data.siloGlow()){
                var material=pass==0&&batch.parts().stream().allMatch(NuclearVisualData.Part::statusLamp)?WHITE:batch.material();
                if(e.dogEgg()&&material.getPath().equals("textures/entity/nuclear/v20_body.png"))material=DogEggVisual.SILO;
                var consumer=buffers.getBuffer(pass==0?RenderType.entityCutoutNoCull(material):RenderType.eyes(material));
                for(var part:batch.parts()){
                    if(part.rocket()&&(!showRocket||e.dogEgg())||pass==1&&part.statusLamp()&&lamp==0)continue;
                    int group=part.group();
                    var transform=poses.position(group,data.radar().get(group)?radarFrame:frame);
                    pose.pushPose();pose.last().pose().mul(transform);pose.last().normal().mul(poses.normal(group));
                    var stream=mesh.part(part.name()).main();
                    if(part.statusLamp())DnvTurretMeshRenderSupport.renderTintedQuads(
                            com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream.quads(stream),pose,consumer,LightTexture.FULL_BRIGHT,lamp);
                    else DnvTurretMeshRenderSupport.renderTintedQuads(
                            com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream.quads(stream),pose,consumer,
                            pass==0?surfaceLight(e,yaw,part,transform,light):LightTexture.FULL_BRIGHT,!e.mechanicsRunning()?0x484848:pass==0?part.tint():0xffffff);
                    pose.popPose();
                }
            }
            if(e.dogEgg()&&showRocket){
                var tail=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearRackMotion.tail(frame,0);
                pose.pushPose();pose.translate(tail.x,tail.y,tail.z);
                DogEggVisual.render(pose,buffers,(float)time,false,light);pose.popPose();
            }
            pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
        }
        private int surfaceLight(NuclearSiloEntity entity,float yaw,NuclearVisualData.Part part,org.joml.Matrix4f transform,int fallback){
            transform.transformPosition(probe.set(part.lightX(),part.lightY(),part.lightZ()));
            probe.rotateY((float)Math.toRadians(-yaw));
            var pos=net.minecraft.core.BlockPos.containing(entity.getX()+probe.x,entity.getY()+probe.y,entity.getZ()+probe.z);
            if(!entity.level().hasChunkAt(pos))return fallback;
            if(lightSamples.size()>=512)lightSamples.clear();
            return lightSamples.computeIfAbsent(pos.asLong(),ignored->net.minecraft.client.renderer.LevelRenderer.getLightColor(entity.level(),pos));
        }
    }
    public static final class Missile extends EntityRenderer<NuclearMissileEntity> {
        public Missile(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(NuclearMissileEntity e){return WHITE;}
        @Override public boolean shouldRender(NuclearMissileEntity e,Frustum f,double x,double y,double z){return f.isVisible(e.getBoundingBox().inflate(e.getBbHeight()));}
        @Override public void render(NuclearMissileEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            if(e.dogEgg()){
                float pitch=net.minecraft.util.Mth.lerp(partial,e.xRotO,e.getXRot());
                pose.pushPose();pose.mulPose(new Quaternionf().rotateY((float)Math.toRadians(-yaw))
                        .rotateX((float)Math.toRadians(pitch+90)));
                DogEggVisual.render(pose,buffers,e.flightAge()+partial,e.armed(),light);
                pose.popPose();super.render(e,yaw,partial,pose,buffers,light);return;
            }
            var data=NuclearVisualData.get();if(data.motion()==null)return;
            float pitch=net.minecraft.util.Mth.lerp(partial,e.xRotO,e.getXRot());
            Quaternionf rotation=new Quaternionf().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch+90));
            pose.pushPose();pose.mulPose(rotation);var mesh=DnvTurretMesh.NUCLEAR_MISSILE.get(DnvRenderDetail.distanceSquared(e));
            for(int pass=0;pass<2;pass++)for(var batch:pass==0?data.missileBase():data.missileGlow()){
                var consumer=buffers.getBuffer(pass==0?RenderType.entityCutoutNoCull(batch.material()):RenderType.eyes(batch.material()));
                for(var part:batch.parts()){
                    DnvTurretMeshRenderSupport.renderTintedQuads(
                            com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream.quads(mesh.part(part.name()).main()),pose,consumer,
                            pass==0?light:LightTexture.FULL_BRIGHT,pass==0?part.tint():0xffffff);
                }
            }
            if(e.armed()){
                float burnAge=e.flightAge()+partial;
                com.mojang.blaze3d.vertex.VertexConsumer consumer=buffers.getBuffer(com.liucodex.doggiesnewvehicle.client.fx.DnvJetRenderType.TYPE);
                int detail=DnvRenderDetail.distanceSquared(e)<48*48?2:1;
                int nozzle=0;
                for(var port:data.nozzles()){
                    pose.pushPose();pose.translate(port.x,port.y,port.z);
                    boolean central=nozzle++==0;
                    NuclearExhaust.render((x,y,z,r,g,b,a)->{
                        var current=pose.last();
                        consumer.vertex(current.pose(),(float)x,(float)y,(float)z).color(r,g,b,a)
                                .uv(0,0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                .uv2(LightTexture.FULL_BRIGHT).normal(current.normal(),0,-1,0).endVertex();
                    },central,burnAge,detail);
                    pose.popPose();
                }
            }
            pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
        }
    }
}
