package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

/** A disposable view of an impact. It never owns the timer, damage, terrain or persisted payload progress. */
public final class VacuumEffectEntity extends Entity {
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(VacuumEffectEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DEBRIS=SynchedEntityData.defineId(VacuumEffectEntity.class,EntityDataSerializers.COMPOUND_TAG);
    public static final int MAX_DEBRIS=96, DEBRIS_TICKS=32;
    public record Debris(BlockState state,BlockPos position,int age) { }
    private final List<Debris> fragments=new ArrayList<>();
    private int debrisSequence=-1;
    public VacuumEffectEntity(EntityType<? extends VacuumEffectEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(AGE,0);entityData.define(DEBRIS,new CompoundTag());}
    public int age(){return entityData.get(AGE);}
    public List<Debris> debris(){return List.copyOf(fragments);}
    public void projectAge(int age){entityData.set(AGE,age);}
    public void publishDebris(List<Debris> batch){
        if(level().isClientSide||batch.isEmpty())return;
        var tag=new CompoundTag();tag.putInt("Sequence",age());var list=new ListTag();
        for(int i=0;i<Math.min(8,batch.size());i++){
            var d=batch.get(i);var entry=new CompoundTag();entry.putLong("Position",d.position().asLong());
            entry.putInt("State",Block.getId(d.state()));entry.putInt("Age",d.age());list.add(entry);
        }
        tag.put("Blocks",list);entityData.set(DEBRIS,tag);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(!level().isClientSide||!key.equals(DEBRIS))return;
        var tag=entityData.get(DEBRIS);int sequence=tag.getInt("Sequence");
        if(sequence<=debrisSequence)return;debrisSequence=sequence;
        var list=tag.getList("Blocks",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(8,list.size());i++){
            var data=list.getCompound(i);var position=BlockPos.of(data.getLong("Position"));
            var state=Block.stateById(data.getInt("State"));int born=data.getInt("Age");
            if(state.isAir()||born<0||born>=VacuumPayloadProgress.PULL_TICKS
                    ||position.distToCenterSqr(position())>24*24)continue;
            if(fragments.size()>=MAX_DEBRIS)fragments.remove(0);
            fragments.add(new Debris(state,position,born));
        }
    }
    @Override public void tick(){
        super.tick();
        if(level() instanceof ServerLevel server){
            var impact=NuclearSiloStore.get(server).pendingImpact(getUUID());
            if(impact==null||impact.kind()!=SiloPayloadKind.DOGEGG){discard();return;}
            projectAge(impact.vacuum().age());
        }else fragments.removeIf(d->age()-d.age()>DEBRIS_TICKS||age()>=VacuumPayloadProgress.PULL_TICKS);
    }
    @Override public boolean isPickable(){return false;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){ /* The impact store restores this projection. */ }
    @Override protected void readAdditionalSaveData(CompoundTag tag){ }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
