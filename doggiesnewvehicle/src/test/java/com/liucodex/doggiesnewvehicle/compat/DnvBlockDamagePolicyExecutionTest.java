package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

/** Executes compiled policy and final native admission with explicit gamerule/event/entity edges. */
class DnvBlockDamagePolicyExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy";
    static final Class<?> POLICY=load();
    static Class<?> load(){try{
        var map=new HashMap<String,String>();
        for(String name:List.of("world/level/Level","server/level/ServerLevel"))
            NuclearRecoveryExecutionTest.map(map,"net/minecraft/"+name,World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/projectile/Projectile",Shot.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/GameRules",Rules.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/GameRules$Key",Key.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraftforge/event/ForgeEventFactory",Hooks.class);
        NuclearRecoveryExecutionTest.map(map,"net/neoforged/neoforge/event/EventHooks",Hooks.class);
        NuclearRecoveryExecutionTest.map(map,"com/atsuishio/superbwarfare/config/server/ExplosionConfig",Config.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraftforge/common/ForgeConfigSpec$BooleanValue",Flag.class);
        NuclearRecoveryExecutionTest.map(map,"net/neoforged/neoforge/common/ModConfigSpec$BooleanValue",Flag.class);
        return NuclearRecoveryExecutionTest.extract(ROOT,
                Set.of("owned","mark","allowed","terrainAllowed","nativeBlastBlocks"),map,null,false);
    }catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
    static boolean owned(Body body)throws Exception{return (boolean)POLICY.getMethod("owned",Body.class).invoke(null,body);}
    static boolean allowed(World world,Body body)throws Exception{return (boolean)POLICY.getMethod("allowed",World.class,Body.class).invoke(null,world,body);}
    static Body entity(String name,Class<?> parent)throws Exception{
        var writer=new ClassWriter(0);String owner=name.replace('.','/'),base=Type.getInternalName(parent);
        writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,owner,null,base,null);
        var ctor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,base,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(1,1);ctor.visitEnd();writer.visitEnd();
        var type=new ExactQueuedShotFixture.Loader(Map.of(owner,writer.toByteArray())).loadClass(name);
        return (Body)type.getConstructor().newInstance();
    }
    @Test void hardFalseCannotBeOverriddenAndCurrentRulesAreReadAfterFlight()throws Exception{
        var world=new World();var host=entity("com.liucodex.noise.entity.Host",Body.class);
        world.rules.grief=false;world.eventAllows=true;
        assertFalse(allowed(world,host));assertEquals(0,world.hooks);
        world.rules.grief=true;assertTrue(allowed(world,host));assertEquals(1,world.hooks);
        world.eventAllows=false;assertFalse(allowed(world,host));
        world.eventAllows=true;assertTrue(allowed(world,null));
        world.rules.grief=false;assertFalse(allowed(world,null));
    }
    @Test void nativeProjectileOriginSurvivesOwnerLossAndDataRoundtripWithoutTaggingForeignShots()throws Exception{
        var host=entity("com.liucodex.doggiesnewvehicle.armored.Host",Body.class);
        var shot=(Shot)entity("thirdparty.NativeProjectile",Shot.class);shot.owner=host;
        assertTrue(owned(shot));POLICY.getMethod("mark",Body.class,Body.class).invoke(null,shot,host);
        var saved=shot.data.copy();shot.owner=null;assertTrue(owned(shot));
        var restored=(Shot)entity("thirdparty.ReloadedProjectile",Shot.class);restored.data=saved;
        assertTrue(owned(restored));
        var foreign=entity("thirdparty.Vehicle",Body.class);var foreignShot=entity("thirdparty.Bullet",Shot.class);
        POLICY.getMethod("mark",Body.class,Body.class).invoke(null,foreignShot,foreign);
        assertFalse(owned(foreignShot));assertFalse(owned(null));
        ((Shot)foreignShot).owner=foreignShot;assertFalse(owned(foreignShot));
    }
    @Test void queuedCraterReadsTheLiveSbwSwitchAndClipsLibertadHeToSixBlocks()throws Exception{
        var world=new World();var shell=new Shot();
        var state=new CompoundTag();state.putFloat("BlockBlastRadius",6);
        shell.data.put("LibertadShot",state);
        var limit=POLICY.getMethod("nativeBlastBlocks",World.class,Body.class,Vec3.class,Collection.class);
        var center=Vec3.atCenterOf(BlockPos.ZERO);
        var six=new BlockPos(6,0,0);var seven=new BlockPos(7,0,0);var unloaded=new BlockPos(0,0,5);
        world.unloaded=unloaded;
        var proposed=List.of(BlockPos.ZERO,six,seven,unloaded);
        assertEquals(List.of(BlockPos.ZERO,six),limit.invoke(null,world,shell,center,proposed));
        try {
            Config.EXPLOSION_DESTROY.enabled=false;
            assertEquals(List.of(),limit.invoke(null,world,shell,center,proposed));
            assertTrue((boolean)POLICY.getMethod("terrainAllowed",World.class,Body.class).invoke(null,world,shell));
            assertSame(proposed,limit.invoke(null,world,entity("thirdparty.NativeShell",Shot.class),center,proposed));
        } finally {
            Config.EXPLOSION_DESTROY.enabled=true;
        }
        assertEquals(List.of(BlockPos.ZERO,six),limit.invoke(null,world,shell,center,proposed));
    }
    @Test void nativeFinalizationClearsOnlyDnvTerrainWhenTheRuleChanges()throws Exception{
        var map=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/Explosion",Blast.class);
        NuclearRecoveryExecutionTest.map(map,ROOT,Bridge.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/mixin/ExplosionBlockDamageMixin",
                Set.of("dnv$blockAdmission"),map,Blast.class,false);
        var blast=(Blast)type.getConstructor().newInstance();blast.entity=entity("com.liucodex.silentheaven.entity.Host",Body.class);
        var hook=type.getMethod("dnv$blockAdmission",boolean.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        blast.blocks.add(1);hook.invoke(blast,false,null);assertEquals(List.of(1),blast.blocks);
        blast.level.rules.grief=false;hook.invoke(blast,false,null);assertTrue(blast.blocks.isEmpty());
        blast.entity=entity("thirdparty.OtherHost",Body.class);blast.blocks.add(2);hook.invoke(blast,false,null);
        assertEquals(List.of(2),blast.blocks);
    }
    @Test void nativePenetrationWritesAndVehicleCrushingUseTheSameLiveAdmission()throws Exception{
        var map=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(map,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Body.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(map,ROOT,Bridge.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/mixin/ProjectileBlockDamageMixin",
                Set.of("dnv$levelBlock","dnv$serverBlock"),map,Body.class,false);
        var projectile=type.getConstructor().newInstance();var world=new World();world.rules.grief=false;
        for(String method:List.of("dnv$levelBlock","dnv$serverBlock")){
            var hook=type.getMethod(method,World.class,net.minecraft.core.BlockPos.class,boolean.class);
            assertEquals(false,hook.invoke(projectile,world,net.minecraft.core.BlockPos.ZERO,true));assertEquals(0,world.writes);
        }
        world.rules.grief=true;
        assertEquals(true,type.getMethod("dnv$levelBlock",World.class,net.minecraft.core.BlockPos.class,boolean.class)
                .invoke(projectile,world,net.minecraft.core.BlockPos.ZERO,true));assertEquals(1,world.writes);
        map.remove("com/liucodex/doggiesnewvehicle/compat/mixin/ProjectileBlockDamageMixin");
        type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/mixin/VehicleBlockDamageMixin",
                Set.of("dnv$collisionTerrain"),map,null,false);
        var vehicle=entity("com.liucodex.doggiesnewvehicle.armored.Tank",Body.class);vehicle.world.rules.grief=false;
        var callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("collision",true);
        type.getMethod("dnv$collisionTerrain",Body.class,callback.getClass()).invoke(null,vehicle,callback);
        assertTrue(callback.isCancelled());
        var foreign=entity("thirdparty.NativeTank",Body.class);foreign.world.rules.grief=false;
        callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("collision",true);
        type.getMethod("dnv$collisionTerrain",Body.class,callback.getClass()).invoke(null,foreign,callback);
        assertFalse(callback.isCancelled());
    }
    public static class Bridge {
        public static boolean owned(Body body){try{return DnvBlockDamagePolicyExecutionTest.owned(body);}catch(Exception e){throw new RuntimeException(e);}}
        public static boolean allowed(World world,Body body){try{return DnvBlockDamagePolicyExecutionTest.allowed(world,body);}catch(Exception e){throw new RuntimeException(e);}}
        public static boolean terrainAllowed(World world,Body body){try{return (boolean)POLICY.getMethod("terrainAllowed",World.class,Body.class).invoke(null,world,body);}catch(Exception e){throw new RuntimeException(e);}}
    }
    public static class Body {public World world=new World();public World level(){return world;}
        public CompoundTag data=new CompoundTag();public CompoundTag getPersistentData(){return data;}}
    public static class Shot extends Body {public Body owner;public Body getOwner(){return owner;}}
    public static class Key { }
    public static class Rules {public static final Key RULE_MOBGRIEFING=new Key();public boolean grief=true;public boolean getBoolean(Key key){return grief;}}
    public static class World {public Rules rules=new Rules();public boolean eventAllows=true;public int hooks,writes;public Rules getGameRules(){return rules;}
        public BlockPos unloaded;public boolean hasChunkAt(BlockPos pos){return !pos.equals(unloaded);}
        public boolean destroyBlock(net.minecraft.core.BlockPos pos,boolean drops){writes++;return true;}}
    public static class Config {public static final Flag EXPLOSION_DESTROY=new Flag();}
    public static class Flag {public boolean enabled=true;public Object get(){return enabled;}}
    public static class Hooks {public static boolean getMobGriefingEvent(World world,Body body){world.hooks++;return world.eventAllows;}
        public static boolean canEntityGrief(World world,Body body){return getMobGriefingEvent(world,body);}}
    public static class Blast {public Body entity;public World level=new World();public List<Integer> blocks=new ArrayList<>();public List<Integer> getToBlow(){return blocks;}}
}
