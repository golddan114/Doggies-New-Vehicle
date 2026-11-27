package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.client.overlay.VehicleTeamOverlay;
import com.atsuishio.superbwarfare.client.overlay.OverlayTraceHandler;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvWreckLifecycle;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the native health-bar projection changes; server health and wreck collision are untouched. */
@Mixin(value = VehicleTeamOverlay.class, remap = false)
public abstract class VehicleWreckHealthOverlayMixin {
    @Redirect(method = "render", at = @At(value = "FIELD",
            target = "Lcom/atsuishio/superbwarfare/client/overlay/OverlayTraceHandler;cameraEntity:Lnet/minecraft/world/entity/Entity;"), require = 1)
    private Entity dnv$visibleMarkerTarget() {
        Entity target = OverlayTraceHandler.cameraEntity;
        return target instanceof DnvWreckLifecycle lifecycle && !lifecycle.dnvShowVehicleMarker() ? null : target;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;getHealth()F"), require = 1)
    private float dnv$displayHealth(VehicleEntity vehicle) {
        // Native wreck bars add maxHealth to health. Cancel that offset only for opting-out content.
        return vehicle.isWreck() && vehicle instanceof DnvWreckLifecycle lifecycle && !lifecycle.dnvShowWreckHealth()
                ? -vehicle.getMaxHealth() : vehicle.getHealth();
    }
}
