package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import com.atsuishio.superbwarfare.init.ModSounds;
import com.atsuishio.superbwarfare.init.ModTags;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only nuclear native containers enter this workflow. The box retains inventory until entity spawn commits. */
@Mod.EventBusSubscriber(modid=DoggiesNewVehicle.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NuclearContainerDeployment {
    private NuclearContainerDeployment(){ }
    public static boolean isSiloType(net.minecraft.world.entity.EntityType<?> type){
        return type==DnvArtilleryEntities.NUCLEAR_SILO.get()||type==DnvArtilleryEntities.DOGEGG_SILO.get();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void use(PlayerInteractEvent.RightClickBlock event){
        if(event.isCanceled()||!(event.getLevel().getBlockEntity(event.getPos()) instanceof ContainerBlockEntity box)
                ||!isSiloType(box.entityType))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if(!(event.getEntity() instanceof ServerPlayer player)||!(event.getLevel() instanceof ServerLevel level))return;
        if(event.getItemStack().getItem() instanceof NuclearDeploymentRemoteItem){
            NuclearDeploymentRemoteItem.bind(level,event.getPos(),box,player,event.getItemStack());return;
        }
        if(!player.getMainHandItem().is(ModTags.Items.TOOLS_CROWBAR)){
            message(player,"crowbar",event.getPos());return;
        }
        begin(level,event.getPos(),box,player);
    }
    static void begin(ServerLevel level,BlockPos pos,ContainerBlockEntity box,ServerPlayer player){
        if(box.entityTag!=null&&box.entityTag.getCompound("NuclearSilo").getBoolean("Unfolding")){
            message(player,"working",pos);return;
        }
        var store=NuclearSiloStore.get(level);
        recoverLease(level,store);
        if(!store.canPlace()){message(player,!store.writable()?"stopped":store.facility()==null?"payload_busy":"occupied",pos);return;}
        var low=NuclearDeploymentGeometry.low(pos);var high=NuclearDeploymentGeometry.high(pos);
        if(!level.hasChunksAt(low,high)||level.isOutsideBuildHeight(low)||level.isOutsideBuildHeight(high)
                ||!level.getWorldBorder().isWithinBounds(NuclearDeploymentGeometry.clearance(new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5)))){
            message(player,"bounds",pos);return;
        }
        // The operator may stand on the surrounding ground, but nobody can remain above the excavated shaft.
        if(rejectObstruction(level,pos,player))return;
        NuclearSiloEntity candidate=candidate(level,pos,box,player);if(candidate==null){message(player,"owner",pos);return;}
        Map<BlockPos,BlockState> checked=new LinkedHashMap<>();
        for(int cursor=0;cursor<NuclearExcavation.TOTAL;cursor++){
            BlockPos p=NuclearExcavation.position(pos,cursor);BlockState state=level.getBlockState(p);
            String problem=problem(level,p,state,pos,player);
            if(problem!=null){message(player,problem,p);return;}
            checked.put(p,state);
        }
        // Protection callbacks can mutate inputs: recheck the entire snapshot before acquiring the lease or digging.
        if(level.getBlockEntity(pos)!=box||checked.entrySet().stream().anyMatch(e->!level.getBlockState(e.getKey()).equals(e.getValue()))){
            message(player,"changed",pos);return;
        }
        if(!store.beginUnfold(candidate.getUUID(),pos)){message(player,"occupied",pos);return;}
        startJob(level,pos,box,player,candidate,store);
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.unfold_started",pos.getX(),pos.getY(),pos.getZ()),true);
    }
    private static void startJob(ServerLevel level,BlockPos pos,ContainerBlockEntity box,ServerPlayer player,NuclearSiloEntity candidate,NuclearSiloStore store){
        CompoundTag original=box.entityTag;int originalTick=box.getTick();boolean originalOpened=box.getOpened(),started=false;
        try{
            box.entityTag=candidate.serializeNBT();CompoundTag job=box.entityTag.getCompound("NuclearSilo");
            job.putBoolean("Unfolding",true);job.putUUID("UnfoldActor",player.getUUID());job.putInt("UnfoldCursor",0);
            box.setTick(0);box.setOpened(false);box.setChanged();
            SiloChunkTickets.update(level,candidate.getUUID(),java.util.Set.of(new net.minecraft.world.level.ChunkPos(pos).toLong()));
            started=true;
        }finally{
            if(!started){try{SiloChunkTickets.release(level,candidate.getUUID());}finally{
                store.releaseFacility(candidate.getUUID());box.entityTag=original;
                box.setTick(originalTick);box.setOpened(originalOpened);box.setChanged();}}
        }
    }
    private static NuclearSiloEntity candidate(ServerLevel level,BlockPos pos,ContainerBlockEntity box,ServerPlayer player){
        var entity=(box.entityType==DnvArtilleryEntities.DOGEGG_SILO.get()?DnvArtilleryEntities.DOGEGG_SILO.get():
                DnvArtilleryEntities.NUCLEAR_SILO.get()).create(level);if(entity==null)return null;
        CompoundTag saved=box.entityTag==null?new CompoundTag():box.entityTag.copy();
        CompoundTag mechanical=SiloCycleCodec.write(new SiloCycle(NuclearRules.DEFAULT).snapshot());
        mechanical.putLong("Anchor",pos.asLong());saved.put("NuclearSilo",mechanical);
        if(box.entityTag!=null)entity.load(saved);
        if(!entity.prepareContainerOwner(player.getUUID(),pos))return null;
        float yaw=level.getBlockState(pos).getValue(ContainerBlock.FACING).toYRot();
        entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,yaw,0);entity.setServerYaw(yaw);
        return entity;
    }
    private static String problem(ServerLevel level,BlockPos p,BlockState state,BlockPos centre,ServerPlayer player){
        if(!player.getAbilities().mayBuild||player.isSpectator()||!level.mayInteract(player,p)
                ||!player.mayUseItemAt(p,Direction.UP,player.getMainHandItem()))return "protected";
        if(p.equals(centre))return permittedBreak(level,p,state,player)?null:"protected";
        if(!NuclearExcavation.clears(centre,p)||state.isAir())return null;
        if(!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.terrainAllowed(level,player))return "protected";
        if(state.getDestroySpeed(level,p)<0)return "unbreakable";
        if(level.getBlockEntity(p)!=null||state.hasBlockEntity())return "storage";
        return permittedBreak(level,p,state,player)?null:"protected";
    }
    private static boolean permittedBreak(ServerLevel level,BlockPos p,BlockState state,ServerPlayer player){
        var event=new BlockEvent.BreakEvent(level,p,state,player);MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }
    /** Called at the verified native container tick boundary, including boxes whose opening has not started. */
    public static boolean tick(Level world,BlockPos pos,BlockState state,ContainerBlockEntity box){
        if(!isSiloType(box.entityType))return false;
        if(!(world instanceof ServerLevel level)||box.entityTag==null)return true;
        CompoundTag job=box.entityTag.getCompound("NuclearSilo");if(!job.getBoolean("Unfolding"))return true;
        var store=NuclearSiloStore.get(level);
        if(!store.writable()||!store.unfolding()||!box.entityTag.hasUUID("UUID")
                ||!box.entityTag.getUUID("UUID").equals(store.facility())||!pos.equals(store.anchor())){
            if(store.writable())abort(level,pos,box,null,"stopped");return true;
        }
        ServerPlayer player=job.hasUUID("UnfoldActor")?level.getServer().getPlayerList().getPlayer(job.getUUID("UnfoldActor")):null;
        int cursor=job.getInt("UnfoldCursor");
        if(player==null||player.level()!=level||!player.isAlive()||cursor<0||cursor>NuclearExcavation.TOTAL){abort(level,pos,box,player,"stopped");return true;}
        if(cursor<NuclearExcavation.TOTAL){
            for(int visits=0;visits<NuclearExcavation.PER_TICK&&cursor<NuclearExcavation.TOTAL;visits++){
                BlockPos p=NuclearExcavation.position(pos,cursor);BlockState block=level.getBlockState(p);
                String failure=problem(level,p,block,pos,player);
                if(failure!=null){abort(level,pos,box,player,failure);message(player,failure,p);return true;}
                // Consume before invoking world callbacks. Restart cannot repeat a consumed operation.
                job.putInt("UnfoldCursor",++cursor);box.setChanged();
                if(NuclearExcavation.clears(pos,p)&&!block.isAir()&&!level.destroyBlock(p,true,player)){
                    abort(level,pos,box,player,"changed");return true;
                }
                if(level.getBlockEntity(pos)!=box)return true;
            }
            if(cursor<NuclearExcavation.TOTAL)return true;
            level.setBlock(pos,state.setValue(ContainerBlock.OPENED,true),3);
            level.playSound(null,pos,ModSounds.OPEN.get(),SoundSource.BLOCKS,1,1);
            return true;
        }
        if(box.getTick()<20){box.setTick(box.getTick()+1);box.setChanged();return true;}
        if(rejectObstruction(level,pos,player)){
            abort(level,pos,box,player,"entity");return true;
        }
        String refusal=problem(level,pos,level.getBlockState(pos),pos,player);
        if(refusal!=null){abort(level,pos,box,player,refusal);return true;}
        NuclearSiloEntity entity=candidate(level,pos,box,player);boolean committed=false;
        try{
            if(entity==null||!level.addFreshEntity(entity)||entity.isRemoved()||level.getBlockEntity(pos)!=box)return true;
            if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),3))return true;
            committed=true;
        }finally{
            if(!committed){if(entity!=null)entity.discard();abort(level,pos,box,player,"spawn");}
        }
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.unfold_complete"),true);
        return true;
    }
    private static boolean obstructs(net.minecraft.world.entity.Entity entity){
        return !entity.isSpectator()&&entity.isAlive()&&!(entity instanceof net.minecraft.world.entity.item.ItemEntity);
    }
    private static boolean rejectObstruction(ServerLevel level,BlockPos pos,ServerPlayer player){
        var blocked=level.getEntities((net.minecraft.world.entity.Entity)null,NuclearExcavation.shaft(pos),NuclearContainerDeployment::obstructs);
        if(blocked.isEmpty())return false;
        var entity=blocked.get(0);var at=entity.blockPosition();
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.unfold_entity_detail",
                entity.getDisplayName(),at.getX(),at.getY(),at.getZ()),false);
        return true;
    }
    private static void abort(ServerLevel level,BlockPos pos,ContainerBlockEntity box,ServerPlayer player,String reason){
        var store=NuclearSiloStore.get(level);var id=store.facility();
        if(store.unfolding()&&pos.equals(store.anchor())&&id!=null){SiloChunkTickets.release(level,id);store.releaseFacility(id);}
        if(box.entityTag!=null){var job=box.entityTag.getCompound("NuclearSilo");job.remove("Unfolding");job.remove("UnfoldCursor");job.remove("UnfoldActor");}
        box.setTick(0);box.setOpened(false);box.setChanged();
        if(level.getBlockEntity(pos)==box)level.setBlock(pos,level.getBlockState(pos).setValue(ContainerBlock.OPENED,false),3);
        if(player!=null)message(player,reason,pos);
    }
    public static void recoverLease(ServerLevel level,NuclearSiloStore store){
        if(!store.writable()||store.facility()==null||store.anchor()==null||!level.hasChunkAt(store.anchor()))return;
        if(store.unfolding()){
        if(level.getBlockEntity(store.anchor()) instanceof ContainerBlockEntity box&&isSiloType(box.entityType)
                &&box.entityTag!=null&&box.entityTag.getCompound("NuclearSilo").getBoolean("Unfolding")
                &&box.entityTag.hasUUID("UUID")&&box.entityTag.getUUID("UUID").equals(store.facility()))return;
        }else{
            // A loaded block chunk alone does not prove its asynchronously loaded entities are present.
            if(!level.areEntitiesLoaded(new ChunkPos(store.anchor()).toLong())||level.getEntity(store.facility())!=null)return;
        }
        var id=store.facility();SiloChunkTickets.release(level,id);store.releaseFacility(id);
    }
    private static void message(ServerPlayer player,String kind,BlockPos pos){
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.unfold_"+kind,pos.getX(),pos.getY(),pos.getZ()),true);
    }
}
