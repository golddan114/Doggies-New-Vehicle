package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Loaded-only bounded world edge for the complete moving body. A missing chunk aborts without payload. */
final class NuclearMissileCollision {
    record Result(boolean loaded,double fraction,Vec3 contact) { }
    private NuclearMissileCollision(){ }
    static Result sweep(ServerLevel world,NuclearMissileEntity missile,List<AABB> body,Vec3 motion,java.util.UUID silo){
        var area=NuclearCollision.envelope(body).expandTowards(motion);var low=BlockPos.containing(area.minX,area.minY,area.minZ);
        var high=BlockPos.containing(area.maxX,area.maxY,area.maxZ);
        long cells=(long)(high.getX()-low.getX()+1)*(high.getY()-low.getY()+1)*(high.getZ()-low.getZ()+1);
        // Entity flight is valid above build height; only chunk columns determine admission.
        if(cells>8192||!world.hasChunksAt(low.getX(),low.getZ(),high.getX(),high.getZ()))
            return new Result(false,Double.POSITIVE_INFINITY,null);
        double nearest=Double.POSITIVE_INFINITY;AABB touched=null;var context=CollisionContext.of(missile);
        for(var position:BlockPos.betweenClosed(low,high)){
            if(position.getY()<world.getMinBuildHeight()||position.getY()>=world.getMaxBuildHeight())continue;
            BlockState block=world.getBlockState(position);
            for(var shape:block.getCollisionShape(world,position,context).toAabbs()){
                var obstacle=shape.move(position);double fraction=NuclearCollision.sweep(body,motion,obstacle);
                if(fraction<nearest){nearest=fraction;touched=obstacle;}
            }
        }
        for(Entity target:world.getEntities(missile,area,e->!e.getUUID().equals(silo)&&!e.isSpectator()&&e.isPickable())){
            var targets=target instanceof com.liucodex.doggiesnewvehicle.api.combat.DnvCompoundCollider c?c.dnv$collisionBoxes():List.of(target.getBoundingBox());
            for(var obstacle:targets){double fraction=NuclearCollision.sweep(body,motion,obstacle);if(fraction<nearest){nearest=fraction;touched=obstacle;}}
        }
        return new Result(true,nearest,touched==null?null:NuclearCollision.contact(body,motion,touched,nearest));
    }
}
