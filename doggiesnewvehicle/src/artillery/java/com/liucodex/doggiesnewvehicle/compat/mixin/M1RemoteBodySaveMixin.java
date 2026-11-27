package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.liucodex.doggiesnewvehicle.compat.DnvM1RemoteCrew;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only M1 remote crew changes vanilla rider-save positioning; other vehicles stay untouched. */
@Mixin(value = Entity.class, remap = false)
public abstract class M1RemoteBodySaveMixin {
  @Inject(method = {"saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
      "m_20240_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"}, at = @At("RETURN"), remap = false)
  private void dnv$saveRemoteBody(CompoundTag tag, CallbackInfoReturnable<CompoundTag> callback) {
    DnvM1RemoteCrew.preserveBodySave((Entity) (Object) this, callback.getReturnValue());
  }
}
