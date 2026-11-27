package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.api.combat.DnvCompoundCollider;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Vanilla hit ordering and rider rules, with the opt-in physical pieces replacing the broadphase envelope. */
public final class DnvCompoundRaytrace {
    private DnvCompoundRaytrace(){ }
    public static EntityHitResult pick(Entity actor,Vec3 from,Vec3 to,List<Entity> candidates,double maximum){
        double nearest=maximum;Entity selected=null;Vec3 point=null;
        for(Entity entity:candidates){
            double margin=entity.getPickRadius();Vec3 hit=hit(entity,from,to,margin);
            if(contains(entity,from,margin)){
                if(nearest>=0){selected=entity;point=hit==null?from:hit;nearest=0;}
            }else if(hit!=null){
                double distance=from.distanceToSqr(hit);
                if(distance<nearest||nearest==0){
                    if(entity.getRootVehicle()==actor.getRootVehicle()&&!entity.canRiderInteract()){
                        if(nearest==0){selected=entity;point=hit;}
                    }else{selected=entity;point=hit;nearest=distance;}
                }
            }
        }
        return selected==null?null:new EntityHitResult(selected,point);
    }
    public static EntityHitResult projectile(Vec3 from,Vec3 to,List<Entity> candidates,float margin){
        double nearest=Double.MAX_VALUE;Entity selected=null;Vec3 point=null;
        for(Entity entity:candidates){
            Vec3 hit=hit(entity,from,to,margin);if(hit==null)continue;
            double distance=from.distanceToSqr(hit);
            if(distance<nearest){nearest=distance;selected=entity;point=hit;}
        }
        return selected==null?null:selected instanceof DnvCompoundCollider?new EntityHitResult(selected,point):new EntityHitResult(selected);
    }
    private static List<AABB> pieces(Entity entity){
        return entity instanceof DnvCompoundCollider collider?collider.dnv$collisionBoxes():List.of(entity.getBoundingBox());
    }
    private static boolean contains(Entity entity,Vec3 point,double margin){
        for(var piece:pieces(entity))if(piece.inflate(margin).contains(point))return true;
        return false;
    }
    private static Vec3 hit(Entity entity,Vec3 from,Vec3 to,double margin){
        double nearest=Double.MAX_VALUE;Vec3 result=null;
        for(var piece:pieces(entity)){
            var hit=piece.inflate(margin).clip(from,to);
            if(hit.isPresent()&&from.distanceToSqr(hit.get())<nearest){result=hit.get();nearest=from.distanceToSqr(result);}
        }
        return result;
    }
}
