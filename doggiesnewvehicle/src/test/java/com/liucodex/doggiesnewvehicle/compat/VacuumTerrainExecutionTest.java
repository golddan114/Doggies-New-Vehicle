package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore;
import com.liucodex.doggiesnewvehicle.artillery.silo.SiloPayloadKind;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Runs the compiled native admission/removal methods, including protection and callback tampering. */
class VacuumTerrainExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/artillery/silo/VacuumPayloadWorld";
    @org.junit.jupiter.api.BeforeEach void resetTeleportCallbacks(){
        var bus=(TestBus)BusHolder.EVENT_BUS;bus.cancelTeleport=false;bus.redirect=null;bus.mutateTeleportEnemy=false;bus.foreignTeleport=false;
    }
    Class<?> target() throws Exception {
        var map=new HashMap<String,String>();
        for(String type:List.of("net/minecraft/server/level/ServerLevel","net/minecraft/world/level/Level",
                "net/minecraft/world/level/BlockGetter","net/minecraft/world/level/LevelReader"))
            NuclearRecoveryExecutionTest.map(map,type,World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/state/BlockState",State.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/material/FluidState",Fluid.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/Block",Block.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/LiquidBlock",Liquid.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/Blocks",Blocks.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/entity/BlockEntity",Object.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/border/WorldBorder",Border.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/player/Player",Actor.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/Entity",View.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/LivingEntity",View.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/monster/Enemy",Hostile.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/scores/Team",Team.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/scores/PlayerTeam",Team.class);
        NuclearRecoveryExecutionTest.map(map,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class);
        NuclearRecoveryExecutionTest.map(map,"com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",OwnedVehicle.class);
        NuclearRecoveryExecutionTest.map(map,"com/atsuishio/superbwarfare/tools/SeekTool",Seek.class);
        NuclearRecoveryExecutionTest.map(map,"com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime",Protection.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/Explosion",Blast.class);
        NuclearRecoveryExecutionTest.map(map,"com/liucodex/doggiesnewvehicle/artillery/silo/VacuumEffectEntity",Effect.class);
        NuclearRecoveryExecutionTest.map(map,"com/liucodex/doggiesnewvehicle/artillery/silo/VacuumEffectEntity$Debris",Debris.class);
        for(String prefix:List.of("net/minecraftforge/","net/neoforged/neoforge/")){
            NuclearRecoveryExecutionTest.map(map,prefix+"event/ForgeEventFactory",Hooks.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"event/level/BlockEvent$BreakEvent",Break.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"event/entity/EntityTeleportEvent",Teleport.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"common/MinecraftForge",BusHolder.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"common/NeoForge",BusHolder.class);
        }
        NuclearRecoveryExecutionTest.map(map,"net/minecraftforge/eventbus/api/IEventBus",Bus.class);
        NuclearRecoveryExecutionTest.map(map,"net/neoforged/bus/api/IEventBus",Bus.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraftforge/eventbus/api/Event",Event.class);
        NuclearRecoveryExecutionTest.map(map,"net/neoforged/bus/api/Event",Event.class);
        NuclearRecoveryExecutionTest.map(map,"com/atsuishio/superbwarfare/tools/ParticleTool",Particles.class);
        NuclearRecoveryExecutionTest.map(map,"com/liucodex/doggiesnewvehicle/artillery/silo/VacuumDamage",Damage.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/damagesource/DamageSource",Damage.class);
        return NuclearRecoveryExecutionTest.extract(ROOT,
                Set.of("eligible","peel","burst","center","blastEligible","blastTerrain","pull","converge","target","enemy","pullable"),map,Body.class,false);
    }
    NuclearSiloStore.Impact impact(){return NuclearSiloStore.Impact.of(UUID.randomUUID(),UUID.randomUUID(),BlockPos.ZERO,SiloPayloadKind.DOGEGG);}
    @Test void unteamedEmptyNativeVehiclesArePulledButCannotEnterEnemyDamageOrConvergence()throws Exception{
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;
        var empty=new Vehicle();empty.neutral=true;empty.bounds=empty.bounds.move(20,-8,0);
        var occupied=new Vehicle();occupied.neutral=true;occupied.riders=true;occupied.bounds=empty.bounds;
        var friendly=new Vehicle();friendly.enemy=false;friendly.bounds=empty.bounds;
        var protectedVehicle=new Vehicle();protectedVehicle.neutral=true;protectedVehicle.protectedTarget=true;protectedVehicle.bounds=empty.bounds;
        world.targets.addAll(List.of(empty,occupied,friendly,protectedVehicle));var impact=impact();
        assertEquals(List.of(empty.id),type.getMethod("pull",NuclearSiloStore.Impact.class,int.class).invoke(body,impact,16));
        assertEquals(1,empty.pushes);assertTrue(empty.motion.x<0&&empty.motion.y>0);assertTrue(empty.hurtMarked);
        assertEquals(0,occupied.pushes);assertEquals(0,friendly.pushes);assertEquals(0,protectedVehicle.pushes);
        empty.bounds=empty.bounds.move(-20,8,0);
        type.getMethod("burst",NuclearSiloStore.Impact.class).invoke(body,captured(impact,empty));
        assertEquals(0,empty.kills);assertEquals(0,empty.hits);assertEquals(0,empty.teleports);
    }
    @Test void actorOwnershipAndFriendlyRidersStillVetoTheEntirePullAdmission()throws Exception{
        var type=target();var body=(Body)type.getConstructor().newInstance();var impact=impact();
        var owned=new OwnedVehicle();owned.ownerId=impact.actor();
        var crewed=new Vehicle();crewed.occupants.add(new View(){@Override public UUID getUUID(){return impact.actor();}});
        body.level.targets.addAll(List.of(owned,crewed));
        assertEquals(List.of(),type.getMethod("pull",NuclearSiloStore.Impact.class,int.class).invoke(body,impact,16));
        assertEquals(0,owned.pushes);assertEquals(0,crewed.pushes);
    }
    @Test void nativeAdmissionIncludesBuriedBlocksButRejectsStorageUnbreakableFluidUnloadedAndOutside() throws Exception {
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;
        var eligible=type.getMethod("eligible",NuclearSiloStore.Impact.class,BlockPos.class);var impact=impact();
        world.cells.put(BlockPos.ZERO,new State(false,1,false,new Block()));
        assertTrue((boolean)eligible.invoke(body,impact,BlockPos.ZERO));
        for(State state:List.of(new State(false,-1,false,new Block()),new State(false,1,true,new Block()),
                new State(false,1,false,new Liquid()),new State(false,1,false,new Wet()),
                new State(false,1,false,new Block(){@Override public float getExplosionResistance(){return 1200;}}),
                new State(true,1,false,new Block()))){
            world.cells.put(BlockPos.ZERO,state);assertFalse((boolean)eligible.invoke(body,impact,BlockPos.ZERO));
        }
        world.cells.put(BlockPos.ZERO,new State(false,1,false,new Block()));
        world.loaded=false;assertFalse((boolean)eligible.invoke(body,impact,BlockPos.ZERO));world.loaded=true;
        world.storage=true;assertFalse((boolean)eligible.invoke(body,impact,BlockPos.ZERO));world.storage=false;
        world.border.allowed=false;assertFalse((boolean)eligible.invoke(body,impact,BlockPos.ZERO));world.border.allowed=true;
        world.cells.put(new BlockPos(16,0,0),world.cells.get(BlockPos.ZERO));
        assertTrue((boolean)eligible.invoke(body,impact,new BlockPos(16,0,0)));
        assertFalse((boolean)eligible.invoke(body,impact,new BlockPos(17,0,0)));
        for(var direction:net.minecraft.core.Direction.values())world.cells.put(BlockPos.ZERO.relative(direction),world.cells.get(BlockPos.ZERO));
        assertTrue((boolean)eligible.invoke(body,impact,BlockPos.ZERO));
        world.modify=false;assertFalse((boolean)eligible.invoke(body,impact,BlockPos.ZERO));
    }
    @Test void cancelledMutatedAndFailedRemovalsCannotProduceVisualFragments() throws Exception {
        var type=target();var peel=type.getMethod("peel",NuclearSiloStore.Impact.class,List.class);
        var body=(Body)type.getConstructor().newInstance();var world=body.level;var impact=impact();
        var bus=(TestBus)BusHolder.EVENT_BUS;bus.world=world;bus.cancel=true;
        world.cells.put(BlockPos.ZERO,new State(false,1,false,new Block()));
        peel.invoke(body,impact,List.of(BlockPos.ZERO));assertEquals(0,world.writes);assertTrue(world.effect.debris.isEmpty());
        bus.cancel=false;bus.mutate=true;
        peel.invoke(body,impact,List.of(BlockPos.ZERO));assertEquals(0,world.writes);assertTrue(world.effect.debris.isEmpty());
        bus.mutate=false;world.success=false;world.cells.put(BlockPos.ZERO,new State(false,1,false,new Block()));
        peel.invoke(body,impact,List.of(BlockPos.ZERO));assertEquals(0,world.writes);assertTrue(world.effect.debris.isEmpty());
    }
    @Test void callbacksCannotExpandOrDuplicateTheApprovedWorldWriteSet() throws Exception {
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;
        var bus=(TestBus)BusHolder.EVENT_BUS;bus.world=world;bus.cancel=false;bus.mutate=false;
        world.inject=true;var outside=new BlockPos(1,0,0);
        world.cells.put(BlockPos.ZERO,new State(false,1,false,new Block()));world.cells.put(outside,new State(false,1,false,new Block()));
        type.getMethod("peel",NuclearSiloStore.Impact.class,List.class).invoke(body,impact(),List.of(BlockPos.ZERO));
        assertEquals(1,world.writes);assertFalse(world.cells.get(outside).air);assertEquals(1,world.effect.debris.size());
        assertEquals(BlockPos.ZERO,world.effect.debris.get(0).position());
    }
    @Test void terminalBurstRechecksRangeFriendsAndCallbackAdmissionAndKillsEachApprovedEnemyOnce() throws Exception {
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;
        var allowed=new View();var friend=new View();friend.enemy=false;
        var outside=new View();outside.bounds=outside.bounds.move(17,0,0);
        var added=new View();world.targets.addAll(List.of(allowed,friend,outside));world.injected=added;
        var burst=type.getMethod("burst",NuclearSiloStore.Impact.class);
        burst.invoke(body,impact());assertEquals(1,allowed.kills);assertEquals(0,friend.kills);
        assertEquals(0,outside.kills);assertEquals(0,added.kills);assertEquals(1,world.effect.projections);assertEquals(1,world.hugeBursts);
        allowed.kills=0;world.cancelBlast=true;burst.invoke(body,impact());assertEquals(0,allowed.kills);assertEquals(1,world.hugeBursts);
        world.cancelBlast=false;world.mutateEnemy=true;burst.invoke(body,impact());assertEquals(0,allowed.kills);
    }
    @Test void twelveBlockExecutionBoundaryAndSixteenBlockFlat325DamageRemainSeparateFromTerrainPermission() throws Exception {
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;world.modify=false;
        var inner=new View();inner.bounds=inner.bounds.move(12,0,0);
        var outer=new View();outer.bounds=outer.bounds.move(12.01,0,0);
        var edge=new View();edge.bounds=edge.bounds.move(16,0,0);
        var outside=new View();outside.bounds=outside.bounds.move(16.01,0,0);
        var friend=new View();friend.enemy=false;
        world.targets.addAll(List.of(inner,outer,edge,outside,friend));
        assertEquals(true,type.getMethod("burst",NuclearSiloStore.Impact.class).invoke(body,impact()));
        assertEquals(1,inner.kills);assertEquals(0,inner.hits);
        for(var target:List.of(outer,edge)){assertEquals(0,target.kills);assertEquals(1,target.hits);assertEquals(325F,target.damage);}
        assertEquals(0,outside.hits);assertEquals(0,outside.kills);assertEquals(0,friend.hits);assertEquals(0,friend.kills);
        assertEquals(0,world.writes);assertEquals(1,world.hugeBursts);
    }
    @Test void terminalTerrainIsRadius16ProtectedAndCannotBeExpandedOrRunWithGriefingDisabled() throws Exception {
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;var impact=impact();
        var center=BlockPos.ZERO.above(2);var edge=center.offset(16,0,0);var outside=center.offset(17,0,0);
        var state=new State(false,1,false,new Block());
        for(var pos:List.of(center,edge,outside))world.cells.put(pos,state);
        var eligible=type.getMethod("blastEligible",NuclearSiloStore.Impact.class,BlockPos.class);
        assertEquals(true,eligible.invoke(body,impact,edge));assertEquals(false,eligible.invoke(body,impact,outside));
        var terrain=type.getMethod("blastTerrain",NuclearSiloStore.Impact.class,List.class);
        var bus=(TestBus)BusHolder.EVENT_BUS;bus.world=world;bus.cancel=true;bus.mutate=false;
        terrain.invoke(body,impact,List.of(center,edge));assertEquals(0,world.writes);
        bus.cancel=false;world.modify=false;terrain.invoke(body,impact,List.of(center,edge));assertEquals(0,world.writes);
        world.modify=true;world.inject=true;terrain.invoke(body,impact,List.of(center,edge));
        assertEquals(2,world.writes);assertSame(state,world.cells.get(outside));
        world.cells.put(center,new State(false,1,true,new Block()));assertEquals(false,eligible.invoke(body,impact,center));
    }
    NuclearSiloStore.Impact captured(NuclearSiloStore.Impact impact,View... targets){
        var progress=com.liucodex.doggiesnewvehicle.artillery.silo.VacuumPayloadProgress.EMPTY.start(0).tick(1)
                .capture(Arrays.stream(targets).map(View::getUUID).toList());
        return new NuclearSiloStore.Impact(impact.id(),impact.actor(),impact.position(),impact.progress(),impact.kind(),progress);
    }
    @Test void actualPullReturnsOnlyMovedEnemiesAndHonorsBothTheTickAndCaptureBudgets()throws Exception{
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;
        var ordinary=new View();ordinary.bounds=ordinary.bounds.move(20,-10,0);
        var vehicle=new Vehicle();vehicle.bounds=vehicle.bounds.move(-10,-8,0);
        var friend=new View();friend.enemy=false;var outside=new View();outside.bounds=outside.bounds.move(33,0,0);
        world.targets.addAll(List.of(ordinary,vehicle,friend,outside));
        var pull=type.getMethod("pull",NuclearSiloStore.Impact.class,int.class);
        var impact=impact();var result=(List<?>)pull.invoke(body,impact,1);assertEquals(List.of(ordinary.id),result);
        assertTrue(ordinary.motion.x<0);assertTrue(ordinary.motion.y>0);assertEquals(1,ordinary.velocityWrites);
        result=(List<?>)pull.invoke(body,impact,512);assertEquals(List.of(ordinary.id,vehicle.id),result);
        assertEquals(1,vehicle.pushes);assertEquals(0,friend.velocityWrites);assertEquals(0,outside.velocityWrites);
        var full=new ArrayList<UUID>();full.add(ordinary.id);for(int i=1;i<512;i++)full.add(new UUID(0,i));
        var progress=com.liucodex.doggiesnewvehicle.artillery.silo.VacuumPayloadProgress.EMPTY.start(0).tick(1).capture(full);
        var capped=new NuclearSiloStore.Impact(impact.id(),impact.actor(),impact.position(),impact.progress(),impact.kind(),progress);
        int pushes=vehicle.pushes;assertEquals(List.of(ordinary.id),pull.invoke(body,capped,512));assertEquals(pushes,vehicle.pushes);
    }
    @Test void capturedTallEnemyAtPitBottomAlignsItsBoundingCenterThenReceivesTheOneTerminalHit()throws Exception{
        var type=target();var body=(Body)type.getConstructor().newInstance();var world=body.level;
        var pit=new View();pit.bounds=new net.minecraft.world.phys.AABB(-4,-40,-4,4,0,4);
        pit.motion=new net.minecraft.world.phys.Vec3(0,-1,0);pit.fallDistance=20;
        var untracked=new View();untracked.bounds=untracked.bounds.move(0,-25,0);world.targets.addAll(List.of(pit,untracked));
        world.modify=false;var bus=(TestBus)BusHolder.EVENT_BUS;bus.cancelTeleport=false;bus.redirect=null;bus.mutateTeleportEnemy=false;
        assertEquals(true,type.getMethod("burst",NuclearSiloStore.Impact.class).invoke(body,captured(impact(),pit)));
        assertEquals(1,pit.teleports);assertEquals(1,pit.kills);assertEquals(0,pit.hits);
        assertEquals(new net.minecraft.world.phys.Vec3(.5,2.5,.5),pit.bounds.getCenter());
        assertEquals(net.minecraft.world.phys.Vec3.ZERO,pit.motion);assertEquals(0,pit.fallDistance);assertTrue(pit.hurtMarked);
        assertEquals(0,untracked.teleports);assertEquals(0,untracked.kills);assertEquals(0,world.writes);
    }
    @Test void explosionProtectionAndChangedEnemyStatusPreventBothRecallAndOutOfRangeDamage()throws Exception{
        var type=target();var burst=type.getMethod("burst",NuclearSiloStore.Impact.class);
        for(int mode=0;mode<5;mode++){
            var body=(Body)type.getConstructor().newInstance();var world=body.level;var pit=new View();pit.bounds=pit.bounds.move(0,-30,0);
            world.targets.add(pit);world.cancelBlast=mode==0;world.excludeAll=mode==1;pit.enemy=mode!=2;world.mutateEnemy=mode==3;world.foreign=mode==4;
            burst.invoke(body,captured(impact(),pit));assertEquals(0,pit.teleports);assertEquals(0,pit.kills);
        }
    }
    @Test void cancelledRedirectedBlockedUnloadedAndOccupiedRecallsDoNotMoveTheTarget()throws Exception{
        var type=target();var burst=type.getMethod("burst",NuclearSiloStore.Impact.class);var bus=(TestBus)BusHolder.EVENT_BUS;
        for(int mode=0;mode<8;mode++){
            var body=(Body)type.getConstructor().newInstance();var world=body.level;var pit=new View();pit.bounds=pit.bounds.move(0,-30,0);
            world.targets.add(pit);bus.cancelTeleport=mode==0;bus.redirect=mode==1?new net.minecraft.world.phys.Vec3(100,2,0):null;
            world.clear=mode!=2;world.loaded=mode!=3;pit.riders=mode==4;world.border.allowed=mode!=5;bus.mutateTeleportEnemy=mode==6;
            bus.foreignTeleport=mode==7;
            burst.invoke(body,captured(impact(),pit));assertEquals(0,pit.teleports);assertEquals(0,pit.kills);assertEquals(0,pit.hits);
        }
        bus.cancelTeleport=false;bus.redirect=null;bus.mutateTeleportEnemy=false;bus.foreignTeleport=false;
    }
    @Test void nativeTeleportRefusalCannotBeConvertedIntoOutOfRangeDamageOrMovementReset()throws Exception{
        var type=target();var body=(Body)type.getConstructor().newInstance();var pit=new View();pit.bounds=pit.bounds.move(0,-30,0);
        pit.refuseTeleport=true;pit.motion=new net.minecraft.world.phys.Vec3(0,-1,0);pit.fallDistance=20;body.level.targets.add(pit);
        type.getMethod("burst",NuclearSiloStore.Impact.class).invoke(body,captured(impact(),pit));
        assertEquals(1,pit.teleports);assertEquals(0,pit.kills);assertEquals(0,pit.hits);assertEquals(20,pit.fallDistance);
        assertEquals(new net.minecraft.world.phys.Vec3(0,-1,0),pit.motion);
    }
    public static class Body {
        public World level=new World();
        public boolean mayModify(NuclearSiloStore.Impact i){return level.modify;}
        public Actor actor(NuclearSiloStore.Impact i){return new Actor();}
        public Blast explosion(NuclearSiloStore.Impact i){level.visualId=i.id();return new Blast();}
    }
    public static class World {
        public final Map<BlockPos,State> cells=new HashMap<>();public final Border border=new Border();
        public final Effect effect=new Effect();public boolean loaded=true,clear=true,storage,modify=true,success=true,inject;public int writes,hugeBursts;
        public final List<View> targets=new ArrayList<>();public View injected;public boolean cancelBlast,mutateEnemy,excludeAll,foreign;public UUID visualId;
        public List<View> getEntities(View ignored,net.minecraft.world.phys.AABB box,java.util.function.Predicate<View> filter){
            for(var target:targets)if(target.owner==null)target.owner=this;
            return targets.stream().filter(filter).toList();}
        public boolean hasChunkAt(BlockPos p){return loaded;}public boolean isOutsideBuildHeight(BlockPos p){return p.getY()<-64||p.getY()>=320;}
        public boolean hasChunksAt(BlockPos a,BlockPos b){return loaded;}public boolean noCollision(View target,net.minecraft.world.phys.AABB box){return clear;}
        public Border getWorldBorder(){return border;}public Object getBlockEntity(BlockPos p){return storage?new Object():null;}
        public State getBlockState(BlockPos p){return cells.getOrDefault(p,Blocks.AIR.defaultBlockState());}
        public boolean mayInteract(Actor a,BlockPos p){return true;}
        public boolean setBlock(BlockPos p,State s,int flags){assertEquals(3,flags);if(success){writes++;cells.put(p,s);}return success;}
        public View getEntity(UUID id){var entity=targets.stream().filter(e->e.id.equals(id)).findFirst().orElse(id.equals(visualId)?effect:null);
            if(entity!=null&&entity.owner==null)entity.owner=this;return entity;}
    }
    public static class Particles {public static void spawnHugeExplosionParticles(World w,net.minecraft.world.phys.Vec3 p){w.hugeBursts++;}}
    public static class Actor extends View {
        @Override public Team getTeam(){return new Team(com.liucodex.doggiesnewvehicle.compat.DnvDefaultTeams.PLAYERS);}
    }
    public static class Damage {public static Damage blast(World w,View a,net.minecraft.world.phys.Vec3 p){return new Damage();}}
    public static class Border {public boolean allowed=true;public boolean isWithinBounds(BlockPos p){return allowed;}
        public boolean isWithinBounds(net.minecraft.world.phys.AABB box){return allowed;}}
    public static class Block {public float getExplosionResistance(){return 1;}public State defaultBlockState(){return new State(true,1,false,this);}}
    public static class Liquid extends Block { }
    public static class Wet extends Block { }
    public record Fluid(boolean wet){public boolean isEmpty(){return !wet;}}
    public static class Blocks {public static final Block AIR=new Block();}
    public record State(boolean air,float hardness,boolean storage,Block block){
        public void onBlockExploded(World world,BlockPos pos,Blast blast){world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
        public boolean isAir(){return air;}public float getDestroySpeed(World w,BlockPos p){return hardness;}
        public boolean hasBlockEntity(){return storage;}public Block getBlock(){return block;}
        public Fluid getFluidState(){return new Fluid(block instanceof Wet||block instanceof Liquid);}
    }
    public interface Hostile { }
    public record Team(String name){public String getName(){return name;}}
    public static class Seek {public static final java.util.function.BiPredicate<View,View> IS_FRIENDLY=(a,b)->false;}
    public static class Protection {public static boolean isProtectedPlayerTarget(View e){return e.protectedTarget;}}
    public static class View {public boolean enemy=true,neutral,protectedTarget;public int kills,hits;public float damage;
        public final UUID id=UUID.randomUUID();public boolean riders,hurtMarked,refuseTeleport;public float fallDistance;public int teleports,velocityWrites;public World owner;
        public final List<View> occupants=new ArrayList<>();
        public net.minecraft.world.phys.Vec3 motion=net.minecraft.world.phys.Vec3.ZERO;
        public net.minecraft.world.phys.AABB bounds=new net.minecraft.world.phys.AABB(0,2,0,1,3,1);
        public UUID getUUID(){return id;}public boolean isVehicle(){return riders||!occupants.isEmpty();}
        public boolean isAlive(){return kills==0;}public boolean isRemoved(){return false;}
        public boolean isSpectator(){return false;}public boolean isPassenger(){return false;}
        public Team getTeam(){return neutral?null:new Team(enemy?com.liucodex.doggiesnewvehicle.compat.DnvDefaultTeams.GUARDIANS:
                com.liucodex.doggiesnewvehicle.compat.DnvDefaultTeams.PLAYERS);}
        public boolean isAlliedTo(View other){return getTeam()!=null&&other.getTeam()!=null&&getTeam().name.equals(other.getTeam().name);}
        public java.util.stream.Stream<View> getSelfAndPassengers(){return java.util.stream.Stream.concat(java.util.stream.Stream.of(this),occupants.stream());}
        public World level(){return owner;}
        public net.minecraft.world.phys.Vec3 position(){return new net.minecraft.world.phys.Vec3(bounds.getCenter().x,bounds.minY,bounds.getCenter().z);}
        public net.minecraft.world.phys.Vec3 getDeltaMovement(){return motion;}
        public void setDeltaMovement(double x,double y,double z){motion=new net.minecraft.world.phys.Vec3(x,y,z);velocityWrites++;}
        public void setDeltaMovement(net.minecraft.world.phys.Vec3 value){motion=value;velocityWrites++;}
        public void teleportTo(double x,double y,double z){teleports++;if(!refuseTeleport)bounds=bounds.move(new net.minecraft.world.phys.Vec3(x,y,z).subtract(position()));}
        public net.minecraft.world.phys.AABB getBoundingBox(){return bounds;}public void kill(){kills++;}public boolean hurt(Damage source,float amount){hits++;damage=amount;return true;}}
    public static class Vehicle extends View {public int pushes;public void pushNew(double x,double y,double z){motion=motion.add(x,y,z);pushes++;}}
    public static class OwnedVehicle extends Vehicle {public UUID ownerId;public Optional<UUID> getOptionalOwnerUUID(){return Optional.ofNullable(ownerId);}}
    public static class Effect extends View {public final List<Debris> debris=new ArrayList<>();public int projections;
        public void publishDebris(List<Debris> d){debris.addAll(d);}public void projectAge(int age){projections++;}}
    public record Debris(State state,BlockPos position,int age) { }
    public static class Blast {public final List<BlockPos> blocks=new ArrayList<>();public List<BlockPos> getToBlow(){return blocks;}}
    public static class Hooks {
        public static boolean onExplosionStart(World w,Blast b){return w.cancelBlast;}
        public static void onExplosionDetonate(World w,Blast b,List<View> entities,double radius){
            if(w.excludeAll)entities.clear();
            if(w.inject){b.blocks.add(BlockPos.ZERO);b.blocks.add(new BlockPos(1,0,0));}
            if(w.injected!=null){if(!entities.isEmpty())entities.add(entities.get(0));entities.add(w.injected);}
            if(w.mutateEnemy&&!entities.isEmpty())entities.get(0).enemy=false;
            if(w.foreign&&!entities.isEmpty())entities.get(0).owner=new World();
        }}
    public static class Event {public boolean canceled;public boolean isCanceled(){return canceled;}}
    public static class Break extends Event {public final BlockPos position;public Break(World w,BlockPos p,State s,Actor a){position=p;}}
    public static class Teleport extends Event {public final View entity;public net.minecraft.world.phys.Vec3 target;
        public Teleport(View entity,double x,double y,double z){this.entity=entity;target=new net.minecraft.world.phys.Vec3(x,y,z);}
        public net.minecraft.world.phys.Vec3 getTarget(){return target;}}
    public interface Bus {boolean post(Event event);Event postNeo(Event event);}
    public static class TestBus implements Bus {public World world;public boolean cancel,mutate,cancelTeleport,mutateTeleportEnemy,foreignTeleport;public net.minecraft.world.phys.Vec3 redirect;
        public boolean post(Event event){
            if(event instanceof Teleport e){e.canceled=cancelTeleport;if(redirect!=null)e.target=redirect;
                if(mutateTeleportEnemy)e.entity.enemy=false;if(foreignTeleport)e.entity.owner=new World();}
            else if(event instanceof Break e){e.canceled=cancel;if(mutate)world.cells.put(e.position,new State(false,2,false,new Block()));}
            return event.canceled;}
        public Event postNeo(Event e){post(e);return e;}}
    public static class BusHolder {public static final Bus EVENT_BUS=new TestBus();}
}
