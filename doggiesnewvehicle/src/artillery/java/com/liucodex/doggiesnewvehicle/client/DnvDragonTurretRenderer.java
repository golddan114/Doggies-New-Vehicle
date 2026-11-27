package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Six authored rigs; resource-scoped plans retain no entity/world and expire on mesh replacement. */
public class DnvDragonTurretRenderer extends EntityRenderer<Entity> {
    private static final Map<String,Materials> MATERIALS=DnvDragonTurretProfile.ALL.values().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(p->p.entity,p->new Materials(p.entity)));
    private final Map<DnvTurretMesh.MeshData,List<Part>> plans=new WeakHashMap<>();
    public DnvDragonTurretRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=2;}
    @Override public ResourceLocation getTextureLocation(Entity entity){return MATERIALS.get(DnvM1ClientBridge.profile(entity).entity).texture;}
    @Override public void render(Entity entity,float entityYaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light) {
        var p=DnvM1ClientBridge.profile(entity);var state=DnvM1ClientBridge.visual(entity,partialTick);
        var mesh=DnvTurretMesh.DRAGON.get(p.entity).get(DnvRenderDetail.distanceSquared(entity));
        // Six profiles x three detail levels. Bound old resource-pack plans even before a weak-key sweep.
        if(plans.size()>18)plans.clear();
        var parts=plans.computeIfAbsent(mesh,m->p.groups.stream().map(g->new Part(g,m.part(g),p.linkByGroup.get(g)))
            .filter(part->part.mesh.main().length>0||part.mesh.emissive().length>0).toList());
        var materials=MATERIALS.get(p.entity);
        float lamp=DnvTurretLampBrightness.value(state.powered(),state.damaged(),entity.tickCount+partialTick);
        boolean glow=lamp>=.02F&&!state.destroyed();
        var main=buffers.getBuffer(materials.main);
        for(var part:parts) {
            stack.pushPose();
            stack.mulPose(Axis.YP.rotationDegrees(-(part.base?state.baseYaw():state.yaw())));
            if(part.link!=null) DnvM1Hydraulics.apply(stack,p,part.link,state.elevation());
            else if(part.pitched) {
                stack.translate(p.pitch.x,p.pitch.y,p.pitch.z);stack.mulPose(Axis.XP.rotationDegrees(-state.elevation()));
                stack.translate(-p.pitch.x,-p.pitch.y,-p.pitch.z);
                if(part.barrel>=0)stack.translate(0,0,-p.recoilStroke*DnvM1ClientBridge.recoil(entity,part.barrel,partialTick));
            }
            // Only emissive parts need their completed pose copied for the second buffer pass.
            if(glow&&part.glow){part.pose.set(stack.last().pose());part.normal.set(stack.last().normal());}
            if(part.mesh.main().length>0)DnvTurretMeshRenderSupport.renderPart(part.mesh,stack,main,light,state.destroyed(),DnvTurretMeshRenderSupport.MeshPass.MAIN);
            stack.popPose();
        }
        if(glow) {
            var emission=buffers.getBuffer(materials.glow);
            for(var part:parts)if(part.glow) {
                stack.pushPose();stack.last().pose().set(part.pose);stack.last().normal().set(part.normal);
                DnvTurretMeshRenderSupport.renderStream(part.mesh.emissive(),stack,emission,LightTexture.FULL_BRIGHT,Math.round(lamp*255));
                stack.popPose();
            }
        }
        super.render(entity,entityYaw,partialTick,stack,buffers,light);
    }
    private static final class Materials {
        final ResourceLocation texture;
        final RenderType main,glow;
        Materials(String entity){
            texture=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/"+entity+".png");
            main=RenderType.entityCutout(texture);
            glow=RenderType.eyes(ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/"+entity+"_emission.png"));
        }
    }
    // Render-thread scratch only, reused between sequential entities; never submitted by reference.
    private static final class Part {
        final DnvTurretMesh.MeshPart mesh;
        final DnvDragonTurretProfile.Link link;
        final boolean base,pitched,glow;
        final int barrel;
        final Matrix4f pose;
        final Matrix3f normal;
        Part(String group,DnvTurretMesh.MeshPart mesh,DnvDragonTurretProfile.Link link){
            this.mesh=mesh;this.link=link;base=group.equals("base_static");glow=mesh.emissive().length>0;
            barrel=group.startsWith("recoil_barrel_")?Integer.parseInt(group.substring(14))-1:-1;
            pitched=group.equals("pitch_assembly")||barrel>=0;
            pose=glow?new Matrix4f():null;normal=glow?new Matrix3f():null;
        }
    }
}
