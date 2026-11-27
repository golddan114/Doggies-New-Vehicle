package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.tools.VectorToolKt;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=CubePrototype.ID,value=Dist.CLIENT)
public final class CubeFighterSight {
    private static final double RANGE=512;
    private static CubeVehicle trackedJet;
    private static Vec3 lastSent;
    private static double boreRange;

    private static CubeVehicle gunner(Minecraft mc) {
        if (mc.player==null || !(mc.player.getVehicle() instanceof CubeVehicle jet) || jet.carrier()
                || jet.getFirstPassenger()!=mc.player || !"Cannon".equals(jet.getGunName(0))) return null;
        return jet;
    }

    private static Vec3 trace(CubeVehicle jet,Vec3 start,Vec3 direction) {
        Vec3 end=jet.level().clip(new ClipContext(start,start.add(direction.scale(RANGE)),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,jet)).getLocation();
        // Bound each entity query to the section of the ray currently being tested.
        double distance=start.distanceTo(end);
        for (double offset=0;offset<distance;offset+=32) {
            Vec3 a=start.add(direction.scale(offset));
            Vec3 b=start.add(direction.scale(Math.min(distance,offset+32)));
            var hit=ProjectileUtil.getEntityHitResult(jet.level(),jet,a,b,new AABB(a,b).inflate(1),
                    e->e.isPickable() && !e.isSpectator() && e.getRootVehicle()!=jet,0);
            if (hit!=null) return hit.getLocation();
        }
        return end;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        CubeVehicle jet=gunner(mc);
        if (jet!=trackedJet) {trackedJet=jet;lastSent=null;}
        if (jet==null || mc.screen!=null) return;
        var muzzle=jet.getShootPos("Cannon",1);
        boreRange=muzzle.distanceTo(trace(jet,muzzle,jet.getShootVec("Cannon",1)));
        if (!jet.hoverMode()) {lastSent=null;return;}
        // Tick input belongs to the pilot. A mirrored F5 camera looks back at the
        // jet and an ADS render pose can still describe the previous frame.
        var pilot=mc.player;
        var start=mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT
                ? pilot.getEyePosition() : mc.gameRenderer.getMainCamera().getPosition();
        Vec3 point=trace(jet,start,pilot.getViewVector(1));
        if (lastSent==null || point.distanceToSqr(lastSent)>.0001 || jet.tickCount%10==0) {
            new CubeAimPoint(jet.getId(),point).send();
            lastSent=point;
        }
    }

    @Mod.EventBusSubscriber(modid=CubePrototype.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Overlay {
        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("fighter_sight",(gui,g,partial,width,height)->{
                var mc=Minecraft.getInstance();
                var jet=gunner(mc);
                if (jet==null || mc.options.hideGui) return;
                int x=width/2,y=height/2;
                g.fill(x-1,y-1,x+2,y+2,0xb0202020);
                g.fill(x,y,x+1,y+1,0xffffffff);
                var muzzle=jet.getShootPos("Cannon",partial);
                var point=muzzle.add(jet.getShootVec("Cannon",partial).scale(jet==trackedJet ? boreRange : RANGE));
                var screen=VectorToolKt.worldToScreen(point);
                if (screen.z<=0 || screen.x<0 || screen.x>=width || screen.y<0 || screen.y>=height) return;
                int u=(int)Math.round(screen.x), v=(int)Math.round(screen.y), gold=0xffffc700;
                g.fill(u-5,v,u-1,v+1,gold);g.fill(u+2,v,u+6,v+1,gold);
                g.fill(u,v-5,u+1,v-1,gold);g.fill(u,v+2,u+1,v+6,gold);
            });
        }
    }
}
