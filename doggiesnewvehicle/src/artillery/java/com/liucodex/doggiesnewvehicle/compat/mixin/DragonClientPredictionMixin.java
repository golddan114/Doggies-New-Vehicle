package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native packet/held-fire timing, but wait for accepted feedback before playing the local shot. */
@Mixin(value=ClientEventHandler.class,remap=false)
public abstract class DragonClientPredictionMixin {
    @Redirect(method="clientShootVehicle",at=@At(value="INVOKE",target=
        "Lcom/atsuishio/superbwarfare/event/ClientEventHandler;playVehicleClientSounds("
        + "Lnet/minecraft/world/entity/player/Player;Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;)V"),
        require=1,allow=1,remap=false)
    private void dnv$acceptedLocalSound(ClientEventHandler handler,Player player,VehicleEntity vehicle){
        if(!(vehicle instanceof DnvNativeShotAdmission host)||!host.deferNativeClientSound())
            handler.playVehicleClientSounds(player,vehicle);
    }
}
