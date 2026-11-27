package com.liucodex.doggiesnewvehicle.client;

import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Read-only dry-air centre trajectory. No zeroing, aim assist, or server fire-state writes. */
public final class DnvArtilleryAimPoint {
    private DnvArtilleryAimPoint(){}
    public static Vec3 point(RenderContext context,Vec3 original){
        var player=context.getPlayer();
        if(!(player.getVehicle() instanceof VehicleEntity vehicle))return null;
        var namespace=EntityType.getKey(vehicle.getType()).getNamespace();
        if(!namespace.equals("doggievehicle")&&!namespace.equals("doggiesnewvehicle"))return null;
        var gun=vehicle.getGunData(vehicle.getSeatIndex(player));
        if(gun==null)return null;
        if(vehicle instanceof DnvBlizzardEntity blizzard){
            return "ColdBeam".equals(vehicle.getGunName(vehicle.getSeatIndex(player)))
                ? blizzard.primaryAimPoint(context.getPartialTick()):null;
        }
        String projectile=gun.get(GunProp.PROJECTILE).getItemId();
        if(!java.util.Set.of("superbwarfare:cannon_shell","superbwarfare:small_cannon_shell",
                "doggievehicle:storm_bullet").contains(projectile))return null;
        var from=vehicle.getShootPos(player,context.getPartialTick());
        var direction=vehicle.getShootVec(player,context.getPartialTick());
        double speed=((Number)gun.get(GunProp.VELOCITY)).doubleValue();
        double gravity=((Number)gun.get(GunProp.GRAVITY)).doubleValue();
        int life=((Number)gun.get(GunProp.PROJECTILE_LIFE)).intValue();
        if(!Double.isFinite(speed)||speed<=0||speed>128||!Double.isFinite(gravity)||gravity<0||gravity>4||life<1)return null;
        int ticks=Math.min(64,life);
        double range=Math.min(512,Math.min(from.distanceTo(original),speed*ticks));
        if(range<.1||direction.lengthSqr()<1e-10)return null;
        // A display projection follows the verified native move-then-gravity order.
        // It does not depend on autonomy permissions, safety decisions, or AI state.
        double remaining=range/speed;
        Vec3 velocity=direction.normalize().scale(speed);
        for(int step=0;step<ticks&&remaining>1e-8;step++){
            double duration=Math.min(1,remaining);
            Vec3 end=from.add(velocity.scale(duration));
            if(!vehicle.level().hasChunksAt(BlockPos.containing(from),BlockPos.containing(end)))return null;
            var hit=vehicle.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,vehicle));
            if(hit.getType()!=HitResult.Type.MISS)return hit.getLocation();
            from=end;remaining-=duration;
            velocity=velocity.add(0,-gravity,0);
        }
        return from;
    }
}
