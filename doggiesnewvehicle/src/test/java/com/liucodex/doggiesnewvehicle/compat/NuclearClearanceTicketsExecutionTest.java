package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class NuclearClearanceTicketsExecutionTest {
    @Test void productionTicketPortLoadsOneColumnAsynchronouslyRefreshesOnceAndReleasesOnMoveAndClose() throws Exception {
        var f=new Fixture();var a=new BlockPos(0,100,0);var b=new BlockPos(32,100,0);
        assertFalse(f.ready(a));assertEquals(1,f.body.world.source.tickets.size());assertEquals(1,f.body.world.source.adds);
        assertFalse(f.ready(a));assertEquals(1,f.body.world.source.adds);
        f.body.world.time++;assertFalse(f.ready(a));assertEquals(2,f.body.world.source.adds);
        f.body.world.source.loaded=true;assertTrue(f.ready(a));assertTrue(f.ready(b));
        assertEquals(Set.of(new ChunkPos(b)),f.body.world.source.tickets);assertEquals(1,f.body.world.source.removes);
        f.type.getMethod("release").invoke(f.body);f.type.getMethod("release").invoke(f.body);
        assertTrue(f.body.world.source.tickets.isEmpty());assertEquals(2,f.body.world.source.removes);
    }
    @Test void productionObstaclePortReadsOnlyFullChunkAndTranslatesRealShapeCoordinates() throws Exception {
        var f=new Fixture();var p=new BlockPos(-20,70,31);assertFalse(f.ready(p));
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->f.type.getMethod("obstacles",BlockPos.class).invoke(f.body,p));
        assertEquals(0,f.body.world.source.reads);f.body.world.source.loaded=true;assertTrue(f.ready(p));
        assertEquals(List.of(new AABB(-20,70,31,-19,70.5,32)),f.type.getMethod("obstacles",BlockPos.class).invoke(f.body,p));
        assertEquals(1,f.body.world.source.reads);
    }
    static class Fixture {
        final Class<?> type;final Body body;
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerChunkCache",Source.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/chunk/LevelChunk",Chunk.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/block/state/BlockState",Block.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/BlockGetter",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/phys/shapes/CollisionContext",Context.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/phys/shapes/VoxelShape",Shape.class);
            type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearClearanceWorld",Set.of("ready","obstacles","release"),names,Body.class);
            body=(Body)type.getConstructor().newInstance();
        }
        boolean ready(BlockPos p) throws Exception{return (boolean)type.getMethod("ready",BlockPos.class).invoke(body,p);}
    }
    public static class Body {
        public static final TicketType<UUID> TYPE=TicketType.create("dnv_clearance_test",Comparator.comparing(UUID::toString),40);
        public final World world=new World();public final UUID query=UUID.randomUUID();public ChunkPos held;public long refreshed=Long.MIN_VALUE;
    }
    public static class World {public final Source source=new Source();public long time=10;public Source getChunkSource(){return source;}public long getGameTime(){return time;}}
    public static class Source {
        public boolean loaded;public int adds,removes,reads;public final Set<ChunkPos> tickets=new HashSet<>();
        public <T> void addRegionTicket(TicketType<T> type,ChunkPos p,int distance,T key){assertEquals(0,distance);tickets.add(p);assertEquals(1,tickets.size());adds++;}
        public <T> void removeRegionTicket(TicketType<T> type,ChunkPos p,int distance,T key){assertTrue(tickets.remove(p));removes++;}
        public Chunk getChunkNow(int x,int z){return loaded?new Chunk(this):null;}
    }
    public static class Chunk {final Source source;Chunk(Source source){this.source=source;}public Block getBlockState(BlockPos p){source.reads++;return new Block();}}
    public static class Block {public Shape getCollisionShape(World world,BlockPos p,Context context){return new Shape();}}
    public static class Shape {public List<AABB> toAabbs(){return List.of(new AABB(0,0,0,1,.5,1));}}
    public interface Context {static Context empty(){return new Context(){};}}
}
