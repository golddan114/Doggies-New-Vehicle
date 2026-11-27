package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Exact SBW client entry before/after real Mixin application and compiled DNV target validation.
 * Actor/world/network/audio are explicit ports; this does not claim Minecraft input or audible playback. */
class ApocalypseClientFireExecutionTest {
    @TempDir Path temp;
    private static final String SBW="com/atsuishio/superbwarfare/";
    private static final String CLIENT=SBW+"event/ClientEventHandler";
    private static final String TARGETING="com/liucodex/doggiesnewvehicle/armored/ApocalypseTargeting";
    private static final String TANK="com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity";
    private static final String ADMISSION="com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotAdmission";
    private static final String ARMORED="com/liucodex/doggiesnewvehicle/armored/";
    private static final String PROBE="com/liucodex/doggiesnewvehicle/compat/clientfireprobe/";

    @Test void aircraftBodyInTheReticleUsesTheSamePointForAcquisitionAndRetention() throws Exception {
        var fixture = new Fixture(true);
        fixture.player.vehicle = fixture.tank;
        var aircraft = new Vehicle();
        aircraft.world = fixture.tank.world;
        var origin = fixture.tank.lockOrigin();
        var before = angleBuilder(false);
        var after = angleBuilder(true);
        for (double altitude : List.of(12.0, 100.0, 254.5)) {
            aircraft.anchor = origin.add(4, altitude - 5, 12);
            aircraft.box = new AABB(aircraft.anchor, aircraft.anchor.add(0, 10, 0)).inflate(1, 0, 1);
            Vec3 direction = origin.vectorTo(aircraft.box.getCenter()).normalize();
            // The exact dependency's previous angle calculation fails the close flyby.
            if (altitude < 20) assertFalse(acquires(before, fixture.player, origin, direction, aircraft));
            assertTrue(acquires(after, fixture.player, origin, direction, aircraft));
            assertFalse(acquires(after, fixture.player, origin, direction.reverse(), aircraft));
            fixture.state("lockingEntityVehicle", aircraft);
            fixture.fire(fixture.tank);
            assertEquals(aircraft.id, Network.packets.get(Network.packets.size() - 1).target);
            int sent = Network.packets.size();
            fixture.tank.world.loaded = false;
            fixture.fire(fixture.tank);
            assertEquals(sent, Network.packets.size(), "an untracked column must still prevent sending");
            fixture.tank.world.loaded = true;
        }
        fixture.tank.group = 1;
        aircraft.anchor = origin.add(4, 7, 12);
        aircraft.box = new AABB(aircraft.anchor, aircraft.anchor.add(0, 10, 0));
        assertFalse(acquires(after, fixture.player, origin, origin.vectorTo(aircraft.box.getCenter()).normalize(), aircraft));
    }

    @SuppressWarnings("unchecked")
    private static boolean acquires(Class<?> type, Player seeker, Vec3 origin, Vec3 view, Body target) throws Exception {
        var query = type.getConstructor(Body.class).newInstance(seeker);
        type.getMethod("withinAngle", Vec3.class, Vec3.class, double.class).invoke(query, origin, view, 10.0);
        var filters = (List<Predicate<Body>>) type.getField("filters").get(query);
        return filters.size() == 1 && filters.get(0).test(target);
    }

