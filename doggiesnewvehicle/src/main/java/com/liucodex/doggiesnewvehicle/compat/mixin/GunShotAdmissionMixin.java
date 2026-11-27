package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwShotExecution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunItem.class, remap = false)
public abstract class GunShotAdmissionMixin {
  @Redirect(method = "shootBullet(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)Z",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"), remap = false)
  private boolean dnv$worldAdmission(ServerLevel level, Entity projectile, ShootParameters parameters) {
    com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotVisual.prepare(projectile,parameters);
    return DnvSbwShotExecution.admit(level, projectile, parameters);
  }

  @Inject(method = "shootBullet(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)Z",
      at = @At("RETURN"), cancellable = true, remap = false)
  private void dnv$admissionResult(ShootParameters parameters, CallbackInfoReturnable<Boolean> callback) {
    boolean original = callback.getReturnValueZ();
    boolean accepted = DnvSbwShotExecution.bulletResult(parameters, original);
    // An unchanged result must leave other addons' return hooks reachable.
    if (accepted != original) callback.setReturnValue(accepted);
  }
}
