package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Authored mesh, six independent attachment transforms and a separate fullbright emission pass. */
public final class DnvMothershipRenderer extends EntityRenderer<DnvMothershipEntity> {
    private static final ResourceLocation BASE=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/mothership_base.png");
    private static final ResourceLocation EMISSION=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/mothership_emission.png");
    private final MothershipRig rig=MothershipRig.get();
    private final Map<String,DnvMeshCodec.Part> lodParts=loadLod();
    private final Map<DnvMothershipEntity,Boolean> farLod=new WeakHashMap<>();
    public DnvMothershipRenderer(EntityRendererProvider.Context context) {super(context);shadowRadius=0;}

    private Map<String,DnvMeshCodec.Part> loadLod() {
        try(InputStream input=DnvMothershipRenderer.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/mothership_lod.shm")) {
            if(input==null)throw new IllegalStateException("Missing client mothership LOD");
            Map<String,DnvMeshCodec.Part> result=DnvMeshCodec.parse(input.readAllBytes());
            if(!result.keySet().equals(rig.parts.keySet()))throw new IllegalStateException("Mothership LOD part identity changed");
            for(String name:result.keySet())if(result.get(name).material()!=rig.parts.get(name).material())
                throw new IllegalStateException("Mothership LOD material changed: "+name);
            return result;
        } catch(IOException e) {throw new IllegalStateException("Cannot load mothership LOD",e);}
    }

    private Map<String,DnvMeshCodec.Part> renderParts(DnvMothershipEntity ship) {
        double distance=ship.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        boolean far=Boolean.TRUE.equals(farLod.get(ship));
        if(far ? distance<144*144 : distance>=160*160)far=!far;
        farLod.put(ship,far);
        return far?lodParts:rig.parts;
    }
    @Override public ResourceLocation getTextureLocation(DnvMothershipEntity entity) {return BASE;}