    private Class<?> angleBuilder(boolean fixed) throws Exception {
        String seek = SBW + "tools/SeekTool", builder = seek + "$Builder";
        String vector = SBW + "tools/VectorTool";
        String vectorKt = vector + "Kt";
        var transform = new DnvMuzzleMixinApplicationTest();
        transform.temp = temp;
        byte[][] bytes;
        try (var engine = transform.loader()) {
            bytes = DnvMuzzleMixinApplicationTest.transform(engine, "tools/SeekTool$Builder",
                    "ApocalypseSeekFilterMixin", "CLIENT", false);
        }
        var query = new ClassNode();
        new ClassReader(bytes[fixed ? 1 : 0]).accept(query, ClassReader.SKIP_FRAMES);
        query.methods.removeIf(m -> !(m.name.equals("withinAngle") && m.desc.startsWith("(Lnet/minecraft/world/phys/Vec3;"))
                && !(m.name.equals("custom") && m.desc.startsWith("(Ljava/util/function/Predicate;"))
                && !m.name.contains("withinAngle$lambda$37") && !m.name.contains("dnv$airframeAim"));
        query.fields.removeIf(f -> !Set.of("entity", "filters").contains(f.name));
        query.fields.forEach(f -> f.access = Opcodes.ACC_PUBLIC);
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/entity/Entity;)V", null, null);
        var code = ctor.instructions;
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, builder, "entity", "Lnet/minecraft/world/entity/Entity;"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, builder, "filters", "Ljava/util/List;"));
        code.add(new InsnNode(Opcodes.RETURN));
        query.methods.add(ctor);

        var names = new HashMap<String, String>();
        names.put(builder, PROBE + "Angles");
        names.put(seek, PROBE + "NativeAngle");
        names.put(vector, PROBE + "NativePoint");
        names.put(vectorKt, PROBE + "NativeAngleTo");
        map(names, "net/minecraft/world/entity/Entity", Body.class);
        map(names, "net/minecraft/world/entity/player/Player", Player.class);
        map(names, TANK, Tank.class);
        var methodNames = new HashMap<String, String>();
        var fieldNames = new HashMap<String, String>();
        var mappings = Path.of("build/createSrgToMcp/output.srg");
        if (Files.exists(mappings)) for (String line : Files.readAllLines(mappings)) {
            var columns = line.split(" ");
            if (columns[0].equals("MD:")) methodNames.put(simple(columns[1]), simple(columns[3]));
            if (columns[0].equals("FD:")) fieldNames.put(simple(columns[1]), simple(columns[2]));
        }
        var definitions = new HashMap<String, byte[]>();
        definitions.put(PROBE + "Angles", write(query, names, methodNames, fieldNames));
        try (var jar = new java.util.zip.ZipFile(System.getProperty("dnv.sbw.jar"))) {
            for (String owner : List.of(seek, vector, vectorKt)) {
                var node = new ClassNode();
                try (var in = jar.getInputStream(jar.getEntry(owner + ".class"))) {
                    new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
                }
                node.methods.removeIf(m -> !(m.name.equals("calculateAngle") && m.desc.startsWith("(Lnet/minecraft/world/phys/Vec3;"))
                        && !m.name.equals("lerpGetEntityBoundingBoxCenter") && !m.name.equals("angleTo"));
                node.fields.clear();
                definitions.put(names.get(owner), write(node, names, methodNames, fieldNames));
            }
        }
        return new ExactQueuedShotFixture.Loader(definitions).loadClass((PROBE + "Angles").replace('/', '.'));
    }

    @Test void nativeEntryReproducesRepeatedUnsentMissilesWithLocalFireSounds() throws Exception {
        var f=new Fixture(false);f.state("lockOnVehicle",false);f.state("lockingEntityVehicle",null);
        for(int i=0;i<40;i++)f.fire(f.tank);
        assertEquals(40,Network.packets.size());assertEquals(40,f.handler.sounds);
        assertTrue(Network.packets.stream().allMatch(p->p.target==null));
    }

    @Test void missingAndIncompleteLocksStaySilentAndHoldingFireRecoversAfterLock() throws Exception {
        var f=new Fixture(true);f.state("lockOnVehicle",true);f.state("lockingEntityVehicle",null);
        for(int i=0;i<40;i++)f.fire(f.tank);
        f.state("lockingEntityVehicle",f.target);f.state("lockOnVehicle",false);f.fire(f.tank);
        assertTrue(Network.packets.isEmpty());assertEquals(0,f.handler.sounds);
        assertEquals(true,f.state("holdFireVehicle"));assertEquals(41,f.state("seekingTimeVehicle"));
        f.state("lockOnVehicle",true);f.fire(f.tank);
        assertEquals(1,Network.packets.size());assertEquals(f.target.id,Network.packets.get(0).target);
        assertEquals(0,f.handler.sounds);assertEquals(true,f.state("holdFireVehicle"));
    }

    @Test void staleInvalidAndOccludedTargetsCannotProduceAPacketOrPredictedSound() throws Exception {
        for(int reason=0;reason<12;reason++){
            var f=new Fixture(true);
            switch(reason){
                case 0->f.target.alive=false;case 1->f.target.removed=true;
                case 2->f.target.world=new World();case 3->f.target.friendly=true;
                case 4->f.target.smoke=true;case 5->f.tank.data.seek.range=2;
                case 6->f.tank.world.loaded=false;case 7->f.tank.world.blocked=true;
                case 8->f.tank.passenger=new Player();case 9->f.player.alive=false;
                case 10->f.tank.wreck=true;case 11->f.player.spectator=true;
            }
            f.fire(f.tank);assertTrue(Network.packets.isEmpty(),"reason="+reason);assertEquals(0,f.handler.sounds);
        }
    }

    @Test void ownedGunsDeferLocalSoundButOtherVehiclesRetainNativePrediction() throws Exception {
        var f=new Fixture(true);f.state("lockOnVehicle",false);f.state("lockingEntityVehicle",null);
        for(int group=0;group<2;group++){f.tank.group=group;f.fire(f.tank);}
        f.fire(new Vehicle());f.fire(f.defaultVehicle);assertEquals(4,Network.packets.size());assertEquals(2,f.handler.sounds);
    }

    @Test void blockedOrUnloadedMuzzlesStaySilentAndAllThreeVehiclesRecoverWhileHeld() throws Exception {
        var f=new Fixture(true);f.state("lockOnVehicle",false);f.state("lockingEntityVehicle",null);
        for (Tank tank : List.of(f.tank, f.blizzard, f.project)) {
            tank.group = tank == f.blizzard ? 1 : 0;
            for (int reason=0;reason<3;reason++) {
                int before=Network.packets.size(), sounds=f.handler.sounds;
                tank.world.blocked=reason==0;tank.world.loaded=reason!=1;tank.pose=reason!=2;
                if(reason==2 && tank!=f.tank)continue;
                for(int i=0;i<20;i++)f.fire(tank);
                assertEquals(before,Network.packets.size());assertEquals(sounds,f.handler.sounds);
                assertEquals(true,f.state("holdFireVehicle"));
                tank.world.blocked=false;tank.world.loaded=true;tank.pose=true;f.fire(tank);
                assertEquals(before+1,Network.packets.size());assertEquals(sounds,f.handler.sounds);
            }
        }
    }

    @Test void clientSeatLifecycleAndStaleGunRejectAndTvDoesNotRequireALock() throws Exception {
        var f=new Fixture(true);f.state("lockOnVehicle",false);f.state("lockingEntityVehicle",null);
        for(Tank tank:List.of(f.tank,f.blizzard,f.project)) {
            tank.group=tank==f.blizzard?1:0;
            for(int reason=0;reason<6;reason++) {
                int before=Network.packets.size();
                if(reason==0)tank.passenger=null;if(reason==1)tank.removed=true;
                if(reason==2)f.player.alive=false;if(reason==3)f.player.spectator=true;
                if(reason==4)tank.wreck=true;
                f.fire(tank,reason==5?new Data():tank.data);assertEquals(before,Network.packets.size());
                tank.passenger=f.player;tank.removed=false;f.player.alive=true;f.player.spectator=false;tank.wreck=false;
            }
            f.fire(tank);assertNull(Network.packets.get(Network.packets.size()-1).target);
        }
        int before=Network.packets.size();f.blizzard.tv=true;f.fire(f.blizzard);
        assertEquals(before,Network.packets.size());f.blizzard.tv=false;f.blizzard.powered=false;f.fire(f.blizzard);
        assertEquals(before,Network.packets.size());f.blizzard.powered=true;f.fire(f.blizzard);
        assertEquals(before+1,Network.packets.size());
    }

    @Test void missileGateIsClientOnlyAndAppliedExactlyOnce() throws Exception {
        var test=new DnvMuzzleMixinApplicationTest();test.temp=temp;
        for(String side:List.of("CLIENT","SERVER"))try(var engine=test.loader()){
            var bytes=transformClient(engine,side);
            var node=new ClassNode();new ClassReader(bytes[1]).accept(node,0);
            long hooks=node.methods.stream().filter(m->m.name.equals("clientShootVehicle"))
                    .flatMap(m->Arrays.stream(m.instructions.toArray())).filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).filter(m->m.name.contains("dnv$missileLock")).count();
            assertEquals(side.equals("CLIENT")?1:0,hooks,side);
        }
    }

    @Test void heldReadyRocketsAndMissilesSendRequestsWithoutPlayingUnconfirmedSounds() throws Exception {
        for (int group : List.of(0,1,2)) {
            var f=new Fixture(true);f.tank.group=group;
            for(int i=0;i<40;i++)f.fire(f.tank);
            assertEquals(40,Network.packets.size());assertEquals(0,f.handler.sounds);
            assertEquals(true,f.state("holdFireVehicle"));
        }
    }

    @Test void acceptedAudioUsesConfirmedGunAndCoversFirstPersonAndThirdPersonZoomWithoutDuplicates() throws Exception {
        var f=new Fixture(true);
        for (Tank tank : List.of(f.tank,f.blizzard,f.project)) {
            Client.INSTANCE.player=f.player;f.player.vehicle=tank;
            for (int view=0;view<3;view++) {
                Client.INSTANCE.options.view=View.values()[view];
                for(boolean zoom:List.of(false,true)) {
                    f.state("zoomVehicle",zoom);Client.INSTANCE.level.sounds.clear();
                    f.accept(new Accepted(tank,f.player,0,"confirmed"));
                    assertEquals(view==0||zoom?1:0,Client.INSTANCE.level.sounds.size());
                    if(view==0||zoom)assertEquals("confirmed-1p",Client.INSTANCE.level.sounds.get(0).name);
                }
            }
            Client.INSTANCE.options.view=View.FIRST_PERSON;f.state("zoomVehicle",false);
            for (int invalid=0;invalid<5;invalid++) {
                Client.INSTANCE.level.sounds.clear();
                if(invalid==0)f.player.vehicle=null;
                if(invalid==1)Client.INSTANCE.player=null;
                f.accept(new Accepted(tank,invalid==2?new Player():f.player,0,invalid==3?null:invalid==4?"missing":"confirmed"));
                assertTrue(Client.INSTANCE.level.sounds.isEmpty());
                Client.INSTANCE.player=f.player;f.player.vehicle=tank;
            }
        }
    }

    private final class Fixture {
        final Player player=new Player();final Tank tank,blizzard,project;final Vehicle defaultVehicle;final Hostile target=new Hostile();
        final ClientBase handler;final Class<?> client, feedback;
        Fixture(boolean transformed) throws Exception {
            target.box=new AABB(99,65,-1,101,67,1);Network.packets.clear();Client.INSTANCE=new Client();
            var names=new HashMap<String,String>();names.put(CLIENT,PROBE+"Client");
            map(names,SBW+"entity/vehicle/base/VehicleEntity",Vehicle.class);
            map(names,"com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity",Tank.class);
            map(names,ARMORED+"DnvBlizzardEntity",Tank.class);map(names,ARMORED+"DnvProject612Entity",Tank.class);
            for(String owner:List.of("ApocalypseArmament","BlizzardMissileArmament","Project612Armament"))map(names,ARMORED+owner,Clearance.class);
            map(names,ARMORED+"BlizzardRig",Rig.class);names.put(ARMORED+"BlizzardLoadedPath",PROBE+"LoadedPath");
            map(names,TARGETING,TargetPort.class);
            map(names,ADMISSION,Gate.class);
            map(names,SBW+"entity/vehicle/base/AutoAimableEntity",OwnedVehicle.class);
            map(names,SBW+"data/gun/GunData",Data.class);map(names,SBW+"data/gun/GunProp",Prop.class);
            map(names,SBW+"data/gun/SeekWeaponInfo",SeekInfo.class);map(names,SBW+"tools/SeekTool",Seek.class);
            map(names,SBW+"tools/MinecraftUtil",Network.class);map(names,SBW+"network/message/send/VehicleFireMessage",Packet.class);
            map(names,"net/minecraft/world/entity/Entity",Body.class);map(names,"net/minecraft/world/entity/LivingEntity",Living.class);
            map(names,"net/minecraft/world/entity/player/Player",Player.class);map(names,"net/minecraft/world/entity/monster/Enemy",Enemy.class);
            map(names,"net/minecraft/world/level/Level",World.class);map(names,"net/minecraft/world/level/border/WorldBorder",Border.class);
            map(names,"net/minecraft/world/level/LevelAccessor",WorldAccess.class);map(names,"net/minecraft/world/level/LevelReader",WorldAccess.class);
            map(names,"net/minecraft/world/level/ClipContext",Clip.class);map(names,"net/minecraft/world/level/ClipContext$Block",Clip.Block.class);
            map(names,"net/minecraft/world/level/ClipContext$Fluid",Clip.Fluid.class);
            map(names,"net/minecraft/client/Minecraft",Client.class);map(names,"net/minecraft/client/Options",Options.class);
            map(names,"net/minecraft/client/CameraType",View.class);
            map(names,"net/minecraft/client/player/LocalPlayer",Player.class);
            map(names,"net/minecraft/client/multiplayer/ClientLevel",AudioWorld.class);
            map(names,"net/minecraft/client/renderer/GameRenderer",Renderer.class);map(names,"net/minecraft/client/Camera",Camera.class);
            map(names,"net/minecraft/sounds/SoundEvent",Sound.class);map(names,"net/minecraft/sounds/SoundSource",Channel.class);
            map(names,SBW+"data/gun/SoundInfo",AudioInfo.class);map(names,SBW+"api/event/ClientVehicleFireEvent",Accepted.class);
            map(names,"kotlin/jvm/internal/DefaultConstructorMarker",Object.class);
            map(names,"net/minecraft/network/protocol/common/custom/CustomPacketPayload",Packet.class);
            var methodNames=new HashMap<String,String>();var fieldNames=new HashMap<String,String>();
            // Neo bytecode already uses official names; Forge uses its current exact mapping output.
            var mapping=Path.of("build/createSrgToMcp/output.srg");
            if(Files.exists(mapping))for(String line:Files.readAllLines(mapping)){
                var p=line.split(" ");if(p[0].equals("MD:"))methodNames.put(simple(p[1]),simple(p[3]));
                if(p[0].equals("FD:"))fieldNames.put(simple(p[1]),simple(p[2]));
            }
            var definitions=new HashMap<String,byte[]>();var targeting=compiled(TARGETING);
            targeting.methods.removeIf(m->!Set.of("<init>","valid","eligible","loadedRay","loadedLockRay","lambda$eligible$0").contains(m.name));
            targeting.superName=Type.getInternalName(TargetPort.class);
            for(var m:targeting.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals("java/lang/Object"))call.owner=targeting.superName;
            var targetNames=new HashMap<>(names);targetNames.put(TARGETING,PROBE+"Targeting");
            targeting.methods.forEach(m->m.access=(m.access&Opcodes.ACC_STATIC)|Opcodes.ACC_PUBLIC);
            definitions.put(PROBE+"Targeting",write(targeting,targetNames,methodNames,fieldNames));
            for(String owner:List.of("DnvApocalypseEntity","DnvBlizzardEntity","DnvProject612Entity")) {
                var tankNode=compiled(ARMORED+owner);
                tankNode.methods.removeIf(m->!Set.of("permitsNativeClientFire","deferNativeClientSound").contains(m.name));tankNode.fields.clear();tankNode.interfaces.clear();
                tankNode.interfaces.add(Type.getInternalName(Gate.class));tankNode.superName=Type.getInternalName(Tank.class);constructor(tankNode);
                var tankNames=new HashMap<>(names);tankNames.put(ARMORED+owner,PROBE+owner);
                definitions.put(PROBE+owner,write(tankNode,tankNames,methodNames,fieldNames));
            }
            definitions.put(PROBE+"LoadedPath",write(compiled(ARMORED+"BlizzardLoadedPath"),names,methodNames,fieldNames));
            for(String owner:List.of("ApocalypseArmament","BlizzardMissileArmament","Project612Armament")) {
                var clearance=compiled(ARMORED+owner);
                clearance.methods.removeIf(m->!Set.of("clear","muzzleClear").contains(m.name));
                clearance.fields.removeIf(field->!field.name.equals("tank"));clearance.fields.forEach(field->field.access=Opcodes.ACC_PUBLIC);
                clearance.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC);clearance.access=Opcodes.ACC_PUBLIC;
                clearance.superName=Type.getInternalName(Clearance.class);constructor(clearance);
                var clearNames=new HashMap<>(names);clearNames.put(ARMORED+owner,PROBE+owner);clearNames.put(TARGETING,PROBE+"Targeting");
                definitions.put(PROBE+owner,write(clearance,clearNames,methodNames,fieldNames));
            }
            // Execute the actual interface default as well, so other DNV weapon owners remain unchanged.
            var api=compiled(ADMISSION);
            api.access=Opcodes.ACC_PUBLIC;api.methods.removeIf(m->!m.name.equals("permitsNativeClientFire"));api.interfaces.clear();
            api.interfaces.add(Type.getInternalName(Gate.class));api.superName=Type.getInternalName(Vehicle.class);constructor(api);
            var defaultNames=new HashMap<>(names);defaultNames.put(ADMISSION,PROBE+"DefaultVehicle");
            definitions.put(PROBE+"DefaultVehicle",write(api,defaultNames,methodNames,fieldNames));
            var test=new DnvMuzzleMixinApplicationTest();test.temp=temp;byte[][] bytes;
            try(var engine=test.loader()){bytes=transformClient(engine,"CLIENT");}
            var node=new ClassNode();new ClassReader(bytes[transformed?1:0]).accept(node,ClassReader.SKIP_FRAMES);
            node.methods.removeIf(m->!m.name.equals("clientShootVehicle")&&!m.name.contains("dnv$missileLock")&&!m.name.contains("dnv$acceptedLocalSound"));
            node.fields.removeIf(f->!Set.of("lockingEntityVehicle","lockingPosVehicle","missileLockingPos","zoomVehicle","lockOnVehicle","holdFireVehicle","seekingTimeVehicle").contains(f.name));
            node.superName=Type.getInternalName(ClientBase.class);node.interfaces.clear();node.access=Opcodes.ACC_PUBLIC;
            var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
            ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
            definitions.put(PROBE+"Client",write(node,names,methodNames,fieldNames));
            String events="com/liucodex/doggiesnewvehicle/artillery/client/DnvArtilleryClientEvents$GameEvents";
            var eventsNode=compiled(events);eventsNode.methods.removeIf(m->!m.name.equals("acceptedFire"));eventsNode.fields.clear();
            var eventNames=new HashMap<>(names);eventNames.put(events,PROBE+"Feedback");
            definitions.put(PROBE+"Feedback",write(eventsNode,eventNames,methodNames,fieldNames));
            var loader=new ExactQueuedShotFixture.Loader(definitions);
            tank=(Tank)loader.loadClass((PROBE+"DnvApocalypseEntity").replace('/','.')).getConstructor().newInstance();
            blizzard=(Tank)loader.loadClass((PROBE+"DnvBlizzardEntity").replace('/','.')).getConstructor().newInstance();
            project=(Tank)loader.loadClass((PROBE+"DnvProject612Entity").replace('/','.')).getConstructor().newInstance();
            defaultVehicle=(Vehicle)loader.loadClass((PROBE+"DefaultVehicle").replace('/','.')).getConstructor().newInstance();
            player.world=tank.world;target.world=tank.world;tank.passenger=player;
            for(var entry:Map.of(tank,"ApocalypseArmament",blizzard,"BlizzardMissileArmament",project,"Project612Armament").entrySet()) {
                var host=entry.getKey();host.passenger=player;host.world=tank.world;
                var clearance=loader.loadClass((PROBE+entry.getValue()).replace('/','.')).getConstructor().newInstance();
                clearance.getClass().getField("tank").set(clearance,host);
                host.armament=host.missiles=(Clearance)clearance;
            }
            blizzard.group=1;project.group=0;
            tank.targeting=(TargetPort)loader.loadClass((PROBE+"Targeting").replace('/','.')).getConstructor(Tank.class).newInstance(tank);
            client=loader.loadClass((PROBE+"Client").replace('/','.'));handler=(ClientBase)client.getConstructor().newInstance();
            feedback=loader.loadClass((PROBE+"Feedback").replace('/','.'));
            state("lockOnVehicle",true);state("lockingEntityVehicle",target);state("holdFireVehicle",true);state("seekingTimeVehicle",41);
        }
        void fire(Vehicle vehicle) throws Exception {client.getMethod("clientShootVehicle",Player.class,Vehicle.class,Data.class).invoke(handler,player,vehicle,vehicle.getGunData(0));}
        void fire(Vehicle vehicle,Data gun) throws Exception {client.getMethod("clientShootVehicle",Player.class,Vehicle.class,Data.class).invoke(handler,player,vehicle,gun);}
        void state(String name,Object value) throws Exception {client.getField(name).set(null,value);}
        Object state(String name) throws Exception {return client.getField(name).get(null);}
        void accept(Accepted event) throws Exception {feedback.getMethod("acceptedFire",Accepted.class).invoke(null,event);}
    }

    private static ClassNode compiled(String name) throws Exception {
        var node=new ClassNode();String artifact=System.getProperty("dnv.artifact", "");
        if(!artifact.isBlank())try(var jar=new java.util.zip.ZipFile(artifact)) {
            var entry=java.util.Objects.requireNonNull(jar.getEntry(name+".class"),name);
            try(var input=jar.getInputStream(entry)){new ClassReader(input.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            return node;
        }
        try(var input=ApocalypseClientFireExecutionTest.class.getResourceAsStream("/"+name+".class")){
            new ClassReader(input.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
        return node;
    }

    private static byte[][] transformClient(ClassLoader engine,String side) throws Exception {
        String artifact=System.getProperty("dnv.artifact", ""), runtime=System.getProperty("dnv.minecraft.srg", "");
        if(!artifact.isBlank()&&!runtime.isBlank())return (byte[][])engine
                .loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService")
                .getMethod("transformReleaseOnSide",String.class,String.class,String.class,String.class,String.class,boolean.class)
                .invoke(null,runtime,artifact,CLIENT,"ApocalypseClientFireMixin",side,false);
        return DnvMuzzleMixinApplicationTest.transform(engine,"event/ClientEventHandler","ApocalypseClientFireMixin",side,false);
    }

    private static void constructor(ClassNode node){
        var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
    }

    private static byte[] write(ClassNode node,Map<String,String> names,Map<String,String> methods,Map<String,String> fields){
        node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;node.visibleAnnotations=null;
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
            @Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}};
        node.accept(new ClassRemapper(writer,new Remapper(){
            @Override public String map(String n){return names.getOrDefault(n,n);}
            @Override public String mapMethodName(String owner,String name,String desc){return methods.getOrDefault(name,name);}
            @Override public String mapFieldName(String owner,String name,String desc){return fields.getOrDefault(name,name);}
        }));return writer.toByteArray();
    }
    private static String simple(String name){return name.substring(name.lastIndexOf('/')+1);}
    private static void map(Map<String,String> names,String name,Class<?> type){names.put(name,Type.getInternalName(type));}
    public static class Body {
        public UUID id=UUID.randomUUID();public World world=new World();public boolean alive=true,removed,spectator,friendly,smoke;
        public AABB box=new AABB(0,65,0,1,67,1);
        public Vec3 anchor=Vec3.ZERO;
        public double xo,yo,zo;
        public Vec3 position(){return anchor;}
        public double getX(){return anchor.x;}public double getY(){return anchor.y;}public double getZ(){return anchor.z;}
        public float getBbHeight(){return (float)box.getYsize();}
        public UUID getUUID(){return id;}public World level(){return world;}public boolean isAlive(){return alive;}
        public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}public boolean isAlliedTo(Body other){return friendly||other.friendly;}
        public Body getRootVehicle(){return this;}public AABB getBoundingBox(){return box;}public Stream<Body> getSelfAndPassengers(){return Stream.of(this);}
    }
    public static class Living extends Body {}public interface Enemy {}public static class Hostile extends Living implements Enemy {}
    public static class Player extends Living {public Body vehicle;public Body getVehicle(){return vehicle;}public boolean isCreative(){return false;}}
    public static class Vehicle extends Body {public Data data=new Data();public boolean wreck;
        public boolean isWreck(){return wreck;}public String getLastDriverUUID(){return "";}public Data getGunData(int seat){return data;}
        public Data getGunData(String name){return name.equals("missing")?null:name.equals("confirmed")?new Data("confirmed-1p"):data;}}
    public static class OwnedVehicle extends Vehicle {public Optional<UUID> getOptionalOwnerUUID(){return Optional.empty();}}
    public static class Tank extends Vehicle {
        public int group=2;public Player passenger;public TargetPort targeting;public Clearance armament,missiles;
        public boolean pose=true,powered=true,tv;
        public int getWeaponIndex(int seat){return group;}public Body getFirstPassenger(){return passenger;}
        public TargetPort targeting(){return targeting;}public Vec3 lockOrigin(){return new Vec3(.5,66,.5);}
        public Data getGunData(String name){return super.getGunData(name);}
        public String getGunName(int seat){return group==2?"Missiles":"Main";}
        public boolean poseClear(){return pose;}public boolean muzzleClear(String name,Vec3 muzzle){return armament.muzzleClear(name,muzzle);}
        public Vec3 firingPivot(){return lockOrigin();}public Vec3 firingPivot(String name){return lockOrigin();}
        public Vec3 getShootPos(String name,float partial){return lockOrigin().add(0,0,3);}
        public Vec3 getShootVec(String name,float partial){return new Vec3(0,0,1);}
        public org.joml.Matrix4d groupTransform(String name,float partial){return new org.joml.Matrix4d().translation(.5,66,.5);}
        public boolean driverReady(){return powered&&!tv&&!wreck&&passenger!=null&&passenger.alive&&!passenger.spectator;}
    }
    public static class Clearance {
        public boolean clear(){throw new AssertionError("Execute compiled muzzle query");}
        public boolean muzzleClear(String name,Vec3 muzzle){throw new AssertionError("Execute compiled muzzle query");}
    }
    public static class Rig {
        public static final Rig INSTANCE=new Rig();public Vec3 yaw=Vec3.ZERO;
        public static Vec3 point(org.joml.Matrix4d matrix,Vec3 p){var v=matrix.transformPosition(new org.joml.Vector3d(p.x,p.y,p.z));return new Vec3(v.x,v.y,v.z);}
    }
    public static class TargetPort {public boolean valid(Body target,Player player){throw new AssertionError("Execute compiled DNV validation");}}
    public interface Gate {boolean permitsNativeClientFire(Body actor,Body target,boolean locked,Data gun);default boolean deferNativeClientSound(){return false;}}
    public enum Prop {SEEK_WEAPON_INFO,SOUND_INFO}
    public static class Data {public SeekInfo seek=new SeekInfo();public AudioInfo audio;Data(){this("selected-1p");}Data(String sound){audio=new AudioInfo(sound);}public Object get(Prop prop){return prop==Prop.SOUND_INFO?audio:seek;}}
    public static class SeekInfo {public double range=256;public double getSeekRange(){return range;}public boolean getInputBlockPos(){return false;}}
    public static class Seek {public static final Predicate<Body> NOT_IN_SMOKE=e->!e.smoke;public static final java.util.function.BiPredicate<Body,Body> IS_FRIENDLY=(a,b)->a.friendly||b.friendly;}
    public interface WorldAccess {boolean hasChunk(int x,int z);}
    public static class World implements WorldAccess {
        public boolean loaded=true,blocked;public boolean hasChunkAt(BlockPos p){return loaded;}
        public boolean hasChunk(int x,int z){return loaded;}
        public Border getWorldBorder(){return new Border();}public boolean isOutsideBuildHeight(BlockPos p){return p.getY()<-64||p.getY()>=320;}
        public BlockHitResult clip(Clip ray){var p=BlockPos.containing(ray.to);return blocked?new BlockHitResult(ray.to,Direction.UP,p,false):BlockHitResult.miss(ray.to,Direction.UP,p);}
    }
    public static class Border {public boolean isWithinBounds(BlockPos p){return true;}}
    public static class Clip {public enum Block{COLLIDER}public enum Fluid{NONE}public Vec3 to;
        public Clip(Vec3 from,Vec3 to,Block block,Fluid fluid,Body source){this.to=to;}}
    public enum View {FIRST_PERSON,THIRD_PERSON_BACK,THIRD_PERSON_FRONT;public boolean isFirstPerson(){return this==FIRST_PERSON;}}
    public static class Options {public View view=View.FIRST_PERSON;public View getCameraType(){return view;}}
    public static class Client {public static Client INSTANCE;public Options options=new Options();public Player player;public AudioWorld level=new AudioWorld();public Renderer gameRenderer=new Renderer();public static Client getInstance(){return INSTANCE;}}
    public static class Network {public static List<Packet> packets=new ArrayList<>();public static Client getMc(){return Client.INSTANCE;}
        public static void sendPacketToServer(Object packet){packets.add((Packet)packet);}
        public static void sendPacketToServer(Packet packet){packets.add(packet);}}
    public static class Packet {public final UUID target;
        public Packet(UUID target,Vector3f point,String weapon,Integer seat,int defaults,Object ignored){this.target=target;}}
    public static class ClientBase {public int sounds;public void playVehicleClientSounds(Player player,Vehicle vehicle){sounds++;}}
    public record Accepted(Vehicle vehicle,Body shooter,int index,String weaponName){public Vehicle getVehicle(){return vehicle;}public Body getShooter(){return shooter;}public String getWeaponName(){return weaponName;}}
    public record Sound(String name){}
    public static class AudioInfo {private final Sound sound;AudioInfo(String name){sound=new Sound(name);}public Sound getFire1P(){return sound;}}
    public enum Channel {PLAYERS}
    public static class AudioWorld {public List<Sound> sounds=new ArrayList<>();public void playLocalSound(double x,double y,double z,Sound sound,Channel channel,float volume,float pitch,boolean delay){assertEquals(new Vec3(x,y,z),new Vec3(8,9,10));sounds.add(sound);}}
    public static class Renderer {public Camera getMainCamera(){return new Camera();}}
    public static class Camera {public Vec3 getPosition(){return new Vec3(8,9,10);}}
}
