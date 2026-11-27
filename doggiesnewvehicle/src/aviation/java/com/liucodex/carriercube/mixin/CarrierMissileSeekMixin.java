package com.liucodex.carriercube.mixin;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.carriercube.CubePrototype;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class CarrierMissileSeekMixin {
    @Redirect(method = "vehicleWeaponSeeking", at = @At(value = "INVOKE",
            target = "Lcom/atsuishio/superbwarfare/tools/SeekTool$Builder;heightRange(DD)Lcom/atsuishio/superbwarfare/tools/SeekTool$Builder;"))
    private SeekTool.Builder carrier$targetHeight(SeekTool.Builder query, double min, double max, Player pilot) {
        return CubePrototype.targetHeight(query, min, max, pilot);
    }

    @Redirect(method = "vehicleWeaponSeeking", at = @At(value = "INVOKE",
            target = "Lcom/atsuishio/superbwarfare/tools/SeekTool$Builder;notFriendly()Lcom/atsuishio/superbwarfare/tools/SeekTool$Builder;"))
    private SeekTool.Builder carrier$filterCandidates(SeekTool.Builder query, Player pilot) {
        query.notFriendly();
        if (!CubePrototype.fliesFighter(pilot)) return query;
        return query.custom(candidate -> CubePrototype.selectedMissileTarget(pilot,candidate));
    }

    @Inject(method = "vehicleWeaponSeeking", at = @At("HEAD"))
    private void carrier$releaseIneligibleTarget(Player pilot, CallbackInfo callback) {
        var locked=ClientEventHandler.lockingEntityVehicle;
        if (CubePrototype.fliesFighter(pilot) && locked!=null && !CubePrototype.selectedMissileTarget(pilot,locked))
            ((ClientEventHandler)(Object)this).seekFailure(pilot);
    }
}
