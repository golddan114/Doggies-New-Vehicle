package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Native gameplay adapter; explosion callbacks may only remove eligible entities/blocks. */
public final class NuclearPayloadWorld implements NuclearPayloadEngine.World {
    private final ServerLevel level;
    private final NuclearPayloadRules rules=NuclearPayloadRules.DEFAULT;
    private final NuclearRadiationHandler radiation;
    public NuclearPayloadWorld(ServerLevel level){this(level,NuclearRadiationHandler.dnv());}
    public NuclearPayloadWorld(ServerLevel level,NuclearRadiationHandler radiation){
        this.level=level;this.radiation=java.util.Objects.requireNonNull(radiation);
    }
    private Entity actor(NuclearSiloStore.Impact impact){return level.getPlayerByUUID(impact.actor());}
    private Vec3 center(NuclearSiloStore.Impact impact){return Vec3.atCenterOf(impact.position());}
    private Explosion explosion(NuclearSiloStore.Impact impact){
        var p=center(impact);return new Explosion(level,actor(impact),p.x,p.y,p.z,rules.blastRadius(),false,Explosion.BlockInteraction.DESTROY);
    }
    @Override public boolean begin(NuclearSiloStore.Impact impact){
        var explosion=explosion(impact);
        if(ForgeEventFactory.onExplosionStart(level,explosion))return false;
        Vec3 point=center(impact);
        // Same native operation and position distance as /kill @e[distance=..radius].
        // The whole-impact start cancellation still vetoes all effects.
        if(rules.centerKillRadius()>0)
            for(Entity entity:level.getEntities((Entity)null,new AABB(point,point).inflate(rules.centerKillRadius()),
                    e->e.position().distanceToSqr(point)<=rules.centerKillRadius()*rules.centerKillRadius()))entity.kill();
        var targets=protectedEntities(impact,rules.blastRadius(),e->e.isAlive()&&!e.isSpectator()&&(e instanceof LivingEntity||e instanceof VehicleEntity));
        var damage=NuclearDamage.blast(level,actor(impact),point);
        for(Entity target:targets){
            float max=target instanceof LivingEntity living?living.getMaxHealth():((VehicleEntity)target).getMaxHealth();
            if(Float.isFinite(max)&&max>0)target.hurt(damage,max*rules.blastFraction());
        }
        // Requery after blast: only surviving living targets receive the genuine harmful effects.
        for(Entity entity:protectedEntities(impact,rules.radiationRadius(),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator())){
            LivingEntity living=(LivingEntity)entity;
            radiation.apply(living,actor(impact),rules.radiationTicks());
        }
        // Spore scents reject hurt; exact registry AND class checks fail closed on unknown integrations.
        for(Entity scent:protectedEntities(impact,rules.scentRadius(),NuclearPayloadWorld::scent))scent.discard();
        return true;
    }
    @Override public void present(NuclearSiloStore.Impact impact){
        Vec3 point=center(impact);
        DnvProjectileBlastEffects.spawnExplosionParticles(actor(impact),ParticleTool.ParticleType.EPIC,level,point);
        NuclearMushroomCloud.spawn(level,point);
        level.playSound(null,point.x,point.y,point.z,
                com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds.NUCLEAR_EXPLOSION.get(),net.minecraft.sounds.SoundSource.BLOCKS,1F,1F);
    }
    @Override public void advanced(NuclearSiloStore.Impact impact,int before,int after){
        int radius=Math.max(impact.progress().terrainRadius(),impact.progress().fluidRadius());
        double oldFront=NuclearCraterShape.front(radius,before),front=NuclearCraterShape.front(radius,after);
        if((int)(front/2)>(int)(oldFront/2))NuclearMushroomCloud.advance(level,center(impact),front,radius);
    }
    private List<Entity> protectedEntities(NuclearSiloStore.Impact impact,int radius,java.util.function.Predicate<Entity> predicate){
        Vec3 point=center(impact);
        var entities=new ArrayList<>(level.getEntities((Entity)null,new AABB(point,point).inflate(radius),
                e->predicate.test(e)&&e.getBoundingBox().getCenter().distanceToSqr(point)<=radius*radius));
        var allowed=new HashSet<>(entities);
        ForgeEventFactory.onExplosionDetonate(level,explosion(impact),entities,radius);
        var seen=new HashSet<Entity>();
        entities.removeIf(e->!allowed.contains(e)||!seen.add(e)||!predicate.test(e)||e.getBoundingBox().getCenter().distanceToSqr(point)>radius*radius);
        return entities;
    }
    static boolean scent(Entity entity){
        return com.liucodex.doggiesnewvehicle.artillery.compat.DnvSporeScent.matches(entity);
    }
    @Override public boolean mayModify(NuclearSiloStore.Impact impact){
        Entity source=actor(impact);
        return com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,source);
    }
    @Override public boolean eligible(NuclearSiloStore.Impact impact,BlockPos pos,NuclearPayloadEngine.Phase phase){
        int radius=phase==NuclearPayloadEngine.Phase.FLUID?impact.progress().fluidRadius():impact.progress().terrainRadius();
        double distanceSquared=impact.progress().distanceSquared(pos.subtract(impact.position()),radius);
        if(distanceSquared>1||!level.hasChunkAt(pos)
                ||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var state=level.getBlockState(pos);
        if(phase==NuclearPayloadEngine.Phase.FIRE)return NuclearResidualFire.eligible(level,pos);
        if(phase==NuclearPayloadEngine.Phase.FLUID)return !state.getFluidState().isEmpty()
                &&(state.hasProperty(BlockStateProperties.WATERLOGGED)||state.getBlock() instanceof LiquidBlock
                ||state.getFluidState().createLegacyBlock().getBlock()==state.getBlock());
        if(state.isAir()||state.getDestroySpeed(level,pos)<0||state.getBlock() instanceof LiquidBlock)return false;
        return state.getBlock().getExplosionResistance()<radius*4*(1-Math.sqrt(distanceSquared));
    }
    @Override public void apply(NuclearSiloStore.Impact impact,List<BlockPos> positions,NuclearPayloadEngine.Phase phase){
        if(!mayModify(impact))return;
        var explosion=explosion(impact);var allowed=new HashSet<>(positions);explosion.getToBlow().addAll(positions);
        int radius=phase==NuclearPayloadEngine.Phase.FLUID?impact.progress().fluidRadius():impact.progress().terrainRadius();
        ForgeEventFactory.onExplosionDetonate(level,explosion,new ArrayList<>(),radius);
        if(!mayModify(impact))return;
        var seen=new HashSet<BlockPos>();
        explosion.getToBlow().removeIf(pos->!allowed.contains(pos)||!seen.add(pos)||!eligible(impact,pos,phase));
        if(phase==NuclearPayloadEngine.Phase.TERRAIN){
            explosion.finalizeExplosion(false); // Never explode(): entity damage already occurred once.
        }else if(phase==NuclearPayloadEngine.Phase.FLUID){
            for(BlockPos pos:explosion.getToBlow()){
                var state=level.getBlockState(pos);
                if(state.hasProperty(BlockStateProperties.WATERLOGGED))level.setBlock(pos,state.setValue(BlockStateProperties.WATERLOGGED,false),3);
                else level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            }
        }else{
            for(BlockPos pos:explosion.getToBlow())NuclearResidualFire.place(level,pos,actor(impact));
        }
    }
}
