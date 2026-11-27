package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs the compiled deployment transaction; world/registry/item storage are ports. */
class ApocalypseAiDeploymentExecutionTest {
    @Test void repeatedPlacementRejectsNativeObbVehiclesThatVanillaCollisionDoesNotExpose() throws Exception {
        var f=new Fixture();f.context.stack.count=3;
        assertEquals(Result.CONSUME,f.use());
        Tank first=Content.created;f.context.world.entities.add(first);
        assertTrue(f.context.world.noCollision(first,first.getBoundingBox()),"OBB vehicles do not expose vanilla collision shapes");
        assertEquals(Result.FAIL,f.use());
        assertEquals(Result.FAIL,f.use());
        assertEquals(1,f.context.world.insertions);assertEquals(1,f.context.world.members);
        assertEquals(2,f.context.stack.count);assertEquals(new Vec3(.5,1,.5),new Vec3(first.x,first.y,first.z));
        assertEquals(2,f.context.player.messages.size());
    }
    @Test void nativeWrecksAndPhysicsOnlyRegionsBlockPlacementButRemovedSpectatorAndNoPhysicsDoNot() throws Exception {
        for(int state=0;state<5;state++) {
            var f=new Fixture();Tank other=new Tank();other.moveTo(.5,1,.5,0,0);
            other.physicsOnly=true;other.wreck=state==0;other.removed=state==1;other.spectator=state==2;other.noPhysics=state==3;
            if(state==4)other.moveTo(30,1,30,0,0);
            f.context.world.entities.add(other);
            assertEquals(state==0?Result.FAIL:Result.CONSUME,f.use(),"state "+state);
            assertEquals(state==0?0:1,f.context.world.insertions);
            assertEquals(state==0?1:0,f.context.stack.count);
        }
    }
    @Test void ordinaryAndSneakUseShareStandingClearanceAndDoNotConsumeTwice() throws Exception {
        double placedZ=0;
        for(boolean sneaking:new boolean[]{false,true}) {
            var f=new Fixture();f.context.player.point=new Vec3(.5,1,-3);f.context.player.sneaking=sneaking;
            assertEquals(Result.CONSUME,f.type.getMethod("onItemUseFirst",Stack.class,Context.class).invoke(f.item,f.context.stack,f.context));
            assertEquals(1,f.context.world.insertions);assertEquals(0,f.context.stack.count);
            assertEquals(Pose.STANDING,f.context.player.askedPose);assertTrue(Content.created.z>1);
            if(sneaking)assertEquals(placedZ,Content.created.z);else placedZ=Content.created.z;
            assertFalse(Content.created.getCollisionOBB().bounds.intersects(f.context.player.size.makeBoundingBox(f.context.player.point)));
        }
    }
    @Test void nativeWorldAcceptanceIsTheOnlyItemConsumptionAndMembershipCommitPoint() throws Exception {
        var f=new Fixture();f.context.world.accept=false;
        assertEquals(Result.FAIL,f.use());assertEquals(1,f.context.stack.count);assertEquals(0,f.context.world.members);
        f.context.world.accept=true;assertEquals(Result.CONSUME,f.use());
        assertEquals(0,f.context.stack.count);assertEquals(1,f.context.world.members);
        assertEquals(f.context.player,Content.created.deployer);assertEquals(1,Content.created.updateCount);
        var creative=new Fixture();creative.context.player.abilities.instabuild=true;
        assertEquals(Result.CONSUME,creative.use());assertEquals(1,creative.context.stack.count);
    }
    @Test void unloadedBorderHeightPermissionAndWholeHullCollisionRejectBeforeInsertion() throws Exception {
        for(int failure=0;failure<7;failure++){
            var f=new Fixture();
            switch(failure){
                case 0->f.context.world.loaded=false;case 1->f.context.world.border.allowed=false;
                case 2->f.context.world.tooHigh=true;case 3->f.context.player.abilities.mayBuild=false;
                case 4->f.context.player.mayUse=false;case 5->f.context.world.outerPermission=false;
                case 6->f.context.world.outerCollision=true;
            }
            assertEquals(Result.FAIL,f.use(),"failure "+failure);assertEquals(0,f.context.world.insertions);
            assertEquals(1,f.context.stack.count);assertEquals(0,f.context.world.members);
            assertEquals(1,f.context.player.messages.size());
        }
    }
    @Test void generatedAliasUsesOneAuthoredConfigurationAndMatchesProcessedPlayerData() throws Exception {
        try(var base=getClass().getResourceAsStream("/data/doggievehicle/sbw/vehicles/apocalypse.json");
            var ai=getClass().getResourceAsStream("/data/doggievehicle/sbw/vehicles/apocalypse_ai.json")){
            assertNotNull(base);assertNotNull(ai);
            var player=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(base,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var allied=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(ai,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var engine=allied.getAsJsonObject("EngineInfo");
            assertEquals(.454666666667,engine.get("MaxForwardSpeedRate").getAsDouble(),1E-9);
            assertEquals(.227333333333,engine.get("MaxBackwardSpeedRate").getAsDouble(),1E-9);
            assertEquals(.04,engine.get("SteeringSpeed").getAsDouble(),1E-9);
            engine.add("MaxForwardSpeedRate",player.getAsJsonObject("EngineInfo").get("MaxForwardSpeedRate"));
            engine.add("MaxBackwardSpeedRate",player.getAsJsonObject("EngineInfo").get("MaxBackwardSpeedRate"));
            engine.add("SteeringSpeed",player.getAsJsonObject("EngineInfo").get("SteeringSpeed"));
            assertEquals(player,allied,"all five weapons and remaining chassis data stay shared");
        }
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/apocalypse_ai.json")));
    }
    static class Fixture{
        final Context context=new Context();final Class<?> type;final Object item;
        Fixture()throws Exception{
            String source="com/liucodex/doggiesnewvehicle/item/ApocalypseAiDeployerItem",name="com/liucodex/doggiesnewvehicle/compat/executionprobe/AlliedDeployer";
            Map<String,String> names=new HashMap<>();names.put(source,name);
            Map.ofEntries(Map.entry("net/minecraft/world/item/Item",Item.class),Map.entry("net/minecraft/world/item/context/UseOnContext",Context.class),
                    Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/server/level/ServerLevel",Server.class),
                    Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/entity/player/Player",Player.class),
                    Map.entry("net/minecraft/world/entity/player/Abilities",Abilities.class),Map.entry("net/minecraft/world/entity/EntityType",EntityType.class),
                    Map.entry("net/minecraft/world/entity/Pose",Pose.class),Map.entry("net/minecraft/world/entity/EntityDimensions",Dimensions.class),
                    Map.entry("net/minecraft/network/chat/Component",Text.class),Map.entry("net/minecraft/network/chat/MutableComponent",Text.class),
                    Map.entry("net/minecraft/world/item/ItemStack",Stack.class),Map.entry("net/minecraft/world/level/border/WorldBorder",Border.class),
                    Map.entry("net/minecraft/world/InteractionResult",Result.class),
                    Map.entry("net/minecraftforge/registries/RegistryObject",Registry.class),Map.entry("net/neoforged/neoforge/registries/DeferredHolder",Registry.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseContent",Content.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity",Tank.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Tank.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/OBBEntity",Shaped.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/OBB",Box.class))
                    .forEach((k,v)->names.put(k,Type.getInternalName(v)));
            ClassNode n=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(n,ClassReader.SKIP_FRAMES);}
            n.methods.removeIf(m->m.name.equals("<init>")||m.name.equals("appendHoverText"));
            for(var method:n.methods)for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/network/chat/Component")) {
                    call.itf=false;if(call.getOpcode()==Opcodes.INVOKEINTERFACE)call.setOpcode(Opcodes.INVOKEVIRTUAL);
                }
            MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,n.superName,"<init>","()V",false));
            init.instructions.add(new InsnNode(Opcodes.RETURN));n.methods.add(init);
            ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}};
            n.accept(new ClassRemapper(out,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            type=new ExactQueuedShotFixture.Loader(Map.of(name,out.toByteArray())).loadClass(name.replace('/','.'));item=type.getConstructor().newInstance();
        }
        Object use()throws Exception{return type.getMethod("useOn",Context.class).invoke(item,context);}
    }
    public enum Result{SUCCESS,FAIL,CONSUME}
    public static class Item{}
    public static class Body{
        public boolean removed,spectator,noPhysics;
        public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}
        public AABB getBoundingBox(){return new AABB(0,0,0,1,1,1);}
    }
    public static class Abilities{public boolean mayBuild=true,instabuild;}
    public static class Player extends Body{
        public Abilities abilities=new Abilities();public boolean mayUse=true,sneaking;public Vec3 point=new Vec3(30,1,-30);
        public final Dimensions size=new Dimensions();public Pose askedPose;public List<Text> messages=new ArrayList<>();
        public Vec3 position(){return point;}public Dimensions getDimensions(Pose pose){askedPose=pose;return size;}
        public void displayClientMessage(Text text,boolean overlay){messages.add(text);}
        public Abilities getAbilities(){return abilities;}public boolean mayUseItemAt(BlockPos pos,Direction face,Stack stack){return mayUse;}public float getYRot(){return 0;}
    }
    public enum Pose{STANDING}
    public static class Dimensions{public AABB makeBoundingBox(Vec3 p){return new AABB(p.x-.3,p.y,p.z-.3,p.x+.3,p.y+1.8,p.z+.3);}}
    public record Text(String key){public static Text translatable(String key){return new Text(key);}}
    public static class Stack{public int count=1;public void shrink(int amount){count-=amount;}}
    public static class Context{
        public Server world=new Server();public Player player=new Player();public Stack stack=new Stack();
        public World getLevel(){return world;}public Player getPlayer(){return player;}public Stack getItemInHand(){return stack;}
        public BlockPos getClickedPos(){return BlockPos.ZERO;}public Direction getClickedFace(){return Direction.UP;}
    }
    public static class World{}
    public static class Server extends World{
        public boolean loaded=true,tooHigh,outerPermission=true,outerCollision,accept=true;public int insertions,members;public Border border=new Border();
        public final List<Body> entities=new ArrayList<>();
        public List<Body> getEntities(Body excluded,AABB bounds,java.util.function.Predicate<? super Body> filter){
            return entities.stream().filter(e->e!=excluded&&bounds.intersects(e.getBoundingBox())&&filter.test(e)).toList();
        }
        public boolean hasChunkAt(BlockPos p){return loaded;}public boolean hasChunksAt(BlockPos a,BlockPos b){return loaded;}
        public boolean mayInteract(Player player,BlockPos p){return outerPermission||p.getX()==0;}
        public boolean isOutsideBuildHeight(BlockPos p){return tooHigh;}public Border getWorldBorder(){return border;}
        public boolean noCollision(Body body,AABB bounds){return !outerCollision||bounds.maxX<=1;}
        public boolean addFreshEntity(Body body){insertions++;if(accept)members++;return accept;}
    }
    public static class Border{public boolean allowed=true;public boolean isWithinBounds(AABB bounds){return allowed;}}
    public record Box(AABB bounds){
        public static AABB getWorldAABB(Box box){return box.bounds;}
        public static boolean isColliding(Box box,AABB bounds){return box.bounds.intersects(bounds);}
    }
    public interface Shaped{List<Box> getOBBs();boolean enableAABB();}
    public static class Tank extends Body implements Shaped{
        public int updateCount;public Player deployer;public double x,y,z;
        public boolean physicsOnly,wreck;public boolean enableAABB(){return false;}
        public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
        public void moveTo(double x,double y,double z,float yaw,float pitch){this.x=x;this.y=y;this.z=z;}public void updateOBB(){updateCount++;}public void refreshBoundingBoxFromOBBs(){}
        public List<Box> getOBBs(){return physicsOnly?List.of():List.of(new Box(new AABB(0,0,0,1,1,1).move(x,y,z)),new Box(new AABB(-3,0,-6,3,4,6).move(x,y,z)));}
        @Override public AABB getBoundingBox(){return new AABB(-3,0,-6,3,4,6).move(x,y,z);}
        public Box getCollisionOBB(){return new Box(new AABB(-3,0,-6,3,3,6).move(x,y,z));}public void deployedBy(Player player){deployer=player;}
    }
    public static class EntityType{public Body create(World world){return Content.created=new Tank();}}
    public static class Registry{public Object get(){return new EntityType();}}
    public static class Content{public static final Registry ALLIED_TANK=new Registry();public static Tank created;}
}
