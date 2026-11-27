package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** The actual block ray adapter executes against a bounded chunk/block port, including all clip calls. */
public class MothershipRayExecutionTest {
    public static class Actor {public final World world=new World();public World level(){return world;}}
    public enum Block {COLLIDER} public enum Fluid {NONE}
    public record Context(Vec3 from,Vec3 to,Block block,Fluid fluid,Actor actor) { }
    public static class Border {public boolean isWithinBounds(BlockPos pos){return Math.abs(pos.getX())<10000&&Math.abs(pos.getZ())<10000;}}
    public static class World {
        public final List<AABB> walls=new ArrayList<>();public final Set<Integer> absent=new HashSet<>();public int clips;
        public final Set<String> absentPairs=new HashSet<>();
        public int getMinBuildHeight(){return -64;}public int getMaxBuildHeight(){return 320;}
        public Border getWorldBorder(){return new Border();}public boolean hasChunk(int x,int z){return !absent.contains(x)&&!absentPairs.contains(x+","+z);}
        public BlockHitResult clip(Context ray) {
            clips++;Vec3 end=ray.to;double distance=ray.from.distanceToSqr(end);boolean hit=false;
            for(AABB wall:walls) {
                var intersection=wall.clip(ray.from,ray.to);
                if(intersection.isPresent()&&ray.from.distanceToSqr(intersection.get())<distance) {
                    end=intersection.get();distance=ray.from.distanceToSqr(end);hit=true;
                }
            }
            return hit?new BlockHitResult(end,Direction.WEST,BlockPos.containing(end),false):BlockHitResult.miss(end,Direction.WEST,BlockPos.containing(end));
        }
    }
    @Test void rayExtendsBeyondTheFlyingTargetAndStopsAtTheFirstSolidBlock() throws Exception {
        Actor ship=new Actor();ship.world.walls.add(new AABB(100,70,-1,101,90,1));ship.world.walls.add(new AABB(150,70,-1,151,90,1));
        var hit=trace(ship,new Vec3(0,80,0),new Vec3(256,80,0));
        assertEquals(new Vec3(100,80,0),hit.end());assertTrue(hit.impact());assertEquals(1,ship.world.clips);
        ship.world.walls.clear();var open=trace(ship,new Vec3(0,80,0),new Vec3(1000,80,0));
        assertEquals(new Vec3(512,80,0),open.end());assertFalse(open.impact());
    }
    @Test void absentChunksAndWorldHeightBoundTheRayWithoutForcingLoadsOrInventingImpacts() throws Exception {
        Actor ship=new Actor();ship.world.absent.add(3);
        var trace=trace(ship,new Vec3(0,80,0),new Vec3(256,80,0));
        assertTrue(trace.end().x>=47&&trace.end().x<48);assertFalse(trace.impact());
        ship.world.absent.add(0);int before=ship.world.clips;
        assertEquals(new Vec3(0,80,0),trace(ship,new Vec3(0,80,0),new Vec3(256,80,0)).end());assertEquals(before,ship.world.clips);
        ship.world.absent.clear();var vertical=trace(ship,new Vec3(0,300,0),new Vec3(0,556,0));
        assertTrue(vertical.end().y<320);assertFalse(vertical.impact());
    }
    @Test void diagonalChunkCornerDoesNotClipThroughAnUnloadedIntermediateSliver()throws Exception {
        Actor ship=new Actor();ship.world.absentPairs.add("1,0");
        var result=trace(ship,new Vec3(15.8,80,15.7),new Vec3(100,80,99.9));
        assertTrue(result.end().x<16);assertFalse(result.impact());
    }
    private static Class<?> executable;
    private static MothershipWorld.Trace trace(Actor ship,Vec3 from,Vec3 to) throws Exception {
        if(executable==null) {
            String original="com/liucodex/doggiesnewvehicle/boss/MothershipWorld",target=original+"ExecutedRay";
            ClassNode node=new ClassNode();try(var in=MothershipRayExecutionTest.class.getClassLoader().getResourceAsStream(original+".class")){new ClassReader(in).accept(node,0);}
            node.methods.removeIf(m->!m.name.equals("trace"));node.fields.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
            Map<String,String> names=Map.of(original,target,
                    "net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class),
                    "net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class),
                    "net/minecraft/world/level/Level",Type.getInternalName(World.class),
                    "net/minecraft/world/level/border/WorldBorder",Type.getInternalName(Border.class),
                    "net/minecraft/world/level/ClipContext",Type.getInternalName(Context.class),
                    "net/minecraft/world/level/ClipContext$Block",Type.getInternalName(Block.class),
                    "net/minecraft/world/level/ClipContext$Fluid",Type.getInternalName(Fluid.class));
            ClassWriter writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
            byte[] bytes=writer.toByteArray();executable=new ClassLoader(MothershipRayExecutionTest.class.getClassLoader()) {
                Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
            }.define();
        }
        return (MothershipWorld.Trace)executable.getMethod("trace",Actor.class,Vec3.class,Vec3.class).invoke(null,ship,from,to);
    }
}
