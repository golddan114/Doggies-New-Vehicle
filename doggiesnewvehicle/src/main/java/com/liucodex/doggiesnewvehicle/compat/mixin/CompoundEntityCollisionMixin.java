package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.liucodex.doggiesnewvehicle.api.combat.DnvCompoundCollider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replace only opt-in entities' broadphase boxes with their physical pieces at vanilla's collision boundary. */
@Mixin(Entity.class)
public abstract class CompoundEntityCollisionMixin {
    @ModifyVariable(method="collideBoundingBox",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static List<VoxelShape> dnv$compound(List<VoxelShape> original,Entity mover,Vec3 motion,AABB box,Level level,List<VoxelShape> argument){
        if(original.isEmpty())return original;
        var query=box.expandTowards(motion).inflate(1e-7);
        var candidates=level.getEntities(mover,query,EntitySelector.NO_SPECTATORS);
        List<VoxelShape> changed=null;
        for(var candidate:candidates){
            if(!(candidate instanceof DnvCompoundCollider collider))continue;
            var broad=candidate.getBoundingBox();
            if(changed==null)changed=new ArrayList<>(original);
            boolean replaced=changed.removeIf(shape->{var boxes=shape.toAabbs();return boxes.size()==1&&boxes.get(0).equals(broad);});
            if(replaced)for(var piece:collider.dnv$collisionBoxes())if(piece.intersects(query))changed.add(Shapes.create(piece));
        }
        return changed==null?original:changed;
    }
}
