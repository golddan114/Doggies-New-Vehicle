package com.liucodex.carriercube.mixin;

import com.liucodex.carriercube.YueCarrier;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class CarrierRiderTrackingMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<net.minecraft.server.network.ServerPlayerConnection> seenBy;

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void keepMountedCarrier(ServerPlayer player, CallbackInfo callback) {
        // A mounted rider already tracks this live carrier. A warp can move
        // both before the chunk sender marks its destination as delivered.
        if (entity instanceof YueCarrier && player.getVehicle() == entity && entity.isAlive()
                && entity.level() == player.level() && seenBy.contains(player.connection)) callback.cancel();
    }
}
