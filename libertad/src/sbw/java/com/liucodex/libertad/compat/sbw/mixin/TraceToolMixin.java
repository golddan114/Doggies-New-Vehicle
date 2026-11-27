package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.tools.TraceTool;
import com.atsuishio.superbwarfare.tools.TraceTool.RayTraceResultEntity;
import com.liucodex.libertad.compat.sbw.NativeShells;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TraceTool.class, remap = false)
abstract class TraceToolMixin {
    @Inject(method = "getEntitiesAlongVector(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private static void libertad$boundMarkedEntityTrace(Level level, Vec3 from, Vec3 direction, Predicate<Entity> filter,
            CallbackInfoReturnable<List<RayTraceResultEntity>> callback) {
        if (filter instanceof NativeShells.PenetrationFilter bounded) {
            callback.setReturnValue(bounded.trace((ServerLevel) level, from, direction));
        }
    }
}
