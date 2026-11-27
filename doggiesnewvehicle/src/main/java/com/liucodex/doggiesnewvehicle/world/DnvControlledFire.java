package com.liucodex.doggiesnewvehicle.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ordinary fire contact/appearance; bounded local propagation inherits its parent's expiry. */
public class DnvControlledFire extends FireBlock {
    public static final int LIFE_TICKS=100;
    public static final IntegerProperty SECONDS_LEFT=IntegerProperty.create("seconds_left",1,LIFE_TICKS/20);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"doggiesnewvehicle");
    public static final RegistryObject<Block> FIRE=BLOCKS.register("combat_fire",DnvControlledFire::new);
    protected DnvControlledFire(){
        super(BlockBehaviour.Properties.copy(Blocks.FIRE));
        registerDefaultState(defaultBlockState().setValue(SECONDS_LEFT,LIFE_TICKS/20));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);builder.add(SECONDS_LEFT);
    }
    @Override public int getIgniteOdds(BlockState state){return ((FireBlock)Blocks.FIRE).getIgniteOdds(state);}
    @Override public int getBurnOdds(BlockState state){return ((FireBlock)Blocks.FIRE).getBurnOdds(state);}
    public static boolean eligible(ServerLevel level,BlockPos pos){
        return level.getBlockState(pos).isAir()
                &&level.hasChunkAt(pos.below())&&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    public static void place(ServerLevel level,BlockPos pos,Entity actor,Block fire){
        if(!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,actor)||!eligible(level,pos))return;
        var event=new ResiduePlacement(BlockSnapshot.create(level.dimension(),level,pos),level.getBlockState(pos.below()),actor,fire.defaultBlockState());
        MinecraftForge.EVENT_BUS.post(event);
        if(!event.isCanceled()&&com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,actor)
                &&eligible(level,pos))level.setBlock(pos,fire.defaultBlockState(),3);
    }
    /** Preflight reports the proposed block, including when the saved operator is offline. The snapshot still holds original air. */
    private static final class ResiduePlacement extends BlockEvent.EntityPlaceEvent {
        private final BlockState proposed;
        private ResiduePlacement(BlockSnapshot snapshot,BlockState against,Entity actor,BlockState proposed){
            super(snapshot,against,actor);this.proposed=proposed;
        }
        @Override public BlockState getPlacedBlock(){return proposed;}
        @Override public BlockState getState(){return getPlacedBlock();}
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        if(!level.isClientSide&&!old.is(this))level.scheduleTick(pos,this,20);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        state=level.getBlockState(pos);
        if(!state.is(this))return;
        int remaining=state.getValue(SECONDS_LEFT);
        if(remaining<=1||level.isRainingAt(pos)){
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);return;
        }
        remaining--;
        level.setBlock(pos,state.setValue(SECONDS_LEFT,remaining),3);
        level.scheduleTick(pos,this,20);
        if(!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                ||!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,null))return;
        for(Direction direction:Direction.values()){
            BlockPos neighbor=pos.relative(direction);
            if(!safe(level,neighbor)||level.isRainingAt(neighbor))continue;
            BlockState before=level.getBlockState(neighbor);
            if(before.is(this)||before.is(Blocks.FIRE)||!before.getFluidState().isEmpty())continue;
            int chance;
            boolean burn=!before.isAir();
            if(burn)chance=before.getFlammability(level,neighbor,direction.getOpposite());
            else{
                chance=0;
                for(Direction face:Direction.values()){
                    BlockPos fuel=neighbor.relative(face);
                    if(safe(level,fuel))chance=Math.max(chance,level.getBlockState(fuel).getFireSpreadSpeed(level,fuel,face.getOpposite()));
                }
            }
            if(chance<=0||random.nextInt(burn?300:100)>=chance)continue;
            BlockState child=getStateForPlacement(level,neighbor).setValue(SECONDS_LEFT,remaining);
            // One preflight precedes BOTH fuel combustion and child placement.
            var event=new SpreadPlacement(BlockSnapshot.create(level.dimension(),level,neighbor),
                    level.getBlockState(neighbor.below()),child);
            MinecraftForge.EVENT_BUS.post(event);
            if(event.isCanceled()||!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,null)
                    ||level.getBlockState(neighbor)!=before)continue;
            if(burn)before.onCaughtFire(level,neighbor,direction.getOpposite(),null);
            // Native callbacks (e.g. TNT) own their changes; never replace their result.
            if(level.getBlockState(neighbor)==before)level.setBlock(neighbor,child,3);
        }
    }
    private static boolean safe(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        for(Direction face:Direction.values())if(!level.hasChunkAt(pos.relative(face)))return false;
        return true;
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,
            LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        // Vanilla's updateShape returns minecraft:fire and loses our countdown.
        return state.canSurvive(level,pos)?getStateForPlacement(level,pos)
                .setValue(AGE,state.getValue(AGE)).setValue(SECONDS_LEFT,state.getValue(SECONDS_LEFT))
                :Blocks.AIR.defaultBlockState();
    }
    private static final class SpreadPlacement extends BlockEvent.EntityPlaceEvent {
        private final BlockState proposed;
        private SpreadPlacement(BlockSnapshot snapshot,BlockState against,BlockState proposed){
            super(snapshot,against,null);this.proposed=proposed;
        }
        @Override public BlockState getPlacedBlock(){return proposed;}
        @Override public BlockState getState(){return proposed;}
    }
}
