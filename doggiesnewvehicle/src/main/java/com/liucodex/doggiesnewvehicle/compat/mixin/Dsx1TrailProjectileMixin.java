package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotVisual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records accepted native flight only. No projectile scan, movement change or extra damage. */
@Mixin(value=ProjectileEntity.class,remap=false)
public abstract class Dsx1TrailProjectileMixin implements DnvNativeShotVisual.Trail {
    @Unique private Vec3 dnv$previous;
    @Unique private int dnv$sequence;
    @Unique private boolean dnv$finished;
    @Unique private double dnv$hiddenDistance;
    @Unique private DnvNativeShotVisual.TrailSink dnv$sink;
    @Override public void dnv$startDsx1Trail(Vec3 origin,DnvNativeShotVisual.TrailSink sink){
        dnv$previous=origin;dnv$sequence=0;dnv$finished=false;dnv$sink=sink;dnv$hiddenDistance=4;
    }
    @Inject(method="m_8119_",at=@At("TAIL"),remap=false,require=1)
    private void dnv$flightSample(CallbackInfo ci){
        if(dnv$previous!=null&&!dnv$finished)dnv$sample(((Entity)(Object)this).position());
    }
    @Inject(method="m_6532_",at=@At("TAIL"),remap=false,require=1)
    private void dnv$impactSample(HitResult hit,CallbackInfo ci){
        if(dnv$previous!=null&&!dnv$finished&&hit.getType()!=HitResult.Type.MISS){
            dnv$sample(hit.getLocation());dnv$finished=true;
        }
    }
    @Unique private void dnv$sample(Vec3 end){
        Entity self=(Entity)(Object)this;
        if(!(self.level() instanceof ServerLevel level))return;
        if(dnv$sequence>=128||!DnvNativeShotVisual.validSegment(dnv$previous,end)){dnv$finished=true;return;}
        double length=dnv$previous.distanceTo(end);
        if(length<1e-5)return;
        Vec3 from=dnv$previous;
        if(dnv$hiddenDistance>0){
            double hidden=Math.min(length,dnv$hiddenDistance);
            from=from.add(end.subtract(from).scale(hidden/length));
            dnv$hiddenDistance=Math.max(0,dnv$hiddenDistance-length);
        }
        dnv$previous=end;
        if(from.distanceToSqr(end)<1e-10)return;
        dnv$sink.send(level,self.getUUID(),dnv$sequence++,from,end);
    }
}
