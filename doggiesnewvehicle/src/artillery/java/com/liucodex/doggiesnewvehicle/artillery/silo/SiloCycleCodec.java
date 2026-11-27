package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Entity NBT and read-only synchronized snapshots use the same validated mechanical record. */
public final class SiloCycleCodec {
    private SiloCycleCodec(){ }
    public static CompoundTag write(SiloCycle.Snapshot state){
        CompoundTag tag=new CompoundTag();tag.putInt("Schema",6);tag.putString("Phase",state.phase().name());tag.putInt("Remaining",state.remaining());
        if(state.launch()!=null)tag.putUUID("Launch",state.launch());
        if(state.target()!=null)tag.putLong("Target",new BlockPos(state.target().x(),state.target().y(),state.target().z()).asLong());
        if(state.phase()==SiloCycle.Phase.RETURNING)tag.putBoolean("PreparationRetained",state.preparationRetained());
        return tag;
    }
    public static SiloCycle.Snapshot read(CompoundTag tag){
        if(!tag.contains("Schema",Tag.TAG_INT)||(tag.getInt("Schema")<1||tag.getInt("Schema")>6)||!tag.contains("Phase",Tag.TAG_STRING)||!tag.contains("Remaining",Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid cycle schema");
        var phase=SiloCycle.Phase.valueOf(tag.getString("Phase"));SiloCycle.Target target=null;
        if(phase==SiloCycle.Phase.RETURNING&&tag.getInt("Schema")<6)throw new IllegalArgumentException("Returning phase requires schema 6");
        if(phase==SiloCycle.Phase.RETURNING&&(!tag.contains("PreparationRetained",Tag.TAG_BYTE)
                ||(tag.getByte("PreparationRetained")!=0&&tag.getByte("PreparationRetained")!=1)))
            throw new IllegalArgumentException("Invalid retained preparation");
        if(phase!=SiloCycle.Phase.RETURNING&&tag.contains("PreparationRetained"))throw new IllegalArgumentException("Unexpected retained preparation");
        if(tag.contains("Launch")&&!tag.hasUUID("Launch"))throw new IllegalArgumentException("Launch UUID type");
        if(tag.contains("Target")){
            if(!tag.contains("Target",Tag.TAG_LONG))throw new IllegalArgumentException("Target type");
            BlockPos p=BlockPos.of(tag.getLong("Target"));target=new SiloCycle.Target(p.getX(),p.getY(),p.getZ());
        }
        int remaining=tag.getInt("Remaining");
        if(tag.getInt("Schema")<5&&(phase==SiloCycle.Phase.OPENING||phase==SiloCycle.Phase.CLOSING||phase==SiloCycle.Phase.DEPARTING)){
            int oldDuration=tag.getInt("Schema")==4?(phase==SiloCycle.Phase.OPENING?148:phase==SiloCycle.Phase.DEPARTING?321:374)
                    :tag.getInt("Schema")==3?(phase==SiloCycle.Phase.DEPARTING?100:160):phase==SiloCycle.Phase.DEPARTING?54:tag.getInt("Schema")==1
                    ?(phase==SiloCycle.Phase.OPENING?190:40):(phase==SiloCycle.Phase.OPENING?54:80);
            if(remaining<0||remaining>oldDuration)throw new IllegalArgumentException("Invalid legacy duration");
            int duration=switch(phase){
                case OPENING->NuclearRules.DEFAULT.openTicks();
                case CLOSING->NuclearRules.DEFAULT.closeTicks();
                default->NuclearRules.DEFAULT.departureTicks();
            };
            remaining=(int)Math.ceil(remaining*(double)duration/oldDuration);
        }
        var state=new SiloCycle.Snapshot(phase,remaining,tag.hasUUID("Launch")?tag.getUUID("Launch"):null,target,
                phase==SiloCycle.Phase.RETURNING&&tag.getBoolean("PreparationRetained"));
        SiloCycle validation=new SiloCycle(NuclearRules.DEFAULT);validation.restore(state);return state;
    }
}
