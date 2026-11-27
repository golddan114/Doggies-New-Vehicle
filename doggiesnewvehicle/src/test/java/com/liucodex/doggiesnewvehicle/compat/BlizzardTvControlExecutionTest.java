package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production missile's nonce, owner, vehicle and rate checks around explicit world seams. */
class BlizzardTvControlExecutionTest {
    @Test void nonOwnerStaleSessionWrongVehicleAndInvalidAnglesCannotSteerOrExit() throws Exception {
        var f=fixture();
        for(int scenario=0;scenario<6;scenario++) {
            Actor actor=f.actor; UUID nonce=f.nonce; float yaw=70,pitch=-20;
            if(scenario==0)actor=new Actor();
            if(scenario==1)nonce=UUID.randomUUID();
            if(scenario==2)f.actor.vehicle=new Tank();
            if(scenario==3)yaw=Float.NaN;
            if(scenario==4)pitch=Float.POSITIVE_INFINITY;
            if(scenario==5)f.tank.current=new Missile();
            f.invoke(actor,nonce,yaw,pitch,false);
            assertEquals(0,f.scalar("desiredYaw")); assertEquals(0,f.scalar("desiredPitch"));
            f.actor.vehicle=f.tank;f.tank.current=f.missile;
        }
        f.invoke(new Actor(),f.nonce,0,0,true);f.invoke(f.actor,UUID.randomUUID(),0,0,true);
        assertTrue(f.bool("controlled"));assertEquals(0,f.tank.ends);
    }
    @Test void validInputIsFiniteBoundedAndOnlyOneSteerIsAppliedPerTickWhileExitRemainsAvailable() throws Exception {
        var f=fixture();
        f.invoke(f.actor,f.nonce,390,-100,false);
        assertEquals(30,f.scalar("desiredYaw"));assertEquals(-85,f.scalar("desiredPitch"));
        f.invoke(f.actor,f.nonce,-80,20,false);
        assertEquals(30,f.scalar("desiredYaw"));
        f.world.now++;
        f.invoke(f.actor,f.nonce,-80,20,false);
        assertEquals(-80,f.scalar("desiredYaw"));assertEquals(20,f.scalar("desiredPitch"));
        f.invoke(f.actor,f.nonce,0,0,true);
        assertFalse(f.bool("controlled"));assertEquals(1,f.tank.ends);
        f.invoke(f.actor,f.nonce,0,0,true);assertEquals(1,f.tank.ends);
        assertSame(f.tank,f.actor.vehicle);assertEquals(new Vec3(6,40,9),f.actor.position);
    }
    @Test void savedFlightKeepsProgressDropsOldControlAndCannotMoveBeyondItsRemainingDistanceOrLifetime() throws Exception {
        var f = fixture(); var kind = f.missile.getClass();
        kind.getField("flightAge").setInt(f.missile, 99); kind.getField("travelled").setDouble(f.missile, 239.9);
        var tag = new net.minecraft.nbt.CompoundTag();
        kind.getMethod("addAdditionalSaveData", net.minecraft.nbt.CompoundTag.class).invoke(f.missile, tag);
        kind.getMethod("readAdditionalSaveData", net.minecraft.nbt.CompoundTag.class).invoke(f.missile, tag);
        assertFalse(f.bool("controlled")); assertNull(kind.getField("sessionId").get(f.missile));
        assertEquals(99, kind.getField("flightAge").getInt(f.missile));
        assertEquals(239.9, kind.getField("travelled").getDouble(f.missile));
        Vec3 before = f.missile.position;
        kind.getMethod("tick").invoke(f.missile);
        assertEquals(.1, before.distanceTo(f.missile.position), 1e-10);
        assertEquals(240, kind.getField("travelled").getDouble(f.missile), 1e-10); assertTrue(f.missile.removed);
        assertEquals(0, f.world.impacts);
        for (boolean distance : new boolean[] {false, true}) {
            f = fixture(); kind = f.missile.getClass();
            tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("BlizzardFlightAge", distance ? 0 : 400);
            tag.putDouble("BlizzardTravelled", distance ? Double.NaN : 0);
            kind.getMethod("readAdditionalSaveData", net.minecraft.nbt.CompoundTag.class).invoke(f.missile, tag);
            before = f.missile.position; kind.getMethod("tick").invoke(f.missile);
            assertEquals(before, f.missile.position); assertTrue(f.missile.removed); assertEquals(0, f.world.impacts);
        }
    }
    @Test void serverTickReleasesLostAuthorityAndUnloadedFlightWithoutMovingThePilotOrProducingAnImpact() throws Exception {
        var valid = fixture(); valid.missile.getClass().getMethod("tick").invoke(valid.missile);
        assertTrue(valid.bool("controlled")); assertEquals(.6, new Vec3(6,40,9).distanceTo(valid.missile.position), 1e-10);
        for (int cause = 0; cause < 9; cause++) {
            var f = fixture();
            if (cause == 0) f.world.tank = null;
            if (cause == 1) f.tank.current = new Missile();
            if (cause == 2) f.tank.wreck = true;
            if (cause == 3) f.missile.owner = new Entity();
            if (cause == 4) f.world.server.players.current = null;
            if (cause == 5) f.actor.alive = false;
            if (cause == 6) f.actor.spectator = true;
            if (cause == 7) f.actor.vehicle = null;
            if (cause == 8) f.world.now += 22;
            f.missile.getClass().getMethod("tick").invoke(f.missile);
            assertFalse(f.bool("controlled")); assertEquals(0, f.world.impacts);
            assertEquals(new Vec3(6,40,9), f.actor.position);
        }
        var unloaded = fixture(); unloaded.world.loaded = false;
        unloaded.missile.getClass().getMethod("tick").invoke(unloaded.missile);
        assertFalse(unloaded.bool("controlled")); assertTrue(unloaded.missile.removed);
        assertEquals(1, unloaded.tank.ends); assertEquals(0, unloaded.world.impacts);
        assertEquals(new Vec3(6,40,9), unloaded.missile.position);
    }
    record Fixture(Missile missile,Actor actor,Tank tank,World world,UUID nonce) {
        void invoke(Actor sender,UUID session,float yaw,float pitch,boolean exit)throws Exception {
            missile.getClass().getMethod("steer",Actor.class,UUID.class,float.class,float.class,boolean.class).invoke(missile,sender,session,yaw,pitch,exit);
        }
        float scalar(String name)throws Exception{return missile.getClass().getField(name).getFloat(missile);}
        boolean bool(String name)throws Exception{return missile.getClass().getField(name).getBoolean(missile);}
    }
    private Fixture fixture()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/armored/DnvBlizzardMissile",tank="com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity";
        String base="com/atsuishio/superbwarfare/entity/projectile/JavelinMissileEntity",target="com/liucodex/doggiesnewvehicle/compat/executionprobe/BlizzardMissile";
        Map<String,String> names=new HashMap<>(Map.of(source,target,base,Type.getInternalName(Missile.class),tank,Type.getInternalName(Tank.class),
                "net/minecraft/world/entity/Entity",Type.getInternalName(Entity.class),"net/minecraft/world/level/Level",Type.getInternalName(World.class),
                "net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class),"net/minecraft/server/level/ServerPlayer",Type.getInternalName(Actor.class)));
        names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardTvNetwork", Type.getInternalName(Network.class));
        names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardLoadedPath", Type.getInternalName(Loaded.class));
        names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardTrace", Type.getInternalName(Trace.class));
        names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardTrace$Hit", Type.getInternalName(Trace.Hit.class));
        names.put("net/minecraft/world/level/ClipContext", Type.getInternalName(Clip.class));
        names.put("net/minecraft/world/phys/HitResult", Type.getInternalName(Hit.class));
        names.put("net/minecraft/world/phys/BlockHitResult", Type.getInternalName(Hit.class));
        names.put("net/minecraft/server/MinecraftServer", Type.getInternalName(Server.class));
        names.put("net/minecraft/server/players/PlayerList", Type.getInternalName(Players.class));
        ClassNode node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
        node.methods.removeIf(m->!Set.of("steer","state","matches","source","releaseControl","tick","addAdditionalSaveData","readAdditionalSaveData").contains(m.name)
                && !m.name.startsWith("lambda$tick$"));
        node.innerClasses.clear();
        node.methods.forEach(m->{
            m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
            for(var insn:m.instructions)if(insn instanceof MethodInsnNode call && call.owner.equals(tank))call.desc=call.desc.replace(source,base);
        });
        node.fields.forEach(f->f.access=(f.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
        MethodNode constructor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,base,"<init>","()V",false));constructor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(constructor);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
        Class<?> type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        var missile=(Missile)type.getConstructor().newInstance();var actor=new Actor();var tankObject=new Tank();var world=new World();var nonce=UUID.randomUUID();
        actor.vehicle=tankObject;actor.world=world;missile.owner=actor;missile.world=world;tankObject.current=missile;world.tank=tankObject;
        world.server.players.current=actor;
        type.getField("vehicleId").set(missile,tankObject.id);type.getField("sessionId").set(missile,nonce);type.getField("controlled").setBoolean(missile,true);
        type.getField("lastSteerTick").setLong(missile,world.now - 1);
        return new Fixture(missile,actor,tankObject,world,nonce);
    }
    public static class Network {public static void sendState(Actor p,com.liucodex.doggiesnewvehicle.armored.BlizzardTvNetwork.State s){}}
    public static class Entity {public UUID id=UUID.randomUUID();public World world;public Entity vehicle;public Vec3 position=new Vec3(6,40,9);public boolean alive=true,spectator;
        public int getId(){return 42;} public UUID getUUID(){return id;}public Entity getVehicle(){return vehicle;}public World level(){return world;}public boolean isAlive(){return alive;}public boolean isSpectator(){return spectator;}
        public boolean isPickable(){return true;}public Entity getRootVehicle(){return vehicle==null?this:vehicle;}}
    public static class Actor extends Entity { public World serverLevel(){return world;} }
    public static class Missile extends Entity {public Entity owner;public boolean removed;public Vec3 velocity=Vec3.ZERO;public float yaw,pitch,yRotO,xRotO;
        public Entity getOwner(){return owner;}public void baseTick(){} public void syncMotion(){}public Vec3 position(){return position;}
        public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(Vec3 v){velocity=v;}public void setPos(Vec3 v){position=v;}
        public float getYRot(){return yaw;}public float getXRot(){return pitch;}public void setYRot(float v){yaw=v;}public void setXRot(float v){pitch=v;}
        public void discard(){removed=true;}public void impact(){world.impacts++;}
        public void m_7380_(net.minecraft.nbt.CompoundTag tag){}public void m_7378_(net.minecraft.nbt.CompoundTag tag){}
    }
    public static class Tank extends Entity {public Missile current;public int ends;public boolean wreck;public boolean isWreck(){return wreck;}public boolean ownsTv(Missile m){return current==m;}public void endTv(Missile m){if(current==m){ends++;current=null;}}}
    public static class World {public Tank tank;public long now=5;public boolean isClientSide,loaded=true;public int impacts;public Server server=new Server();
        public Entity getEntity(UUID id){return tank!=null&&tank.id.equals(id)?tank:null;}public long getGameTime(){return now;}
        public Hit clip(Clip context){return new Hit();}public Server getServer(){return server;}}
    public static class Server {public Players players=new Players();public Players getPlayerList(){return players;}}
    public static class Players {public Actor current;public Actor getPlayer(UUID id){return current!=null&&current.id.equals(id)?current:null;}}
    public static class Loaded {
        public static Vec3 clipStep(Entity owner, Vec3 from, Vec3 to) { return owner.world.loaded ? to : null; }
    }
    public static class Clip {public Clip(Vec3 from,Vec3 to,net.minecraft.world.level.ClipContext.Block block,
        net.minecraft.world.level.ClipContext.Fluid fluid,Entity owner){}}
    public static class Hit {public net.minecraft.world.phys.HitResult.Type getType(){return net.minecraft.world.phys.HitResult.Type.MISS;}
        public Vec3 getLocation(){return Vec3.ZERO;}}
    public static class Trace {
        public record Hit(Entity entity,Vec3 position){}
        public static Hit nearest(World level,Vec3 from,Vec3 to,java.util.function.Predicate<Entity> filter){return null;}
    }
}
