package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.level.BlockEvent;

/** Native authority for bounded pull, protected terrain removal and the one terminal burst. */
public final class VacuumPayloadWorld implements VacuumPayloadEngine.World {
    private final ServerLevel level;
    public VacuumPayloadWorld(ServerLevel level){this.level=level;}
    public static Vec3 center(NuclearSiloStore.Impact impact){return Vec3.atCenterOf(impact.position()).add(0,2,0);}
    private Player actor(NuclearSiloStore.Impact impact){
        var online=level.getServer().getPlayerList().getPlayer(impact.actor());
        if(online!=null)return online;
        var cache=level.getServer().getProfileCache();
        return cache==null?null:cache.get(impact.actor()).map(profile->FakePlayerFactory.get(level,profile)).orElse(null);
    }
    private Explosion explosion(NuclearSiloStore.Impact impact){
        var p=center(impact);
        return new Explosion(level,actor(impact),p.x,p.y,p.z,VacuumPayloadProgress.BLAST_RADIUS,false,Explosion.BlockInteraction.DESTROY);
    }
    @Override public boolean available(NuclearSiloStore.Impact impact){return level.hasChunkAt(impact.position());}
    @Override public boolean ensureVisual(NuclearSiloStore.Impact impact){
        Entity current=level.getEntity(impact.id());
        if(current instanceof VacuumEffectEntity effect&&!effect.isRemoved()){
            effect.projectAge(impact.vacuum().age());return true;
        }
        if(current!=null&&!current.isRemoved())return false;
        var effect=DnvArtilleryEntities.VACUUM_EFFECT.get().create(level);if(effect==null)return false;
        effect.setUUID(impact.id());effect.setPos(center(impact));effect.projectAge(impact.vacuum().age());
        return level.addFreshEntity(effect);
    }
    @Override public boolean begin(NuclearSiloStore.Impact impact){
        if(ForgeEventFactory.onExplosionStart(level,explosion(impact)))return false;
        var p=center(impact);
        level.playSound(null,p.x,p.y,p.z,com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.DOGEGG_IMPACT.get(),
                net.minecraft.sounds.SoundSource.BLOCKS,1F,1F);
        return true;
    }
    private boolean enemy(NuclearSiloStore.Impact impact,Entity entity){return target(impact,entity,false);}
    private boolean pullable(NuclearSiloStore.Impact impact,Entity entity){return target(impact,entity,true);}
    private boolean target(NuclearSiloStore.Impact impact,Entity entity,boolean pulling){
        Player owner=actor(impact);
        if(owner==null||!entity.isAlive()||entity.isSpectator()||entity.isRemoved()
                ||entity.getUUID().equals(impact.actor())||entity.isPassenger()
                ||!(entity instanceof LivingEntity||entity instanceof VehicleEntity)
                ||DnvCombatPolicyRuntime.isProtectedPlayerTarget(entity))return false;
        if(entity instanceof com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity vehicle
                &&vehicle.getOptionalOwnerUUID().filter(impact.actor()::equals).isPresent())return false;
        boolean friendly=entity.isAlliedTo(owner)||owner.isAlliedTo(entity)||SeekTool.IS_FRIENDLY.test(owner,entity)
                ||entity.getSelfAndPassengers().anyMatch(p->p.getUUID().equals(impact.actor())||owner.isAlliedTo(p)||p.isAlliedTo(owner));
        String sourceTeam=owner.getTeam()==null?null:owner.getTeam().getName();
        String targetTeam=entity.getTeam()==null?null:entity.getTeam().getName();
        return pulling?VacuumTargetRules.pullable(sourceTeam,targetTeam,friendly,entity instanceof Enemy,
                entity instanceof VehicleEntity&&!entity.isVehicle())
                :VacuumTargetRules.hostile(sourceTeam,targetTeam,friendly,entity instanceof Enemy);
    }
    @Override public List<java.util.UUID> pull(NuclearSiloStore.Impact impact,int budget){
        var p=center(impact);var pulled=new ArrayList<java.util.UUID>();
        var captured=new HashSet<>(impact.vacuum().captured());
        var candidates=level.getEntities((Entity)null,new AABB(p,p).inflate(VacuumPayloadProgress.PULL_RADIUS),
                e->e.getBoundingBox().getCenter().distanceToSqr(p)<=VacuumPayloadProgress.PULL_RADIUS*VacuumPayloadProgress.PULL_RADIUS&&pullable(impact,e));
        for(Entity entity:candidates){
            if(pulled.size()>=budget)break;
            if(!captured.contains(entity.getUUID())&&captured.size()>=VacuumPayloadProgress.MAX_CAPTURED)continue;
            Vec3 delta=p.subtract(entity.getBoundingBox().getCenter()),old=entity.getDeltaMovement();
            var next=VacuumPull.velocity(delta.x,delta.y,delta.z,old.x,old.y,old.z,impact.vacuum().age());
            if(entity instanceof VehicleEntity vehicle)vehicle.pushNew(next.x()-old.x,next.y()-old.y,next.z()-old.z);
            else entity.setDeltaMovement(next.x(),next.y(),next.z());
            entity.hurtMarked=true;entity.fallDistance=0;
            captured.add(entity.getUUID());pulled.add(entity.getUUID());
        }
        return List.copyOf(pulled);
    }
    private boolean mayModify(NuclearSiloStore.Impact impact){
        Player source=actor(impact);
        return source!=null&&com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,source);
    }
    @Override public boolean eligible(NuclearSiloStore.Impact impact,BlockPos position){
        if(!mayModify(impact)||!level.hasChunkAt(position)||level.isOutsideBuildHeight(position)
                ||!level.getWorldBorder().isWithinBounds(position)
                ||!NuclearSphereScan.contains(position.subtract(impact.position()),VacuumPayloadProgress.TERRAIN_RADIUS))return false;
        var state=level.getBlockState(position);
        if(state.isAir()||!state.getFluidState().isEmpty()||state.getBlock() instanceof LiquidBlock||state.getDestroySpeed(level,position)<0
                ||state.hasBlockEntity()||level.getBlockEntity(position)!=null||state.getBlock().getExplosionResistance()>=1200)return false;
        return true;
    }
    @Override public void peel(NuclearSiloStore.Impact impact,List<BlockPos> positions){
        if(!mayModify(impact))return;
        var blast=explosion(impact);blast.getToBlow().addAll(positions);
        ForgeEventFactory.onExplosionDetonate(level,blast,new ArrayList<>(),VacuumPayloadProgress.TERRAIN_RADIUS);
        if(!mayModify(impact))return;
        var allowed=new HashSet<>(positions);var seen=new HashSet<BlockPos>();
        var fragments=new ArrayList<VacuumEffectEntity.Debris>();var source=actor(impact);
        for(var pos:blast.getToBlow()){
            if(!allowed.contains(pos)||!seen.add(pos)||!eligible(impact,pos)||source==null||!level.mayInteract(source,pos))continue;
            var state=level.getBlockState(pos);var event=new BlockEvent.BreakEvent(level,pos,state,source);
            if(MinecraftForge.EVENT_BUS.post(event)||!eligible(impact,pos)||!level.getBlockState(pos).equals(state))continue;
            // No drops or block-entity copies. Only a successful native removal generates a visual fragment.
            if(level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)&&fragments.size()<8)
                fragments.add(new VacuumEffectEntity.Debris(state,pos.immutable(),impact.vacuum().age()));
        }
        if(level.getEntity(impact.id()) instanceof VacuumEffectEntity effect)effect.publishDebris(fragments);
    }
    @Override public boolean blastEligible(NuclearSiloStore.Impact impact,BlockPos position){
        if(!mayModify(impact)||!level.hasChunkAt(position)||level.isOutsideBuildHeight(position)
                ||!level.getWorldBorder().isWithinBounds(position)
                ||Vec3.atCenterOf(position).distanceToSqr(center(impact))>VacuumPayloadProgress.BLAST_RADIUS*VacuumPayloadProgress.BLAST_RADIUS)return false;
        var state=level.getBlockState(position);
        return !state.isAir()&&state.getFluidState().isEmpty()&&state.getDestroySpeed(level,position)>=0
                &&!state.hasBlockEntity()&&level.getBlockEntity(position)==null&&state.getBlock().getExplosionResistance()<1200;
    }
    @Override public void blastTerrain(NuclearSiloStore.Impact impact,List<BlockPos> positions){
        if(!mayModify(impact))return;
        var blast=explosion(impact);blast.getToBlow().addAll(positions);
        ForgeEventFactory.onExplosionDetonate(level,blast,new ArrayList<>(),VacuumPayloadProgress.BLAST_RADIUS);
        if(!mayModify(impact))return;
        var allowed=new HashSet<>(positions);var seen=new HashSet<BlockPos>();var source=actor(impact);
        for(BlockPos pos:blast.getToBlow()){
            if(!allowed.contains(pos)||!seen.add(pos)||!blastEligible(impact,pos)||source==null||!level.mayInteract(source,pos))continue;
            var state=level.getBlockState(pos);var event=new BlockEvent.BreakEvent(level,pos,state,source);
            MinecraftForge.EVENT_BUS.post(event);
            if(!event.isCanceled()&&blastEligible(impact,pos)&&level.getBlockState(pos).equals(state))
                state.onBlockExploded(level,pos,blast);
        }
    }
    @Override public boolean burst(NuclearSiloStore.Impact impact){
        var blast=explosion(impact);if(ForgeEventFactory.onExplosionStart(level,blast))return false;
        var p=center(impact);int radius=VacuumPayloadProgress.BLAST_RADIUS;
        var targets=new ArrayList<>(level.getEntities((Entity)null,new AABB(p,p).inflate(radius),
                e->e.getBoundingBox().getCenter().distanceToSqr(p)<=radius*radius&&enemy(impact,e)));
        var captured=new HashSet<>(impact.vacuum().captured());
        for(var id:captured){var target=level.getEntity(id);if(target!=null&&enemy(impact,target)&&!targets.contains(target))targets.add(target);}
        var allowed=new HashSet<>(targets);var seen=new HashSet<Entity>();
        ForgeEventFactory.onExplosionDetonate(level,blast,targets,radius);
        var damage=VacuumDamage.blast(level,actor(impact),p);
        for(Entity target:targets)if(allowed.contains(target)&&seen.add(target)&&target.level()==level&&enemy(impact,target)){
            if(captured.contains(target.getUUID()))converge(impact,target);
            if(target.level()!=level||!enemy(impact,target)||target.getBoundingBox().getCenter().distanceToSqr(p)>radius*radius)continue;
            if(target.getBoundingBox().getCenter().distanceToSqr(p)<=VacuumPayloadProgress.EXECUTE_RADIUS*VacuumPayloadProgress.EXECUTE_RADIUS)target.kill();
            else target.hurt(damage,VacuumPayloadProgress.BLAST_DAMAGE);
        }
        com.atsuishio.superbwarfare.tools.ParticleTool.spawnHugeExplosionParticles(level,p);
        // The native HUGE preset supplies its matching explosion audio and client particles once.
        if(level.getEntity(impact.id()) instanceof VacuumEffectEntity effect)effect.projectAge(impact.vacuum().age());
        return true;
    }

    private void converge(NuclearSiloStore.Impact impact,Entity target){
        if(target.level()!=level||!enemy(impact,target)||target.isVehicle())return;
        var p=center(impact);
        var destination=target.position().add(p.subtract(target.getBoundingBox().getCenter()));
        var event=new EntityTeleportEvent(target,destination.x,destination.y,destination.z);
        MinecraftForge.EVENT_BUS.post(event);
        var next=event.getTarget();
        if(event.isCanceled()||target.level()!=level||!enemy(impact,target)||target.isVehicle()
                ||!Double.isFinite(next.x)||!Double.isFinite(next.y)||!Double.isFinite(next.z))return;
        var box=target.getBoundingBox().move(next.subtract(target.position()));
        var minimum=BlockPos.containing(box.minX,box.minY,box.minZ);
        var maximum=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        if(box.getCenter().distanceToSqr(p)>VacuumPayloadProgress.BLAST_RADIUS*VacuumPayloadProgress.BLAST_RADIUS
                ||level.isOutsideBuildHeight(minimum)||level.isOutsideBuildHeight(maximum)
                ||!level.getWorldBorder().isWithinBounds(box)||!level.hasChunksAt(minimum,maximum)||!level.noCollision(target,box))return;
        target.teleportTo(next.x,next.y,next.z);
        if(target.level()!=level||target.position().distanceToSqr(next)>1e-6)return;
        target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;target.hurtMarked=true;
    }
}
