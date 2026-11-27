package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Non-persistent inspection ticket, separate from the store-owned facility/flight tickets. */
final class NuclearClearanceWorld implements NuclearClearance.World {
    private static final TicketType<UUID> TYPE=TicketType.create("dnv_nuclear_clearance",Comparator.comparing(UUID::toString),40);
    private final ServerLevel world;
    private final UUID query=UUID.randomUUID();
    private ChunkPos held;
    private long refreshed=Long.MIN_VALUE;
    NuclearClearanceWorld(ServerLevel world){this.world=world;}
    @Override public int minY(){return world.getMinBuildHeight();}
    @Override public int maxY(){return world.getMaxBuildHeight();}
    @Override public boolean ready(BlockPos position){
        var next=new ChunkPos(position);
        if(!next.equals(held)){release();held=next;}
        if(refreshed!=world.getGameTime()){
            world.getChunkSource().addRegionTicket(TYPE,held,0,query);refreshed=world.getGameTime();
        }
        return world.getChunkSource().getChunkNow(held.x,held.z)!=null;
    }
    @Override public List<AABB> obstacles(BlockPos position){
        // ready() has established a FULL chunk; no getChunk/create path is used here.
        var chunk=world.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);
        if(chunk==null)throw new IllegalStateException("Clearance chunk disappeared on server thread");
        return chunk.getBlockState(position).getCollisionShape(world,position,CollisionContext.empty())
                .toAabbs().stream().map(box->box.move(position)).toList();
    }
    @Override public NuclearClearance.BlockKind kind(BlockPos position){
        var state=block(position);
        if(state.getDestroySpeed(world,position)<0)return NuclearClearance.BlockKind.UNBREAKABLE;
        return state.is(BlockTags.LEAVES)?NuclearClearance.BlockKind.FOLIAGE:NuclearClearance.BlockKind.NORMAL;
    }
    @Override public NuclearClearance.Description describe(BlockPos position){
        var block=block(position).getBlock();
        return new NuclearClearance.Description(block.getDescriptionId(),BuiltInRegistries.BLOCK.getKey(block).toString());
    }
    private net.minecraft.world.level.block.state.BlockState block(BlockPos position){
        var chunk=world.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);
        if(chunk==null)throw new IllegalStateException("Clearance chunk disappeared on server thread");
        return chunk.getBlockState(position);
    }
    @Override public void release(){
        if(held!=null){world.getChunkSource().removeRegionTicket(TYPE,held,0,query);held=null;refreshed=Long.MIN_VALUE;}
    }
}
