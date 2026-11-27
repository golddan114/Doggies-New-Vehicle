package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.liucodex.doggiesnewvehicle.compat.DnvCompoundRaytrace;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One native broadphase query; ordinary entities keep vanilla hit/rider semantics. */
@Mixin(ProjectileUtil.class)
public abstract class CompoundEntityRaytraceMixin {
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
            +"Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
            +"Lnet/minecraft/world/phys/EntityHitResult;",at=@At("HEAD"),cancellable=true)
    private static void dnv$pick(Entity actor,Vec3 from,Vec3 to,AABB bounds,Predicate<Entity> filter,double maximum,
            CallbackInfoReturnable<EntityHitResult> callback){
        callback.setReturnValue(DnvCompoundRaytrace.pick(actor,from,to,actor.level().getEntities(actor,bounds,filter),maximum));
    }
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
            +"Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;"
            +"Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("HEAD"),cancellable=true)
    private static void dnv$projectile(Level level,Entity actor,Vec3 from,Vec3 to,AABB bounds,Predicate<Entity> filter,float margin,
            CallbackInfoReturnable<EntityHitResult> callback){
        callback.setReturnValue(DnvCompoundRaytrace.projectile(from,to,level.getEntities(actor,bounds,filter),margin));
    }
}
