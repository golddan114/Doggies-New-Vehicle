package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCompoundCollider;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Compiled transactions and collision admission, substituting only world/native service edges. */
class NuclearRecoveryExecutionTest {
    @Test void unfoldInitializationRestoresBoxAndLeaseAfterEveryThrowingEdge() throws Exception {
        var names=new HashMap<String,String>();
        map(names,"net/minecraft/server/level/ServerLevel",World.class);map(names,"net/minecraft/server/level/ServerPlayer",Operator.class);
        map(names,"com/atsuishio/superbwarfare/block/entity/ContainerBlockEntity",Box.class);
        map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",Body.class);
        map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class);map(names,"net/minecraft/core/RegistryAccess",Registry.class);
        map(names,"net/minecraft/core/HolderLookup$Provider",Registry.class);
        var type=extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearContainerDeployment",Set.of("startJob"),names,null);
        var method=type.getMethod("startJob",World.class,BlockPos.class,Box.class,Operator.class,Body.class,NuclearSiloStore.class);
        for(int failure=0;failure<4;failure++){
            var world=new World();var box=new Box();var candidate=new Body();var store=new NuclearSiloStore();
            assertTrue(store.beginUnfold(candidate.id,BlockPos.ZERO));CompoundTag original=box.entityTag;
            candidate.failure=failure==1;world.failure=failure>=2;world.releaseFailure=failure==3;
            if(failure==0){
                method.invoke(null,world,BlockPos.ZERO,box,new Operator(),candidate,store);
                assertTrue(box.entityTag.getCompound("NuclearSilo").getBoolean("Unfolding"));
                assertFalse(box.getOpened());assertEquals(0,box.getTick());assertTrue(store.unfolding());assertTrue(world.ticket);
            }else{
                assertThrows(InvocationTargetException.class,()->method.invoke(null,world,BlockPos.ZERO,box,new Operator(),candidate,store));
                assertSame(original,box.entityTag);assertEquals("retained",box.entityTag.getString("UnknownNativeField"));
                assertEquals(7,box.getTick());assertTrue(box.getOpened());assertNull(store.facility());assertFalse(store.unfolding());
                assertFalse(world.ticket);assertEquals(1,world.releases);
            }
        }
    }
    @Test void physicalMovementReplacesOnlyOptInBroadBoxAndLeavesTheWellHollow() throws Exception {
        var names=new HashMap<String,String>();map(names,"net/minecraft/world/entity/Entity",Body.class);map(names,"net/minecraft/world/level/Level",World.class);
        map(names,"net/minecraft/world/entity/EntitySelector",Selector.class);map(names,"net/minecraft/world/phys/shapes/VoxelShape",Shape.class);
        map(names,"net/minecraft/world/phys/shapes/Shapes",ShapeFactory.class);
        var type=extract("com/liucodex/doggiesnewvehicle/compat/mixin/CompoundEntityCollisionMixin",Set.of("dnv$compound"),names,null);
        var method=type.getMethod("dnv$compound",List.class,Body.class,Vec3.class,AABB.class,World.class,List.class);
        var world=new World();var silo=new Collider();world.entities.add(silo);
        var ordinary=new Shape(new AABB(8,0,0,9,1,1));var original=List.of(ordinary,new Shape(silo.bounds));
        var well=new AABB(-.2,0,-.2,.2,1,.2);
        assertEquals(List.of(ordinary),method.invoke(null,original,new Body(),Vec3.ZERO,well,world,original));
        var rim=new AABB(1.9,0,-.2,2.1,1,.2);var changed=(List<?>)method.invoke(null,original,new Body(),Vec3.ZERO,rim,world,original);
        assertEquals(2,changed.size());assertSame(ordinary,changed.get(0));assertEquals(silo.pieces.get(0),((Shape)changed.get(1)).box);
        world.entities.clear();assertSame(original,method.invoke(null,original,new Body(),Vec3.ZERO,well,world,original));
    }
    static void map(Map<String,String> names,String source,Class<?> target){names.put(source,Type.getInternalName(target));}
    @Test void completeBodyWorldSweepAllowsHighFlightAndRejectsUnloadedColumnsBeforeReads() throws Exception {
        var names=new HashMap<String,String>();map(names,"net/minecraft/server/level/ServerLevel",World.class);
        map(names,"net/minecraft/world/entity/Entity",Body.class);
        map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Body.class);
        map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileCollision$Result",SweepResult.class);
        map(names,"net/minecraft/world/phys/shapes/CollisionContext",Context.class);
        map(names,"net/minecraft/world/phys/shapes/VoxelShape",Shape.class);
        map(names,"net/minecraft/world/level/block/state/BlockState",Cell.class);
        map(names,"net/minecraft/world/level/BlockGetter",World.class);
        var type=extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileCollision",Set.of("sweep"),names,null);
        var method=type.getMethod("sweep",World.class,Body.class,List.class,Vec3.class,UUID.class);
        var world=new World();var missile=new Body();var body=List.of(new AABB(-.5,1024,-.5,.5,1026,.5));
        var motion=new Vec3(10,0,0);var owner=UUID.randomUUID();
        var result=(SweepResult)method.invoke(null,world,missile,body,motion,owner);
        assertTrue(result.loaded());assertEquals(Double.POSITIVE_INFINITY,result.fraction());assertEquals(0,world.blockReads);
        var target=new Body();target.bounds=new AABB(5,1024,-.5,6,1026,.5);world.entities.add(target);
        result=(SweepResult)method.invoke(null,world,missile,body,motion,owner);
        assertTrue(result.loaded());assertEquals(.45,result.fraction(),1e-8);assertEquals(5,result.contact().x,1e-8);
        world.columnsLoaded=false;int queries=world.entityQueries;
        result=(SweepResult)method.invoke(null,world,missile,body,motion,owner);
        assertFalse(result.loaded());assertEquals(0,world.blockReads);assertEquals(queries,world.entityQueries);
        world.columnsLoaded=true;world.entities.clear();world.solid=BlockPos.containing(5,319,0);
        result=(SweepResult)method.invoke(null,world,missile,List.of(new AABB(0,319,0,1,322,1)),motion,owner);
        assertTrue(result.loaded());assertEquals(.4,result.fraction(),1e-8);assertTrue(world.blockReads>0);
        assertFalse(world.outsideHeightRead);
    }
    @Test void pickingAndProjectileRaysPassThroughTheWellAndChooseTheClosestRealSurface() throws Exception {
        var names=new HashMap<String,String>();map(names,"net/minecraft/world/entity/Entity",Body.class);
        map(names,"net/minecraft/world/phys/EntityHitResult",Hit.class);
        var type=extract("com/liucodex/doggiesnewvehicle/compat/DnvCompoundRaytrace",Set.of("pick","projectile","pieces","contains","hit"),names,null);
        var pick=type.getMethod("pick",Body.class,Vec3.class,Vec3.class,List.class,double.class);
        var projectile=type.getMethod("projectile",Vec3.class,Vec3.class,List.class,float.class);
        var actor=new Body();var silo=new Collider();var from=new Vec3(0,5,0);var to=new Vec3(0,-7,0);
        assertNull(pick.invoke(null,actor,from,to,List.of(silo),144D));assertNull(projectile.invoke(null,from,to,List.of(silo),0F));
        from=new Vec3(5,0,0);to=new Vec3(-5,0,0);
        var hit=(Hit)pick.invoke(null,actor,from,to,List.of(silo),100D);assertSame(silo,hit.entity);assertEquals(3,hit.point.x,1e-8);
        hit=(Hit)projectile.invoke(null,from,to,List.of(silo),0F);assertSame(silo,hit.entity);assertEquals(3,hit.point.x,1e-8);
        var nearer=new Body();nearer.bounds=new AABB(4,-1,-1,4.5,1,1);
        hit=(Hit)pick.invoke(null,actor,from,to,List.of(silo,nearer),100D);assertSame(nearer,hit.entity);
        nearer.root=actor;assertNull(pick.invoke(null,actor,from,to,List.of(nearer),100D));
        nearer.riderInteraction=true;assertNotNull(pick.invoke(null,actor,from,to,List.of(nearer),100D));
    }
    static Class<?> extract(String source,Set<String> methods,Map<String,String> names,Class<?> parent) throws Exception {
        return extract(source,methods,names,parent,true);
    }
    static Class<?> extract(String source,Set<String> methods,Map<String,String> names,Class<?> parent,boolean retainLambdas) throws Exception {
        String target="com/liucodex/doggiesnewvehicle/compat/recoveryprobe/"+source.substring(source.lastIndexOf('/')+1);
        var node=new ClassNode();try(var input=NuclearRecoveryExecutionTest.class.getResourceAsStream("/"+source+".class")){new ClassReader(input.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
        node.access=Opcodes.ACC_PUBLIC;node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
        // Selected native methods still need their own lambdas; unrelated actor/world lambdas remain excluded.
        node.methods.removeIf(m->!methods.contains(m.name)&&!(m.name.startsWith("lambda$")
                &&(retainLambdas||methods.stream().anyMatch(name->m.name.startsWith("lambda$"+name+"$")))));
        node.methods.forEach(m->m.access=(m.access&Opcodes.ACC_STATIC)|Opcodes.ACC_PUBLIC);
        if(parent!=null){node.superName=Type.getInternalName(parent);var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
            ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        }else node.superName="java/lang/Object";
        names.put(source,target);var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){
            @Override public String map(String key){return names.getOrDefault(key,key);}
            @Override public String mapMethodName(String owner,String name,String descriptor){
                return owner.equals("net/neoforged/bus/api/IEventBus")&&name.equals("post")?"postNeo":name;
            }
        }));
        return new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
    }
    public static class Body {public UUID id=UUID.randomUUID();public boolean failure,armed=true,removed,invulnerable;public int discards;
        public World world=new World();public AABB bounds=new AABB(-3,-8,-3,3,11,3);public Body root=this;public boolean riderInteraction;
        public boolean isSpectator(){return false;}public boolean isPickable(){return true;}
        public float getPickRadius(){return 0;}public Body getRootVehicle(){return root;}public boolean canRiderInteract(){return riderInteraction;}
        public UUID getUUID(){return id;}public World level(){return world;}public AABB getBoundingBox(){return bounds;}
        public boolean isRemoved(){return removed;}public boolean isInvulnerableTo(Damage source){return invulnerable;}public void discard(){removed=true;discards++;}
        public CompoundTag serializeNBT(){if(failure)throw new IllegalStateException("serialization");var tag=new CompoundTag();tag.putUUID("UUID",id);tag.put("NuclearSilo",new CompoundTag());return tag;}
        public CompoundTag serializeNBT(Registry registry){return serializeNBT();}
    }
    public static class Collider extends Body implements DnvCompoundCollider {
        public List<AABB> pieces=List.of(new AABB(2,-8,-3,3,11,3),new AABB(-3,-8,-3,-2,11,3));public List<AABB> dnv$collisionBoxes(){return pieces;}
    }
    public static class World {public boolean isClientSide,failure,releaseFailure,ticket;public int releases;public List<Body> entities=new ArrayList<>();
        public boolean columnsLoaded=true,outsideHeightRead;public int blockReads,entityQueries;public BlockPos solid;
        public boolean hasChunksAt(int x0,int z0,int x1,int z1){return columnsLoaded;}
        public int getMinBuildHeight(){return -64;}public int getMaxBuildHeight(){return 320;}
        public Cell getBlockState(BlockPos position){blockReads++;outsideHeightRead|=position.getY()<-64||position.getY()>=320;
            return new Cell(position.equals(solid));}
        public Registry registryAccess(){return new Registry();}
        public List<Body> getEntities(Body mover,AABB box,Predicate<Body> predicate){entityQueries++;return entities.stream().filter(predicate).toList();}}
    public static class Registry { }
    public static class Operator {public UUID getUUID(){return UUID.randomUUID();}}
    public static class Box {public CompoundTag entityTag=new CompoundTag();public int tick=7,changed;public boolean opened=true;
        public Box(){entityTag.putString("UnknownNativeField","retained");}public int getTick(){return tick;}public boolean getOpened(){return opened;}
        public void setTick(int value){tick=value;}public void setOpened(boolean value){opened=value;}public void setChanged(){changed++;}}
    public static class Tickets {public static void update(World world,UUID id,Set<Long> chunks){world.ticket=true;if(world.failure)throw new IllegalStateException("ticket callback");}
        public static void release(World world,UUID id){world.releases++;world.ticket=false;if(world.releaseFailure)throw new IllegalStateException("release callback");}}
    public record Flag(String id) { }
    public static class DamageTags {public static final Flag IS_PROJECTILE=new Flag("projectile"),IS_EXPLOSION=new Flag("explosion");}
    public record Damage(boolean projectile,boolean explosion){public boolean is(Flag flag){return flag==DamageTags.IS_PROJECTILE?projectile:explosion;}}
    public static class Selector {public static final Predicate<Body> NO_SPECTATORS=b->true;}
    public static class Shape {public final AABB box;public Shape(AABB box){this.box=box;}
        public List<AABB> toAabbs(){return box==null?List.of():List.of(box);}}
    public static class ShapeFactory {public static Shape create(AABB box){return new Shape(box);}}
    public interface Context {static Context of(Body body){return new Context(){};}}
    public record Cell(boolean solid){public Shape getCollisionShape(World world,BlockPos position,Context context){
        return new Shape(solid?new AABB(0,0,0,1,1,1):null);}}
    public record SweepResult(boolean loaded,double fraction,Vec3 contact) { }
    public static class Hit {public Body entity;public Vec3 point;public Hit(Body entity){this(entity,entity.bounds.getCenter());}
        public Hit(Body entity,Vec3 point){this.entity=entity;this.point=point;}}
}
