package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the entity's readiness, scoped driving, tick entry and service/passenger policy. */
class AlliedApocalypseEntityExecutionTest {
    @Test void driverlessEngineIdlesAndStopsForNoPowerWreckDeathOrRemoval() throws Exception {
        var f=new Fixture();assertEquals(true,f.call("engineRunning"));
        f.tank.power=.7f;f.call("synchronizeAim");f.tank.power=0;f.tank.world.isClientSide=true;f.call("synchronizeAim");
        assertEquals(.7f,f.tank.power);assertEquals(true,f.call("engineRunning"));
        f.tank.energy=0;assertEquals(false,f.call("engineRunning"));f.tank.energy=100;
        f.tank.wreck=true;assertEquals(false,f.call("engineRunning"));f.tank.wreck=false;
        f.tank.alive=false;assertEquals(false,f.call("engineRunning"));f.tank.alive=true;
        f.tank.removed=true;assertEquals(false,f.call("engineRunning"));
    }
    @Test void permitExistsOnlyInsideNativeTravelAndAlwaysClearsOnException() throws Exception {
        var f=new Fixture();assertTrue(f.ready());assertFalse(f.permit());f.call("travel");
        assertTrue(f.tank.permitDuringTravel);assertFalse(f.permit());
        f.tank.failTravel=true;assertThrows(java.lang.reflect.InvocationTargetException.class,()->f.call("travel"));
        assertFalse(f.permit());
    }
    @Test void uninitializedClientDeadWreckRemovedOccupiedAndUnloadedCannotDrive() throws Exception {
        for(int failure=0;failure<7;failure++){
            var f=new Fixture();
            switch(failure){
                case 0->f.members.ready=false;case 1->f.tank.world=new World();case 2->f.tank.alive=false;
                case 3->f.tank.wreck=true;case 4->f.tank.removed=true;
                case 5->f.tank.passengers.add(new Body());case 6->((Server)f.tank.world).entities.clear();
            }
            assertFalse(f.ready());f.call("travel");assertFalse(f.tank.permitDuringTravel);assertFalse(f.permit());
        }
    }
    @Test void tickWrapsNativeOnceAndClientDoesNotRunServerAutonomy() throws Exception {
        var f=new Fixture();f.call("tick");assertEquals(List.of("supply","before","native","supply","after"),f.tank.events);
        f.tank.events.clear();f.tank.world.isClientSide=true;f.call("tick");assertEquals(List.of("native"),f.tank.events);
    }
    @Test void serverAimIsMirroredOnClientWithPreviousAnglesPreservedForInterpolation() throws Exception {
        var f=new Fixture();f.tank.yaw=67;f.tank.pitch=-12;f.call("synchronizeAim");
        f.tank.yaw=3;f.tank.pitch=2;f.tank.world.isClientSide=true;f.call("tick");
        assertEquals(67,f.tank.yaw);assertEquals(-12,f.tank.pitch);
        assertEquals(3,f.tank.previousYaw);assertEquals(2,f.tank.previousPitch);
        assertEquals(List.of("native"),f.tank.events);
    }
    @Test void serviceRequiresDeployerAllyOrAdminAndPassengersAlwaysRefused() throws Exception {
        var f=new Fixture();var player=new Player();f.field("deployer",UUID.randomUUID());
        assertFalse((boolean)f.call("mayService",new Class<?>[]{Player.class},player));
        f.field("deployer",player.id);assertTrue((boolean)f.call("mayService",new Class<?>[]{Player.class},player));
        f.field("deployer",UUID.randomUUID());player.ally=true;assertTrue((boolean)f.call("mayService",new Class<?>[]{Player.class},player));
        player.spectator=true;assertFalse((boolean)f.call("mayService",new Class<?>[]{Player.class},player));
        player.spectator=false;player.ally=false;player.admin=true;assertTrue((boolean)f.call("mayService",new Class<?>[]{Player.class},player));
        assertFalse((boolean)f.call("canAddPassenger",new Class<?>[]{Body.class},player));
    }
    static class Fixture{
        final Class<?> type;final Parent tank;final Members members=new Members();
        Fixture()throws Exception{
            String source="com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity",name="com/liucodex/doggiesnewvehicle/compat/executionprobe/AlliedEntity";
            Map<String,String> names=new HashMap<>();names.put(source,name);
            Map.ofEntries(Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity",Parent.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAlliedMembership",Members.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomy",Control.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousSupply",Supply.class),
                    Map.entry("net/minecraft/network/syncher/EntityDataAccessor",Accessor.class),
                    Map.entry("net/minecraft/network/syncher/SynchedEntityData",Data.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousWeapons",Object.class),
                    Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/entity/player/Player",Player.class),
                    Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/server/level/ServerLevel",Server.class))
                    .forEach((k,v)->names.put(k,Type.getInternalName(v)));
            ClassNode n=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(n,ClassReader.SKIP_FRAMES);}
            n.methods.removeIf(m->!Set.of("autonomousReady","nativeDriveAuthorized","travel","tick","synchronizeAim","engineRunning","mayService","canAddPassenger").contains(m.name));
            n.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            n.fields.forEach(field->field.access&=~Opcodes.ACC_FINAL);
            MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,n.superName,"<init>","()V",false));
            init.instructions.add(new InsnNode(Opcodes.RETURN));n.methods.add(init);
            ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}};
            n.accept(new ClassRemapper(out,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            type=new ExactQueuedShotFixture.Loader(Map.of(name,out.toByteArray())).loadClass(name.replace('/','.'));
            tank=(Parent)type.getConstructor().newInstance();((Server)tank.world).entities.put(tank.id,tank);
            field("membership",members);field("autonomy",new Control(tank.events));
            field("supply",new Supply(tank.events));field("AI_YAW",new Accessor());field("AI_PITCH",new Accessor());
            field("AI_POWER",new Accessor());
        }
        void field(String name,Object value)throws Exception{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(tank,value);}
        Object call(String name)throws Exception{return call(name,new Class<?>[0]);}
        Object call(String name,Class<?>[] params,Object...args)throws Exception{return type.getMethod(name,params).invoke(tank,args);}
        boolean ready()throws Exception{return (boolean)call("autonomousReady");}boolean permit()throws Exception{return (boolean)call("nativeDriveAuthorized");}
    }
    public static class World{public boolean isClientSide;}
    public static class Server extends World{public final Map<UUID,Body> entities=new HashMap<>();public Body getEntity(UUID id){return entities.get(id);}}
    public static class Body{public final UUID id=UUID.randomUUID();public UUID getUUID(){return id;}}
    public static class Player extends Body{public boolean spectator,creative,admin,ally;public boolean isSpectator(){return spectator;}public boolean isCreative(){return creative;}public boolean hasPermissions(int level){return admin;}}
    public static class Parent extends Body{
        public World world=new Server();public boolean alive=true,wreck,removed,failTravel,permitDuringTravel;
        public final List<Body> passengers=new ArrayList<>();public final List<String> events=new ArrayList<>();
        public final Data entityData=new Data();public float yaw,pitch,previousYaw,previousPitch,power;public int energy=100;
        public int getEnergy(){return energy;}public float getPower(){return power;}public void setPower(float value){power=value;}
        public float getTurretYRot(){return yaw;}public float getTurretXRot(){return pitch;}
        public void setTurretYRot(float value){yaw=value;}public void setTurretXRot(float value){pitch=value;}
        public World level(){return world;}public boolean isAlive(){return alive;}public boolean isWreck(){return wreck;}public boolean isRemoved(){return removed;}
        public List<Body> getPassengers(){return passengers;}public boolean isAlliedTo(Body player){return player instanceof Player p&&p.ally;}
        public void travel(){try{permitDuringTravel=(boolean)getClass().getMethod("nativeDriveAuthorized").invoke(this);}catch(Exception e){throw new AssertionError(e);}if(failTravel)throw new IllegalStateException("native failure");}
        public void tick(){previousYaw=yaw;previousPitch=pitch;events.add("native");}
    }
    public static class Members{public boolean ready=true;public boolean initialized(){return ready;}}
    public static class Control{private final List<String> events;public Control(List<String> events){this.events=events;}public void beforeNativeTick(){events.add("before");}public void afterNativeTick(){events.add("after");}}
    public static class Supply{private final List<String> events;public Supply(List<String> events){this.events=events;}public void tick(){events.add("supply");}}
    public static class Accessor{}
    public static class Data{private final Map<Accessor,Object> values=new HashMap<>();public Object get(Accessor key){return values.getOrDefault(key,0f);}public void set(Accessor key,Object value){values.put(key,value);}}
}
