package com.liucodex.carriercube;

import com.liucodex.doggiesnewvehicle.fx.DnvJetMesh;
import com.liucodex.doggiesnewvehicle.client.fx.DnvJetRenderType;
import com.liucodex.doggiesnewvehicle.client.DnvVehicleLoopSoundManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = CubePrototype.ID, value = Dist.CLIENT)
public final class CubePropulsion {
    private static final Set<CubeVehicle> visible = new LinkedHashSet<>();
    private static MultiBufferSource.BufferSource buffers;
    private static DnvVehicleLoopSoundManager<CubeVehicle> loops;
    private static final Map<CubeVehicle, WarpTone> warpTones = new LinkedHashMap<>();

    private static final class WarpTone extends EntityBoundSoundInstance {
        WarpTone(CubeVehicle ship) {
            super(CubePrototype.WARP_LOCK.get(),net.minecraft.sounds.SoundSource.HOSTILE,1,1,ship,ship.getId());
            looping=true;
        }
    }

    @SubscribeEvent
    public static void watchCarrier(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof CubeVehicle ship && ship.carrier())
            warpTones.putIfAbsent(ship,null);
    }

    @SubscribeEvent
    public static void forgetCarrier(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof CubeVehicle ship) {
            var sound=warpTones.remove(ship);
            if (sound!=null) Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    static void enqueue(CubeVehicle vehicle) {
        if (vehicle.thrust() > .01 || vehicle.chargingWarp() || vehicle.arrivalAge(0)<40) visible.add(vehicle);
    }

    @SubscribeEvent
    public static void soundTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (loops == null) {
            loops = new DnvVehicleLoopSoundManager<>(CubeVehicle.class, 13, CubePrototype.IDLE.get(),
                    CubePrototype.PROPULSION.get(), null,
                    v -> v.carrier() && v.isAlive() && !v.isRemoved() && !v.isWreck(),
                    CubeVehicle::thrust, 160, 176, true);
        }
        loops.tick();
        var mc=Minecraft.getInstance();
        var sounds=mc.getSoundManager();
        warpTones.replaceAll((ship,sound)->{
            boolean audible=mc.player!=null && ship.level()==mc.level && ship.isAlive() && ship.chargingWarp()
                    && ship.distanceToSqr(mc.player)<192*192;
            if (!audible) {
                if (sound!=null) sounds.stop(sound);
                return null;
            }
            if (sound!=null && sounds.isActive(sound)) return sound;
            var tone=new WarpTone(ship);
            sounds.play(tone);
            return tone;
        });
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            visible.clear();
            warpTones.entrySet().removeIf(entry->{
                if (entry.getKey().level()!=event.getLevel()) return false;
                if (entry.getValue()!=null) Minecraft.getInstance().getSoundManager().stop(entry.getValue());
                return true;
            });
        }
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) visible.clear();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || visible.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new BufferBuilder(128 * 1024));
        var camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        try {
            for (var vehicle : visible) {
                if (vehicle.isRemoved() || vehicle.level() != Minecraft.getInstance().level) continue;
                double distance = vehicle.distanceToSqr(camera);
                if (distance >= 256 * 256) continue;
                var pose = event.getPoseStack();
                var origin = vehicle.getPosition(partial).subtract(camera);
                pose.pushPose();
                pose.translate(origin.x, origin.y, origin.z);
                CubeRenderer.applyBodyRotation(vehicle, partial, pose);
                int detail = distance < 96 * 96 ? 2 : 1;
                double tick = vehicle.tickCount + partial;
                if (vehicle.carrier()) {
                    pose.scale(CubeGeometry.SHIP_SCALE, CubeGeometry.SHIP_SCALE, CubeGeometry.SHIP_SCALE);
                    warpRings(vehicle,pose,partial);
                    for (int side : new int[]{-1, 1}) {
                        for (var port : SHIP_PORTS) {
                            jet(pose, side * port.x, port.y, port.z, port.radius, port.down ? 90 : 0,
                                    vehicle.thrust(), tick + side * port.x, detail);
                        }
                    }
                } else {
                    pose.translate(.103, 1.933, -4.04);
                    pose.mulPose(Axis.XP.rotationDegrees(-vehicle.nozzleAngle(partial)));
                    jet(pose, 0, 0, -1.23, .40, 0, vehicle.thrust(), tick, detail);
                }
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            visible.clear();
        }
    }

    private static void warpRings(CubeVehicle ship,PoseStack pose,float partial) {
        double q=ship.chargeFraction(partial);
        if(ship.chargingWarp()) {
            double elapsed=400*q;
            int latest=(int)(elapsed/27);
            for(int n=Math.max(0,latest-3);n<=latest;n++) {
                double u=(elapsed-27*n)/86;
                if(u<=1)ring(pose,70-140*u,24,18,(.32+.68*q)*Math.min(1,Math.min(u,1-u)*12));
            }
        }
        double age=ship.arrivalAge(partial);
        if(age>=0 && age<40) {
            var out=buffers.getBuffer(DnvJetRenderType.TYPE);
            var mat=pose.last();
            CubeWarpExit.draw((x,y,z,r,g,b,a)->out.vertex(mat.pose(),(float)x,(float)y,(float)z)
                    .color(r,g,b,a).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(mat.normal(),0,0,1).endVertex(),age);
        }
    }

    private static void ring(PoseStack pose,double z,double rx,double ry,double intensity) {
        var out=buffers.getBuffer(DnvJetRenderType.TYPE);
        var mat=pose.last();
        for(double band:new double[]{.90,.22}) {
            float alpha=(float)(intensity*(band>.5?.18:.91));
            for(int i=0;i<64;i++)for(int corner=0;corner<4;corner++){
                double a=(i+(corner>=2?1:0))*Math.PI/32;
                double w=corner==0 || corner==3 ? -band : band;
                out.vertex(mat.pose(),(float)((rx+w)*Math.cos(a)),(float)(12+(ry+w)*Math.sin(a)),(float)z)
                        .color(.50F,.88F,1F,alpha).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(mat.normal(),0,0,1).endVertex();
            }
        }
    }

    // Nozzle mouths measured in local space from the widest ring of each authored plume.
    private record Port(double x, double y, double z, double radius, boolean down) {}
    private static final Port[] SHIP_PORTS = {
            new Port(9.01,5.09,-40.97,.94,true), new Port(9.01,5.09,-48.13,.94,true),
            new Port(9.01,5.10,-8.65,.79,true), new Port(9.01,5.10,-12.52,.74,true),
            new Port(2.345,15.85,-59.88,.925,false), new Port(2.345,12,-59.88,.925,false),
            new Port(6.07,15.85,-59.88,.92,false), new Port(6.07,12,-59.88,.92,false),
            new Port(11.375,11.11,-59.12,.815,false), new Port(11.08,16.21,-59.09,.555,false),
            new Port(1.295,7.405,-59.60,.475,false), new Port(3.425,7.405,-59.60,.475,false),
            new Port(5.555,7.405,-59.60,.475,false), new Port(7.545,7.405,-59.59,.435,false),
            new Port(17.57,16.41,-16.88,.53,false)
    };

    private static void jet(PoseStack pose, double x, double y, double z, double radius,
                            int down, double thrust, double tick, int detail) {
        pose.pushPose();
        pose.translate(x,y,z);
        pose.mulPose(Axis.XP.rotationDegrees(down == 90 ? 90 : 180));
        var vertex = buffers.getBuffer(DnvJetRenderType.TYPE);
        var matrix = pose.last();
        DnvJetMesh.Sink sink = (vx,vy,vz,r,g,b,a) -> vertex.vertex(matrix.pose(),(float)vx,(float)vy,(float)vz)
                .color(r,g,b,a).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(matrix.normal(),0,0,1).endVertex();
        float glow = (float) (.25 + thrust * .7);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4;
            sink.vertex(0,0,.012,.65F,.88F,1,glow);
            sink.vertex(radius*.83*Math.cos(a), radius*.83*Math.sin(a),.012,.22F,.59F,1,glow);
            sink.vertex(radius*.83*Math.cos(b), radius*.83*Math.sin(b),.012,.22F,.59F,1,glow);
            sink.vertex(0,0,.012,.65F,.88F,1,glow);
        }
        DnvJetMesh.exhaust(sink,thrust,radius,tick,detail);
        pose.popPose();
    }
}