    @Override public boolean shouldRender(DnvMothershipEntity entity,Frustum frustum,double x,double y,double z) {
        if(!entity.entryActive()) {
            Vec3 anchor=entity.renderBeaconAnchor();
            if(Math.min(entity.distanceToSqr(x,y,z),anchor.distanceToSqr(x,y,z))>256*256)return false;
            return frustum.isVisible(entity.getBoundingBox().minmax(
                    new net.minecraft.world.phys.AABB(entity.position(),anchor).inflate(48)));
        }
        if(entity.distanceToSqr(x,y,z)>256*256 && !com.liucodex.doggiesnewvehicle.boss.BeamVisibility.nearby(
                new Vec3(x,y,z),entity.position(),entity.position().add(entity.renderBeamOffset())))return false;
        double fxMargin = entity.renderShotStarted() >= 0 || entity.renderAbortStarted() >= 0 ? 32 : 4;
        return frustum.isVisible(entity.getBoundingBox().inflate(fxMargin).minmax(
                new net.minecraft.world.phys.AABB(entity.position(),entity.position().add(entity.renderBeamOffset())).inflate(fxMargin)));
    }
    @Override public void render(DnvMothershipEntity ship,float ignoredYaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(ship.renderEntryAge()<com.liucodex.doggiesnewvehicle.boss.MothershipArrival.BEACON_TICKS) {
            MothershipFxPass.enqueue(ship);return;
        }
        Map<String,DnvMeshCodec.Part> parts=renderParts(ship);
        float yaw=Mth.rotLerp(partial,ship.yRotO,ship.getYRot());
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial,ship.xRotO,ship.getXRot())));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial,ship.getPrevRoll(),ship.getRoll())));
        pose.translate(0,rig.lift,0);
        boolean wreck=ship.isWreck()||ship.getHealth()<=0;
        if(wreck)MothershipWreckParticles.emit(ship);
        int alpha=ship.entryActive()?255:(int)Math.round(255*com.liucodex.doggiesnewvehicle.boss.MothershipArrival.reveal(ship.renderEntryAge()+partial));
        draw(pose,buffers,parts.get("body_static/body"),light,wreck,alpha);
        draw(pose,buffers,parts.get("body_static/light"),light,wreck,alpha);
        for(int i=0;i<6;i++) {
            var station=rig.stations.get(i);var angles=ship.renderPose(i,partial);
            pose.pushPose();translate(pose,station.yawPivot());pose.mulPose(Axis.YP.rotationDegrees((float)-angles.yaw()));
            draw(pose,buffers,parts.get("yaw_assembly_"+station.id()+"/body"),light,wreck,alpha);
            translate(pose,station.pitchOffset());pose.mulPose(Axis.XP.rotationDegrees((float)-angles.elevation()));
            draw(pose,buffers,parts.get("pitch_assembly_"+station.id()+"/body"),light,wreck,alpha);pose.popPose();
        }
        pose.popPose();
        MothershipFxPass.enqueue(ship);
        super.render(ship,ignoredYaw,partial,pose,buffers,light);
    }

    /** Called after scene entities have populated depth, with a camera-relative world pose. */
    public static void renderFx(DnvMothershipEntity ship,float partial,PoseStack pose,MultiBufferSource buffers,Vec3 camera) {
        if(ship.isWreck()||ship.getHealth()<=0)return;
        var rig=MothershipRig.get();
        float yaw=Mth.rotLerp(partial,ship.yRotO,ship.getYRot());
        Vec3 origin=new Vec3(Mth.lerp(partial,ship.xo,ship.getX()),Mth.lerp(partial,ship.yo,ship.getY()),Mth.lerp(partial,ship.zo,ship.getZ()));
        if(!ship.entryActive()) {
            MothershipFxRenderer.arrival(pose,buffers,ship.renderBeaconAnchor(),origin,camera,
                    ship.renderEntryAge()+partial,yaw);
            return;
        }
        V bottom=DnvMothershipEntity.v(origin);
        Vec3 muzzle=DnvMothershipEntity.mc(rig.modelToWorld(rig.doomsday,bottom,yaw));
        var nozzles=new ArrayList<MothershipFxRenderer.Nozzle>(10);
        for(var engine:rig.engines) nozzles.add(new MothershipFxRenderer.Nozzle(
                DnvMothershipEntity.mc(rig.modelToWorld(engine.position(),bottom,yaw)),
                DnvMothershipEntity.mc(MothershipRig.yaw(engine.direction(),yaw)),engine.radius()));
        long start=ship.renderShotStarted();double elapsed=start<0?-1:ship.level().getGameTime()-start+partial;
        long abort=ship.renderAbortStarted();
        Vec3 end=start<0 && abort<0?muzzle:ship.renderBeamEnd();
        if(abort>=0) {
            double age=ship.level().getGameTime()-abort+partial;
            MothershipFxRenderer.renderAbortAt(pose,buffers,camera,camera,muzzle,end,nozzles,
                    ship.renderAbortElapsed(),age,ship.renderThrottle(),ship.level().getGameTime()+partial);
            MothershipFxRenderer.hullCharge(pose,buffers,origin,camera,camera,ship.renderAbortElapsed(),
                    com.liucodex.doggiesnewvehicle.boss.fx.MothershipFx.sampleAbort(ship.renderAbortElapsed(),age,0));
        } else MothershipFxRenderer.renderAt(pose,buffers,camera,camera,muzzle,end,nozzles,
                    elapsed,ship.renderThrottle(),ship.renderImpact(),ship.level().getGameTime()+partial);
        if(start>=0) {
            MothershipFxRenderer.hullCharge(pose,buffers,origin,camera,camera,elapsed);
            MothershipFxRenderer.groundPulse(pose,buffers,ship.renderGroundPoint(),camera,camera,elapsed);
        }
        for(int bay=0;bay<2;bay++)MothershipFxRenderer.dropCharge(pose,buffers,
                DnvMothershipEntity.mc(rig.modelToWorld(rig.drops.get(bay),bottom,yaw)),camera,
                camera,ship.renderDropCharge(bay),ship.level().getGameTime()+partial);
    }
    private static void translate(PoseStack pose,V p) {pose.translate(p.x(),p.y(),p.z());}
    private static void draw(PoseStack pose,MultiBufferSource buffers,DnvMeshCodec.Part part,int light,boolean wreck,int alpha) {
        if(part==null||alpha<=0)return;
        boolean emission=part.material()==1;
        if(wreck&&emission)return;
        int tint=wreck?45:255;
        VertexConsumer buffer=buffers.getBuffer(alpha<255?RenderType.entityTranslucent(emission?EMISSION:BASE)
                :emission?RenderType.eyes(EMISSION):RenderType.entityCutoutNoCull(BASE));
        float[] stream=DnvQuadStream.quads(part.stream());var matrix=pose.last();int packed=emission?LightTexture.FULL_BRIGHT:light;
        for(int base=0;base<stream.length;base+=32) {
            for(int corner=0;corner<4;corner++) {
                int i=base+corner*8;
                buffer.vertex(matrix.pose(),stream[i],stream[i+1],stream[i+2]).color(tint,tint,tint,alpha)
                        .uv(stream[i+6],stream[i+7]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packed)
                        .normal(matrix.normal(),stream[i+3],stream[i+4],stream[i+5]).endVertex();
            }
        }
    }
}
