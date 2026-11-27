package com.liucodex.carriercube;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector4f;

@Mod.EventBusSubscriber(modid=CubePrototype.ID,value=Dist.CLIENT)
public final class CubeHomeHud {
    private static float nx,ny,bowX,bowY;
    private static int heading;
    private static int range,bowRange;
    private static CubeVehicle aircraft;
    @SubscribeEvent public static void project(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();aircraft=null;
        if(mc.player==null || !(mc.player.getVehicle() instanceof CubeVehicle plane) || plane.carrier())return;
        aircraft=plane;
        if(!plane.bridgeView().contains("homePoint")){range=-1;return;}
        var point=BlockPos.of(plane.bridgeView().getLong("homePoint")).getCenter();
        var delta=point.subtract(event.getCamera().getPosition());
        range=(int)Math.round(delta.length());
        var v=new Vector4f((float)delta.x,(float)delta.y,(float)delta.z,1);
        event.getPoseStack().last().pose().transform(v);event.getProjectionMatrix().transform(v);
        heading=plane.bridgeView().getInt("homeYaw");
        var dir=net.minecraft.world.phys.Vec3.directionFromRotation(0,heading);
        bowRange=(int)Math.round(plane.position().distanceTo(point.add(dir.scale(CubeGeometry.HALF_LENGTH))));
        var axis=new Vector4f((float)dir.x,0,(float)dir.z,0);
        event.getPoseStack().last().pose().transform(axis);event.getProjectionMatrix().transform(axis);
        // Screen-space derivative of the projected ship axis, including perspective.
        bowX=axis.x*v.w-v.x*axis.w;
        bowY=v.y*axis.w-axis.y*v.w;
        float w=Math.max(.001F,Math.abs(v.w));nx=v.x/w;ny=-v.y/w;
        if(v.w<=0) {float scale=1.4F/Math.max(.001F,Math.max(Math.abs(nx),Math.abs(ny)));
            if(Math.abs(nx)+Math.abs(ny)<.001F)ny=1.4F;else {nx*=scale;ny*=scale;}}
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(aircraft==null || mc.player==null || mc.player.getVehicle()!=aircraft || mc.options.hideGui || mc.screen!=null)return;
        var g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        int seconds=aircraft.bridgeView().getInt("countdown");
        if(seconds>0)g.drawCenteredString(mc.font,Component.translatable("screen.dnvcarriercube.remaining",seconds),w/2,h/2-44,0xffffd273);
        int guidance=aircraft.bridgeView().getInt("guidance");
        if(guidance>0)g.drawCenteredString(mc.font,Component.translatable("screen.dnvcarriercube.guide"+guidance),w/2,h/2-44,0xff9fe0cf);
        if(aircraft.onDeck() && guidance!=1 || range<0)return;
        int x=Mth.clamp(Math.round((nx+1)*w/2),27,w-27),y=Mth.clamp(Math.round((ny+1)*h/2),14,h-24);
        int c=0xff9fe0cf;
        for(int dx:new int[]{-1,1})for(int dy:new int[]{-1,1}) {
            int a=x+dx*8,b=y+dy*8;
            g.hLine(Math.min(a,a-dx*4),Math.max(a,a-dx*4),b,c);
            g.vLine(a,Math.min(b,b-dy*4),Math.max(b,b-dy*4),c);
        }
        float dx=bowX*w,dy=bowY*h;
        double length=Math.hypot(dx,dy);
        if (length>.001) {
            for (int i=8;i<=23;i++) {
                int px=x+(int)Math.round(dx*i/length), py=y+(int)Math.round(dy*i/length);
                g.fill(px,py,px+2,py+2,c);
            }
        }
        String compass=new String[]{"S","SW","W","NW","N","NE","E","SE"}[Math.floorMod(Math.round(heading/45F),8)];
        g.drawCenteredString(mc.font,Component.translatable("screen.dnvcarriercube.home",range).append(" · "+compass),x,y+12,c);
        if(guidance==0 && range<450) {
            var info=Component.translatable("screen.dnvcarriercube.bow_range",bowRange,CubeGeometry.CAPTURE_RADIUS);
            int half=mc.font.width(info)/2;
            g.drawCenteredString(mc.font,info,Mth.clamp(x,half+1,w-half-1),Math.min(h-10,y+23),bowRange<=CubeGeometry.CAPTURE_RADIUS?c:0xffffd273);
        }
    }
}
