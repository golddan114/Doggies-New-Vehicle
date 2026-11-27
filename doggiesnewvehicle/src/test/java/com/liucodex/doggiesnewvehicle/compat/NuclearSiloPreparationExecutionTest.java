package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Current compiled entry, permission, rack and fire methods; native storage/world are explicit ports. */
class NuclearSiloPreparationExecutionTest {
    @Test void creativeItemStartsAnimationOnceOnTheServerWithoutSkippingResourceOrFireGates() throws Exception {
        var f=new Fixture();f.body.energy=0;
        f.world.isClientSide=true;var empty=f.body.cycle.snapshot();
        assertEquals(InteractionResult.SUCCESS,f.use());assertEquals(empty,f.body.cycle.snapshot());assertEquals(0,f.world.created);
        f.world.isClientSide=false;assertEquals(InteractionResult.CONSUME,f.use());
        assertEquals(SiloCycle.Phase.RELOADING,f.body.cycle.snapshot().phase());assertEquals(1720,f.body.cycle.snapshot().remaining());
        assertEquals(1,f.world.created);assertTrue(f.body.publishes>0);int published=f.body.publishes;UUID rack=f.body.rackId;
        assertEquals(2,f.body.ammo);assertEquals(0,f.body.energy);assertFalse(f.fire());
        f.body.cycle.tick(true);assertEquals(InteractionResult.CONSUME,f.use());assertEquals(1719,f.body.cycle.snapshot().remaining());
        assertEquals(rack,f.body.rackId);assertEquals(1,f.world.created);assertEquals(published,f.body.publishes);
        for(int i=0;i<1719;i++)f.body.cycle.tick(true);
        assertEquals(InteractionResult.CONSUME,f.use());assertEquals(SiloCycle.Phase.READY,f.body.cycle.snapshot().phase());
        assertFalse(f.fire());f.body.energy=NuclearRules.DEFAULT.shotFE();assertTrue(f.fire());
        assertEquals(2,f.body.ammo);assertEquals(NuclearRules.DEFAULT.shotFE(),f.body.energy);
    }
    @Test void creativeItemRejectsSurvivalForeignLockedDamagedEmptyAndBusyFacilities() throws Exception {
        var f=new Fixture();
        f.actor.creative=false;assertEquals(InteractionResult.FAIL,f.use());f.actor.creative=true;
        f.actor.spectator=true;assertEquals(InteractionResult.FAIL,f.use());f.actor.spectator=false;
        f.actor.alive=false;assertEquals(InteractionResult.FAIL,f.use());f.actor.alive=true;
        f.actor.world=new World();assertEquals(InteractionResult.FAIL,f.use());f.actor.world=f.world;
        f.body.owner=UUID.randomUUID();assertEquals(InteractionResult.FAIL,f.use());f.body.owner=f.actor.id;
        f.body.locked=true;assertEquals(InteractionResult.FAIL,f.use());f.body.locked=false;
        f.body.health=0;assertEquals(InteractionResult.FAIL,f.use());f.body.health=800;
        f.body.wreck=true;assertEquals(InteractionResult.FAIL,f.use());f.body.wreck=false;
        f.body.invalidData=new CompoundTag();assertEquals(InteractionResult.FAIL,f.use());f.body.invalidData=null;
        f.body.removed=true;assertEquals(InteractionResult.FAIL,f.use());f.body.removed=false;
        f.body.ammo=0;assertEquals(InteractionResult.FAIL,f.use());f.body.ammo=2;
        for(var phase:List.of(SiloCycle.Phase.OPENING,SiloCycle.Phase.RETURNING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING)){
            var state=new SiloCycle.Snapshot(phase,1,UUID.randomUUID(),new SiloCycle.Target(100,64,0));f.body.cycle.restore(state);
            assertEquals(InteractionResult.FAIL,f.use());assertEquals(state,f.body.cycle.snapshot());
        }
        assertEquals(0,f.world.created);assertEquals(0,f.body.publishes);assertEquals(2,f.body.ammo);
    }
    @Test void creativeOwnerCompletesTheExistingRoundOnceAndStillNeedsLaunchPower() throws Exception {
        var f=new Fixture();f.body.energy=0;
        assertEquals(1,f.dispatcher.execute("dnv nuclear ready",new Source(f.actor)));
        assertEquals(SiloCycle.Phase.READY,f.body.cycle.snapshot().phase());assertEquals(2,f.body.ammo);assertEquals(0,f.body.energy);
        assertEquals(1,f.world.created);assertEquals(1,f.world.added);UUID rack=f.body.rackId;int published=f.body.publishes;
        assertEquals(1,f.dispatcher.execute("dnv nuclear ready",new Source(f.actor)));
        assertEquals(rack,f.body.rackId);assertEquals(1,f.world.created);assertEquals(published,f.body.publishes);
        assertFalse(f.fire());assertEquals(SiloCycle.Phase.READY,f.body.cycle.snapshot().phase());
        f.body.energy=NuclearRules.DEFAULT.shotFE();assertTrue(f.fire());
        assertEquals(SiloCycle.Phase.OPENING,f.body.cycle.snapshot().phase());assertEquals(rack,f.body.cycle.snapshot().launch());
        assertEquals(2,f.body.ammo);assertEquals(NuclearRules.DEFAULT.shotFE(),f.body.energy);
    }
    @Test void commandAndFacilityBothRejectNonCreativeForeignLockedDeadMissingAndBusyAccess() throws Exception {
        var f=new Fixture();
        assertThrows(CommandSyntaxException.class,()->f.dispatcher.execute("dnv nuclear ready",new Source(null)));
        f.actor.creative=false;assertThrows(CommandSyntaxException.class,()->f.dispatcher.execute("dnv nuclear ready",new Source(f.actor)));
        var complete=f.body.getClass().getMethod("completeCreativePreparation",Actor.class);
        assertEquals(false,complete.invoke(f.body,f.actor));f.actor.creative=true;
        f.actor.spectator=true;assertEquals(false,complete.invoke(f.body,f.actor));f.actor.spectator=false;
        f.actor.alive=false;assertEquals(false,complete.invoke(f.body,f.actor));f.actor.alive=true;
        f.actor.world=new World();assertEquals(false,complete.invoke(f.body,f.actor));f.actor.world=f.world;
        f.body.owner=UUID.randomUUID();assertEquals(0,f.dispatcher.execute("dnv nuclear ready",new Source(f.actor)));f.body.owner=f.actor.id;
        f.body.locked=true;assertEquals(false,complete.invoke(f.body,f.actor));f.body.locked=false;
        f.body.health=0;assertEquals(false,complete.invoke(f.body,f.actor));f.body.health=800;
        f.body.ammo=0;assertEquals(false,complete.invoke(f.body,f.actor));f.body.ammo=2;
        f.world.entities.remove(f.body.id);assertEquals(0,f.dispatcher.execute("dnv nuclear ready",new Source(f.actor)));
        f.world.entities.put(f.body.id,f.body);
        for(var phase:List.of(SiloCycle.Phase.OPENING,SiloCycle.Phase.RETURNING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING)){
            var state=new SiloCycle.Snapshot(phase,1,UUID.randomUUID(),new SiloCycle.Target(100,64,0));f.body.cycle.restore(state);
            assertEquals(0,f.dispatcher.execute("dnv nuclear ready",new Source(f.actor)));assertEquals(state,f.body.cycle.snapshot());
        }
        assertEquals(0,f.world.created);assertEquals(0,f.body.publishes);assertEquals(2,f.body.ammo);
    }
    @Test void cancelledRackStaysOwnedAndRegisteredForTheWholeReturnThenCanBeRetried() throws Exception {
        var f=new Fixture();f.dispatcher.execute("dnv nuclear ready",new Source(f.actor));assertTrue(f.fire());
        for(int i=0;i<NuclearRules.DEFAULT.openTicks();i++)f.body.cycle.tick(true);
        f.body.cycle.launched(false);UUID rack=f.body.rackId;var missile=(Missile)f.world.getEntity(rack);
        var owns=f.body.getClass().getMethod("ownsRack",UUID.class);
        var update=f.body.getClass().getMethod("updateRack",World.class);
        for(int i=0;i<NuclearRules.DEFAULT.openTicks();i++){
            assertEquals(true,owns.invoke(f.body,rack));update.invoke(f.body,f.world);
            assertSame(missile,f.world.getEntity(rack));assertFalse(missile.removed);f.body.cycle.tick(true);
        }
        assertEquals(SiloCycle.Phase.READY,f.body.cycle.snapshot().phase());assertEquals(rack,f.body.rackId);
        assertEquals(1,f.world.created);assertEquals(2,f.body.ammo);assertEquals(NuclearRules.DEFAULT.shotFE(),f.body.energy);
        assertTrue(f.fire());assertEquals(rack,f.body.cycle.snapshot().launch());
    }
    @Test void anEmptiedReturnCannotPresentReplacementStockAsTheAlreadyLoadedRound() throws Exception {
        var f=new Fixture();f.dispatcher.execute("dnv nuclear ready",new Source(f.actor));assertTrue(f.fire());
        for(int i=0;i<NuclearRules.DEFAULT.openTicks();i++)f.body.cycle.tick(true);
        f.body.cycle.launched(false);UUID old=f.body.rackId;var missile=(Missile)f.world.getEntity(old);
        var update=f.body.getClass().getMethod("updateRack",World.class);
        f.body.ammo=0;f.body.cycle.tick(false);update.invoke(f.body,f.world);
        assertTrue(missile.removed);assertNull(f.body.rackId);
        assertEquals(false,f.body.getClass().getMethod("ownsRack",UUID.class).invoke(f.body,old));
        f.body.ammo=1;
        for(int i=0;i<NuclearRules.DEFAULT.openTicks()-1;i++){update.invoke(f.body,f.world);f.body.cycle.tick(true);}
        assertEquals(SiloCycle.Phase.EMPTY,f.body.cycle.snapshot().phase());assertNull(f.body.rackId);assertEquals(1,f.world.created);
        f.body.cycle.tick(true);assertEquals(6000,f.body.cycle.snapshot().remaining());assertFalse(f.fire());
    }
    @Test void unsetTargetRejectsButExplicitZeroCoordinatesAreAcceptedBeforeClearance() throws Exception {
        var f=new Fixture();f.dispatcher.execute("dnv nuclear ready",new Source(f.actor));
        f.body.indicatorTarget=null;
        var fire=f.body.getClass().getMethod("requestIndicatorFire",Actor.class,BlockPos.class,int.class,boolean.class);
        assertEquals(false,fire.invoke(f.body,f.actor,BlockPos.ZERO,0,false));
        assertEquals("message.doggiesnewvehicle.nuclear.indicator_target",
                f.body.getClass().getMethod("indicatorRejectionKey",boolean.class).invoke(f.body,true));
        var set=f.body.getClass().getMethod("setIndicatorTarget",Actor.class,BlockPos.class,int.class,boolean.class);
        assertEquals(true,set.invoke(f.body,f.actor,BlockPos.ZERO,0,false));
        assertEquals(true,fire.invoke(f.body,f.actor,BlockPos.ZERO,0,false));
        assertEquals(new SiloCycle.Target(0,0,0),f.body.cycle.snapshot().target());
        assertEquals(SiloCycle.Phase.OPENING,f.body.cycle.snapshot().phase());
        assertEquals(2,f.body.ammo);assertEquals(NuclearRules.DEFAULT.shotFE(),f.body.energy);
    }
    private static class Fixture {
        final World world=new World();final Actor actor=new Actor(world);final Body body;
        final CommandDispatcher<Source> dispatcher=new CommandDispatcher<>();
        final java.lang.reflect.Method use;
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/border/WorldBorder",Border.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerPlayer",Actor.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/player/Player",Actor.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Entity.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/EntityType",Kind.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Registry.class);
            NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Registry.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryEntities",Factories.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Missile.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloStore",Store.class);
            var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",
                    Set.of("startCreativePreparation","creativePreparationAllowed","completeCreativePreparation","canBindIndicator","mechanicsRunning","ownsRack","rack","updateRack","requestIndicatorFire",
                            "setIndicatorTarget","validTarget","point","indicatorRejectionKey"),
                    new HashMap<>(names),Body.class);
            body=(Body)type.getConstructor().newInstance();body.world=world;body.owner=actor.id;world.store.facility=body.id;world.entities.put(body.id,body);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",Body.class);
            var item=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearPreparationRemoteItem",
                    Set.of("useOnSilo"),new HashMap<>(names),null);
            use=item.getMethod("useOnSilo",Body.class,Actor.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/commands/CommandSourceStack",Source.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/commands/Commands",Commands.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/event/RegisterCommandsEvent",Event.class);
            NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/event/RegisterCommandsEvent",Event.class);
            var commands=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloCommands",
                    Set.of("register","creativeSource","ready"),names,null);
            commands.getMethod("register",CommandDispatcher.class).invoke(null,dispatcher);
        }
        boolean fire() throws Exception {return (boolean)body.getClass().getMethod("requestIndicatorFire",Actor.class,BlockPos.class,int.class,boolean.class)
                .invoke(body,actor,body.indicatorTarget,0,false);}
        InteractionResult use() throws Exception {return (InteractionResult)use.invoke(null,body,actor);}
    }
    public static class Entity {public UUID id=UUID.randomUUID();public boolean removed;public UUID getUUID(){return id;}public boolean isRemoved(){return removed;}}
    public static class Actor extends Entity {
        public World world;public boolean creative=true,spectator,alive=true;public List<Component> messages=new ArrayList<>();
        public Actor(World world){this.world=world;}public World level(){return world;}public World serverLevel(){return world;}
        public boolean isCreative(){return creative;}public boolean isSpectator(){return spectator;}public boolean isAlive(){return alive;}
        public void displayClientMessage(Component message,boolean overlay){messages.add(message);}
    }
    public static class Body extends Entity {
        public boolean dogEgg(){return false;}
        public SiloPayloadKind payloadKind(){return SiloPayloadKind.NUCLEAR;}
        public final SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);public World world;public UUID owner,rackId;
        public int ammo=2,energy=NuclearRules.DEFAULT.shotFE(),rackLoadTicks,publishes;public boolean locked,wreck;public float health=800;
        public CompoundTag invalidData;public BlockPos indicatorTarget=new BlockPos(100,64,0);
        public World level(){return world;}public boolean getLocked(){return locked;}public boolean isWreck(){return wreck;}public float getHealth(){return health;}
        public Optional<UUID> getOptionalOwnerUUID(){return Optional.ofNullable(owner);}public int ammoSlot(){return ammo>0?0:-1;}
        public int getEnergy(){return energy;}public float getYRot(){return 0;}public Vec3 position(){return new Vec3(.5,64,.5);}
        public void publish(){publishes++;}public Component statusMessage(){return Component.literal("status");}
        public static void announceLaunch(World world){world.announcements++;}
        public static void announceLaunch(World world,SiloPayloadKind kind){world.announcements++;}
        public boolean completeCreativePreparation(Actor actor){throw new AssertionError("Execute the compiled production method");}
        public boolean startCreativePreparation(Actor actor){throw new AssertionError("Execute the compiled production method");}
    }
    public static class World {
        public boolean isClientSide;
        public boolean isOutsideBuildHeight(BlockPos target){return target.getY()<-64||target.getY()>=320;}
        public Border getWorldBorder(){return new Border();}
        public final Store store=new Store();public final Map<UUID,Entity> entities=new HashMap<>();public int created,added,announcements;
        public Entity getEntity(UUID id){return entities.get(id);}public boolean addFreshEntity(Entity entity){entities.put(entity.id,entity);added++;return true;}
    }
    public static class Border {public boolean isWithinBounds(BlockPos target){return Math.abs((long)target.getX())<30000000&&Math.abs((long)target.getZ())<30000000;}}
    public static class Store {public UUID facility;public static Store get(World world){return world.store;}public UUID facility(){return facility;}
        public boolean committed(UUID id){return false;}}
    public static class Missile extends Entity {
        public SiloPayloadKind payloadKind(){return SiloPayloadKind.NUCLEAR;}
        public UUID silo;public Vec3 position;public boolean belongsToRack(UUID id){return id.equals(silo);}
        public void initializeRack(UUID id,UUID actor,UUID facility,Vec3 origin,float yaw){this.id=id;silo=facility;position=origin;}
        public void discard(){removed=true;}public void setPos(Vec3 value){position=value;}public void setYRot(float value){}public void setXRot(float value){}
    }
    public static class Registry {public Object get(){return new Kind();}}
    public static class Factories {public static final Registry NUCLEAR_MISSILE=new Registry();}
    public static class Kind {public Entity create(World world){world.created++;return new Missile();}}
    public record Source(Entity entity) {
        public Entity getEntity(){return entity;}public Actor getPlayerOrException(){return (Actor)entity;}public void sendFailure(Component message){}
    }
    public static class Commands {public static LiteralArgumentBuilder<Source> literal(String name){return LiteralArgumentBuilder.literal(name);}}
    public record Event(CommandDispatcher<Source> dispatcher){public CommandDispatcher<Source> getDispatcher(){return dispatcher;}}
}
