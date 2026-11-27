package com.liucodex.doggiesnewvehicle.client.mixin;

import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.client.overlay.VehicleCrosshairOverlay;
import com.atsuishio.superbwarfare.tools.VectorToolKt;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Align DNV reticles with the real muzzle trajectory, including static native artwork. */
@Mixin(value=VehicleCrosshairOverlay.class,remap=false)
public abstract class BlizzardCrosshairMixin {
    @Unique private Vec3 dnv$screenAim;
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@At("HEAD"),require=1)
    private void dnv$beginReticle(RenderContext context,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dnv$screenAim = null;
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@At("RETURN"),require=1)
    private void dnv$chargeColumn(RenderContext context,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof
                com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity tank) || !tank.mode2()
                || !tank.isAlive() || tank.isWreck() || tank.hasTv() || tank.getWeaponIndex(0) != 0
                || tank.getFirstPassenger() != mc.player) return;
        int x = context.getScreenWidth() / 2, y = context.getScreenHeight() / 2;
        if (dnv$screenAim != null && Double.isFinite(dnv$screenAim.x) && Double.isFinite(dnv$screenAim.y)) {
            x = (int)Math.round(dnv$screenAim.x); y = (int)Math.round(dnv$screenAim.y);
        }
        com.liucodex.doggiesnewvehicle.client.BlizzardChargeHud.draw(context.getGuiGraphics(),x,y,tank.coldPhase(),tank.coldProgress());
    }
    @Redirect(method="render",at=@At(value="INVOKE",target=
            "Lcom/atsuishio/superbwarfare/tools/VectorToolKt;worldToScreen(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",ordinal=0),require=1)
    private Vec3 dnv$beamCrosshair(Vec3 original,RenderContext context){
        var point=com.liucodex.doggiesnewvehicle.client.DnvArtilleryAimPoint.point(context,original);
        dnv$screenAim=point==null?null:VectorToolKt.worldToScreen(point);
        return point==null?VectorToolKt.worldToScreen(original):dnv$screenAim;
    }
    @Redirect(method="render",at=@At(value="INVOKE",target=
        "Lcom/atsuishio/superbwarfare/client/RenderHelper;blit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;FFFFFFFFI)V"),require=1)
    private void dnv$placePlainReticle(com.mojang.blaze3d.vertex.PoseStack stack,net.minecraft.resources.ResourceLocation texture,
            float x,float y,float u,float v,float width,float height,float textureWidth,float textureHeight,int color,RenderContext context){
        if(dnv$fixed(texture)){
            x+=(float)dnv$screenAim.x-context.getScreenWidth()/2f;
            y+=(float)dnv$screenAim.y-context.getScreenHeight()/2f;
        }
        com.atsuishio.superbwarfare.client.RenderHelper.blit(stack,texture,x,y,u,v,width,height,textureWidth,textureHeight,color);
    }
    @Unique private boolean dnv$fixed(net.minecraft.resources.ResourceLocation texture){
        if(dnv$screenAim==null||!Double.isFinite(dnv$screenAim.x)||!Double.isFinite(dnv$screenAim.y))return false;
        String path=texture.getPath();
        return path.equals("textures/overlay/vehicle/crosshair/common_cannon_zooming.png")
            ||path.equals("textures/overlay/vehicle/crosshair/common_cannon.png")
            ||path.equals("textures/overlay/vehicle/crosshair/ru_apc.png")
            ||path.equals("textures/overlay/vehicle/crosshair/common_cross.png");
    }
    @Redirect(method="render",at=@At(value="INVOKE",target=
        "Lcom/atsuishio/superbwarfare/client/RenderHelper;preciseBlit(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;FFFFFFFF)V"),require=1)
    private void dnv$placeFixedReticle(net.minecraft.client.gui.GuiGraphics graphics,net.minecraft.resources.ResourceLocation texture,
            float x,float y,float u,float v,float width,float height,float textureWidth,float textureHeight,RenderContext context){
        if(dnv$fixed(texture)){
            x+=(float)dnv$screenAim.x-context.getScreenWidth()/2f;
            y+=(float)dnv$screenAim.y-context.getScreenHeight()/2f;
        }
        com.atsuishio.superbwarfare.client.RenderHelper.preciseBlit(graphics,texture,x,y,u,v,width,height,textureWidth,textureHeight);
    }
}
