package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.armored.BlizzardTvNetwork;
import java.util.*;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes compiled camera/session callbacks with explicit client-world and input seams, not a running game. */
class BlizzardTvCameraExecutionTest {
    @Test void cameraAttachesWithoutMovingPilotAndRestoresOnImpactOrManualExit() throws Exception {
        var probe = probe(); var mc = Client.instance;
        var state = state(mc, UUID.randomUUID());
        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null, state);
        probe.getMethod("tick", Tick.class).invoke(null, new Tick());
        assertSame(mc.world.missile, mc.camera); assertEquals(CameraType.FIRST_PERSON, mc.options.type);
        assertEquals(new Vec3(12, 30, -8), mc.player.position);
        assertSame(mc.world.vehicle, mc.player.vehicle);
        var angles = new Angles(mc.world.missile);
        probe.getMethod("turn",Entity.class,double.class,double.class).invoke(null, mc.player, 100d, -40d);
        probe.getMethod("tick", Tick.class).invoke(null, new Tick());
        assertEquals(65,Network.sent.get(0).yaw()); assertEquals(-13,Network.sent.get(0).pitch());
        assertEquals(12,mc.player.yaw); assertEquals(-7,mc.player.pitch);
        probe.getMethod("camera", Angles.class).invoke(null, angles);
        probe.getMethod("afterSetup", Camera.class, float.class).invoke(null, angles.camera, .5F);
        assertEquals(mc.world.missile.position.add(0, .1, 0), angles.camera.position);
        assertEquals(50, angles.yaw); assertEquals(-7, angles.pitch); assertEquals(0, angles.roll);
        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null,
                new BlizzardTvNetwork.State(state.vehicle(), state.missile(), state.session(), state.entityId(), false));
        assertSame(mc.player, mc.camera); assertEquals(CameraType.THIRD_PERSON_BACK, mc.options.type);
        assertEquals(12, mc.player.yaw); assertEquals(-7, mc.player.pitch);
        assertSame(mc.world.vehicle, mc.player.vehicle); assertEquals(new Vec3(12, 30, -8), mc.player.position);

        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null, state);
        probe.getMethod("tick", Tick.class).invoke(null, new Tick());
        mc.screen = new Screen();
        probe.getMethod("tick", Tick.class).invoke(null, new Tick());
        assertSame(mc.player, mc.camera); assertTrue(Network.sent.get(Network.sent.size()-1).exit());
    }
    @Test void delayedOldStopsCannotCancelNewSessionAndMissingSpawnTimeoutRestoresInputs() throws Exception {
        var probe = probe(); var mc = Client.instance;
        var first = state(mc, UUID.randomUUID()); var second = state(mc, UUID.randomUUID());
        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null, first);
        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null, second);
        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null,
                new BlizzardTvNetwork.State(first.vehicle(), first.missile(), first.session(), first.entityId(), false));
        probe.getMethod("tick", Tick.class).invoke(null, new Tick());
        assertSame(mc.world.missile, mc.camera);
        probe.getMethod("close", boolean.class).invoke(null, true);
        assertEquals(second.session(), Network.sent.get(Network.sent.size()-1).session());
        Network.sent.clear(); mc.world.missile = null;
        probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null, second);
        for (int i=0;i<41;i++) probe.getMethod("tick", Tick.class).invoke(null, new Tick());
        assertSame(mc.player, mc.camera); assertEquals(CameraType.THIRD_PERSON_BACK, mc.options.type);
        assertEquals(1, Network.sent.size()); assertTrue(Network.sent.get(0).exit());
    }
    @Test void deathDismountDisconnectAndWrongVehicleNeverLeaveProjectileCameraActive() throws Exception {
        for (int cause=0;cause<4;cause++) {
            var probe=probe(); var mc=Client.instance;
            probe.getMethod("receive", BlizzardTvNetwork.State.class).invoke(null,state(mc,UUID.randomUUID()));
            probe.getMethod("tick",Tick.class).invoke(null,new Tick());
            if(cause==0)mc.player.alive=false;
            if(cause==1)mc.player.vehicle=null;
            if(cause==2)mc.level=null;
            if(cause==3)mc.player.vehicle=new Entity();
            probe.getMethod("tick",Tick.class).invoke(null,new Tick());
            assertSame(mc.player,mc.camera); assertEquals(CameraType.THIRD_PERSON_BACK,mc.options.type);
            assertEquals(12,mc.player.yaw); assertEquals(-7,mc.player.pitch);
        }
    }
    @Test void nativeVehiclePositionOverwrittenAfterAngleEventIsCorrectedAtSetupReturn() throws Exception {
        var probe=probe(); var mc=Client.instance;
        probe.getMethod("receive",BlizzardTvNetwork.State.class).invoke(null,state(mc,UUID.randomUUID()));
        probe.getMethod("tick",Tick.class).invoke(null,new Tick());
        var angles=new Angles(mc.world.missile);
        probe.getMethod("camera",Angles.class).invoke(null,angles);
        // Neo 1.21.1 posts the event before SBW applies the occupied vehicle lens.
        angles.camera.position=mc.world.vehicle.position;
        angles.camera.yaw=12;angles.camera.pitch=20;
        probe.getMethod("afterSetup",Camera.class,float.class).invoke(null,angles.camera,.5F);
        assertEquals(mc.world.missile.position.add(0,.1,0),angles.camera.position);
        assertEquals(50,angles.camera.yaw);assertEquals(-7,angles.camera.pitch);
    }
    @Test void leavingVehicleNeverRestoresTheOldVehicleAsCamera() throws Exception {
        var probe=probe();var mc=Client.instance;mc.camera=mc.world.vehicle;
        probe.getMethod("receive",BlizzardTvNetwork.State.class).invoke(null,state(mc,UUID.randomUUID()));
        probe.getMethod("tick",Tick.class).invoke(null,new Tick());
        mc.player.vehicle=null;
        probe.getMethod("tick",Tick.class).invoke(null,new Tick());
        assertSame(mc.player,mc.camera);
    }
    @Test void cameraFinalizationUsesTheEffectiveSetupTimeOnlyDuringAnOwnedTvSession() throws Exception {
        var probe=probe();var mc=Client.instance;
        var camera=new Camera();camera.entity=mc.world.missile;
        camera.position=new Vec3(1,2,3);camera.yaw=80;camera.pitch=40;
        probe.getMethod("afterSetup",Camera.class,float.class).invoke(null,camera,.125F);
        assertEquals(new Vec3(1,2,3),camera.position);assertEquals(80,camera.yaw);assertEquals(40,camera.pitch);
        probe.getMethod("receive",BlizzardTvNetwork.State.class).invoke(null,state(mc,UUID.randomUUID()));
        probe.getMethod("tick",Tick.class).invoke(null,new Tick());
        mc.world.missile.yRotO=10;mc.world.missile.yaw=50;mc.world.missile.xRotO=-7;mc.world.missile.pitch=9;
        probe.getMethod("afterSetup",Camera.class,float.class).invoke(null,camera,.125F);
        assertEquals(.125F,mc.world.missile.lastPositionPartial);
        assertEquals(15,camera.yaw);assertEquals(-5,camera.pitch);
        camera.entity=mc.player;camera.position=new Vec3(1,2,3);camera.yaw=80;
        probe.getMethod("afterSetup",Camera.class,float.class).invoke(null,camera,.75F);
        assertEquals(new Vec3(1,2,3),camera.position);assertEquals(80,camera.yaw);
        probe.getMethod("close",boolean.class).invoke(null,false);
        camera.entity=mc.world.missile;
        probe.getMethod("afterSetup",Camera.class,float.class).invoke(null,camera,.75F);
        assertEquals(new Vec3(1,2,3),camera.position);assertEquals(80,camera.yaw);
    }
    private static BlizzardTvNetwork.State state(Client mc,UUID nonce) {
        return new BlizzardTvNetwork.State(mc.world.vehicle.id,mc.world.missile.id,nonce,42,true);
    }
    private Class<?> probe() throws Exception {
        Client.instance=new Client(); Network.sent.clear();
        String source="com/liucodex/doggiesnewvehicle/client/BlizzardTvCamera", target="com/liucodex/doggiesnewvehicle/compat/executionprobe/BlizzardCamera";
        Map<String,String> names=new HashMap<>(); names.put(source,target);
        names.put("net/minecraft/client/Minecraft",Type.getInternalName(Client.class));
        names.put("net/minecraft/client/Options",Type.getInternalName(Options.class));
        names.put("net/minecraft/client/gui/screens/Screen",Type.getInternalName(Screen.class));
        names.put("net/minecraft/client/multiplayer/ClientPacketListener",Type.getInternalName(Connection.class));
        names.put("net/minecraft/client/multiplayer/ClientLevel",Type.getInternalName(World.class));
        names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
        names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Entity.class));
        names.put("net/minecraft/client/player/LocalPlayer",Type.getInternalName(Player.class));
        names.put("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardMissile",Type.getInternalName(Missile.class));
        names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardTvNetwork",Type.getInternalName(Network.class));
        names.put("net/minecraft/client/Camera",Type.getInternalName(Camera.class));
        names.put("com/liucodex/doggiesnewvehicle/client/mixin/BlizzardCameraAccess",Type.getInternalName(CameraAccess.class));
        names.put("net/minecraftforge/event/TickEvent$ClientTickEvent",Type.getInternalName(Tick.class));
        names.put("net/minecraftforge/event/TickEvent$Phase",Type.getInternalName(Phase.class));
        names.put("net/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles",Type.getInternalName(Angles.class));
        names.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Type.getInternalName(Tick.class));
        names.put("net/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles",Type.getInternalName(Angles.class));
        ClassNode node=new ClassNode(); try(var in=getClass().getResourceAsStream("/"+source+".class")) { new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES); }
        node.methods.removeIf(m->!Set.of("receive","missile","tick","camera","afterSetup","close","turn").contains(m.name));
        node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
        node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String name){return names.getOrDefault(name,name);}}));
        return new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
    }
    public enum Phase { END }
    public static class Tick { public Phase phase=Phase.END; }
    public static class Screen { }
    public static class Connection { }
    public static class Entity {
        public UUID id=UUID.randomUUID(); public boolean alive=true,removed; public World world; public Entity vehicle;
        public float yaw=12,pitch=-7,yRotO=50,xRotO=-7,lastPositionPartial; public Vec3 position=new Vec3(12,30,-8);
        public UUID getUUID(){return id;} public boolean isAlive(){return alive;} public boolean isRemoved(){return removed;}
        public Entity getVehicle(){return vehicle;} public World level(){return world;}
        public float getYRot(){return yaw;} public float getXRot(){return pitch;}
        public void setYRot(float v){yaw=v;} public void setXRot(float v){pitch=v;} public Vec3 getPosition(float p){lastPositionPartial=p;return position;}
    }
    public static class Missile extends Entity { }
    public static class Player extends Entity { }
    public static class World { public Entity vehicle=new Entity(); public Missile missile=new Missile(); public Entity getEntity(int id){return id==42?missile:null;} }
    public static class Options { public CameraType type=CameraType.THIRD_PERSON_BACK; public CameraType getCameraType(){return type;} public void setCameraType(CameraType v){type=v;} }
    public static class Client {
        public static Client instance; public World world=new World(),level=world; public Player player=new Player(); public Entity camera=player;
        public Options options=new Options(); public Screen screen;
        public Client(){player.vehicle=world.vehicle;player.world=world;world.vehicle.world=world;world.missile.world=world;world.missile.position=new Vec3(40,34,0);world.missile.yaw=50;}
        public static Client getInstance(){return instance;} public Entity getCameraEntity(){return camera;} public void setCameraEntity(Entity e){camera=e;}
        public boolean isWindowActive(){return true;}
        public Connection getConnection(){return level==null?null:new Connection();}
    }
    public static class Network { public static List<BlizzardTvNetwork.Control> sent=new ArrayList<>(); public static void sendControl(BlizzardTvNetwork.Control control){sent.add(control);} }
    public interface CameraAccess { void dnv$blizzardPosition(double x,double y,double z); void dnv$blizzardRotation(float yaw,float pitch); }
    public static class Camera implements CameraAccess {
        public Entity entity;public Vec3 position=Vec3.ZERO;public float yaw,pitch;
        public Entity getEntity(){return entity;}
        public void dnv$blizzardPosition(double x,double y,double z){position=new Vec3(x,y,z);}
        public void dnv$blizzardRotation(float y,float p){yaw=y;pitch=p;}
    }
    public static class Angles {
        public Camera camera=new Camera();public float yaw,pitch,roll=30;
        public Angles(Entity e){camera.entity=e;} public Camera getCamera(){return camera;} public double getPartialTick(){return .5;}
        public void setYaw(float v){yaw=v;}public void setPitch(float v){pitch=v;}public void setRoll(float v){roll=v;}
    }
}
