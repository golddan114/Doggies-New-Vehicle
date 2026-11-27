package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.subdata.Reload;
import com.atsuishio.superbwarfare.event.GunEventHandler;
import com.liucodex.doggiesnewvehicle.entity.DnvM1YunlongEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Audio-only projection for SBW 0.8.9.1's first reload timer read; the native timer is never written. */
@Mixin(value = GunEventHandler.class, remap = false)
public abstract class M1ReloadSoundMixin {
  @Redirect(method = "gunTick(Lnet/minecraft/world/entity/Entity;Lcom/atsuishio/superbwarfare/data/gun/GunData;ZD)V",
      at = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/data/gun/subdata/Reload;time()I", ordinal = 0),
      require = 1, allow = 1, remap = false)
  private int dnv$reloadSoundComparisonTime(Reload reload, Entity entity, GunData data,
      boolean selected, double environmentRate) {
    int remaining = reload.time();
    if (!(entity instanceof DnvM1YunlongEntity)) return remaining;
    int duration = data.get(GunProp.EMPTY_RELOAD_TIME);
    int cue = data.get(GunProp.SOUND_INFO).getVehicleReloadSoundTime();
    int nativeCue = cue == 0 ? duration - 1 : Mth.clamp(cue, 1, duration - 1);
    // SBW compares even while idle. The complement cannot equal its cue, including malformed int bounds.
    if (!data.reloading() || remaining <= 1 || duration <= 0) return ~nativeCue;
    if (duration <= 2 && remaining == 2) {
      // Native completion runs after reducing 2 to 1, then resets to 0. Emit once before that reset.
      // Project to the native cue (0 or 1), without changing duration, ammunition, RPM or persisted data.
      return nativeCue;
    }
    return remaining;
  }
}
