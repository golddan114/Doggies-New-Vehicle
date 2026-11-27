package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.boss.fx.MothershipWreckFx;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

/** Render-visible wrecks only. Explicit quality/budget replaces vanilla's 32-block particle cutoff. */
public final class MothershipWreckParticles {
    private static final MothershipWreckFx SURFACE=new MothershipWreckFx(MothershipRig.get());
    private static final MothershipWreckFx.Budget BUDGET=new MothershipWreckFx.Budget();
    private MothershipWreckParticles() { }
    public static void emit(DnvMothershipEntity ship) {
        var client=Minecraft.getInstance();
        if(client.level==null||client.level!=ship.level()||ship.isRemoved()||!ship.isWreck())return;
        var camera=DnvMothershipEntity.v(client.gameRenderer.getMainCamera().getPosition());
        var bottom=DnvMothershipEntity.v(ship.position());
        var body=MothershipWreckFx.rotation(ship.getYRot(),ship.getXRot(),ship.getRoll());
        int setting=client.options.particles().get().getId();
        var plan=MothershipWreckFx.plan(SURFACE.distance(camera,bottom,body),setting);
        if(plan.sites()==0||Math.floorMod(ship.tickCount+ship.getId(),plan.interval())!=0)return;
        int count=BUDGET.claim(client.level,client.level.getGameTime(),ship,plan.sites()*plan.particlesPerSite(),setting);
        for(int i=0;i<count;i++) {
            int site=i/plan.particlesPerSite(),layer=i%plan.particlesPerSite();
            var p=SURFACE.world(SURFACE.anchor(ship.tickCount,site),bottom,body);
            // Skin point remains attached under pitch/roll; emitted smoke then rises in world space.
            client.level.addParticle(layer==1?ParticleTypes.FLAME:ParticleTypes.LARGE_SMOKE,true,
                    p.x(),p.y(),p.z(),0,layer==1?.10:.16,0);
        }
    }
}
