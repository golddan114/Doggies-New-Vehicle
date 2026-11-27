package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes production loaded-block selection and native callback/mutation ordering with world ports. */
class NuclearCleanupExecutionTest {
    final BlockPos center=new BlockPos(-17,64,-33);
    final com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore.Impact impact=
        new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore.Impact(UUID.randomUUID(),UUID.randomUUID(),center,
                new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadProgress(true,0,0,0,28,28));
    final com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase terrain=
        com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase.TERRAIN;
    final com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase fluid=
        com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase.FLUID;
    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.World adapter(World world)throws Exception {
        return adapter(world,null);
    }
    @FunctionalInterface interface RadiationProbe {void apply(Living target,Entity actor,int duration);}
    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.World adapter(World world,RadiationProbe radiation)throws Exception {
        String source="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearPayloadWorld",target="com/liucodex/doggiesnewvehicle/compat/executionprobe/NuclearWorld";
        Map<String,String> names=new HashMap<>();names.put(source,target);
        String radiationSource="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearRadiationHandler",radiationTarget=target+"RadiationHandler";
        names.put(radiationSource,radiationTarget);
        String scentSource="com/liucodex/doggiesnewvehicle/artillery/compat/DnvSporeScent";
        names.put(scentSource,target+"Scent");
        String cloudSource="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMushroomCloud";
        names.put(cloudSource,target+"Cloud");
        String shapeSource="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearCraterShape";
        names.put(shapeSource,target+"Shape");
        Map.ofEntries(Map.entry("server/level/ServerLevel",World.class),Map.entry("world/level/Level",World.class),
            Map.entry("server/level/ServerPlayer",Player.class),Map.entry("core/particles/ParticleOptions",Smoke.class),
            Map.entry("core/particles/SimpleParticleType",Smoke.class),Map.entry("core/particles/ParticleTypes",Particles.class),
            Map.entry("world/level/BlockGetter",World.class),Map.entry("world/entity/Entity",Entity.class),
            Map.entry("world/entity/player/Player",Player.class),
            Map.entry("world/level/block/state/BlockState",State.class),Map.entry("world/level/block/state/BlockBehaviour$BlockStateBase",State.class),
            Map.entry("world/level/block/Block",Block.class),Map.entry("world/level/block/LiquidBlock",Liquid.class),
            Map.entry("world/level/block/Blocks",Catalog.class),Map.entry("world/level/material/FluidState",Fluid.class),
            Map.entry("world/level/block/state/properties/Property",Property.class),Map.entry("world/level/block/state/properties/BooleanProperty",Property.class),
            Map.entry("world/level/block/state/properties/BlockStateProperties",Properties.class),
            Map.entry("world/level/GameRules",Rules.class),Map.entry("world/level/GameRules$Key",Key.class),
            Map.entry("world/level/Explosion",Blast.class),Map.entry("world/level/border/WorldBorder",Border.class))
            .forEach((name,type)->names.put("net/minecraft/"+name,Type.getInternalName(type)));
        names.put("net/minecraftforge/event/ForgeEventFactory",Type.getInternalName(Hooks.class));
        names.put("net/neoforged/neoforge/event/EventHooks",Type.getInternalName(Hooks.class));
        names.put("net/minecraft/world/entity/LivingEntity",Type.getInternalName(Living.class));
        names.put("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Type.getInternalName(Vehicle.class));
        names.put("net/minecraft/world/entity/EntityType",Type.getInternalName(Kind.class));
        names.put("net/minecraft/core/registries/BuiltInRegistries",Type.getInternalName(RegistryCatalog.class));
        names.put("net/minecraft/core/Registry",Type.getInternalName(EntityRegistry.class));
        names.put("net/minecraft/core/DefaultedRegistry",Type.getInternalName(EntityRegistry.class));
        names.put("net/minecraft/resources/ResourceLocation",Type.getInternalName(Id.class));
        names.put("net/minecraft/world/damagesource/DamageSource",Type.getInternalName(Damage.class));
        names.put("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Type.getInternalName(Hooks.class));
        names.put("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearDamage",Type.getInternalName(DamagePort.class));
        names.put("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearEffects",Type.getInternalName(Effects.class));
        names.put("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearResidualFire",Type.getInternalName(Fire.class));
        names.put("net/minecraft/world/effect/MobEffects",Type.getInternalName(Effects.class));
        names.put("net/minecraft/world/effect/MobEffectInstance",Type.getInternalName(EffectPacket.class));
        names.put("net/minecraft/world/effect/MobEffect",Type.getInternalName(EffectKind.class));
        names.put("net/minecraft/core/Holder",Type.getInternalName(EffectKind.class));
        names.put("net/minecraftforge/registries/RegistryObject",Type.getInternalName(Ref.class));
        names.put("net/neoforged/neoforge/registries/DeferredHolder",Type.getInternalName(Ref.class));
        names.put("com/liucodex/doggiesnewvehicle/artillery/DnvArtillerySounds",Type.getInternalName(Sounds.class));
        names.put("net/minecraft/sounds/SoundEvent",Type.getInternalName(Tone.class));
        names.put("com/atsuishio/superbwarfare/tools/ParticleTool$ParticleType",Type.getInternalName(Style.class));
        names.put("com/liucodex/doggiesnewvehicle/compat/DnvProjectileBlastEffects",Type.getInternalName(FX.class));
        var classes=new HashMap<String,byte[]>();
        for(String compiled:List.of(source,radiationSource,scentSource,cloudSource,shapeSource)){
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            try(var in=getClass().getResourceAsStream("/"+compiled+".class")){
                new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}),0);
            }
            classes.put(names.get(compiled),writer.toByteArray());
        }
        var loader=new ExactQueuedShotFixture.Loader(classes);
        var type=loader.loadClass(target.replace('/','.'));
        if(radiation!=null){
            var handler=loader.loadClass(radiationTarget.replace('/','.'));
            Object selected=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{handler},(proxy,method,args)->{
                assertEquals("apply",method.getName());radiation.apply((Living)args[0],(Entity)args[1],(Integer)args[2]);return null;
            });
            return (com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.World)type.getConstructor(World.class,handler).newInstance(world,selected);
        }
        return (com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.World)type.getConstructor(World.class).newInstance(world);
    }
    @Test void actualAdapterRejectsUnloadedHeightBorderAndUnbreakableBlocksBeforeMutation()throws Exception {
        var world=new World();var port=adapter(world);
        var solid=new State(new Block(),false,false,1);world.cells.put(center,solid);
        assertTrue(port.eligible(impact,center,terrain));assertFalse(port.eligible(impact,center.offset(29,0,0),terrain));
        world.unloaded.add(center);world.reads.clear();assertFalse(port.eligible(impact,center,terrain));assertTrue(world.reads.isEmpty());
        world.unloaded.clear();world.high.add(center);assertFalse(port.eligible(impact,center,terrain));assertTrue(world.reads.isEmpty());
        world.high.clear();world.border.outside.add(center);assertFalse(port.eligible(impact,center,terrain));assertTrue(world.reads.isEmpty());
        world.border.outside.clear();world.cells.put(center,new State(new Block(),false,false,-1));assertFalse(port.eligible(impact,center,terrain));
        world.cells.put(center,new State(new Liquid(),true,false,100));assertFalse(port.eligible(impact,center,terrain));
    }
    @Test void liquidSphereAcceptsSurfaceKeepsWaterloggedHostAndRemovesTrueFluidCarrier()throws Exception {
        var world=new World();var port=adapter(world);BlockPos edge=center.offset(28,0,0),wet=center.offset(1,0,0);
        world.cells.put(edge,new State(new Liquid(),true,false,100));world.cells.put(wet,new State(new Block(),true,true,1));
        assertTrue(port.eligible(impact,edge,fluid));assertFalse(port.eligible(impact,center.offset(28,1,0),fluid));
        var host=world.cells.get(wet).block;port.apply(impact,List.of(edge,wet),fluid);
        assertSame(Catalog.AIR,world.cells.get(edge).block);assertSame(host,world.cells.get(wet).block);
        assertFalse(world.cells.get(wet).wet);assertEquals(0,world.finalized);assertEquals(2,world.writes);
        assertEquals(3,world.flags);
    }
    @Test void protectionCanRemoveButCannotInjectDuplicateOrOutOfRangeBlocks()throws Exception {
        var world=new World();var port=adapter(world);BlockPos protectedPos=center.offset(1,0,0),outside=center.offset(29,0,0);
        for(var p:List.of(center,protectedPos,outside))world.cells.put(p,new State(new Block(),false,false,1));
        world.protectedPos=protectedPos;world.inject=outside;world.duplicate=center;
        port.apply(impact,List.of(center,protectedPos),terrain);
        assertEquals(List.of(center),world.broken);assertEquals(1,world.finalized);assertEquals(1,world.hooks);
        // Fluid mutation observes the same claim filter and cannot accept hook-created cleanup positions.
        world.cells.put(protectedPos,new State(new Liquid(),true,false,100));world.inject=center.offset(20,0,0);world.duplicate=null;
        port.apply(impact,List.of(protectedPos),fluid);assertEquals(0,world.writes);
    }
    @Test void mobGriefingAndNativeGriefVetoPreventCallbacksAndMutations()throws Exception {
        var world=new World();var port=adapter(world);world.cells.put(center,new State(new Block(),false,false,1));
        world.rules.allowed=false;assertFalse(port.mayModify(impact));port.apply(impact,List.of(center),terrain);
        assertEquals(0,world.hooks);assertEquals(0,world.finalized);
        world.rules.allowed=true;world.actor=new Player();world.griefVeto=true;assertFalse(port.mayModify(impact));
        port.apply(impact,List.of(center),terrain);assertEquals(0,world.hooks);assertEquals(0,world.finalized);
    }
    @Test void sbwSwitchVetoesEveryNuclearTerrainPhaseIncludingAnAlreadyPlannedBatch()throws Exception {
        var world=new World();var port=adapter(world);
        var solid=new State(new Block(),false,false,1);var water=new State(new Liquid(),true,false,100);
        var wet=center.east();var fire=center.west();
        world.cells.put(center,solid);world.cells.put(wet,water);world.fireEligible.add(fire);
        assertTrue(port.mayModify(impact));
        world.explosions=false;
        port.apply(impact,List.of(center),terrain);port.apply(impact,List.of(wet),fluid);
        port.apply(impact,List.of(fire),com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase.FIRE);
        assertSame(solid,world.cells.get(center));assertSame(water,world.cells.get(wet));
        assertEquals(0,world.hooks);assertEquals(0,world.writes);assertEquals(0,world.finalized);
        assertTrue(world.fires.isEmpty());
    }
    Living living(World world,double offset){var e=new Living();e.point=Vec3.atCenterOf(center).add(offset,0,0);world.entities.add(e);return e;}
    Entity scent(World world,double offset)throws Exception {
        String name="com/Harbinger/Spore/Sentities/Utility/ScentEntity",base=Type.getInternalName(Entity.class);
        var w=new ClassWriter(0);w.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,name,null,base,null);
        var ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,base,"<init>","()V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(1,1);ctor.visitEnd();w.visitEnd();
        var e=(Entity)new ExactQueuedShotFixture.Loader(Map.of(name,w.toByteArray())).loadClass(name.replace('/','.')).getConstructor().newInstance();
        e.kind=new Kind("spore:scent");e.point=Vec3.atCenterOf(center).add(offset,0,0);world.entities.add(e);return e;
    }
    @Test void productionBlastUses112AndRadiation48WithoutTeamFilteringOrDistanceDamageFalloff()throws Exception {
        var world=new World();var port=adapter(world);
        Living centerTarget=living(world,12.01),radEdge=living(world,48),blastEdge=living(world,112),outside=living(world,112.01);
        var radDiagonal=living(world,48);radDiagonal.point=radDiagonal.point.add(0,.01,0);
        var blastDiagonal=living(world,112);blastDiagonal.point=blastDiagonal.point.add(0,.01,0);
        var vehicle=new Vehicle();vehicle.point=Vec3.atCenterOf(center).add(-112,0,0);world.entities.add(vehicle);
        assertTrue(port.begin(impact));
        for(Entity e:List.of(centerTarget,radEdge,blastEdge)){assertEquals(1,e.hits);assertEquals(1200,e.damage);assertEquals(Vec3.atCenterOf(center),e.source.point);}
        assertEquals(1,vehicle.hits);assertEquals(1800,vehicle.damage,.001); // Each target's own maximum, no fixed damage.
        assertEquals(0,outside.hits);assertEquals(1,centerTarget.effects.size());assertEquals(1,radEdge.effects.size());assertTrue(blastEdge.effects.isEmpty());
        assertEquals(0,blastDiagonal.hits);assertEquals(1,radDiagonal.hits);assertTrue(radDiagonal.effects.isEmpty());
        for(var effect:centerTarget.effects){assertEquals(400,effect.duration);assertEquals(0,effect.amplifier);}
        assertEquals(Set.of("radiation"),centerTarget.effects.stream().map(e->e.kind.name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(0,world.visuals);assertEquals(0,world.sounds);assertEquals(List.of(112D,48D,52D),world.hookRadii);
    }
    @Test void newCraterAddsTheDeepCoreAndWideShallowOuterRing()throws Exception {
        var world=new World();var port=adapter(world);
        var wave=new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore.Impact(impact.id(),impact.actor(),center);
        var wide=center.offset(39,0,0);var deep=center.offset(0,-15,0);var high=center.offset(0,21,0);
        var outerDeep=center.offset(30,-8,0);var below=center.offset(0,-17,0);
        for(var pos:List.of(wide,deep,high,outerDeep,below))world.cells.put(pos,new State(new Block(),false,false,1));
        assertTrue(port.eligible(wave,wide,terrain));assertTrue(port.eligible(wave,deep,terrain));
        assertFalse(port.eligible(wave,outerDeep,terrain));assertFalse(port.eligible(wave,below,terrain));
        assertFalse(port.eligible(wave,high,terrain));assertFalse(port.eligible(impact,wide,terrain));
        port.apply(wave,List.of(wide,deep,high),terrain);
        assertEquals(List.of(wide,deep),world.broken);
    }
    @Test void mushroomStartsSmallAndGrowsOnlyWhenCommittedFrontAdvances()throws Exception {
        var world=new World();var port=adapter(world);
        Vec3 point=Vec3.atCenterOf(center);
        var near=new Player();near.point=point.add(159,0,0);
        var far=new Player();far.point=point.add(161,0,0);
        world.viewers.addAll(List.of(near,far));
        port.present(impact);
        assertEquals(1,world.visuals);assertEquals(1,world.sounds);
        assertEquals(2,world.smoke.size());assertEquals(42,world.smoke.stream().mapToInt(Puff::count).sum());
        assertTrue(world.smoke.stream().allMatch(puff->puff.viewer()==near));
        var wave=new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore.Impact(impact.id(),impact.actor(),center);
        port.advanced(wave,0,37);assertEquals(2,world.smoke.size());
        port.advanced(wave,37,10000);assertEquals(33,world.smoke.size());
        assertEquals(134,world.smoke.stream().mapToInt(Puff::count).sum());
        assertTrue(world.smoke.stream().allMatch(puff->puff.viewer()==near));
        assertTrue(world.smoke.stream().skip(2).anyMatch(puff->puff.point().y>point.y+8));
        port.advanced(wave,10000,10000);assertEquals(33,world.smoke.size());
        assertEquals(1,world.visuals);assertEquals(1,world.sounds);
    }
    @Test void exactScentIsDiscardedWithin52AfterDamageWithoutRemovingWrongClassesOrOtherSporeEntities()throws Exception {
        var world=new World();var port=adapter(world);Entity edge=scent(world,52),outside=scent(world,52.01);
        var wrongClass=new Entity();wrongClass.kind=new Kind("spore:scent");wrongClass.point=Vec3.atCenterOf(center).add(14,0,0);world.entities.add(wrongClass);
        var fungal=living(world,14);fungal.kind=new Kind("spore:infected");
        port.begin(impact);assertTrue(edge.removed);assertFalse(outside.removed);assertFalse(wrongClass.removed);assertFalse(fungal.removed);
        assertEquals(0,edge.hits);assertEquals(1,fungal.hits);
    }
    @Test void startCancellationAndClaimEntityRemovalPreventAllAssociatedPayloadSideEffects()throws Exception {
        var world=new World();var port=adapter(world);var body=living(world,14);var scent=scent(world,14);
        world.cancel=true;assertFalse(port.begin(impact));assertEquals(0,body.hits);assertTrue(body.effects.isEmpty());assertFalse(scent.removed);assertEquals(0,world.visuals);assertEquals(0,world.sounds);
        world.cancel=false;world.protectedEntity=body;world.duplicateEntity=body;world.injectEntity=living(world,114);
        port.begin(impact);assertEquals(0,body.hits);assertTrue(body.effects.isEmpty());assertEquals(0,world.injectEntity.hits);
    }
    @Test void deathCreatedScentIsQueriedAfterDamageAndEffectImmunityIsNotCircumvented()throws Exception {
        var world=new World();var port=adapter(world);var dead=living(world,14);var immune=living(world,15);
        var lateScent=scent(world,0);world.entities.remove(lateScent);dead.afterHit=()->{dead.alive=false;world.entities.add(lateScent);};immune.immune=true;
        port.begin(impact);assertTrue(lateScent.removed);assertTrue(dead.effects.isEmpty());assertTrue(immune.effects.isEmpty());
        assertEquals(1,immune.hits);
    }
    @Test void centerUsesNativeKillForAllEntityKindsIncludingBoundarySpectatorsAndIgnoresBlastEntityVeto()throws Exception {
        var world=new World();var port=adapter(world);
        var player=new Player();player.spectator=true;player.point=Vec3.atCenterOf(center).add(0,12,0);world.entities.add(player);
        var item=new Entity();item.point=Vec3.atCenterOf(center);world.entities.add(item);
        var vehicle=new Vehicle();vehicle.point=Vec3.atCenterOf(center).add(-12,0,0);world.entities.add(vehicle);
        var outside=new Entity();outside.point=Vec3.atCenterOf(center).add(12,.01,0);world.entities.add(outside);
        world.protectedEntity=player;
        world.cancel=true;assertFalse(port.begin(impact));assertEquals(0,player.kills);assertEquals(0,item.kills);
        world.cancel=false;assertTrue(port.begin(impact));
        for(Entity e:List.of(player,item,vehicle)){assertEquals(1,e.kills);assertEquals(0,e.hits);assertFalse(e.alive);}
        assertEquals(0,outside.kills);assertFalse(outside.removed);
    }
    @Test void expandedEntityShellsReceiveEffectsWhileCenterAndScentKeepTheirOriginalBoundaries()throws Exception {
        var world=new World();var port=adapter(world);
        var newlyKilled=living(world,10);var newRadiation=living(world,46);var pastRadiation=living(world,48.01);
        var newBlast=living(world,100);var pastBlast=living(world,112.01);var newScent=scent(world,50);
        assertTrue(port.begin(impact));assertEquals(1,newlyKilled.kills);assertEquals(0,newlyKilled.hits);
        assertEquals(1,newRadiation.hits);assertEquals(1,newRadiation.effects.size());
        assertEquals(1,pastRadiation.hits);assertTrue(pastRadiation.effects.isEmpty());
        assertEquals(1,newBlast.hits);assertEquals(0,pastBlast.hits);assertTrue(newScent.removed);
    }
    @Test void selectedRadiationImplementationReceivesOnlyProtectedSurvivorsOnceAndReplacesDnvEffect()throws Exception {
        var world=new World();world.actor=new Player();var admitted=living(world,47);var protectedBody=living(world,46);
        var dead=living(world,45);dead.afterHit=()->dead.alive=false;
        var outside=living(world,48.01);world.protectedEntity=protectedBody;
        var calls=new ArrayList<Living>();
        var port=adapter(world,(target,actor,duration)->{
            calls.add(target);assertSame(world.actor,actor);assertEquals(400,duration);
            target.addEffect(new EffectPacket(new EffectKind("replacement"),duration,0),actor);
        });
        assertTrue(port.begin(impact));assertEquals(List.of(admitted),calls);
        assertEquals(List.of("replacement"),admitted.effects.stream().map(e->e.kind.name).toList());
        assertTrue(protectedBody.effects.isEmpty());assertTrue(dead.effects.isEmpty());assertTrue(outside.effects.isEmpty());
    }
    @Test void selectedRadiationRefusalDoesNotFallBackAndCancelledImpactNeverCallsIt()throws Exception {
        var world=new World();var body=living(world,47);world.duplicateEntity=body;
        var calls=new ArrayList<Living>();
        var port=adapter(world,(target,actor,duration)->{assertNull(actor);calls.add(target);});
        world.cancel=true;assertFalse(port.begin(impact));assertTrue(calls.isEmpty());
        world.cancel=false;assertTrue(port.begin(impact));assertEquals(List.of(body),calls);
        assertTrue(body.effects.isEmpty()); // Replacement refusal must not add the old DNV effect.
    }
    @Test void residualFireUsesExpandedTerrainSphereAndLoadedBlockBoundaries()throws Exception {
        var world=new World();var port=adapter(world);var phase=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase.FIRE;
        var edge=center.offset(28,0,0);var diagonalOutside=center.offset(28,1,0);
        world.fireEligible.addAll(List.of(edge,diagonalOutside));
        assertTrue(port.eligible(impact,edge,phase));assertFalse(port.eligible(impact,diagonalOutside,phase));
        port.apply(impact,List.of(edge,diagonalOutside),phase);assertEquals(List.of(edge),world.fires);
        assertEquals(List.of(28D),world.hookRadii);
        world.unloaded.add(edge);assertFalse(port.eligible(impact,edge,phase));
    }
    @Test void resumedOldTerrainFluidAndFireUseFrozenRadiusInBothSelectionAndProtectionCallback()throws Exception {
        var world=new World();var port=adapter(world);
        var old=new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore.Impact(impact.id(),impact.actor(),center,
                new com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadProgress(true,100,100,100,24,24));
        var p=center.offset(25,0,0);world.cells.put(p,new State(new Liquid(),true,false,100));world.fireEligible.add(p);
        assertFalse(port.eligible(old,p,terrain));assertFalse(port.eligible(old,p,fluid));
        assertFalse(port.eligible(old,p,com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase.FIRE));
        world.cells.put(center,new State(new Block(),false,false,1));port.apply(old,List.of(center),terrain);
        world.cells.put(center,new State(new Liquid(),true,false,100));port.apply(old,List.of(center),fluid);
        world.fireEligible.add(center);port.apply(old,List.of(center),com.liucodex.doggiesnewvehicle.artillery.silo.NuclearPayloadEngine.Phase.FIRE);
        assertEquals(List.of(24D,24D,24D),world.hookRadii);assertEquals(List.of(center),world.fires);
    }
    public static class Kind {final String id;Kind(String id){this.id=id;}}
    public static class Id {final String id;Id(String id){this.id=id;}@Override public String toString(){return id;}}
    public interface EntityRegistry {Id getKey(Object type);}
    public static class RegistryCatalog {public static final EntityRegistry ENTITY_TYPE=type->new Id(((Kind)type).id);}
    public static class Entity {
        public Vec3 point=Vec3.ZERO;Kind kind=new Kind("minecraft:pig");boolean removed,alive=true,spectator;int hits,kills;float damage;Damage source;Runnable afterHit;
        public Entity(){ }
        public boolean isAlive(){return alive;}public boolean isSpectator(){return spectator;}
        public AABB getBoundingBox(){return new AABB(point,point).inflate(.1);}
        public Kind getType(){return kind;}public void discard(){removed=true;}
        public Vec3 position(){return point;}public void kill(){kills++;alive=false;removed=true;}
        public double distanceToSqr(Vec3 pos){return point.distanceToSqr(pos);}
        public boolean hurt(Damage source,float amount){hits++;damage=amount;this.source=source;if(afterHit!=null)afterHit.run();return true;}
    }
    public static class Living extends Entity {
        public float getMaxHealth(){return 2000;}
        final List<EffectPacket> effects=new ArrayList<>();boolean immune;
        public boolean addEffect(EffectPacket effect,Entity source){if(immune)return false;effects.add(effect);return true;}
    }
    public static class Player extends Living { }
    public static class Vehicle extends Entity { public float getMaxHealth(){return 3000;} }
    public static class Damage {final Vec3 point;Damage(Vec3 point){this.point=point;}}
    public static class DamagePort {public static Damage blast(World world,Entity actor,Vec3 point){return new Damage(point);}}
    public static class EffectKind {final String name;EffectKind(String name){this.name=name;}}
    public static class Ref extends EffectKind {final Object value;Ref(Object value){super(value instanceof EffectKind e?e.name:"");this.value=value;}public Object get(){return value;}}
    public static class Effects {public static final Ref RADIATION=new Ref(new EffectKind("radiation"));public static final EffectKind WITHER=new EffectKind("wither");}
    public static class EffectPacket {final EffectKind kind;final int duration,amplifier;public EffectPacket(EffectKind kind,int duration,int amplifier){this.kind=kind;this.duration=duration;this.amplifier=amplifier;}}
    public static class Tone { }
    public static class Fire {
        public static boolean eligible(World world,BlockPos pos){return world.fireEligible.contains(pos);}
        public static void place(World world,BlockPos pos,Entity actor){world.fires.add(pos);}
    }
    public static class Sounds {public static final Ref NUCLEAR_EXPLOSION=new Ref(new Tone());}
    public static class Style {public static final Style EPIC=new Style();}
    public static class FX {public static void spawnExplosionParticles(Entity actor,Style style,World world,Vec3 point){assertSame(Style.EPIC,style);world.visuals++;}}
    public static class Smoke { }
    public static class Particles {public static final Smoke CAMPFIRE_COSY_SMOKE=new Smoke();}
    record Puff(Player viewer,Vec3 point,int count){ }
    public static class Property { }
    public static class Properties {public static final Property WATERLOGGED=new Property();}
    public static class Block {
        public float getExplosionResistance(){return 1;}
        public State defaultBlockState(){return new State(this,false,false,0);}
    }
    public static class Liquid extends Block { }
    public static class Catalog {public static final Block AIR=new Block();}
    public static class State {
        final Block block;final boolean liquid,logged;boolean wet;final float hardness;
        State(Block block,boolean liquid,boolean logged,float hardness){this.block=block;this.liquid=liquid;this.logged=logged;this.wet=liquid&&logged;this.hardness=hardness;}
        public boolean isAir(){return block==Catalog.AIR;}public float getDestroySpeed(World world,BlockPos p){return hardness;}
        public Block getBlock(){return block;}public Fluid getFluidState(){return new Fluid(this);}
        public boolean hasProperty(Property property){return logged;}
        public Object setValue(Property property,Comparable value){wet=(Boolean)value;return this;}
    }
    public static class Fluid {
        final State state;Fluid(State state){this.state=state;}
        public boolean isEmpty(){return !state.liquid||state.logged&&!state.wet;}
        public State createLegacyBlock(){return state.block instanceof Liquid?state:new Liquid().defaultBlockState();}
    }
    public static class Key { }
    public static class Rules {
        public static final Key RULE_MOBGRIEFING=new Key();boolean allowed=true;
        public boolean getBoolean(Key key){return allowed;}
    }
    public static class Border {final Set<BlockPos> outside=new HashSet<>();public boolean isWithinBounds(BlockPos p){return !outside.contains(p);}}
    public static class World {
        final Set<BlockPos> fireEligible=new HashSet<>();final List<BlockPos> fires=new ArrayList<>();
        final Map<BlockPos,State> cells=new HashMap<>();final Set<BlockPos> reads=new HashSet<>(),unloaded=new HashSet<>(),high=new HashSet<>();
        final List<BlockPos> broken=new ArrayList<>();final Border border=new Border();final Rules rules=new Rules();
        int finalized,hooks,writes,flags,visuals,sounds;boolean explosions=true,griefVeto,cancel;Player actor;BlockPos protectedPos,inject,duplicate;
        final List<Entity> entities=new ArrayList<>();final List<Double> hookRadii=new ArrayList<>();Entity protectedEntity,injectEntity,duplicateEntity;
        final List<Player> viewers=new ArrayList<>();final List<Puff> smoke=new ArrayList<>();
        public List<Player> players(){return viewers;}
        public boolean sendParticles(Player viewer,Smoke kind,boolean force,double x,double y,double z,int count,
                double width,double height,double depth,double speed){
            assertSame(Particles.CAMPFIRE_COSY_SMOKE,kind);assertTrue(force);smoke.add(new Puff(viewer,new Vec3(x,y,z),count));return true;
        }
        public boolean hasChunkAt(BlockPos p){return !unloaded.contains(p);}public boolean isOutsideBuildHeight(BlockPos p){return high.contains(p);}
        public Border getWorldBorder(){return border;}public State getBlockState(BlockPos p){reads.add(p);return cells.getOrDefault(p,Catalog.AIR.defaultBlockState());}
        public Rules getGameRules(){return rules;}public Player getPlayerByUUID(UUID id){return actor;}
        public boolean setBlock(BlockPos p,State state,int flags){cells.put(p,state);writes++;this.flags=flags;return true;}
        public List<Entity> getEntities(Entity except,AABB bounds,java.util.function.Predicate<Entity> predicate){return new ArrayList<>(entities.stream().filter(e->bounds.intersects(e.getBoundingBox())&&predicate.test(e)).toList());}
        public void playSound(Player player,double x,double y,double z,Tone tone,net.minecraft.sounds.SoundSource category,float volume,float pitch){assertNull(player);assertEquals(1,volume);assertEquals(1,pitch);sounds++;}
    }
    public static class Blast {
        final World world;final List<BlockPos> blocks=new ArrayList<>();
        public Blast(World world,Entity source,double x,double y,double z,float radius,boolean fire,BlockInteraction interaction){
            this.world=world;assertEquals(112,radius);assertFalse(fire);assertEquals(BlockInteraction.DESTROY,interaction);
        }
        public List<BlockPos> getToBlow(){return blocks;}
        public void explode(){fail("Cleanup must not deal entity damage again");}
        public void finalizeExplosion(boolean particles){assertFalse(particles);world.finalized++;world.broken.addAll(blocks);}
    }
    public static class Hooks {
        public static boolean onExplosionStart(World world,Blast explosion){return world.cancel;}
        public static boolean allowed(World world,Entity entity){return world.explosions&&world.rules.allowed&&(entity==null||!world.griefVeto);}
        public static boolean getMobGriefingEvent(World world,Entity entity){return !world.griefVeto;}
        public static boolean canEntityGrief(World world,Entity entity){return !world.griefVeto;}
        public static void onExplosionDetonate(World world,Blast explosion,List<Entity> entities,double radius){
            assertTrue(radius==24||radius==28||radius==36||radius==40||radius==48||radius==52||radius==112);
            world.hooks++;world.hookRadii.add(radius);explosion.blocks.remove(world.protectedPos);
            if(world.inject!=null)explosion.blocks.add(world.inject);if(world.duplicate!=null)explosion.blocks.add(world.duplicate);
            entities.remove(world.protectedEntity);if(world.injectEntity!=null)entities.add(world.injectEntity);if(world.duplicateEntity!=null&&world.protectedEntity==null)entities.add(world.duplicateEntity);
        }
    }
}
