package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Production binding, validation and dispatch; loaded world/permission/native deployment are explicit edges. */
class NuclearRemoteExecutionTest {
    static final BlockPos POSITION=new BlockPos(10,64,20);
    static final String ROOT="com/liucodex/doggiesnewvehicle/artillery/silo/";
    @Test void bindThenRetreatBeyondSurvivalReachDispatchesOnlyThePairedBoxAndPreservesUserData() throws Exception {
        var f=new Fixture();f.bind();assertTrue(f.stack.data.contains("DnvNuclearDeploymentRemote"));assertEquals(1,f.world.box.changed);
        assertEquals("keep",f.stack.data.getString("UserLabel"));
        f.actor.position=POSITION.getCenter().add(12,0,0);f.activate();assertEquals(1,f.world.deployed);
        assertSame(f.actor,f.world.operator);assertSame(f.world.box,f.world.deployedBox);
        f.actor.position=POSITION.getCenter().add(32,0,0);f.activate();assertEquals(2,f.world.deployed);
        f.actor.position=POSITION.getCenter().add(32.01,0,0);f.activate();assertEquals(2,f.world.deployed);assertTrue(f.actor.last().contains("remote_range"));
    }
    @Test void otherActorDimensionOrPermissionCannotUseABindingAsAuthority() throws Exception {
        for(String reason:List.of("actor","dimension","mayBuild","spectator","dead","claim","itemPermission")){
            var f=new Fixture();f.bind();
            switch(reason){
                case "actor"->f.actor.id=UUID.randomUUID();case "dimension"->f.world.dimension="minecraft:the_nether";
                case "mayBuild"->f.actor.abilities.mayBuild=false;case "spectator"->f.actor.spectator=true;
                case "dead"->f.actor.alive=false;case "claim"->f.world.allowed=false;case "itemPermission"->f.actor.allowed=false;
            }
            f.activate();assertEquals(0,f.world.deployed,reason);
        }
    }
    @Test void unloadedAndReplacedBoxesFailWithoutLoadingOrRebinding() throws Exception {
        var f=new Fixture();f.bind();f.world.loaded=false;int reads=f.world.boxReads;f.activate();
        assertEquals(reads,f.world.boxReads);assertEquals(0,f.world.deployed);assertTrue(f.actor.last().contains("remote_unloaded"));
        f.world.loaded=true;f.world.box=new Box();f.activate();assertEquals(0,f.world.deployed);assertTrue(f.actor.last().contains("remote_missing"));
        f.world.box=null;f.activate();assertEquals(0,f.world.deployed);
    }
    @Test void pairingIsPersistentReferenceAndMalformedOrClearedMetadataCannotDeploy() throws Exception {
        var f=new Fixture();f.bind();var saved=f.stack.data.copy();var boxData=f.world.box.persistent.copy();
        f.stack=new Stack();f.stack.data=saved;f.world.box=new Box();f.world.box.persistent=boxData;f.activate();assertEquals(1,f.world.deployed);
        f.stack.data.getCompound("DnvNuclearDeploymentRemote").putString("Position","wrong type");f.activate();assertEquals(1,f.world.deployed);
        f.stack.data=saved.copy();f.type.getMethod("writeBinding",Stack.class,CompoundTag.class).invoke(null,f.stack,null);
        f.activate();assertEquals(1,f.world.deployed);assertEquals("keep",f.stack.data.getString("UserLabel"));
        assertTrue(f.actor.last().contains("remote_unbound"));
    }
    @Test void bindRejectsBusyOrOutOfReachAndReusesSameBoxIdentity() throws Exception {
        var f=new Fixture();f.actor.position=POSITION.getCenter().add(9,0,0);f.bind();assertFalse(f.stack.data.contains("DnvNuclearDeploymentRemote"));
        f.actor.position=POSITION.getCenter();f.world.box.entityTag.getCompound("NuclearSilo").putBoolean("Unfolding",true);
        f.bind();assertFalse(f.stack.data.contains("DnvNuclearDeploymentRemote"));
        f.world.box.entityTag.getCompound("NuclearSilo").putBoolean("Unfolding",false);f.bind();
        var identity=f.stack.data.getCompound("DnvNuclearDeploymentRemote").getUUID("Target");f.bind();
        assertEquals(identity,f.stack.data.getCompound("DnvNuclearDeploymentRemote").getUUID("Target"));
    }
    static class Fixture {
        final Class<?> type;final World world=new World();final Actor actor=new Actor(world);Stack stack=new Stack();
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerPlayer",Actor.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/player/Player",Actor.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/player/Abilities",Abilities.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/item/ItemStack",Stack.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/block/entity/BlockEntity",Box.class);
            NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/block/entity/ContainerBlockEntity",Box.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/EntityType",Kind.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
            NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryEntities",Entities.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/resources/ResourceKey",Dimension.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/resources/ResourceLocation",Name.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/border/WorldBorder",Border.class);
            NuclearRecoveryExecutionTest.map(names,ROOT+"NuclearContainerDeployment",Deployment.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/neoforge/DnvNeoItemData",NeoData.class);
            type=NuclearRecoveryExecutionTest.extract(ROOT+"NuclearDeploymentRemoteItem",Set.of("bind","activate","operator","readBinding","writeBinding","message"),names,null);
        }
        void bind() throws Exception{type.getMethod("bind",World.class,BlockPos.class,Box.class,Actor.class,Stack.class).invoke(null,world,POSITION,world.box,actor,stack);}
        void activate() throws Exception{type.getMethod("activate",World.class,Actor.class,Stack.class).invoke(null,world,actor,stack);}
    }
    public static class World {
        public Box box=new Box();public boolean loaded=true,allowed=true;public int boxReads,deployed;public String dimension="minecraft:overworld";public Actor operator;public Box deployedBox;
        public boolean hasChunkAt(BlockPos p){return loaded;}public Box getBlockEntity(BlockPos p){assertTrue(loaded);boxReads++;return box;}
        public boolean mayInteract(Actor a,BlockPos p){return allowed;}public boolean isOutsideBuildHeight(BlockPos p){return p.getY()<-64||p.getY()>=320;}
        public Border getWorldBorder(){return new Border();}public Dimension dimension(){return new Dimension(dimension);}
    }
    public static class Box {
        public Kind entityType=Entities.KIND;public CompoundTag entityTag=new CompoundTag(),persistent=new CompoundTag();public int changed;
        public Box(){entityTag.put("NuclearSilo",new CompoundTag());}public CompoundTag getPersistentData(){return persistent;}public void setChanged(){changed++;}
    }
    public static class Actor {
        public UUID id=UUID.randomUUID();public Vec3 position=POSITION.getCenter();public boolean alive=true,spectator,allowed=true;
        public final World world;public final Abilities abilities=new Abilities();public final List<Component> messages=new ArrayList<>();
        public Actor(World world){this.world=world;}public World level(){return world;}public UUID getUUID(){return id;}
        public boolean isAlive(){return alive;}public boolean isSpectator(){return spectator;}public Abilities getAbilities(){return abilities;}
        public double distanceToSqr(Vec3 p){return position.distanceToSqr(p);}public boolean mayUseItemAt(BlockPos p,Direction d,Stack s){return allowed;}
        public void displayClientMessage(Component message,boolean overlay){messages.add(message);}public String last(){return messages.get(messages.size()-1).toString();}
    }
    public static class Abilities {public boolean mayBuild=true;}
    public static class Stack {public CompoundTag data=new CompoundTag();public Stack(){data.putString("UserLabel","keep");}
        public CompoundTag getTag(){return data;}public void setTag(CompoundTag tag){data=tag;}}
    public static class NeoData {public static CompoundTag read(Stack stack){return stack.data;}public static void set(Stack stack,CompoundTag tag){stack.data=tag;}}
    public static class Deployment {
        public static boolean isSiloType(Kind kind){return kind==Entities.KIND;}
        public static void begin(World w,BlockPos p,Box b,Actor a){w.deployed++;w.operator=a;w.deployedBox=b;}}
    public static class Kind { }
    public static class Ref {public Object get(){return Entities.KIND;}}
    public static class Entities {public static final Kind KIND=new Kind();public static final Ref NUCLEAR_SILO=new Ref();}
    public record Dimension(String value){public Name location(){return new Name(value);}}
    public record Name(String value){@Override public String toString(){return value;}}
    public static class Border {public boolean isWithinBounds(BlockPos p){return true;}}
}
