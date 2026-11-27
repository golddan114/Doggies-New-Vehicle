package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.util.*;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes the compiled client emission entry; camera/options and particle backend are explicit ports. */
public class MothershipWreckParticlesExecutionTest {
    @Test void remoteHullEmitsBeyondVanilla32OnlyOncePerTickAndNeverForLiveRemovedOrOtherWorld()throws Exception {
        Client client=Client.INSTANCE;client.level=new World();client.options.value=ParticleStatus.ALL;
        Actor ship=new Actor(client.level);Class<?> type=load();var emit=type.getMethod("emit",Actor.class);
        emit.invoke(null,ship);assertEquals(28,client.level.points.size());assertEquals(14,client.level.flames);
        assertTrue(client.level.points.stream().allMatch(p->p.distanceToSqr(client.gameRenderer.camera.point)>32*32));
        emit.invoke(null,ship);assertEquals(28,client.level.points.size(),"shadow/extra render pass cannot double emission");
        client.level.now++;ship.removed=true;emit.invoke(null,ship);ship.removed=false;ship.wreck=false;emit.invoke(null,ship);
        assertEquals(28,client.level.points.size());
        ship.wreck=true;client.level=new World();emit.invoke(null,ship);assertTrue(client.level.points.isEmpty());
        ship.world=client.level;emit.invoke(null,ship);assertEquals(28,client.level.points.size());
        client.level=null;emit.invoke(null,ship);
    }
    @Test void decreasedMinimalAndMultipleShipsRespectSettingsAndOneSharedCap()throws Exception {
        Client client=Client.INSTANCE;client.level=new World();client.options.value=ParticleStatus.DECREASED;
        var emit=load().getMethod("emit",Actor.class);
        emit.invoke(null,new Actor(client.level));assertEquals(14,client.level.points.size());
        emit.invoke(null,new Actor(client.level));assertEquals(20,client.level.points.size());
        emit.invoke(null,new Actor(client.level));assertEquals(20,client.level.points.size());
        client.level=new World();client.options.value=ParticleStatus.MINIMAL;
        emit.invoke(null,new Actor(client.level));assertEquals(5,client.level.points.size());assertEquals(0,client.level.flames);
        emit.invoke(null,new Actor(client.level));assertEquals(8,client.level.points.size());
    }
    public static class Actor {
        public World world;public boolean removed,wreck=true;public int tickCount;
        Actor(World w){world=w;}public World level(){return world;}public boolean isRemoved(){return removed;}
        public boolean isWreck(){return wreck;}public int getId(){return 0;}public float getYRot(){return 37;}
        public float getXRot(){return 12;}public float getRoll(){return -9;}public Vec3 position(){return new Vec3(0,84,0);}
        public static V v(Vec3 p){return new V(p.x,p.y,p.z);}
    }
    public static class World {
        public long now;public int flames;public final List<Vec3> points=new ArrayList<>();public long getGameTime(){return now;}
        public void addParticle(Cue cue,boolean force,double x,double y,double z,double dx,double dy,double dz){
            assertTrue(force,"bounded custom policy must bypass vanilla 32-block discard");assertTrue(dy>0);
            points.add(new Vec3(x,y,z));if(cue==Particles.FLAME)flames++;
        }
    }
    public static class Client {
        static final Client INSTANCE=new Client();public World level;public final Options options=new Options();public final GameRenderer gameRenderer=new GameRenderer();
        public static Client getInstance(){return INSTANCE;}
    }
    public static class Options {public ParticleStatus value=ParticleStatus.ALL;public Option particles(){return new Option(value);}}
    public record Option(Object value){public Object get(){return value;}}
    public static class GameRenderer {public final Camera camera=new Camera();public Camera getMainCamera(){return camera;}}
    public static class Camera {public Vec3 point=new Vec3(90,84,0);public Vec3 getPosition(){return point;}}
    public static class Cue { }
    public static class Particles {public static final Cue FLAME=new Cue(),LARGE_SMOKE=new Cue();}
    private static Class<?> load()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/boss/client/MothershipWreckParticles",target="com/liucodex/doggiesnewvehicle/boss/ExecutedWreckParticles";
        Map<String,String> map=new HashMap<>();map.put(source,target);
        for(String name:List.of("com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity","com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity","net/minecraft/world/entity/Entity"))map.put(name,Type.getInternalName(Actor.class));
        for(String name:List.of("net/minecraft/world/level/Level","net/minecraft/client/multiplayer/ClientLevel"))map.put(name,Type.getInternalName(World.class));
        map.put("net/minecraft/client/Minecraft",Type.getInternalName(Client.class));map.put("net/minecraft/client/Options",Type.getInternalName(Options.class));
        map.put("net/minecraft/client/OptionInstance",Type.getInternalName(Option.class));map.put("net/minecraft/client/renderer/GameRenderer",Type.getInternalName(GameRenderer.class));
        map.put("net/minecraft/client/Camera",Type.getInternalName(Camera.class));map.put("net/minecraft/core/particles/ParticleTypes",Type.getInternalName(Particles.class));
        for(String name:List.of("net/minecraft/core/particles/SimpleParticleType","net/minecraft/core/particles/ParticleOptions"))map.put(name,Type.getInternalName(Cue.class));
        byte[] bytes;try(var in=MothershipWreckParticlesExecutionTest.class.getClassLoader().getResourceAsStream(source+".class")){
            assertNotNull(in);var writer=new ClassWriter(0);new ClassReader(in).accept(new ClassRemapper(writer,new SimpleRemapper(map)),0);bytes=writer.toByteArray();
        }
        return new ClassLoader(MothershipWreckParticlesExecutionTest.class.getClassLoader()){
            Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}
